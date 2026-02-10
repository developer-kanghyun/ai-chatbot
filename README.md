# AI Chatbot Service (2일차 제출)

Spring Boot 기반의 OpenAI Chat Completions API를 활용한 채팅 서비스입니다.

## 기술 스택
- Java 17
- Spring Boot 3.x
- Gradle
- WebClient (OpenAI API 통신)
- InMemory Repository (Conversation/Message 저장)
- Swagger/OpenAPI 문서화

## 실행 방법

### 1. 환경변수 설정
프로젝트 루트에 `.env` 파일을 생성하고 다음 내용을 설정합니다:

```properties
OPENAI_API_KEY=sk-proj-your-api-key-here
DB_PASSWORD=your-db-password
```

### 2. 애플리케이션 실행

```bash
# 환경변수 로드 후 실행
export $(cat .env | xargs) && ./gradlew bootRun
```

### 3. API 문서 확인
브라우저에서 Swagger UI 접속:
```
http://localhost:8080/swagger-ui.html
```

## API 사용법

### POST /api/chat/completions

OpenAI를 활용한 채팅 완료 API입니다.

**요청 예시 (신규 대화 시작):**
```bash
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "message": "안녕하세요, 자기소개 해주세요"
  }'
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "conversation_id": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
    "message": {
      "id": "m1n2o3p4-5678-90ab-cdef-1234567890ab",
      "role": "assistant",
      "content": "안녕하세요! 저는 AI 어시스턴트입니다...",
      "created_at": "2026-02-09T03:00:00.123Z"
    }
  }
}
```

**요청 예시 (기존 대화 이어가기):**
```bash
curl -X POST http://localhost:8080/api/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "message": "고마워요!",
    "conversation_id": "a1b2c3d4-5678-90ab-cdef-1234567890ab"
  }'
```

### GET /api/conversations

사용자의 대화 목록을 최신순으로 조회합니다. (현재 user_id=1 고정)

**요청 예시:**
```bash
curl -X GET http://localhost:8080/api/conversations
```

**응답 예시:**
```json
{
  "success": true,
  "data": [
    {
      "id": "1",
      "title": "안녕하세요",
      "created_at": "2024-01-20T10:00:00Z",
      "updated_at": "2024-01-20T10:05:00Z"
    }
  ]
}
```

### GET /api/conversations/{conversationId}

특정 대화의 상세 정보와 메시지 히스토리를 조회합니다.

**요청 예시:**
```bash
curl -X GET http://localhost:8080/api/conversations/1
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "id": "1",
    "title": "안녕하세요",
    "messages": [
      {
        "id": "1",
        "role": "user",
        "content": "안녕",
        "created_at": "..."
      },
      {
        "id": "2",
        "role": "assistant",
        "content": "안녕하세요! 무엇을 도와드릴까요?",
        "created_at": "..."
      }
    ],
    "created_at": "...",
    "updated_at": "..."
  }
}
```

### 에러 응답

**400 Bad Request (Validation 에러):**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "message는 필수입니다."
  }
}
```

**404 Not Found (존재하지 않는 대화):**
```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "대화를 찾을 수 없습니다: [conversation_id]"
  }
}
```

**500 Internal Server Error (서버/OpenAI 오류):**
```json
{
  "success": false,
  "error": {
    "code": "INTERNAL_SERVER_ERROR",
    "message": "서버 내부 오류가 발생했습니다."
  }
}
```

## 에러 코드 목록

| 코드 | 설명 | HTTP 상태 |
|------|------|-----------|
| VALIDATION_ERROR | 입력값 검증 실패 | 400 |
| NOT_FOUND | 리소스를 찾을 수 없음 | 404 |
| INTERNAL_SERVER_ERROR | 서버 내부 오류 | 500 |

## 설계 선택사항

### 1. UUID 선택 이유
- **고유성 보장**: 분산 환경에서도 충돌 없이 ID 생성 가능
- **예측 불가**: 순차 ID 대비 보안상 유리
- **문자열 호환**: REST API에서 자연스럽게 사용 가능

### 2. OpenAI 호출 실패 시 저장 정책
- **User 메시지는 저장 유지**: OpenAI 호출 실패와 무관하게 사용자의 입력은 기록
- **Assistant 메시지 미저장**: 응답이 없으면 저장하지 않음
- **이유**: InMemory 구조라 롤백이 없으므로, 최소한의 데이터(사용자 입력)만 보존하는 정책 채택. 추후 DB 트랜잭션 도입 시 재검토 필요.

### 3. InMemory Repository
- **최소 구현**: `ConversationRepository`, `MessageRepository` 인터페이스로 분리
- **교체 용이성**: 추후 JPA 구현체로 교체 시 Service 레이어 수정 불필요
- **Thread-Safe**: `ConcurrentHashMap` 사용으로 동시성 보장

### 4. 응답 텍스트만 저장하는 이유 (2일차 범위)
2일차는 빠른 제출과 안정성이 목표이므로, OpenAI의 Full Response(usage, tokens, finish_reason 등)를 저장하거나 리포트하는 기능은 범위에서 제외했습니다. `assistant` 답변 텍스트만 받아서 저장 및 응답하는 최소 구현으로 진행했습니다. Full Response 저장은 추후 확장 시 고려 가능합니다.

## 테스트 실행

```bash
# 전체 테스트 실행
export $(cat .env | xargs) && ./gradlew test

# 특정 테스트만 실행
export $(cat .env | xargs) && ./gradlew test --tests ChatControllerTest
```

## 프로젝트 구조

```
src/main/java/com/example/chatbot/
├── config/           # 설정 (OpenAI, Swagger 등)
├── controller/       # REST API 컨트롤러
├── domain/           # 도메인 모델 (Conversation, Message, Role)
├── dto/              # 데이터 전송 객체
│   ├── common/       # 공통 응답 포맷
│   ├── request/      # API 요청
│   ├── response/     # API 응답
│   └── openai/       # OpenAI API DTO
├── exception/        # 예외 처리
├── repository/       # 저장소 인터페이스 및 구현
│   └── chat/
│       └── memory/   # InMemory 구현체
└── service/          # 비즈니스 로직
```

## 작성자
- 김강현 (developer-kanghyun)

## 라이센스
MIT
