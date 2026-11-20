# Multi-stage build. One Maven package produces every service jar;
# each runtime target copies only its own executable.

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app

FROM runtime AS inventory-service
COPY --from=build /src/inventory-service/target/inventory-service-exec.jar /app/app.jar
EXPOSE 8082 9090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

FROM runtime AS order-service
COPY --from=build /src/order-service/target/order-service-exec.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
