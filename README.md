# 🤖 AI Chatbot API Server

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.12-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Deployment](https://img.shields.io/badge/Live-Render-success.svg)](https://ai-chatbot-rs7c.onrender.com/health)

다양한 클라이언트(Web, Mobile)에서 즉시 연동 가능한 **엔터프라이즈급 AI 챗봇 REST API 서버**입니다. OpenAI GPT-4 모델을 활용한 지능형 대화와 실시간 스트리밍(SSE)을 지원하며, 상용 환경 배포를 위한 안정적인 인프라 설계를 포함합니다.

---

## 🚀 주요 기능 (Key Features)

- **Intelligent Conversation**: OpenAI GPT API 기반의 문맥 인지 대화
- **SSE Streaming**: `text/event-stream` 기반 실시간 토큰 전송 (사용자 경험 극대화)
- **Rate Limiting**: Redis 기반 실시간 트래픽 제어 (DoS 방지 및 비용 최적화)
- **Security**: API Key 기반 인증 및 필터 기반 로깅 시스템 (MDC 추적)
- **Robust Persistence**: PostgreSQL 기반 대화 이력 및 컨텍스트 관리 (최근 20개 메시지 유지)

---

## 🛠 기술 스택 (Tech Stack)

### Backend
- **Core**: Spring Boot 3.2.12, Java 17
- **Security**: Spring Security 6 (API Key Auth)
- **Web**: Spring WebFlux (WebClient for Non-blocking API calls)
- **ORM**: Spring Data JPA (Hibernate)

### Infrastructure
- **Deployment**: **Render (Blueprints)**
- **Database**: Managed PostgreSQL 18
- **Cache**: Valkey 8 (Managed Redis Service)
- **Container**: Docker (Multi-stage, Layered JAR, JarLauncher)

---

## ☁️ 배포 아키텍처 (Deployment Details)

본 프로젝트는 **Render** 환경에 최적화되어 있으며, 다음의 기술적 난제를 해결하여 배포되었습니다:

- **JDBC URL Runtime assembly**: Render의 `connectionString`(`postgresql://`) 규격을 JDBC 표준(`jdbc:postgresql://`)으로 자동 변환하는 런타임 엔트리포인트 설계 (`entrypoint.sh`).
- **Region Optimized**: 서비스 지연 시간을 최소화하기 위한 인프라 리전 동기화 (Oregon US-West).
- **Zero-Config Blueprints**: `render.yaml` 작성을 통해 클릭 한 번으로 DB, Redis, Web Service를 자동 연계 생성.

---

## 🌐 실시간 서비스 확인
배포된 서버의 상태와 API 문서를 아래 링크를 통해 즉시 확인하실 수 있습니다.
*   **서버 상태 확인 (Health Check)**: [https://ai-chatbot-rs7c.onrender.com/health](https://ai-chatbot-rs7c.onrender.com/health)
    *   접속 시 `{"status": "UP", ...}` 메시지가 나오면 서버가 정상 가동 중입니다.
*   **인터랙티브 API 문서 (Swagger)**: [https://ai-chatbot-rs7c.onrender.com/swagger-ui.html](https://ai-chatbot-rs7c.onrender.com/swagger-ui.html)
    *   웹 브라우저에서 직접 API를 테스트해 볼 수 있습니다.

---

## 🔌 API 사용 안내

### 인증 방법
모든 API 호출 시 헤더에 서비스 등록된 `X-API-Key`를 포함해야 합니다.
```bash
curl -X POST https://ai-chatbot-rs7c.onrender.com/api/chat/completions \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"message": "안녕?", "conversationId": 1}'
```

### 주요 엔드포인트
- `POST /api/chat/completions`: 일반 대화 응답
- `POST /api/chat/completions/stream`: SSE 실시간 스트리밍 답변
- `GET /health`: 서버 및 DB 연결 상태 확인 (공개 경로)
- `GET /swagger-ui.html`: 인터랙티브 API 문서

---

## 🧪 로컬 개발 환경 구축

```bash
# 1. 소스코드 복제
git clone https://github.com/developer-kanghyun/ai-chatbot.git

# 2. 인프라 실행 (Docker Desktop 필요)
docker-compose up -d

# 3. 환경 변수 설정
export OPENAI_API_KEY=your_key
export SPRING_PROFILES_ACTIVE=prod

# 4. 애플리케이션 실행
./gradlew bootRun
```

---

## 🧪 테스트 (Testing)
통합 테스트를 통해 로직 무결성을 검증합니다.
```bash
./gradlew test
```
- `ChatStreamIntegrationTest`: SSE 스트리밍 검증
- `RateLimitIntegrationTest`: 속도 제한 로직 검증

---
