# syntax=docker/dockerfile:1

# Base images pinned by digest for reproducible builds (tag kept for readability); bump deliberately
# (e.g. Renovate) for JRE/OS security patches.

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
# curl is only for the HEALTHCHECK below. Not version-pinning it (DL3008): apt pins self-break on a
# rolling base once the patch is dropped from the archive — the pinned base digest anchors reproducibility.
# hadolint ignore=DL3008
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& groupadd --system app && useradd --system --gid app --home /app app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
# App-level readiness: reports unhealthy if Spring or its DB isn't up.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
	CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
