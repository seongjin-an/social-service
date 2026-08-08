# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY build.gradle.kts settings.gradle.kts ./
COPY common/build.gradle.kts common/
COPY eureka-server/build.gradle.kts eureka-server/
COPY api-gateway/build.gradle.kts api-gateway/
COPY user-service/build.gradle.kts user-service/
COPY connection-service/build.gradle.kts connection-service/
COPY message-service/build.gradle.kts message-service/
COPY fanout-delivery-service/build.gradle.kts fanout-delivery-service/
RUN ./gradlew :fanout-delivery-service:dependencies --no-daemon -q

COPY common/src common/src
COPY fanout-delivery-service/src fanout-delivery-service/src

RUN ./gradlew :fanout-delivery-service:bootJar -x test --no-daemon

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY infra/otel/opentelemetry-javaagent.jar otel-agent.jar
COPY --from=builder /workspace/fanout-delivery-service/build/libs/*.jar app.jar

EXPOSE 8084
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-javaagent:/app/otel-agent.jar", \
  "-jar", "app.jar"]
