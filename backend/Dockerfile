# syntax=docker/dockerfile:1

# ---- Stage 1: build the Spring Boot fat jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies first: copy only the POM, resolve, then copy sources.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: slim runtime image ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN useradd -r -u 1001 -g root appuser

# Copy the built jar (there is exactly one *.jar in target after packaging).
COPY --from=build /app/target/*.jar app.jar

# Writable storage dir for generated PDFs / QR codes (ephemeral on Render).
RUN mkdir -p /app/storage && chown -R appuser:root /app
USER appuser

# Render injects PORT (default 10000); Spring reads it via server.port.
EXPOSE 7060
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
