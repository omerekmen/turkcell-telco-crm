# syntax=docker/dockerfile:1
# Build context: repo root (telco-crm/).
# docker build -f microservices/payment-service/Dockerfile -t payment-service .
FROM telco-platform-builder:local AS builder
WORKDIR /workspace

COPY microservices/payment-service/src              microservices/payment-service/src

# Platform reactor is already installed in the telco-platform-builder base image
# (built once, shared by every service) - this layer only compiles this service.
RUN mvn -f microservices/pom.xml -pl payment-service package -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
COPY --from=builder /workspace/microservices/payment-service/target/*.jar app.jar
EXPOSE 9008
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=12 \
  CMD curl -fsS http://localhost:9008/actuator/health | grep -q UP || exit 1
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
