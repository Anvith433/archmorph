# Deploying ArchMorph

ArchMorph analyses and compiles code that other people upload. Read [THREAT_MODEL.md](THREAT_MODEL.md) first;
this page is the practical checklist.

## 1. Choose a mode

| Who can reach the server | Recommended setup |
|---|---|
| Only you, on your machine | default: no login, bound to loopback (`server.address=127.0.0.1`, or `127.0.0.1:8080:8080` in Docker) |
| A team, on a private network | Docker + `archmorph.security.auth.mode=BASIC` + TLS reverse proxy |
| The internet | not recommended: the build level compiles uploaded code. If you must, add all of the above, disable build validation or run each instance in a disposable VM, and restrict egress |

With authentication disabled and a non-loopback address, ArchMorph logs a warning at startup.

## 2. Run with Docker

```bash
docker compose up --build        # http://localhost:8080
```

The image (see `Dockerfile`) builds the UI and the server and runs them as one process. The runtime image
contains a JDK and Maven because build validation compiles the transformed project with the server's own
Maven; uploaded `mvnw`/`gradlew` are never run.

`docker-compose.yml` applies the container hardening that the in-process sandbox cannot provide on its own:

| Setting | Why |
|---|---|
| non-root user (uid 10001) | the server and the child builds never run as root |
| `read_only: true`, tmpfs `/tmp` | only the `/data` volume (workspaces, Maven repository) and `/tmp` (upload buffering) are writable |
| `cap_drop: [ALL]`, `no-new-privileges` | no Linux capabilities, no privilege escalation through set-uid binaries |
| `mem_limit`, `pids_limit`, `cpus` | bounds memory, process count (fork bombs) and CPU of the server **and** of every build it runs |
| port on `127.0.0.1` | nothing outside the host reaches it until you put a reverse proxy in front |

Data lives in the `archmorph-data` volume: `workspace/` (uploads and results, deleted after
`archmorph.workspace.retention`, 24 h by default) and `m2/` (the shared Maven repository, so builds do not
download everything again).

**Egress.** Build validation downloads the transformed project's dependencies. To stop builds from reaching
arbitrary hosts, either run offline against a pre-filled repository (`ARCHMORPH_VALIDATION_BUILD_OFFLINE=true`)
or attach the container to a network whose only route out is your Maven mirror. Without either, a malicious
build plugin or annotation processor has network access.

## 3. Enable login (HTTP Basic)

1. Create a bcrypt hash. The password is read without echo (or from standard input) and never printed:

   ```bash
   docker compose run --rm archmorph hash-password
   # or, without Docker:  bin/archmorph hash-password
   ```

2. Copy `archmorph.env.example` to `archmorph.env`, fill in the user name and the hash, and enable the
   `env_file` entry in `docker-compose.yml`. More users: `ARCHMORPH_SECURITY_AUTH_USERS_1_USERNAME`, … .
   Outside Docker, use the `archmorph.security.auth.*` properties (see `application.properties`).

3. Restart. The server refuses to start if a user has no name, a duplicate name, or a password that is not a
   bcrypt hash (plain text and `{noop}` are rejected).

What login changes:

* every request except `/actuator/health` needs credentials (the browser shows its login prompt);
* a project is visible only to the user who uploaded it; other users get "not found";
* rate limits and job quotas apply per user;
* state-changing API requests must carry `X-Requested-With` (the UI always sends it). Browsers resend Basic
  credentials automatically, so this header is what stops another site from making requests in your name;
* after 10 failed logins for one user name from one address (or 50 for any names) within 5 minutes, further
  attempts from that address are refused for the rest of the window.

Basic credentials travel with every request, so **always put TLS in front** (reverse proxy). Each request
verifies the bcrypt hash; that costs a few milliseconds of CPU per API call, which is fine for a small team.
There is no logout other than closing the browser, and no self-service password change: change the hash in the
configuration and restart.

## 4. Behind a reverse proxy

* Terminate TLS at the proxy and forward to `127.0.0.1:8080`.
* Set `archmorph.security.trust-forwarded-headers=true` **only** when the proxy is the sole way in; otherwise a
  client could pick its own rate-limit identity with `X-Forwarded-For`.
* Allow request bodies up to the upload limit (`archmorph.upload.max-archive-size`, 100 MB by default), e.g.
  `client_max_body_size 100m;` in nginx, and long enough read timeouts for downloads.
* The UI and API are one origin; `archmorph.security.allowed-origins` only matters when the UI is served from
  somewhere else (e.g. the Vite dev server).

## 5. Operations

* Health: `GET /actuator/health` (no details; the image's `HEALTHCHECK` uses it).
* Logs never contain uploaded source, file contents, credentials or absolute paths; error responses carry a
  request ID to correlate with the logs.
* Gradle build validation stays off unless you set `archmorph.validation.build.gradle-enabled=true` and install
  Gradle in the image (build scripts are code; see THREAT_MODEL.md).
* Sessions and jobs are in memory: a restart forgets them (workspaces on disk are cleaned up by retention).
  Run a single instance.
