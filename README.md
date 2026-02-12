# 🤖 AI Chatbot API Server

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.12-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![OpenAI](https://img.shields.io/badge/OpenAI-GPT--4-orange.svg)](https://openai.com/)
[![Render](https://img.shields.io/badge/Deployment-Render-lightgrey.svg)](https://render.com/)

다양한 클라이언트(Web, Mobile, Desktop)에서 즉시 연동 가능한 **범용 AI 챗봇 REST API 서버**입니다. OpenAI GPT 모델을 기반으로 지능적인 대화 처리를 지원하며, 실시간 답변 생성을 위한 SSE 스트리밍 및 대화 맥락 유지 기능을 제공합니다.

---

## 🚀 주요 기능 (Key Features)

- **Chat Completion**: OpenAI GPT API 연동을 통한 지능적인 대화 응답
- **Context Management**: 최근 20개의 메시지를 기반으로 한 지속적인 대화 맥락 유지
- **SSE Streaming**: `text/event-stream` 기반의 실시간 응답 (Token-by-token)
- **Security & Auth**: `X-API-Key` 헤더 기반의 보안 인증 적용
- **Rate Limiting**: Redis 기반의 실시간 요청 횟수 제한 (과도한 API 호출 방지)
- **Persistent Storage**: PostgreSQL을 사용한 모든 대화 세션 및 메시지 이력 관리
- **Integrated Logging**: MDC 기반의 Request ID 추적 및 HTTP 상세 로깅 (민감 정보 마스킹)

---

## 🛠 기술 스택 (Tech Stack)

### Backend Core
- **Framework**: Spring Boot 3.2.12 (Java 17)
- **Security**: Spring Security 6 (API Key Filter)
- **Data**: Spring Data JPA / Hibernate
- **Communication**: Spring WebFlux (WebClient for OpenAI API)

### Infrastructure & DevOps
- **Database**: PostgreSQL (Persistence)
- **Cache**: Redis (Rate Limiting)
- **Documentation**: Springdoc OpenAPI / Swagger UI
- **Container**: Docker (Multi-stage, Layered JAR)
- **CI/CD**: Render (Blueprint Template)

---

## 📊 데이터베이스 설계 (ERD)

| 테이블 | 설명 | 관계 |
| :--- | :--- | :--- |
| **users** | API Key 및 사용자 기본 정보 관리 | `1 : N` (Conversations) |
| **conversations** | 대화 세션 정보 (제목, 생성일 등) | `1 : N` (Messages) |
| **messages** | 질문(user) 및 답변(assistant) 상세 내용 | - |

---

## 🔌 API 명세 요약

모든 요청은 헤더에 `X-API-Key`가 포함되어야 합니다.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| **POST** | `/api/chat/completions` | 일반 채팅 응답 반환 |
| **GET** | `/api/chat/completions/stream` | 실시간 스트라이밍 응답 (SSE) |
| **GET** | `/api/conversations` | 대화 목록 조회 |
| **GET** | `/api/conversations/{id}` | 대화 상세 조회 |
| **GET** | `/api/conversations/{id}/messages` | 특정 대화의 메시지 내역 조회 |
| **DELETE** | `/api/conversations/{id}` | 대화 세션 삭제 |
| **GET** | `/health` | 서버 상태 체크 (Permit All) |

> 📖 **Swagger UI**: 서버 실행 후 `http://localhost:8080/swagger-ui.html`에서 상세 명세 확인 및 테스트가 가능합니다.

---

## ⚙️ 시작하기 (Getting Started)

### 환경 변수 설정 (.env 또는 OS Env)
서버 실행을 위해 다음 환경 변수가 필요합니다.
```properties
SPRING_PROFILES_ACTIVE=prod
OPENAI_API_KEY=your_openai_api_key_here
DB_PASSWORD=your_db_password
REDIS_HOST=localhost
REDIS_PORT=6379
```

### 로컬 실행 (Docker Compose)
제공된 설정으로 인프라를 한 번에 실행할 수 있습니다.
```bash
docker-compose up -d
./gradlew bootRun
```

---

## 📦 배포 (Deployment)

본 프로젝트는 **Render** 배포를 공식 지원합니다. `render.yaml` 파일을 통해 Blueprint 배포가 가능합니다.

1. GitHub 저장소 연결
2. `render.yaml` 감지 시 즉시 배포 시작
3. 환경 변수(`OPENAI_API_KEY`) 설정 후 완료

---

## 🧪 테스트 (Testing)
통합 테스트를 통해 로직 무결성을 검증합니다.
```bash
./gradlew test
```
- `ChatStreamIntegrationTest`: SSE 스트리밍 검증
- `RateLimitIntegrationTest`: 속도 제한 로직 검증

---

## 📄 라이선스 (License)
본 프로젝트는 교육용 과제로 제작되었으며, 상업적 목적으로 사용 시 OpenAI 사용 정책을 준수해야 합니다.
