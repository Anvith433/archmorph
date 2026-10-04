# Threat model

ArchMorph accepts source code from users, unpacks it, analyses it, rewrites it and — optionally — builds it.
Uploaded projects are **untrusted input**. This document lists the threats considered, the mitigations in
place and what remains.

## Scope and assumptions

* **Deployment model:** a single instance in *local mode* — one developer or a trusted team on a workstation
  or internal network. There is no login. Anyone who can reach the server can upload projects and, knowing a
  project UUID, read that project.
* **Assets:** the host (files, credentials, network position), other users' uploaded source code, service
  availability.
* **Trust boundaries:** HTTP clients → API; uploaded archive → extractor; uploaded source → parser and
  rewriter; uploaded build definition → Maven child process; API → browser (rendering server data).
* **Out of scope:** multi-tenant hosting on the public internet. That requires authentication, per-user
  authorisation and a real sandbox (container / VM per build) — see [Hardening for shared deployments](#hardening-for-shared-deployments).

## Threats, mitigations, residual risk

### Archive handling

| Threat | Vector | Mitigation | Residual risk |
|---|---|---|---|
| Path traversal (Zip Slip) | entry `../../etc/cron.d/x` | every entry normalised and verified to stay inside the destination; `..` segments, absolute paths, drive letters and NUL bytes rejected (`SecureZipExtractor`) | none known |
| Symlink escape | symlink entry pointing outside, then a file written through it | symlink entries rejected; files created with `CREATE_NEW`, so no entry can overwrite or follow an existing path | none known |
| Zip bomb | tiny archive expanding to GBs; nested archives | limits on entry count (20 000), per-entry size (50 MB), total uncompressed size (500 MB) and compression ratio (200), counted on bytes actually written, not on declared sizes; nested archives are stored, never expanded | disk use up to the configured limit per project |
| Oversized upload | huge request body | multipart limit = archive limit (100 MB); Tomcat swallow limit 2 MB | — |
| Non-ZIP content | renamed executable, polyglot file | extension and ZIP magic-byte check before storage; the archive is never executed | — |
| Duplicate entries | two entries with the same name to overwrite a checked file | `CREATE_NEW`: the second entry fails the extraction | — |
| Junk / secrets in VCS dirs | `.git/`, `target/`, IDE folders | skipped outside `src/` (configurable list) | secrets inside source files are still processed (never logged or echoed) |

### Workspace and file access

| Threat | Vector | Mitigation | Residual risk |
|---|---|---|---|
| Cross-project access | guessing or manipulating IDs | server-generated random UUIDs; strict UUID format check; resolved directory must be a direct child of the projects root (`WorkspaceManager`) | anyone who learns a UUID can read that project (local mode, no auth) |
| Path injection via API | `entryId`, report names, file paths in requests | entry IDs validated (`e-NNNN`), report names from a fixed enum, no endpoint accepts file paths | — |
| Trusting uploaded file names | `../../x.zip`, HTML in the name | the stored archive is always `input/archive.zip`; display names are sanitised (`FilenameSanitizer`); download names are slugs | — |
| Data retention | uploaded code kept forever | workspaces and sessions expire (24 h default) and are deleted by `RetentionService`; `DELETE /projects/{id}` deletes immediately; CLI workspaces are temporary | files persist until expiry if the user does not delete them |
| Tampering with originals | transformation writes over input | original and archive are read-only by design; output goes to `transformed/` | — |

### Code execution

| Threat | Vector | Mitigation | Residual risk |
|---|---|---|---|
| Running uploaded scripts | `mvnw`, `.mvn/`, `gradlew`, `gradle/wrapper`, shell scripts, Git hooks | never executed. Analysis is pure parsing; Gradle settings and build scripts are read as text (string literals only), never evaluated. The build runs on a *copy* without `.mvn/`, `mvnw*`, `gradlew*`, `gradle/wrapper/` and `.gradle/`, using the server's own `mvn` / `gradle` | — |
| Gradle build scripts | `build.gradle(.kts)`, `settings.gradle(.kts)`, `buildSrc`, init scripts | Gradle builds are **disabled by default** (`gradle-enabled=false`) because evaluating a build script runs arbitrary code; when enabled: `--no-daemon`, a dedicated `GRADLE_USER_HOME` (no user init scripts, properties or credentials), cleared environment, same timeout and limits as Maven | when enabled, the build script runs with the server user's privileges; `BuildPluginGuard` does not apply to Gradle |
| Command-runner plugins | `exec-maven-plugin`, `maven-antrun-plugin`, Groovy, frontend/node, docker, jib, deploy/release/SCM/wagon plugins | `BuildPluginGuard` refuses the build if any `pom.xml` declares one | other plugins and **annotation processors** declared by the project still run arbitrary code during compilation |
| Credential theft by the build | build reads env vars, `~/.m2/settings.xml`, cloud credentials | environment cleared: only `PATH`, `JAVA_HOME`, `HOME` (an isolated per-project directory), `MAVEN_OPTS`, `LANG` and explicitly configured pass-through variables; separate `maven.repo.local` | the build runs as the server's OS user and can read any file that user can read |
| Resource exhaustion by the build | infinite loop, fork bomb, huge output | wall-clock timeout (4 min) with process-tree kill; `prlimit` CPU-time and file-size limits when available; output capped and absolute paths masked | memory and process count are not limited without a container |
| Network abuse by the build | `<repositories>` pointing at attacker hosts, exfiltration from a plugin | optional `offline=true`; the build only downloads through Maven | without offline mode or egress filtering the build has network access |
| Code execution in the parser | malicious source triggering parser bugs | JavaParser parses only (no class loading, no annotation processing); per-file size cap (2 MB); analysis deadline | parser denial-of-service bugs bounded by the analysis timeout |

**Build validation is process-level isolation, not a security boundary.** Compiling a Maven project runs the
project's plugins and annotation processors; a Gradle build (opt-in) runs its build scripts. To analyse projects you do not trust, either run ArchMorph in a
disposable container/VM with no credentials and restricted egress, or disable the build level:
`archmorph.validation.build.enabled=false` (levels 1–6 still run; they never execute uploaded code).

### Web API and browser

| Threat | Vector | Mitigation | Residual risk |
|---|---|---|---|
| Information leakage | stack traces, absolute paths, exception messages, env vars | `GlobalExceptionHandler` returns safe messages + `ErrorCode` + hint; unknown errors become a generic 500 with request ID; `server.error.include-*=never`; DTOs use project-relative paths; build output masks absolute paths; actuator exposes `health` only, without details | — |
| XSS from uploaded content | class names, comments, string literals with HTML/JS shown in the UI | React text rendering only (no `dangerouslySetInnerHTML`); diffs rendered as text nodes; strict CSP `default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'` | — |
| Malicious downloads | serving uploaded content as HTML | downloads are `application/zip` / `application/json` / `text/markdown` with `Content-Disposition: attachment` and `nosniff` | users must still treat the transformed project as untrusted code |
| Clickjacking, MIME sniffing, referrer leaks | — | `X-Frame-Options: DENY`, `frame-ancestors 'none'`, `nosniff`, `Referrer-Policy: no-referrer`, restrictive `Permissions-Policy` | — |
| Cross-origin abuse | another site calling the API from a browser | CORS allowlist from configuration (default `http://localhost:5173`, never `*`); no cookies or sessions, so no ambient credentials and no CSRF surface | a non-browser client can call the API directly (no auth) |
| Denial of service | many uploads / transforms / downloads | per-client fixed-window rate limits (uploads 10/min, expensive operations 30/min, downloads 60/min, other 600/min); bounded job queue (20) and workers (2); max 3 active jobs per client; analysis and build timeouts | in-memory, per-instance limits; client identity is the (hashed) remote address, so clients behind one NAT share a budget |
| Client spoofing | forged `X-Forwarded-For` to dodge rate limits | forwarded headers ignored unless `trust-forwarded-headers=true` (only behind a trusted proxy) | — |
| Request tracing | correlating errors without leaking data | server-generated request ID in `X-Request-Id`, the response envelope and the log MDC; client-provided IDs are ignored | — |

### Logging and secrets

* Uploaded source code, file contents, secrets, tokens and environment variables are never logged. Logs
  contain project IDs, phase events, counts and durations.
* Remote addresses are hashed (SHA-256) before being used as rate-limit keys.
* No credentials are configured or required by ArchMorph itself.

## Hardening for shared deployments

Before exposing ArchMorph beyond a trusted network:

1. Add authentication (Spring Security is already in place; `ProjectAccessPolicy` is the single
   authorisation seam) and bind projects to their owner.
2. Run each build in its own container or VM (no host mounts, no credentials, CPU/memory/PID limits,
   egress allowlist to your Maven mirror) — or disable build validation.
3. Put a reverse proxy in front for TLS, global rate limiting and request-size limits; then enable
   `trust-forwarded-headers`.
4. Move sessions and jobs to a persistent store if you need restarts or several instances.
5. Run the service as an unprivileged user with a dedicated workspace volume and short retention.
