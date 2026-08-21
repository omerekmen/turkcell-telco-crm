# syntax=docker/dockerfile:1
# Build context: repo root (telco-crm/).
# docker build -f microservices/config-server/Dockerfile -t config-server .
FROM telco-platform-builder:local AS builder
WORKDIR /workspace

COPY microservices/configs                      microservices/configs
COPY microservices/config-server/src              microservices/config-server/src

# Platform reactor is already installed in the telco-platform-builder base image
# (built once, shared by every service) - this layer only compiles this service.
RUN mvn -f microservices/pom.xml -pl config-server package -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
# Bake configs into the image for self-contained dev containers.
# Override CONFIG_DIR to a mounted volume path in staging/prod.
COPY --from=builder /workspace/microservices/configs /configs
COPY --from=builder /workspace/microservices/config-server/target/*.jar app.jar
ENV CONFIG_DIR=/configs
EXPOSE 8888
# /actuator/health is behind config-server basic auth; use the committed default creds.
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=12 \
  CMD curl -fsS -u config:config http://localhost:8888/actuator/health | grep -q UP || exit 1
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
