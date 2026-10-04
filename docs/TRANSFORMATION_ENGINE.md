# Transformation engine

How ArchMorph turns a reviewed module assignment into a transformed project, what it guarantees, and
where it deliberately stops.

```
ModuleDiscoveryReport (final, after user edits)
        │
        ▼
TargetArchitecture ──▶ TransformationMappingEngine ──▶ class map  old FQN → new FQN (complete)
        │
        ▼
DefaultTransformationPlanner ──▶ TransformationPlan  (entries, conflicts, warnings, resource findings, fingerprint)
        │                               │
        │            dry run ◀──────────┤  (rewrite everything in memory, diffs, nothing written)
        ▼                               ▼
DefaultTransformationEngine ──▶ SourceRewriter per file ──▶ transformed/ ──▶ ValidationEngine
```

## 1. Target architecture

`TargetArchitecture` is a strategy with two implementations, chosen per project
(`archmorph.transformation.strategy`, `PUT /projects/{id}/strategy`, `--strategy`).

### MODULAR_MONOLITH (default)

```
<base>                                 @SpringBootApplication class (never moved) and application wiring
<base>.<module>                        the module's public API: the classes other modules use
<base>.<module>.{controller|service|repository|entity|dto|exception|component|common}   internals
<base>.shared                          shared API: shared classes used by modules
<base>.shared.{config|security|exception|infrastructure|common}                          shared internals
```

This is the layout [Spring Modulith](https://spring.io/projects/spring-modulith) expects: every direct
sub-package of the application package is a module, and only the module's base package is accessible to other
modules. Placement depends on the dependency graph. The planner computes the **exposed types**: every class
that a class of a *different* module (or shared/application code) depends on. Exposed classes go to the module
root, the rest into the folder for their role. ArchMorph never changes visibility, so a module's public API is
exactly the set of types other modules use today.

**Application wiring.** A shared class that depends on business modules but that no module uses (a global
`@RestControllerAdvice` handling module exceptions, a `UserDetailsService` implementation backed by a module
repository) would create a shared ↔ module cycle. It is placed directly in `<base>`, which belongs to no
module. Shared code that modules *also* use stays in shared and the cycle is reported.

**Verification.** Validation level 6 checks that every dependency between modules targets the other module's
API package and reports module cycles. The generated `MODULES.md` contains the Spring Modulith test
(`ApplicationModules.of(App.class).verify()`) to keep the boundaries checked in the project's own build. On the
`spring-layered` fixture the transformed project passes that verification.

**Optional Spring Modulith setup.** With `addModulithVerification` (Plan page checkbox, API, `--add-modulith-test`,
or `archmorph.transformation.add-modulith-verification`), `ModulithSetup` adds the `spring-modulith-bom` import and
the `spring-modulith-starter-test` test dependency to `pom.xml` and writes `ModularityTests` next to the
application class. The pom is edited by inserting text at element positions (`PomDocument`), so formatting,
comments and line endings are preserved; nothing is added twice and no existing file is overwritten. The
Spring Modulith version follows the project's Spring Boot line (or `archmorph.transformation.modulith-version`);
for an unknown Spring Boot version nothing is changed and a warning explains why. The generated test is
listed in the plan's `generatedFiles` and validated like every other file.

### MODULAR_BY_DOMAIN

```
<base>.modules.<module>.{controller|service|repository|entity|dto|exception|component|common}
<base>.shared.{config|security|exception|infrastructure|common}
<base>                     the @SpringBootApplication class and application wiring
```

Package-by-module without API/internal separation.

### Common rules

* `<base>` is the longest common package prefix of all production classes (`DefaultBasePackageResolver`);
  for a conventional Spring Boot project that is the application class's package.
* The folder comes from the component type: controller, service, repository, entity, DTO, configuration
  (`config`), security and filters (`security`), exceptions and exception handlers (`exception`),
  other components (`component`), everything else (`common`).
* The application class is **never moved**: Spring scans its package and below, so moving it down would
  silently stop component scanning for every other class.
* `modules` and `shared` are configurable (`archmorph.transformation.modules-package` / `shared-package`).

## 2. Mapping

`DefaultTransformationMappingEngine` produces one `TransformationMapping` per classified top-level type and
the **complete class map** `old FQN → new FQN`, including nested types (`a.Outer.Inner → b.Outer.Inner`). Every
later step (planner, rewriter, validators) works from that map — never from module names or string
replacement of package prefixes.

## 3. Planning

`DefaultTransformationPlanner` is pure (reads the parsed model, never touches the file system) and
deterministic (sorted inputs; the plan carries a SHA-256 fingerprint of its content).

**Unit of work: the file.** A Java file moves as a whole with every type it declares. Nested types follow
their top-level type. Files with several top-level types are flagged `SAFE_WITH_WARNING`.

### 3.1 Actions, safety and risk

| Action | Meaning |
|---|---|
| `MOVE` | file moves to its target directory |
| `REWRITE_PACKAGE` | package declaration changes |
| `REWRITE_IMPORT` | at least one import changes (added / removed / retargeted) |
| `REWRITE_QUALIFIED_REFERENCE` | fully-qualified references in code or Javadoc change |
| `KEEP` | file stays in place (possibly still with import rewrites) |
| `EXCLUDE` | user excluded the class; it stays in place |
| `MANUAL_REVIEW` | ArchMorph will not move it automatically; reasons attached |
| `COPY` | reserved; never used for classes (shared code is moved once, not copied) |

| Safety | Assigned when | Effect |
|---|---|---|
| `SAFE` | no risk detected | moved |
| `SAFE_WITH_WARNING` | several top-level types, low-confidence placement (< 0.5), package-private members whose old neighbours move elsewhere | moved, warning shown |
| `MANUAL_REVIEW` | reflection by name, generated code, explicit package scanning, collisions, demotions below | kept in place |
| `UNSUPPORTED` | dynamic class generation / proxies | kept in place |

Risk follows safety: `SAFE` → `LOW`, `SAFE_WITH_WARNING` → `MEDIUM`, `MANUAL_REVIEW` / `UNSUPPORTED` → `HIGH`.

### 3.2 Collisions

Before any demotion the planner groups entries by target:

* `TARGET_PATH_COLLISION` — two files would land on the same path.
* `TARGET_CLASS_NAME_COLLISION` — two classes would get the same FQN (e.g. `com.a.User` and `com.b.User`
  both mapped to `modules.user.entity.User`).

Every file involved in a collision is kept in its original package and marked `MANUAL_REVIEW`; the
conflict is listed with its sources and resolution. Keeping in place is always compilable because original
FQNs are unique.

### 3.3 Demotion fixpoint

Some moves compile in isolation but break a neighbour. The planner repeats the following rules until none
fires (each pass only demotes, so the loop terminates):

1. **Package-private access**: a moved class uses a package-private class that stays (or goes elsewhere)
   → the move is demoted.
2. **Default package**: a class in a named package cannot import the default package → classes that depend
   on a default-package class that stays are demoted.
3. **Component-scan root**: a Spring-managed class whose target would fall outside the application class's
   package is demoted.
4. **String literals**: a string literal that names a project class keeps that class in place; a literal that
   names a project package flags its file.
5. **Resource references**: a class whose fully-qualified name appears in a resource file (an OpenAPI spec
   naming a validation annotation, `spring.factories`, XML bean definitions) stays in place, because resource
   files are not rewritten and the build or runtime would look for it at the old location.

### 3.4 Tests and resources

* `src/test/java` is planned too. A test named after a class (`UserServiceTest`, `UserServiceIT`,
  `TestUserService`, `…Tests`, `…Spec`, `…IntegrationTest`) moves next to that class; other tests stay and are
  only rewritten. `archmorph.transformation.preserve-tests` controls this.
* Non-Java files (resources, `pom.xml`, everything else) are copied unchanged.
* `ResourceReferenceScanner` reports lines in text resources (`.properties`, `.yml`/`.yaml`, `.xml`, `.json`,
  `spring.factories`, `*.imports`, `.conf`, `.txt`, files up to 1 MB) and `pom.xml` that mention a moved class or
  package (e.g. `logging.level.com.demo.service=DEBUG`). They are **not rewritten**; the plan
  carries them as resource findings and a warning.

## 4. Source rewriting

`SourceRewriter` rewrites one file given the class map and the file's new package.

### 4.1 What changes

1. The package declaration.
2. Imports, through the class map: single-type, static (`import static a.B.C`), nested
   (`import a.Outer.Inner`) and static-on-demand imports are retargeted. Project wildcard imports
   (`import com.demo.model.*`) are replaced by explicit imports of the classes the file actually uses.
3. Implicit visibility that the move breaks is made explicit: a class that used a same-package neighbour
   without an import gets an import when the two are separated.
4. Obsolete imports are removed (the imported class is now in the same package).
5. Fully-qualified names inside MapStruct `java(...)` expressions (`expression`, `defaultExpression`,
   `conditionExpression`), which MapStruct copies into the generated mapper; other strings are never changed.
6. Fully-qualified references in code — types, `new` expressions, static field access, annotations, casts,
   generic arguments, local variable types — and exact fully-qualified names in comments and Javadoc.
7. External and unrelated imports are left untouched.

### 4.2 How

JavaParser parses the file and the import-aware resolver decides *what* must change. The AST supplies exact
source ranges (package name, type-name scopes, `FieldAccessExpr` chains, annotation names, comment ranges),
and the edits are applied to the **original text** from the end of the file backwards. Everything the
transformation does not touch — formatting, comments, blank lines, line endings (LF or CRLF), trailing
whitespace — is preserved byte for byte.

The import block is regenerated as a unit: existing groups, their order and the blank lines between them
are kept; new imports are inserted into the matching group in the file's existing sort order. If the import
block contains comments, ArchMorph falls back to editing individual import lines so the comments survive.

No regular expression is used to find code. The only pattern matching is a whole-word search for exact
fully-qualified names inside comment ranges that the AST already identified.

**Why not `LexicalPreservingPrinter`?** It was the first implementation. In testing it produced irregular
whitespace around changed imports, did not update Javadoc references, and — the decisive problem — silently
kept old qualified type names in some local variable declarations; the dependency-preservation validator
caught it on the multi-package fixture. AST-located text edits avoid all three.

### 4.3 Example (from the `spring-layered` fixture, `MODULAR_BY_DOMAIN` layout)

```diff
--- a/src/main/java/com/demo/service/OrderService.java
+++ b/src/main/java/com/demo/modules/order/service/OrderService.java
-package com.demo.service;
+package com.demo.modules.order.service;

-import static com.demo.common.AppConstants.DEFAULT_CURRENCY;
+import static com.demo.shared.common.AppConstants.DEFAULT_CURRENCY;

-import com.demo.entity.Order;
-import com.demo.repository.OrderRepository;
+import com.demo.modules.order.entity.Order;
+import com.demo.modules.order.repository.OrderRepository;
+import com.demo.modules.user.service.UserService;     // was implicit: same package before the move
 import org.springframework.stereotype.Service;
 ...
-    public com.demo.dto.OrderDto create(com.demo.dto.OrderDto dto) {
+    public com.demo.modules.order.dto.OrderDto create(com.demo.modules.order.dto.OrderDto dto) {
```

The same file with the default `MODULAR_MONOLITH` layout. `OrderService` and `Order` are used by the payment
module, so both form order's public API in `com.demo.order`. The import of `Order` disappears because the two
classes now share a package, and `UserService` is imported from the user module's API:

```diff
-package com.demo.service;
+package com.demo.order;

-import static com.demo.common.AppConstants.DEFAULT_CURRENCY;
+import static com.demo.shared.AppConstants.DEFAULT_CURRENCY;

-import com.demo.entity.Order;
-import com.demo.repository.OrderRepository;
+import com.demo.order.repository.OrderRepository;
+import com.demo.user.UserService;
 import org.springframework.stereotype.Service;
 ...
-    public com.demo.dto.OrderDto create(com.demo.dto.OrderDto dto) {
+    public com.demo.order.dto.OrderDto create(com.demo.order.dto.OrderDto dto) {
```

Wildcard import split (golden case `wildcard-imports`):

```diff
-package com.demo.web;
+package com.demo.modules.report.controller;

-import com.demo.model.*;
+import com.demo.modules.order.entity.Order;
+import com.demo.modules.user.entity.User;
 import java.util.*;
```

Nested and static-nested references (golden case `nested-classes`):

```diff
-import static com.demo.service.OrderService.Status.PAID;
+import static com.demo.modules.order.service.OrderService.Status.PAID;
-import com.demo.service.OrderService.Summary;
+import com.demo.modules.order.service.OrderService.Summary;
 ...
-    private final com.demo.service.OrderService.Summary qualified = new com.demo.service.OrderService.Summary();
+    private final com.demo.modules.order.service.OrderService.Summary qualified = new com.demo.modules.order.service.OrderService.Summary();
```

Ten golden cases live in `src/test/resources/golden/rewriter/` (same-package split, wildcard imports, static
imports, qualified references, nested classes, unsorted imports, default package, CRLF, comments in the
import block, untouched file). Regenerate with `-Dgolden.update=true` and review the diff.

## 5. Execution

After the files are written, `ModuleDocumentation` adds `MODULES.md` to the transformed project (or
`ARCHMORPH-MODULES.md` if the project already has one). It lists every module, its package, public API,
internal packages, the modules it depends on and through which types, review notes, the files kept in place,
and how to verify the boundaries. The content is deterministic.

`DefaultTransformationEngine.execute` writes into `transformed/` only; `original/` and the uploaded archive
are never modified. Every planned file is written exactly once; non-Java files are copied. `dryRun` runs the
same rewriting in memory and returns per-file changes (package changed, import changes, qualified rewrites,
lines added/removed) and diffs.

## 6. Guarantees

Checked by `TransformationInvariantsTest` on every fixture and by the validation levels on every run:

* **No lost files**: every source and resource file of the input exists in the output.
* **Unique destinations**: no two entries share a target path or FQN.
* **Package = directory** for every Java file.
* **No stale imports**: no import refers to the old location of a moved class.
* **Dependency preservation**: the dependency graph rebuilt from the output contains every original
  dependency, mapped through the class map.
* **No duplication**: a shared class exists once.
* **Originals untouched**, **deterministic output** (same input and decisions → byte-identical result and
  the same plan fingerprint).
* On the fixtures, the transformed sources compile in **both** layouts (in-JVM compiler in tests; sandboxed
  Maven in `EndToEndMavenTest`).
* **Module encapsulation** (`MODULAR_MONOLITH`): no class uses an internal class of another module.

## 7. Non-guarantees

* Behavioural equivalence. Spring wiring by package (`@ComponentScan("…")`, `@EntityScan`,
  `@EnableJpaRepositories` with string packages), reflection, SpEL, serialized class names, configuration
  keys and XML mappers can change behaviour; they are flagged, not rewritten.
* Access-level fixes. ArchMorph never widens visibility; it keeps the affected files in place instead.
* Design improvements. Cycles, shared → module dependencies and leaky module APIs are reported, not
  refactored.
