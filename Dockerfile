# syntax=docker/dockerfile:1

# Base images are pinned by multi-arch digest for reproducible, verifiable builds (the tag is kept for
# readability). Digests, unlike apt version pins, stay pullable when superseded — bump them deliberately
# (e.g. via Renovate) to pick up JRE/OS security patches.

# ---- build stage: compile the executable jar on JDK 21 ----
FROM eclipse-temurin:21-jdk@sha256:da9d3a4f7650db39b918fc5a2c3da76556fb8cc8e5f3767cdea0bb409286951a AS build
WORKDIR /workspace
# Build scripts + wrapper first so dependency layers cache across source-only changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY config ./config
COPY src ./src
# Tests need Docker/Testcontainers, so they run in CI, not in the image build.
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- runtime stage: JRE only, non-root ----
FROM eclipse-temurin:21-jre@sha256:273396ed5998598ed1091e8d72711c2d36980a0e65103859c55a4e977a41ffd3 AS runtime
WORKDIR /app
# curl is only for the healthcheck below; drop the apt lists to keep the layer small.
# Intentionally not pinning the curl version (DL3008): apt pins on a rolling Ubuntu base self-break once
# the patch release is superseded and dropped from the archive — the pinned base digest above is what
# anchors reproducibility instead.
# hadolint ignore=DL3008
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& groupadd --system app && useradd --system --gid app --home /app app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
# Report readiness from the app itself, so Docker/Compose see "unhealthy" if Spring fails to start or the DB is down.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
	CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
