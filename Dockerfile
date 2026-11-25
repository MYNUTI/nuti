# ── Stage 1: Build ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Run ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
# 힙 최대치를 서버 메모리의 50%로 — 기본 25%(서버 909MB → 약 227MB)로는 상품 적재 · 등급 전량 계산 중 메모리가 모자란다
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]