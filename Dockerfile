# --- build stage: compile + package the boot jar ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Gradle wrapper + build files first so dependency resolution is cached as its own layer.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN ./gradlew --no-daemon dependencies --quiet || true

COPY src ./src
# Skip tests here: they run a real MySQL/Redis via Testcontainers (Docker), unavailable inside this
# build container. Tests are gated in CI (./gradlew build), not in the image build.
RUN ./gradlew --no-daemon clean bootJar -x test

# --- runtime stage: run the jar on a slim JRE ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
