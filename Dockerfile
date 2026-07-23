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
RUN groupadd --system app && useradd --system --gid app --home /app app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
