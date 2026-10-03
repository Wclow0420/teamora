# ---- Build stage: compile + package with Maven on JDK 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies first
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
# Build the jar (skip tests in the image build; CI runs them separately)
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Run stage: slim JRE, unprivileged user ----
FROM eclipse-temurin:21-jre
# curl is only for the container healthcheck (GET /actuator/health).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 teamora \
    && useradd --system --uid 10001 --gid teamora --no-create-home --shell /usr/sbin/nologin teamora
WORKDIR /app
COPY --from=build --chown=teamora:teamora /app/target/*.jar app.jar
USER teamora:teamora
EXPOSE 8080
# Size the heap from the container's memory limit (75%), not the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
