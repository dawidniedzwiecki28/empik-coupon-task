# syntax=docker/dockerfile:1

# ---- build stage: compile the executable jar on JDK 21 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# Build scripts + wrapper first so dependency layers cache across source-only changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY config ./config
COPY src ./src
# Tests need Docker/Testcontainers, so they run in CI, not in the image build.
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- runtime stage: JRE only, non-root ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# curl is only for the healthcheck below; drop the apt lists to keep the layer small.
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
