# ArchMorph

**Static-analysis-driven migration of layered Spring Boot applications to modular monoliths — with a human in the loop.**

ArchMorph reads a Maven / Spring Boot project from a ZIP archive, reconstructs its architecture from the
Java AST, proposes business modules, and produces a reviewable, deterministic transformation plan. After you
review and adjust the plan, it rewrites packages, imports and qualified references with an AST-located
rewriter, validates the result on seven levels (up to a sandboxed Maven compile), and gives you the
transformed project plus JSON and Markdown reports.

```
com.demo.controller.OrderController        com.demo.modules.order.controller.OrderController
com.demo.service.OrderService        ──▶    com.demo.modules.order.service.OrderService
com.demo.repository.OrderRepository         com.demo.modules.order.repository.OrderRepository
com.demo.common.ApiResponse                 com.demo.shared.common.ApiResponse
```

> **What ArchMorph is not.** It does not migrate an architecture fully automatically and it does not prove
> that behaviour is preserved. Module boundaries are a design decision; ArchMorph gives you evidence,
> confidence indicators and a safe mechanical transformation, and marks everything it cannot do safely as
> *manual review required*. Run your own test suite on the result.

---

## Contents

- [Status](#status)
- [How it works](#how-it-works)
- [Quick start](#quick-start)
- [Using the UI](#using-the-ui)
- [Command line](#command-line)
- [REST API](#rest-api)
- [Configuration](#configuration)
- [Security model](#security-model)
- [Testing](#testing)
- [Known limitations](#known-limitations)
- [Project layout](#project-layout)
- [Documentation](#documentation)

## Status

Working end to end for Maven projects with the conventional layered structure
(`controller` / `service` / `repository` / `entity` / `dto` …), including multi-package, nested-type,
generic, default-package and test-source cases covered by the fixture suite. Experimental for anything else.
Treat every result as a proposal to review.

| Area | State |
|---|---|
| Upload, safe extraction, workspace isolation | implemented, tested against Zip Slip, bombs, symlinks |
| Parsing, classification with confidence + evidence | implemented (JavaParser, lenient, 13 component types) |
| Semantic dependency graph (12 dependency kinds) | implemented, import-aware type resolution |
| Layer rules, metrics, cycles (Tarjan) | implemented |
| Module discovery, optimizer, user edits | implemented; heuristic by nature |
| Deterministic planner (collisions, safety levels, demotion) | implemented |
| AST-located source rewriter | implemented, golden-tested |
| 7-level validation incl. sandboxed Maven | implemented |
| Async jobs, REST API, rate limiting, security headers | implemented, single instance, in memory |
| React review UI | implemented |
| Gradle, Kotlin, multi-module Maven builds | **not supported** (detected and reported) |
| Authentication / multi-user | structure prepared, **not implemented** (local mode) |

## How it works

```
ZIP ─▶ secure validation ─▶ isolated workspace ─▶ safe extraction ─▶ structure detection
    ─▶ scanning ─▶ AST parsing ─▶ class registry ─▶ semantic dependencies ─▶ graph
    ─▶ architecture analysis ─▶ cycles ─▶ module discovery ─▶ optimization ─▶ confidence
    ─▶ target plan ─▶ transformation plan ─▶ (review & edit) ─▶ AST rewrite ─▶ generated project
    ─▶ 7-level validation ─▶ reports ─▶ download
```

Every phase returns a structured result; nothing in the pipeline communicates through console output.

* **Parsing.** JavaParser 3.27 (Java 21 language level, lenient). Every top-level *and nested* type becomes
  `ClassMetadata` with fields, methods, annotations, generics, imports, risk flags (reflection, string class
  names, package-private API) and a source scope (main/test).
* **Classification.** Layered evidence: stereotype annotations (0.95), framework inheritance (0.90),
  structure (0.78), generic annotations (0.70), naming (0.65), package (0.50); agreement raises confidence,
  conflicts lower it. Every classification carries its evidence list.
* **Dependencies.** An import-aware resolver (FQN → same file → single import → same package → wildcard →
  unique simple name, flagged low-confidence) feeds nine extractors producing `CONSTRUCTOR`, `FIELD`,
  `METHOD_PARAMETER`, `METHOD_RETURN`, `METHOD_INVOCATION`, `OBJECT_CREATION`, `INHERITANCE`,
  `IMPLEMENTATION`, `ANNOTATION`, `GENERIC`, `ENTITY_RELATIONSHIP` and `TYPE_REFERENCE` edges, each with
  source location, occurrence count and confidence.
* **Analysis.** Layer rules with severity and rationale, Ca/Ce/instability per class, package style,
  Tarjan strongly-connected components for cycles with severity and a recommendation.
* **Modules.** A documented affinity model combines dependency strength, naming, package, entity,
  endpoint and type-usage signals (weights configurable). Fixed roles (application, configuration,
  security, exception handlers) never join a business module. Code used by several modules goes to
  `shared` exactly once — **never duplicated**. An optimizer merges fragments and emits warnings
  (singleton, oversized, low cohesion, high coupling).
* **Planning.** One entry per file with actions (`MOVE`, `REWRITE_PACKAGE`, `REWRITE_IMPORT`,
  `REWRITE_QUALIFIED_REFERENCE`, `KEEP`, `EXCLUDE`, `MANUAL_REVIEW`), safety (`SAFE`, `SAFE_WITH_WARNING`,
  `MANUAL_REVIEW`, `UNSUPPORTED`) and risk. Path and class-name collisions, package-private access across
  new package boundaries, default-package dependencies and leaving the component-scan root all demote a
  move to *keep in place* — which is always compilable because every class keeps a unique FQN.
* **Rewriting.** JavaParser decides *what* changes, AST node ranges decide *where*; edits are applied to
  the original text so formatting, comments and line endings are preserved. See
  [docs/TRANSFORMATION_ENGINE.md](docs/TRANSFORMATION_ENGINE.md).
* **Validation.** Filesystem → Java parsing → package consistency → import resolution → dependency graph
  preservation → architecture rules → Maven build (allowlisted command, sandboxed child process).

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Quick start

Requirements: **JDK 21**, **Maven 3.9+** (or the bundled `./mvnw`), **Node.js ≥ 20.19** with npm for the UI.
Build validation additionally needs `mvn` on the `PATH` of the server.

### Backend (API on http://localhost:8080)

```bash
./mvnw spring-boot:run
```

### Frontend (dev server on http://localhost:5173, proxies `/api` to the backend)

```bash
cd frontend
npm ci
npm run dev
```

Set `ARCHMORPH_BACKEND=http://host:port` to proxy to a backend elsewhere.

### Single process (UI bundled into the jar)

```bash
(cd frontend && npm ci && npm run build)
./mvnw -Pui -DskipTests package
java -jar target/archmorph-ai-0.0.1-SNAPSHOT.jar      # UI and API on http://localhost:8080
```

### Try it with a fixture

```bash
cd src/test/resources/fixtures/spring-layered && zip -qr /tmp/spring-layered.zip . -x expected.json && cd -
```

Upload `/tmp/spring-layered.zip` in the UI (*New analysis*), or use the CLI / API below.

## Using the UI

| Route | Screen |
|---|---|
| `/` | Landing page |
| `/projects/new` | Upload with client-side checks and progress |
| `/projects/:id` | Overview: metrics, indicators, job progress, cycles, violations, downloads |
| `/projects/:id/architecture` | Current layers and packages vs. proposed modular layout |
| `/projects/:id/dependencies` | Interactive graph with type / cross-module / cycle / violation filters |
| `/projects/:id/modules` | Module review and editor (drag and drop or keyboard "Move to"; rename, merge, split, shared, exclude, lock; undo) |
| `/projects/:id/plan` | Transformation table, conflicts, warnings, resource findings, dry run, transform |
| `/projects/:id/diff` | Side-by-side diff per file |
| `/projects/:id/validation` | Seven validation levels, build output, downloads |

## Command line

```bash
./mvnw -DskipTests package
bin/archmorph analyze   project.zip [--report-dir DIR]
bin/archmorph plan      project.zip [--report-dir DIR]
bin/archmorph transform project.zip --output transformed.zip [--report-dir DIR] [--no-build]
```

Exit codes: `0` success, `1` failure (e.g. the archive was rejected), `2` usage error,
`3` the project was transformed but validation failed (the output and reports are still written). The CLI uses a temporary workspace that is deleted on exit.

## REST API

Versioned under `/api/v1`, JSON envelope
`{success, message, data, errorCode, hint, timestamp, requestId}`. Long work runs as jobs (`202 Accepted`
+ `jobId`). Full reference: [docs/API.md](docs/API.md).

```bash
H=http://localhost:8080/api/v1
curl -s -F file=@/tmp/spring-layered.zip $H/projects            # → {projectId, jobId}
curl -s $H/jobs/$JOB                                             # poll until COMPLETED
curl -s $H/projects/$P/modules
curl -s -X PUT -H 'Content-Type: application/json' \
     -d '{"edits":[{"type":"RENAME_MODULE","module":"payment","newName":"billing"}]}' $H/projects/$P/modules
curl -s -X POST "$H/projects/$P/transform?dryRun=true"           # in memory, nothing written
curl -s -X POST $H/projects/$P/transform                         # job: transform + validate
curl -s $H/projects/$P/validation
curl -s -o transformed.zip $H/projects/$P/download
curl -s $H/projects/$P/reports/transformation-summary.md
```

## Configuration

All settings are typed (`ArchMorphProperties`) and live under `archmorph.*` in
`src/main/resources/application.properties`; override them with `--archmorph.x=y` or environment
variables (`ARCHMORPH_UPLOAD_MAXARCHIVESIZE=50MB` — Spring relaxed binding drops the dashes).

| Key | Default | Meaning |
|---|---|---|
| `archmorph.upload.max-archive-size` | `100MB` | upload limit (also the multipart limit) |
| `archmorph.upload.max-uncompressed-size` / `max-entry-count` / `max-single-entry-size` | `500MB` / `20000` / `50MB` | extraction limits, enforced on bytes written |
| `archmorph.upload.max-compression-ratio` | `200` | zip-bomb guard |
| `archmorph.workspace.root` / `retention` | `./workspace` / `PT24H` | where projects live and how long |
| `archmorph.analysis.max-java-files` / `timeout` | `10000` / `PT5M` | analysis limits |
| `archmorph.module-discovery.*-weight` | see file | affinity weights (normalised) |
| `archmorph.transformation.modules-package` / `shared-package` | `modules` / `shared` | target layout names |
| `archmorph.validation.build.enabled` | `true` | run the sandboxed Maven build level |
| `archmorph.validation.build.mode` | `COMPILE` | `COMPILE` (`test-compile`, tests skipped) or `TEST` |
| `archmorph.validation.build.offline` / `local-repository` | `false` / workspace | Maven offline mode and isolated repository |
| `archmorph.validation.build.timeout` | `PT4M` | hard wall-clock limit, process tree killed |
| `archmorph.security.allowed-origins` | `http://localhost:5173` | CORS allowlist (never `*`) |
| `archmorph.security.rate-limit.*` | enabled | per-client budgets for upload / expensive / download / general |
| `archmorph.jobs.worker-threads` / `max-queued-jobs` / `max-active-jobs-per-client` | `2` / `20` / `3` | job capacity |

**Build validation runs the project's Maven build**, which executes the plugins and annotation processors the
uploaded `pom.xml` declares. ArchMorph never runs `mvnw` or scripts from the upload, blocks command-runner
plugins, clears the environment and enforces a timeout — but this is not a security boundary. For untrusted
projects run ArchMorph in a disposable container or set `archmorph.validation.build.enabled=false`.
See [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md).

## Security model

Local, single-user mode by default (no login; designed so authentication can be added later through
`ProjectAccessPolicy` and Spring Security). Highlights:

* hardened extraction: Zip Slip, absolute paths, symlinks, NUL bytes, bombs, entry and size limits, `CREATE_NEW`
* server-generated UUIDs; every path resolved and verified inside its own workspace; workspaces expire
* uploaded code is never executed except through the allowlisted, sandboxed Maven build
* no file-system paths, stack traces or internal class names in responses; generic 500s with a request ID
* strict CSP, `X-Frame-Options: DENY`, `nosniff`, no-referrer, permissions policy; configured CORS allowlist
* per-client rate limits and job quotas; uploaded source and secrets are never logged

## Testing

```bash
./mvnw test                         # backend: unit, golden, invariant, integration, API, CLI, E2E
cd frontend && npm test             # frontend: vitest + Testing Library
cd frontend && npm run typecheck && npm run build
./mvnw test -Dtest=SourceRewriterGoldenTest -Dgolden.update=true   # regenerate golden files (review the diff!)
```

* **13 fixture projects** (`src/test/resources/fixtures`) with `expected.json` expectations: layered,
  shared components, cycles, ambiguous modules, duplicate class names, nested classes, generics,
  multi-package, default package, malformed Java, security configuration, reflection.
* **Golden rewriter tests** (`src/test/resources/golden/rewriter`): wildcard and static imports, qualified
  references, nested classes, import ordering, CRLF, comments inside the import block, untouched files.
* **Invariant tests** on every fixture: no lost files, unique destinations, package = directory, no stale
  imports, originals untouched, deterministic output, transformed sources compile in-JVM.
* **End-to-end Maven test** (`EndToEndMavenTest`) runs the real sandboxed `mvn test-compile` on the
  transformed project; it runs offline against `~/.m2`, so the Spring Boot dependencies must already be in
  the local repository, and it is skipped when `mvn` is not on the `PATH`.

## Known limitations

* Maven only; Gradle, Kotlin and Groovy sources are detected and reported, not transformed. Multi-module
  Maven builds are analysed from the shallowest `pom.xml` and not restructured.
* Module discovery is heuristic. Naming conventions matter; projects without domain-named classes produce
  low-confidence suggestions that need editing.
* Strings are not code: reflection (`Class.forName("…")`), SpEL, `@ComponentScan` string packages,
  `application.properties`, XML and MyBatis mappers that mention moved names are **reported** (resource
  findings, risk flags) but not rewritten.
* Moving a class never fixes design problems: cycles and shared → module dependencies are reported only.
* Validation proves structural consistency and (optionally) compilation, not behavioural equivalence.
* State is in memory: restarting the server forgets projects (files are cleaned up by retention).
  One instance only; rate limits are per instance.
* Build validation is process-level isolation, not a sandbox boundary (see threat model).

## Project layout

```
src/main/java/com/anvith/archmorph
├── upload, workspace          secure upload, extraction, isolated workspaces
├── parser                     JavaParser, ClassMetadata, ComponentClassifier, structure detection
├── analysis
│   ├── scanner, model, registry
│   ├── dependency             TypeResolver, extractors, DependencyGraph(+Builder)
│   ├── architecture, cycle    layer rules, metrics, Tarjan cycles
│   ├── module                 affinity model, extractor, optimizer, editing, naming
│   ├── transformation         target architecture, mapping, planner, rewriter, engine, diffs
│   └── validation             7 levels, BuildPluginGuard, SandboxedMavenRunner
├── pipeline, project, job     ProjectAnalyzer, ProjectWorkflow, JobManager
├── report                     JSON + Markdown reports
├── api, security, cli         REST API + DTOs, headers/CORS/rate limiting, command line
frontend/                      React 19 + TypeScript + Vite + Tailwind review UI
docs/                          architecture, audit, threat model, transformation engine, API
```

## Documentation

* [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — components, data flow, algorithms
* [docs/TRANSFORMATION_ENGINE.md](docs/TRANSFORMATION_ENGINE.md) — planner, rewriter, guarantees and examples
* [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) — threats, mitigations, residual risk
* [docs/API.md](docs/API.md) — REST reference
* [docs/ARCHITECTURE_AUDIT.md](docs/ARCHITECTURE_AUDIT.md) — audit of the codebase before this rework

The development history of the earlier sprint-based prototype is available in the git history.
