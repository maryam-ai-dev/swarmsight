# Build stage: compile and package with Maven.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Warm the dependency cache before copying source, so code changes do not
# refetch the world.
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Run stage: a slim JRE with just the jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
# curl is used by the compose healthcheck to hit /health.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/target/authority-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
