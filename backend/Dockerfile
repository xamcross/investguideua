# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Cache dependencies first
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline
# Build the single deployable JAR
COPY src ./src
RUN mvn -q -B -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# curl is used by the Docker Compose healthcheck (not bundled in the temurin JRE image);
# also create a non-root runtime user.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 appuser
COPY --from=build /build/target/investguide-backend-*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
