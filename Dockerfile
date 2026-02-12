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

# Spring Boot 레이어 추출 (SB 3.2 공식 패턴)
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination extracted

# Stage 2: 런타임
FROM eclipse-temurin:17-jre-focal
WORKDIR /app

# 보안: Non-root 유저 설정
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# 추출된 4개의 레이어 복사 (의존성 -> 로더 -> 스냅샷 -> 애플리케이션 순)
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

# Render 포트 바인딩 검증용 환경변수 (런타임 주입 PORT가 없을 경우 대비)
ENV PORT=8080

# Spring Boot 3.2+ JarLauncher 기반 실행
# 3.2부터 loader 패키지가 .launch 아래로 변경됨
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
