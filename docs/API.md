# REST API

Base path `/api/v1`. All responses (except file downloads) use one envelope:

```json
{
  "success": true,
  "message": "Project",
  "data": { },
  "errorCode": null,
  "hint": null,
  "timestamp": "2026-10-04T10:45:43.445Z",
  "requestId": "28982603-ac9d-445b-915c-947ae053d8b2"
}
```

On failure `success` is `false`, `data` is absent, `errorCode` is a stable machine code and `hint` (when
present) tells the user what to do. Messages never contain file-system paths, stack traces or internal class
names. Every response carries `X-Request-Id`.

Identifiers are server-generated UUIDs; plan entries use `e-NNNN`. Malformed identifiers are rejected with
`400 INVALID_REQUEST`. Long-running operations return `202 Accepted` with a `jobId`; poll the job.

## Endpoints

| Method | Path | Purpose | Success |
|---|---|---|---|
| `POST` | `/projects` | upload a ZIP (`multipart/form-data`, field `file`) and queue analysis | `202` `{projectId, jobId, status}` |
| `GET` | `/projects/{projectId}` | project status, capabilities, failure, warnings | `200` |
| `DELETE` | `/projects/{projectId}` | delete the project and all its files now | `200` |
| `GET` | `/jobs/{jobId}` | job status and progress events | `200` |
| `POST` | `/projects/{projectId}/analyze` | re-run the analysis | `202` |
| `GET` | `/projects/{projectId}/analysis` | counts, indicators, classifications, violations, cycles, metrics, parse problems | `200` |
| `GET` | `/projects/{projectId}/dependencies?limit=5000` | dependency graph (nodes, edges, `truncated`) | `200` |
| `GET` | `/projects/{projectId}/architecture` | current layers/packages vs proposed layout | `200` |
| `GET` | `/projects/{projectId}/modules` | suggestion, user decisions, final modules | `200` |
| `PUT` | `/projects/{projectId}/modules` | replace the list of user decisions; re-plans | `200` modules |
| `PUT` | `/projects/{projectId}/strategy` | choose the target layout `{"strategy": "MODULAR_MONOLITH" \| "MODULAR_BY_DOMAIN"}`; re-plans | `200` plan |
| `GET` | `/projects/{projectId}/plan` | transformation plan | `200` |
| `GET` | `/projects/{projectId}/diff/{entryId}` | before/after and unified diff for one plan entry | `200` |
| `POST` | `/projects/{projectId}/transform?dryRun=true` | rewrite everything in memory; nothing written | `200` |
| `POST` | `/projects/{projectId}/transform` | transform, then validate (job) | `202` |
| `POST` | `/projects/{projectId}/validate` | re-run validation on the transformed project (job) | `202` |
| `GET` | `/projects/{projectId}/validation` | validation report | `200` |
| `GET` | `/projects/{projectId}/download` | transformed project as ZIP | `200 application/zip` |
| `GET` | `/projects/{projectId}/reports/{name}` | one report file | `200` |

The downloaded project contains `MODULES.md` (module map, public APIs, dependencies, Spring Modulith
verification test).

Report names: `analysis.json`, `analysis.md`, `modules.json`, `transformation-plan.json`,
`transformation-summary.md`, `validation.json`, `validation-report.md`.

`GET /actuator/health` returns the status (`UP`/`DOWN`) and probe groups, without component details.

## Lifecycle

```
POST /projects ──▶ QUEUED ──▶ ANALYZING ──▶ READY_FOR_REVIEW ──(PUT /modules)──▶ READY_FOR_REVIEW
                                                  │
                                    POST /transform ──▶ TRANSFORMING ──▶ VALIDATING ──▶ COMPLETED
                                                                                           │
                                                                     POST /validate ◀──────┘
any step ──▶ FAILED  (project.failure = {code, message, hint})
```

`capabilities` in the project tells a client what is possible now:
`canEditModules`, `canTransform`, `canDownload`, `hasPlan`, `hasValidation`, `manualReviewRequired`.
Calling an operation in the wrong state returns `409 INVALID_STATE`.

## Objects

### Project

```json
{
  "projectId": "74969849-a0d6-45e8-824b-5eca31f1e273",
  "name": "spring-layered",
  "status": "READY_FOR_REVIEW",
  "createdAt": "2026-10-04T10:45:40.321Z",
  "expiresAt": "2026-10-05T10:45:40.321Z",
  "archiveBytes": 16229,
  "capabilities": { "canEditModules": true, "canTransform": true, "canDownload": false,
                    "hasPlan": true, "hasValidation": false, "manualReviewRequired": false },
  "failure": null,
  "latestJobId": "b5131c16-db4a-4135-a5a1-8150b209bfa7",
  "latestJobStatus": "COMPLETED",
  "warnings": ["Configuration or resource files mention moved packages; they were not modified."]
}
```

### Job

```json
{
  "jobId": "b5131c16-…", "projectId": "74969849-…", "type": "ANALYZE", "status": "COMPLETED",
  "createdAt": "…", "startedAt": "…", "finishedAt": "…", "error": null,
  "events": [
    { "timestamp": "…", "event": "EXTRACTION_COMPLETE", "detail": "29 files extracted" },
    { "timestamp": "…", "event": "PARSING_COMPLETE", "detail": "23 Java files parsed" },
    { "timestamp": "…", "event": "DEPENDENCY_ANALYSIS_COMPLETE", "detail": "21 classes, 84 dependencies" },
    { "timestamp": "…", "event": "MODULE_DISCOVERY_COMPLETE", "detail": "3 business modules" },
    { "timestamp": "…", "event": "PLAN_CREATED", "detail": "23 files planned, 21 to move" }
  ]
}
```

`type` is `ANALYZE`, `TRANSFORM` or `VALIDATE`; `status` is `QUEUED`, `ANALYZING`, `PLANNING`,
`TRANSFORMING`, `VALIDATING`, `COMPLETED` or `FAILED`.

### Module decisions (`PUT /modules`)

The body replaces the complete, ordered decision list; the server replays it on top of the suggestion and
rejects the whole request (`400 INVALID_MODULE_OPERATION`) if any decision is invalid.

```json
{ "edits": [
  { "type": "RENAME_MODULE",  "module": "payment", "newName": "billing" },
  { "type": "MERGE_MODULES",  "sources": ["invoice"], "target": "billing" },
  { "type": "SPLIT_MODULE",   "module": "user", "newName": "profile", "classes": ["com.demo.dto.ProfileDto"] },
  { "type": "MOVE_CLASS",     "className": "com.demo.service.AuditService", "target": "order" },
  { "type": "MOVE_TO_SHARED", "className": "com.demo.util.Money" },
  { "type": "EXCLUDE_CLASS",  "className": "com.demo.legacy.OldThing" },
  { "type": "LOCK_CLASS",     "className": "com.demo.service.UserService" }
] }
```

Also `INCLUDE_CLASS` and `UNLOCK_CLASS`. Module names must be lower-case Java identifiers and must not be
reserved (`shared`, `config`, …). Limits: 500 edits, 300-character class names.

Response: `{ suggestion: Module[], decisions: Edit[], finalModules: Module[], warnings: string[], note }` with

```json
{ "name": "order", "category": "BUSINESS_MODULE", "confidence": 0.79, "cohesion": 0.56, "externalCoupling": 0.35,
  "classCount": 5, "internalDependencies": 19, "externalDependencies": 9,
  "dependenciesOnModules": { "shared": 3, "user": 6 },
  "evidence": ["5 of 5 classes share the domain term 'order'", "roles present: [CONTROLLER, SERVICE, REPOSITORY, ENTITY, DTO]",
               "19 internal and 9 outgoing external dependencies; cohesion 0.56"],
  "warnings": [],
  "classes": [ { "qualifiedName": "com.demo.controller.OrderController", "className": "OrderController",
                 "componentType": "CONTROLLER", "confidence": 0.9, "origin": "AUTOMATIC",
                 "locked": false, "excluded": false, "reasons": ["domain term 'order' from class name OrderController"] } ] }
```

Categories: `BUSINESS_MODULE`, `SHARED`, `INFRASTRUCTURE`, `CONFIGURATION`, `SECURITY`, `APPLICATION`, `UNKNOWN`.

### Plan entry

```json
{
  "id": "e-0019", "scope": "MAIN", "className": "OrderService",
  "sourcePath": "src/main/java/com/demo/service/OrderService.java",
  "targetPath": "src/main/java/com/demo/order/OrderService.java",
  "sourcePackage": "com.demo.service", "targetPackage": "com.demo.order",
  "module": "order", "folder": "service",
  "actions": ["MOVE", "REWRITE_IMPORT", "REWRITE_PACKAGE", "REWRITE_QUALIFIED_REFERENCE"],
  "safety": "SAFE", "risk": "LOW", "confidence": 0.9,
  "rewrites": ["rewrite package com.demo.service → com.demo.order",
               "update imports (3 project types affected)", "rewrite 2 fully-qualified reference(s)"],
  "reasons": [],
  "classes": [ { "source": "com.demo.service.OrderService", "target": "com.demo.order.OrderService", "nested": false } ]
}
```

With the default `MODULAR_MONOLITH` strategy `OrderService` lands in the module root `com.demo.order`
because another module (payment) uses it; a class used only inside its module would land in
`com.demo.order.service`. The plan adds `strategy`, `basePackage`, `fingerprint`, `summary` (files, moved, kept, excluded, manualReview,
unsupported, safe, safeWithWarning, conflicts, rewrites), `conflicts` (`{type, target, sources, resolution}`),
`warnings`, `resourceFindings` (`{file, line, reference, snippet}`), `layout` and `classMap`.

### Validation

```json
{
  "status": "WARN",
  "completedAt": "…",
  "levels": [
    { "level": "FILESYSTEM", "label": "Filesystem", "status": "PASS",
      "summary": "23 Java files and 6 resource files present, no collisions", "issueCount": 0, "issues": [], "durationMillis": 3 },
    { "level": "ARCHITECTURE_RULES", "label": "Architecture Rules", "status": "WARN", "summary": "0 error(s), 1 warning(s)",
      "issueCount": 1, "durationMillis": 0,
      "issues": [ { "severity": "WARNING", "file": "src/main/java/com/demo/shared/exception/GlobalExceptionHandler.java", "line": 14,
                    "message": "Shared class GlobalExceptionHandler depends on module 'user' (UserNotFoundException).",
                    "probableCause": "Shared code should not depend on a business module; consider an interface in shared or moving the class." } ] },
    { "level": "BUILD", "label": "Maven Build", "status": "PASS", "summary": "mvn -B -q -DskipTests test-compile succeeded in 2.648 s", "…": "…" }
  ],
  "build": { "command": ["mvn", "-B", "-q", "-DskipTests", "test-compile"], "exitCode": 0,
             "stdout": "", "stderr": "", "durationMillis": 2648, "timedOut": false, "truncated": false }
}
```

Level and overall status: `PASS`, `WARN`, `FAIL`, `SKIPPED`.

## Errors

| HTTP | `errorCode` | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | malformed identifier, missing file, bad JSON, validation failure |
| 400 | `INVALID_ARCHIVE` / `UNSAFE_ARCHIVE_ENTRY` | not a ZIP; traversal, absolute path, symlink, duplicate entry |
| 400 | `INVALID_MODULE_OPERATION` | a module decision cannot be applied |
| 404 | `PROJECT_NOT_FOUND` / `JOB_NOT_FOUND` / `RESOURCE_NOT_FOUND` | unknown or expired ID, report not yet produced |
| 409 | `INVALID_STATE` | e.g. transform before analysis finished, download before transformation |
| 413 | `ARCHIVE_TOO_LARGE` | upload above `max-archive-size` |
| 422 | `ARCHIVE_LIMIT_EXCEEDED` | entry count, entry size, total size or compression ratio exceeded |
| 422 | `INVALID_PROJECT_STRUCTURE` / `SOURCE_NOT_FOUND` / `JAVA_PARSE_ERROR` | unsupported build tool, no Java sources, … |
| 422 | `ANALYSIS_LIMIT_EXCEEDED` / `TIMEOUT` | too many files, analysis deadline exceeded |
| 429 | `RATE_LIMITED` / `TOO_MANY_JOBS` | rate limit or job quota; see `Retry-After` |
| 500 | `WORKSPACE_ERROR` / `STORAGE_ERROR` / `EXTRACTION_ERROR` / `TRANSFORMATION_ERROR` / `INTERNAL_ERROR` | server-side failure; quote the `requestId` |

Example:

```json
{ "success": false,
  "message": "Module names must be lower-case Java identifiers (letters and digits, starting with a letter) and must not be a reserved name such as 'shared' or 'config'.",
  "errorCode": "INVALID_MODULE_OPERATION",
  "timestamp": "2026-10-04T10:45:51.584Z", "requestId": "ef34cd17-b426-421a-87a8-74434aa0662a" }
```

## Rate limits

Per client and minute (configurable under `archmorph.security.rate-limit.*`): uploads 10, expensive
operations (analyze, transform, validate, module edits, strategy changes) 30, downloads 60, everything else 600. Exceeding a
budget returns `429` with `Retry-After`.

## Session

```bash
H=http://localhost:8080/api/v1
P=$(curl -s -F file=@spring-layered.zip $H/projects | jq -r .data.projectId)
until [ "$(curl -s $H/projects/$P | jq -r .data.status)" = READY_FOR_REVIEW ]; do sleep 1; done
curl -s $H/projects/$P/modules | jq '.data.finalModules[] | {name, category, confidence}'
curl -s -X PUT -H 'Content-Type: application/json' \
     -d '{"edits":[{"type":"RENAME_MODULE","module":"payment","newName":"billing"}]}' $H/projects/$P/modules
curl -s -X POST "$H/projects/$P/transform?dryRun=true" | jq .data.plan.summary
curl -s -X POST $H/projects/$P/transform
until [ "$(curl -s $H/projects/$P | jq -r .data.status)" = COMPLETED ]; do sleep 2; done
curl -s $H/projects/$P/validation | jq '.data.levels[] | {label, status}'
curl -s -o transformed.zip $H/projects/$P/download
```
