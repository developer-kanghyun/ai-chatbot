# Stage 1: 빌드 및 레이어 추출
FROM eclipse-temurin:17-jdk-focal AS builder
WORKDIR /build

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar -x test --no-daemon
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination extracted

# Stage 2: 런타임
FROM eclipse-temurin:17-jre-focal
WORKDIR /app

# 보안: Non-root 유저 설정
RUN groupadd -r spring && useradd -r -g spring spring

# 추출된 레이어 복사
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

# 진입점 스크립트 복사 및 권한 설정
COPY entrypoint.sh .
RUN chmod +x entrypoint.sh && chown spring:spring entrypoint.sh

USER spring:spring

# ENTRYPOINT 설정 (고정 PORT 제거)
ENTRYPOINT ["./entrypoint.sh"]
