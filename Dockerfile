# Stage 1: 빌드 및 레이어 추출
FROM eclipse-temurin:17-jdk-focal AS builder
WORKDIR /build

# 캐시 효율을 위해 의존성 파일 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스 복사 및 JAR 생성
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Spring Boot 레이어 추출
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

# 진입점 스크립트 복사 및 설정
COPY entrypoint.sh .
RUN chmod +x entrypoint.sh && chown spring:spring entrypoint.sh

USER spring:spring

ENV PORT=8080
ENTRYPOINT ["./entrypoint.sh"]
