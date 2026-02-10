# AI Chatbot API Specification

## 1. 개요
웹, 모바일 등 다양한 클라이언트에서 사용할 수 있는 범용 AI 챗봇 REST API 서버입니다.
OpenAI GPT API를 연동하여 대화를 처리하고, 대화 이력을 저장하며 SSE 스트리밍을 지원합니다.

- **Base Path**: `/api`
- **Content-Type**: `application/json; charset=utf-8` (스트리밍 제외)

## 2. API 엔드포인트 목록

| Method | Endpoint | 설명 | Auth |
|---|---|---|---|
| POST | `/api/chat/completions` | 메시지 전송 및 AI 응답 (JSON) | Optional -> Required |
| POST | `/api/chat/completions/stream` | 메시지 전송 및 AI 응답 (SSE Stream) | Optional -> Required |
| GET | `/api/conversations` | 대화 목록 조회 (최신순) | Optional -> Required |
| GET | `/api/conversations/{id}` | 대화 상세 조회 | Optional -> Required |
| GET | `/api/conversations/{id}/messages` | 대화 내 메시지 목록 조회 | Optional -> Required |
| PATCH | `/api/conversations/{id}` | 대화 제목 수정 | Optional -> Required |
| DELETE | `/api/conversations/{id}` | 대화 삭제 | Optional -> Required |
| GET | `/health` | 서버 헬스 체크 | No |

---

## 3. 상세 명세

### 3.1 채팅 응답 생성 (Non-Streaming)
**POST** `/api/chat/completions`

**Request Body**
```json
{
  "message": "안녕하세요",
  "conversation_id": "1" // 선택사항 (없으면 신규 생성)
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "conversation_id": "1",
    "message": {
      "id": "102",
      "role": "assistant",
      "content": "안녕하세요! 무엇을 도와드릴까요?",
      "created_at": "2026-02-04T10:00:00Z"
    }
  }
}
```

### 3.2 채팅 응답 생성 (Streaming)
**POST** `/api/chat/completions/stream`

**Request Body**
- 위와 동일

**Response (200 OK)**
- **Content-Type**: `text/event-stream`
```
event: token
data: {"text": "안녕"}

event: token
data: {"text": "하세요"}

event: done
data: {}
```

### 3.3 대화 목록 조회
**GET** `/api/conversations`

**Response (200 OK)**
```json
{
  "success": true,
  "data": [
    {
      "id": "1",
      "title": "첫 번째 대화",
      "created_at": "2026-02-04T09:00:00Z",
      "updated_at": "2026-02-04T10:00:00Z"
    }
  ]
}
```

### 3.4 대화 메시지 조회
**GET** `/api/conversations/{id}/messages`

**Response (200 OK)**
```json
{
  "success": true,
  "data": [
    {
      "id": "101",
      "role": "user",
      "content": "안녕",
      "created_at": "..."
    },
    {
      "id": "102",
      "role": "assistant",
      "content": "안녕하세요!",
      "created_at": "..."
    }
  ]
}
```

### 3.5 에러 응답 포맷
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE", // 예: UNAUTHORIZED, NOT_FOUND
    "message": "에러 상세 메시지"
  }
}
```
