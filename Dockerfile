# Multi-stage build - the runtime image never carries Maven, the JDK's compiler, or the source
# tree, just the JRE and the built jar (PRODUCTION-CHECKLIST.md's cost/perf section - a smaller
# image is faster to pull on every Railway deploy).

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency layer cached separately from source so a source-only change doesn't re-download
# the whole dependency tree on every build.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# ARENA-STABILIZE.md Phase 0.2 - build stamp so a stale deploy is visible in 5 seconds
# (VersionController reads this file). RAILWAY_GIT_COMMIT_SHA is auto-forwarded as a build arg
# by Railway's Dockerfile builder for git-connected services; falls back to "local" outside
# Railway.
ARG RAILWAY_GIT_COMMIT_SHA=local

# Non-root - least-privilege inside the container too, not just at the Postgres-role level
# (see DECISIONS.md's RLS-deferral entry for the DB-role side of this same principle).
RUN addgroup -S arena && adduser -S arena -G arena
COPY --from=build /build/target/*.jar app.jar
RUN { echo "commit=${RAILWAY_GIT_COMMIT_SHA}"; echo "builtAt=$(date -u +%Y-%m-%dT%H:%M:%SZ)"; } > build-info.properties && \
    chown arena:arena app.jar build-info.properties
USER arena

# Railway injects PORT; application.yml already reads it (server.port: ${PORT:8081}).
EXPOSE 8081
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
