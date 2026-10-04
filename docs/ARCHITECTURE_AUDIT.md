# ArchMorph — Architecture Audit

This audit records the state of the repository **before** the completion work
(commit `df20a5d`), and the decisions taken as a result. It is written for
contributors who want to understand why the code looks the way it does.

## 1. Architecture as found

A single Spring Boot 4.1 / Java 21 application (`com.anvith.archmorph`).

```
UploadController  POST /api/projects/upload
      │  (one synchronous request runs everything)
      ▼
ProjectUploadServiceImpl  (~350 lines, System.out reporting)
  ├─ ProjectValidator          extension + first-entry check
  ├─ WorkspaceManager          workspace/projects/<PROJECT-xxxxxxxx>/…
  ├─ ArchiveStorageService     copies upload to workspace/archives/
  ├─ ZipExtractionService      java.util.zip extraction
  ├─ ProjectStructureDetector  first pom.xml found by Files.walk
  ├─ SourceScanner             src/main/java/**/*.java
  ├─ JavaParserService         plain JavaParser (no symbol solver configured)
  ├─ ComponentAnalyzer         first ClassOrInterfaceDeclaration only
  ├─ ProjectClassCollector → DefaultProjectClassRegistry (singleton map)
  ├─ DependencyGraphBuilder    field / constructor / method-parameter extractors
  ├─ DefaultArchitectureAnalyzer → LayerAnalyzer, LayerViolationDetector
  ├─ DefaultCircularDependencyDetector (DFS back-edges)
  ├─ DefaultModuleDiscoveryEngine → DefaultBusinessModuleExtractor
  ├─ DefaultModuleOptimizer    (empty)
  ├─ DefaultTransformationEngine (prints mappings only)
  └─ DefaultLayoutPlanner      (folder grouping, printed)
```

## 2. Implemented components (worked as designed)

| Component | Notes |
|---|---|
| `JavaParserService` | Parses one file; throws on any syntax error. |
| `ComponentAnalyzer` | Annotation → inheritance → interface → naming → package cascade. Good foundation. |
| `DependencyGraph`, `DependencyNode`, `DependencyEdge`, `DependencyType` | Clear model; set-based dedup of edges. |
| `LayerRuleRegistry` / `DefaultLayerRules` | Small explicit rule matrix. |
| `DefaultBasePackageResolver` | Correct segment-wise common prefix. |
| `DefaultPackagePlanner`, `DefaultFolderClassifier`, `TransformationMapping*` | Correct shape of a mapping, but driven by wrong module data. |
| `PlantUmlDiagramGenerator` | Simple edge list. |

## 3. Partially implemented

* **Dependency extraction** – only fields, constructor params and method
  params, extracted with `compilationUnit.findAll(...)` (so nested-class
  dependencies are attributed to the outer class) and resolved by **simple
  name string**.
* **Architecture report** – component counts and string violations only; no
  metrics.
* **Cycle detection** – reports one list per back edge; the same cycle can be
  reported several times; no severity or edge types.
* **Transformation** – mappings are computed and printed; no files are
  written, no packages or imports are rewritten.
* **Upload validation** – extension and "has a first entry" only.

## 4. Dead / unused abstractions

* `ModuleClassifier` / `NamingHeuristicClassifier` – never injected.
  (Also buggy: it strips `Config` before `Configuration`, leaving `uration`.)
* `ConnectedComponentFinder`, `ModuleNamingStrategy` – interfaces without
  implementations.
* `TransformationPlanner` / `DefaultTransformationPlanner` – returns an empty plan.
* `ModuleOptimizer` / `DefaultModuleOptimizer` – returns its input.
* `DiagramGenerator` – never called.
* `FileUtil`, `ZipUtil` – empty classes.
* `WorkspaceConstants.WORKING/ANALYSIS/OUTPUT/TEMP/LOGS/CACHE` – created but unused.
* Dependencies `jgrapht`, `guava`, `commons-io`, `commons-lang3`,
  `jackson-dataformat-yaml` – not imported anywhere.

## 5. Incorrect assumptions

1. **Class identity = simple name.** `DependencyNode.equals` and the registry key
   on `className`, so `user.dto.UserResponse` and `admin.dto.UserResponse` are
   the same node and one silently overwrites the other.
2. **One class per file.** `ComponentAnalyzer` uses `findFirst`, so secondary
   top-level types, enums, records, interfaces after the first class, and
   nested types are invisible.
3. **Module = everything reachable from a controller.** The traversal is
   undirected, so in any connected application every controller "module"
   contains the *entire* component — every class is duplicated into every
   module.
4. **Generic type = last type argument.** `normalizeType("Map<User, Order>")`
   returns `Order`; `List<Map<String,User>>` returns `Map<String,User>`.
5. **The first `pom.xml` found is the root.** `Files.walk` order is not
   guaranteed; a nested module pom can win.
6. **README accuracy.** The README describes transformation and validation that
   were not implemented.

## 6. Known bugs

| # | Bug | Impact |
|---|---|---|
| B1 | Zip Slip: `destination.resolve(entry.getName()).normalize()` is never checked against `destination`. | Arbitrary file write on the host. **Critical.** |
| B2 | No decompression-bomb, entry-count or entry-size limits. | Disk/CPU exhaustion. |
| B3 | `DefaultProjectClassRegistry` is a Spring singleton cleared at the start of each upload. | Concurrent uploads corrupt each other's analysis. |
| B4 | `SourceScanner.java` declares package `analysis.scanner` but lives in `parser/`. | Confusing; breaks tooling. |
| B5 | Every source file is parsed twice. | 2× cost. |
| B6 | `DependencyGraph.getOutgoingEdges/getIncomingEdges` and DFS scan the full edge set per step. | O(V·E) traversal. |
| B7 | `GlobalExceptionHandler` returns raw exception messages; anything unhandled falls through to Spring's default error page. | Information leakage. |
| B8 | `UploadResponse.projectName` echoes `getOriginalFilename()` unchanged. | Reflected untrusted content. |
| B9 | Project IDs are 8 hex chars. | Guessable / collision-prone identifiers. |
| B10 | `hs_err_pid*.log` JVM crash dumps are committed. | Leaks host details. |

## 7. Transformation pipeline gaps

* No complete `old FQN → new FQN` map, so imports could not be rewritten deterministically.
* No collision detection (two classes → same target path).
* No handling of shared/infrastructure classes (they were duplicated into modules).
* No source rewriting (package, imports, qualified names, static imports, same-package references).
* No test-source scope, no resource preservation.
* No dry run, no diffs, no reviewable plan.
* No validation of the generated project.

## 8. Security weaknesses

Zip Slip (B1), zip bombs (B2), untrusted filename reuse (B8), predictable IDs
(B9), error leakage (B7), no CORS policy, no security headers, no rate limiting,
unbounded synchronous work in a request thread, 500 MB multipart limit, no
retention / cleanup of uploaded source code.

## 9. Testing gaps

Only `contextLoads()` existed. No fixtures, no unit tests for parser,
classifier, graph, cycles, modules, planning, ZIP handling, or rewriting.

## 10. Final architecture (implemented by this change)

Incremental evolution: the original packages and domain types were kept and
extended. See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the full picture.

* `upload` – secure upload + `SecureZipExtractor` (limits, Zip Slip, symlinks, ignored dirs).
* `workspace` – `WorkspaceManager` with validated per-project paths.
* `parser` – `ClassMetadata` now describes every top-level and nested type;
  `ComponentClassifier` returns type + confidence + evidence.
* `analysis.registry` – registry keyed by fully-qualified name, created **per analysis**.
* `analysis.dependency` – import-aware `TypeResolver`, 9 extractors, edges with
  location/confidence/occurrences, adjacency-indexed graph.
* `analysis.architecture` – counts, structured violations, Ca/Ce/instability.
* `analysis.cycle` – Tarjan SCC, structured cycles with severity.
* `analysis.module` – signal-based affinity model, shared-class detection,
  optimizer, confidence scoring, user edit operations.
* `analysis.transformation` – target-architecture strategy, mapping engine with
  complete class map, deterministic planner with collisions and safety levels,
  JavaParser AST rewriter, executor, dry-run diffs.
* `analysis.validation` – seven-level validation engine incl. sandboxed Maven.
* `pipeline` / `job` / `project` – asynchronous job model and in-memory stores.
* `api` – versioned REST API with DTOs; `security` – headers, CORS, rate limiting.
* `frontend/` – React + TypeScript + Tailwind review UI.
