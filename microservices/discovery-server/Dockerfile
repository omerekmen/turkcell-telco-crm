# syntax=docker/dockerfile:1
# Build context: repo root (telco-crm/).
# docker build -f microservices/discovery-server/Dockerfile -t discovery-server .
FROM telco-platform-builder:local AS builder
WORKDIR /workspace

COPY microservices/discovery-server/src              microservices/discovery-server/src

# Platform reactor is already installed in the telco-platform-builder base image
# (built once, shared by every service) - this layer only compiles this service.
RUN mvn -f microservices/pom.xml -pl discovery-server package -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
COPY --from=builder /workspace/microservices/discovery-server/target/*.jar app.jar
EXPOSE 8761
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=12 \
  CMD curl -fsS http://localhost:8761/actuator/health | grep -q UP || exit 1
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
