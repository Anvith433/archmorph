# syntax=docker/dockerfile:1
#
# ArchMorph: one image serving the UI and the API.
#
#   docker build -t archmorph .
#   docker compose up            (see docker-compose.yml and docs/DEPLOYMENT.md)
#
# The runtime image contains a JDK and Maven because build validation (level 7) compiles the transformed
# project with the server's own Maven. Uploaded wrappers (mvnw, gradlew) are never run.

# ---------------------------------------------------------------- UI
FROM node:22-alpine AS ui
WORKDIR /ui
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---------------------------------------------------------------- server
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline || true
COPY src ./src
COPY --from=ui /ui/dist ./frontend/dist
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Pui -DskipTests package \
    && cp "$(ls target/archmorph-ai-*.jar | grep -v original | head -n 1)" /archmorph.jar

# ---------------------------------------------------------------- runtime
FROM maven:3.9-eclipse-temurin-21
RUN groupadd --system --gid 10001 archmorph \
    && useradd --system --uid 10001 --gid archmorph --home-dir /data --shell /usr/sbin/nologin archmorph \
    && mkdir -p /data /app \
    && chown archmorph:archmorph /data
COPY --from=build /archmorph.jar /app/archmorph.jar

# Workspaces and the shared Maven repository live on the /data volume; nothing else needs to be writable
# (run with a read-only root file system and a tmpfs on /tmp, as docker-compose.yml does).
ENV ARCHMORPH_WORKSPACE_ROOT=/data/workspace \
    ARCHMORPH_VALIDATION_BUILD_LOCAL_REPOSITORY=/data/m2 \
    ARCHMORPH_SECURITY_ALLOWED_ORIGINS=http://localhost:8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+ExitOnOutOfMemoryError"

USER archmorph
WORKDIR /data
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/archmorph.jar \"$@\"", "archmorph"]
