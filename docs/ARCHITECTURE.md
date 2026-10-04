# ArchMorph architecture

This document describes the system as implemented. For the state of the code before the rework and the
reasons behind the changes see [ARCHITECTURE_AUDIT.md](ARCHITECTURE_AUDIT.md); for the planner and rewriter
in depth see [TRANSFORMATION_ENGINE.md](TRANSFORMATION_ENGINE.md); for security see
[THREAT_MODEL.md](THREAT_MODEL.md).

## 1. Overview

```
            ┌──────────────── frontend (React, Vite) ────────────────┐
            │ upload · overview · graph · modules · plan · diff · validation │
            └───────────────────────────┬────────────────────────────┘
                                        │ /api/v1 (JSON envelope, polling)
┌───────────────────────────────────────┴───────────────────────────────────────┐
│ security: RequestIdFilter → RateLimitFilter → Spring Security (headers, CORS)  │
│ api: ProjectController → ProjectApiService → ApiMapper (DTOs only)             │
│ job: JobManager (bounded executor, per-client quota) · InMemoryJobStore        │
│ project: ProjectWorkflow · ProjectSession · InMemoryProjectSessionStore        │
│ pipeline: ProjectAnalyzer (deadline, progress events)                          │
│ ─────────────────────────────────────────────────────────────────────────────  │
│ upload · workspace · parser · analysis.{scanner,model,registry,dependency,     │
│ graph,architecture,cycle,module,transformation,validation} · report            │
└───────────────────────────────────────────────────────────────────────────────┘
          cli: CliRunner (same ProjectWorkflow, non-web context, temp workspace)
```

ArchMorph is one Spring Boot 4 application (Java 21). There is no database: sessions and jobs live in
memory, files live in per-project workspaces that expire. The same `ProjectWorkflow` serves the REST API
and the CLI, so both produce identical plans and reports.

## 2. Pipeline and data model

| Phase | Component | Output (structured) |
|---|---|---|
| Upload validation | `ProjectValidator` (extension, size, ZIP magic bytes) | accepted file or `ArchMorphException` |
| Storage | `ArchiveStorageServiceImpl` | `input/archive.zip` |
| Extraction | `SecureZipExtractor` | `original/`, `ExtractionReport` |
| Structure | `ProjectStructureDetector` | `ProjectStructure` (build tool, Spring Boot, modules, warnings) |
| Scanning | `SourceScanner` | `SourceFile`s with `SourceScope` MAIN/TEST |
| Parsing | `JavaParserService`, `ComponentAnalyzer` | `ParsedSource`, `ClassMetadata` (top-level + nested) |
| Classification | `ComponentClassifier` | `ComponentClassification` (type, confidence, evidence) |
| Model | `ProjectModelBuilder` | `ProjectModel`, `ProjectClassRegistry` (FQN keyed, per analysis) |
| Dependencies | `DependencyGraphBuilder` + 9 extractors | `DependencyGraph`, `ResolutionStatistics` |
| Architecture | `DefaultArchitectureAnalyzer` | `ArchitectureReport` (counts, violations, `ClassMetric`s) |
| Cycles | `DefaultCircularDependencyDetector` | `CycleReport` of `DependencyCycle`s |
| Modules | `DefaultBusinessModuleExtractor` → `DefaultModuleOptimizer` → `ModuleMetricsCalculator` | `ModuleDiscoveryReport` |
| User edits | `ModuleEditService` | final `ModuleDiscoveryReport` |
| Target | `ModularByDomainArchitecture` (`TargetArchitecture`) | `TargetPlacement` per class |
| Mapping | `DefaultTransformationMappingEngine` | `TransformationMappingReport` (complete class map) |
| Plan | `DefaultTransformationPlanner` | `TransformationPlan` (+ fingerprint) |
| Rewrite / execute | `SourceRewriter`, `DefaultTransformationEngine` | `transformed/`, `TransformationResult` |
| Validation | `ValidationEngine` + 7 `LevelValidator`s | `ValidationReport` |
| Reports | `ReportService`, `MarkdownReports` | `reports/*.json`, `reports/*.md` |

The original domain abstractions were kept and extended: `ComponentType`, `ClassMetadata`,
`ProjectClassInfo`, `ProjectClassRegistry`, `DependencyNode/Edge/Graph/Type`, `ArchitectureReport`,
`CycleReport`, `ModuleInfo`, `ModuleDiscoveryReport`, `ModuleLayout`, `TransformationMapping(+Report)`,
`TransformationPlan(+Entry)`.

### Workspace layout

```
workspace/projects/<uuid>/
├── input/archive.zip     the upload (never modified)
├── original/             extracted sources (never modified)
├── transformed/          generated project
├── dry-run/              scratch space for in-memory checks
├── reports/              analysis.json · modules.json · transformation-plan.json · validation.json · *.md
├── metadata/
└── build-home/           isolated HOME for the sandboxed Maven build
```

## 3. Algorithms

### 3.1 Parsing and classification

JavaParser runs at Java 21 language level in lenient mode; files are read as UTF-8 with an ISO-8859-1
fallback and a 2 MB size cap. A file that does not parse becomes a *parse problem* (reported, kept in place)
rather than an analysis failure.

`ComponentClassifier` evaluates evidence layers and keeps the strongest; agreeing layers add 0.04, a
conflicting layer subtracts 0.08:

| Layer | Confidence | Examples |
|---|---|---|
| stereotype annotation | 0.95 | `@RestController`, `@Service`, `@Repository`, `@Entity`, `@Configuration` |
| framework inheritance | 0.90 | `extends JpaRepository`, `implements UserDetailsService`, `OncePerRequestFilter` |
| structure | 0.78 | `main` + `@SpringBootApplication` → APPLICATION, records/fields-only → DTO |
| generic annotation | 0.70 | `@Component`, `@ControllerAdvice` |
| naming | 0.65 | `*Controller`, `*Service`, `*Repository`, `*Dto`, `*Exception` |
| package | 0.50 | `..controller..`, `..repository..` |

### 3.2 Type resolution and dependencies

`TypeResolver` follows Java scoping: fully-qualified name → type declared in the same file (including
nested) → single-type import → same package → wildcard import → unique simple-name fallback (low confidence,
counted in `ResolutionStatistics`). Identity is always the FQN, so `com.a.User` and `com.b.User` never merge.
When enabled, the JavaParser symbol solver is used as a fallback for method-call targets.

Edge weights used by the affinity model: constructor/field/entity relationship 1.0, inheritance and
implementation 0.9, method invocation 0.8, object creation and generic 0.7, method parameter/return 0.6,
annotation and plain type reference 0.4. Each edge stores occurrences, up to five source locations and a
confidence. The graph is adjacency-indexed (outgoing and incoming maps), so traversal is O(V + E).

### 3.3 Architecture metrics and cycles

* Layer rules (`DefaultLayerRules`) mark e.g. controller → repository or repository → service with severity
  and rationale; every violation keeps its source location.
* Per class: afferent coupling Ca, efferent coupling Ce, instability I = Ce / (Ca + Ce).
* Cycles: iterative Tarjan SCC. Each non-trivial component is reported once with a closed representative
  path, the dependency kinds involved, a severity — LOW for entity/data-only cycles (fields, generics, entity
  relationships, getters), HIGH for cycles of four or more classes or against the layer direction, MEDIUM
  otherwise — and a recommendation. Cycles are never rewritten automatically.

All of these are labelled as static-analysis **indicators**, not quality verdicts.

### 3.4 Module discovery

1. **Fixed roles**: application entry point, configuration, security and exception handlers are placed in
   `application` / `shared` and never join a business module.
2. **Domain clusters**: each class's domain stem (`UserController`, `UserService`, `UserDto` → `user`) forms
   a cluster; generic names (`Base`, `Common`, `Util`, `Helper`, `AppConstants` …) have no stem. Clusters whose
   stem extends another (`OrderItem` → `order`) merge when the base is anchored by an entity, controller or
   service.
3. **Ambiguous classes** are placed with the affinity model:

   ```
   affinity(A,B) = wD·dependency + wN·naming + wP·package + wE·entity + wC·endpoint + wT·typeUsage
                   − crossDomainPenalty · differentDomain
   ```
   (dependency = saturating w/(w+1) of edge weights, layer-direction edges full, reverse edges half; naming =
   stem token similarity; package = similarity of non-layer segments; entity = direct relationship else
   Jaccard of touched entities; endpoint = request path vs stem; typeUsage = Jaccard of neighbours. Default
   weights 0.30/0.20/0.10/0.20/0.10/0.10, normalised.) Used by several modules → `shared`; by one → that module;
   otherwise `shared` with a warning. A class is assigned exactly once (`ModuleDiscoveryReport.assign`
   rejects duplicates).
4. **Optimizer** (locked classes never move):
   * a base class or interface extended/implemented by classes of two or more modules (`BaseEntity`,
     `Person` for `Owner` and `Vet`) moves to `shared`, unless it depends on business code;
   * a module is anchored by a controller, or by an entity together with a service, repository or
     controller; an unanchored module with ≤ 3 classes merges into the module owning ≥ 60 % of its
     dependencies (`Role` → `user`), goes to `shared` if several modules use it and it depends on nothing
     module-specific (an error-response DTO), or to `shared` with a warning if it is isolated;
   * generic-named classes used by ≥ `shared-usage-threshold` modules are promoted to `shared`;
   * shared code that depends on modules but that no module uses becomes **application wiring** and is
     placed in the root package (a global `@RestControllerAdvice` handling module exceptions);
   * warnings: singleton, oversized, low cohesion, high coupling, facades reaching into ≥ 3 modules.

   Class-name stems ignore technology prefixes (`Jdbc`, `Jpa`, `SpringData`, `Mongo`, …) and API version
   suffixes (`V1`, `V2`), and role suffixes match longest first (`RowMapper` before `Mapper`).
5. **Metrics** (`ModuleMetricsCalculator`):
   ```
   cohesion         = internal / (internal + crossing)
   externalCoupling = edges to other business modules / all edges touching the module
   confidence       = 0.35·cohesion + 0.30·nameAgreement + 0.20·stackCompleteness + 0.15·(1 − externalCoupling)
   ```
   plus warnings for singleton, oversized, low-cohesion and high-coupling modules.

Module names come from `DefaultModuleNamingStrategy` (lower-case Java identifiers, reserved names rejected).
`ModuleNamingAssistant` is an extension point; the default is a no-op, no external AI service is called.

### 3.5 User edits

`ModuleEditService` replays an ordered list of decisions on top of the suggestion:
`RENAME_MODULE`, `MERGE_MODULES`, `SPLIT_MODULE`, `MOVE_CLASS`, `MOVE_TO_SHARED`, `EXCLUDE_CLASS` /
`INCLUDE_CLASS` (excluded classes keep their package), `LOCK_CLASS` / `UNLOCK_CLASS`. Invalid decisions are
rejected with `INVALID_MODULE_OPERATION`; nothing is partially applied. Suggestion, decisions and final
modules are all exposed so the UI can show them side by side. Every accepted edit re-plans.

### 3.6 Planning, rewriting, validation

See [TRANSFORMATION_ENGINE.md](TRANSFORMATION_ENGINE.md). Validation levels, in order:

| # | Level | Checks |
|---|---|---|
| 1 | Filesystem | every planned file at its target, no unplanned Java file, no shared targets, every non-Java file preserved |
| 2 | Java parsing | every transformed file parses (files already broken in the original are warnings) |
| 3 | Package consistency | package declaration = directory |
| 4 | Import resolution | project imports resolve to existing classes; no import refers to a moved class's old location |
| 5 | Dependency graph | graph rebuilt from the output preserves every original dependency (through the class map) and declares the same classes |
| 6 | Architecture rules | moved classes under their module package, shared under `shared`; for `MODULAR_MONOLITH` every cross-module dependency must target the other module's API package (error otherwise); module-level cycles and shared → module dependencies as warnings |
| 7 | Build | allowlisted `mvn -B -q -DskipTests test-compile` (or `test`) on a scratch copy, in the sandbox; skipped when disabled |

## 4. Jobs, sessions and progress

* `JobManager` runs ANALYZE / TRANSFORM / VALIDATE jobs on a bounded pool (`worker-threads`,
  `max-queued-jobs`) and enforces `max-active-jobs-per-client`. Each job records timestamped
  `ProgressEvent`s: `UPLOAD_COMPLETE`, `EXTRACTION_COMPLETE`, `STRUCTURE_DETECTED`, `PARSING_STARTED`,
  `PARSING_COMPLETE`, `DEPENDENCY_ANALYSIS_COMPLETE`, `ARCHITECTURE_ANALYSIS_COMPLETE`,
  `MODULE_DISCOVERY_COMPLETE`, `PLAN_CREATED`, `TRANSFORMATION_STARTED`, `TRANSFORMATION_COMPLETE`,
  `VALIDATION_STARTED`, `VALIDATION_COMPLETE`, `REPORTS_GENERATED`. The same events go to the log (SLF4J,
  with request ID in the MDC).
* `ProjectSession` holds the analysis, decisions, plan, transformation and validation state behind a lock;
  project status is `QUEUED → ANALYZING → READY_FOR_REVIEW → TRANSFORMING → VALIDATING → COMPLETED`
  (or `FAILED` with a safe `ErrorInfo`).
* Analysis has a `Deadline` (`archmorph.analysis.timeout`) checked between phases.
* The UI polls `GET /jobs/{id}` through a `ProgressSource` interface; an SSE implementation can replace
  `PollingProgressSource` without touching components.
* `RetentionService` deletes expired workspaces and sessions on a schedule.

## 5. Web layer

* `/api/v1` controllers return `ApiResponse<T>` envelopes built from DTOs (`api.dto.*`) by `ApiMapper`.
  Internal objects, absolute paths and exception text never leave the server; paths in DTOs are
  project-relative.
* `GlobalExceptionHandler` maps `ArchMorphException` (`ErrorCode` + safe message + hint) to status codes
  and turns everything else into a generic 500 with the request ID.
* `ProjectAccessPolicy` (`LocalModeAccessPolicy`) is the single place that decides whether a client may see a
  project — the seam for authentication.
* `SpaForwardController` serves the bundled UI for client-side routes when built with `-Pui`.

## 6. Frontend

React 19 + TypeScript (strict) + Vite + Tailwind 4 + React Router 7; React Flow + dagre for the graph,
`diff` for side-by-side diffs, lucide icons. `ProjectProvider` polls the project and active job and bumps a
version counter that views use as their reload key. All server text is rendered as text (no
`dangerouslySetInnerHTML`); the build is compatible with the strict CSP (no inline scripts or style
elements). Heavy views are code-split.

## 7. Extension points

| Interface | Default | Purpose |
|---|---|---|
| `TargetArchitecture` | `ModularMonolithArchitecture` (default), `ModularByDomainArchitecture` | other target layouts (e.g. hexagonal) |
| `BusinessModuleExtractor`, `ModuleOptimizer` | defaults | alternative discovery strategies |
| `ModuleNamingAssistant` | no-op | optional naming suggestions |
| `LevelValidator` | 7 levels | extra validation |
| `ProjectSessionStore`, `JobStore` | in-memory | persistence |
| `ProjectAccessPolicy` | local mode | authentication / ownership |
| `ProgressSource` (frontend) | polling | SSE / WebSocket |
