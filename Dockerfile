# syntax=docker/dockerfile:1

###### Stage 1 — build the Spring Boot jar (tests are skipped: they need Docker/Testcontainers) ######
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
# Copy the wrapper + pom first so dependency resolution can be cached when only sources change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# The wrapper is checked out on Windows: strip CRLF and add the executable bit.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp clean package -DskipTests

###### Stage 2 — slim runtime (JRE, non-root) ######
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
# curl backs the compose healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd -r -u 1001 -g root spring
USER 1001
COPY --from=build /app/target/erp-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
