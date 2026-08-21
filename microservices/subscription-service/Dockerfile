# syntax=docker/dockerfile:1
# Build context: repo root (telco-crm/).
# docker build -f microservices/subscription-service/Dockerfile -t subscription-service .
ARG PLATFORM_BUILDER_IMAGE=telco-platform-builder:local
FROM ${PLATFORM_BUILDER_IMAGE} AS builder
WORKDIR /workspace

COPY microservices/subscription-service/src              microservices/subscription-service/src

# Platform reactor is already installed in the telco-platform-builder base image
# (built once, shared by every service) - this layer only compiles this service.
RUN mvn -f microservices/pom.xml -pl subscription-service package -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
COPY --from=builder /workspace/microservices/subscription-service/target/*.jar app.jar
EXPOSE 9005
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=12 \
  CMD curl -fsS http://localhost:9005/actuator/health | grep -q UP || exit 1
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
