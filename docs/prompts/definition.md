## 1. 프로젝트 개요

### 1.1 목적

- 다양한 클라이언트(웹/모바일/데스크톱)에서 호출 가능한 AI 챗봇 REST API 서버를 제공한다.
- OpenAI API(GPT)를 연동해 대화를 생성하고 이력을 저장/조회/삭제할 수 있도록 한다.
- Swagger/OpenAPI 문서화를 통해 개발자가 즉시 테스트하고 연동할 수 있게 한다.

### 1.2 목표

- 중간제출(2주차 수요일): 설계 문서 + 핵심 채팅 API 동작 증빙
- 최종평가(4주차): 스트리밍(SSE) + API Key 인증 + 컨텍스트 관리 + Swagger 완성도 + 배포 완료

### 1.3 설계 원칙

- RESTful 설계: 각 HTTP 메서드는 의미에 맞게 사용 (POST=생성/추가, GET=조회, PATCH=부분수정, DELETE=삭제)
- 단일 책임: 각 엔드포인트는 하나의 비즈니스 요청만 처리
- 트랜잭션 안전성: 복합 작업(메시지 전송 시 여러 리소스 변경)은 원자성 보장

---

## 2. 범위/전제

### 2.1 In Scope

- 대화(Conversation) 및 메시지(Message) CRUD 중 필요 부분(C/R/D + 제목 수정)
- GPT 연동(비스트리밍/스트리밍)
- 컨텍스트 관리(최근 N개 메시지)
- API Key 기반 인증 + Rate limiting(선택)
- Swagger/OpenAPI 자동 문서화
- Docker 컨테이너화 및 Railway/Render 배포
- 헬스체크 + 구조화된 로깅

### 2.2 Out of Scope

- 웹 UI/클라이언트 개발
- 멀티모달 처리(이미지/파일)

### 2.3 초기 운영 정책

- 중간제출 전까지는 단일 사용자로 운영한다(모든 대화는 `user_id=1`로 저장).
- 3주차부터 `X-API-Key` 헤더 기반으로 요청자를 식별하고 사용자별 데이터를 분리한다.

---

## 3. 공통 API 규격

### 3.1 기본 정보

- Base Path: `/api`
- 기본 요청/응답 Content-Type: `application/json; charset=utf-8`
- (예외) 스트리밍 응답(FR-08)은 `Content-Type: text/event-stream`을 사용한다. [web:563]
- 시간 포맷: ISO 8601 (예: `2026-02-03T11:30:00Z`)

### 3.2 공통 응답 포맷

**성공**

```json
{
  "success": true,
  "data": { }
}
```

**실패**

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지"
  }
}
```

### 3.3 HTTP 상태 코드

| 코드 | 의미 | 사용 시기 |
| --- | --- | --- |
| 200 | OK | 조회/수정 성공 |
| 201 | Created | 리소스 생성 성공 |
| 400 | Bad Request | 요청 유효성 실패(필드 누락, 형식 오류) |
| 401 | Unauthorized | 인증 실패(API Key 미제공/불일치) |
| 404 | Not Found | 리소스 없음 |
| 429 | Too Many Requests | Rate limit 초과 |
| 500 | Internal Server Error | 서버 오류(DB, OpenAI 호출 등) |

### 3.4 ID 및 타입

- API 응답에서 `id`는 **string 타입**으로 제공한다(내부 DB PK가 Long이어도 직렬화 시 string으로 변환).

---

## 4. 기능 요구사항(FR)

### FR-01 채팅 완료 (비스트리밍)

**Endpoint**: `POST /api/chat/completions`

**Request Body**

```json
{
  "message": "사용자가 입력한 메시지",
  "conversation_id": "기존 대화 ID (선택사항)"
}
```

**처리 흐름**

1. `conversation_id` 이 없으면 새 Conversation을 생성(user_id는 초기 정책상 1로 고정, 제목은 message 앞부분 최대 50자)
2. 사용자 메시지를 저장(role=`"user"`, content=message)
3. 컨텍스트 정책(FR-07)에 따라 최근 N개 메시지를 구성하여 OpenAI API로 전달
4. AI 응답을 저장(role=`"assistant"`)
5. `conversation_id`와 저장된 assistant 메시지를 응답으로 반환

**Response (200)**

```json
{
  "success": true,
  "data": {
    "id": "123",
    "role": "assistant",
    "content": "AI가 생성한 응답",
    "created_at": "2026-02-03T11:30:00Z"
  }
}
```

**에러**

- 400: message 누락 또는 빈 문자열
- 404: 지정한 conversation_id가 존재하지 않음
- 500: OpenAI 호출 실패 또는 DB 오류

**수용기준**

- 요청 1회당 DB의 messages 테이블에 user/assistant 2건이 저장된다
- conversation_id 미지정 시 새 conversation이 생성되고, 응답의 conversation_id로이후 조회로 확인 가능하다
- Postman 시연 영상: 호출 → 응답 확인 → DB(DBeaver/psql)에서 저장 확인 가능
- 같은 conversation_id로 연속 호출 시 이전 메시지들이 컨텍스트로 포함되어 응답된다(FR-06 활성화 시)

---

### FR-02 대화 목록 조회

**Endpoint**: `GET /api/conversations`

**Response (200)**

```json
{
  "success": true,
  "data": [
    {
      "id": "1",
      "title": "자바 equals와 == 차이",
      "created_at": "2026-02-03T21:00:00Z",
      "updated_at": "2026-02-03T21:30:00Z"
    }
  ]
}
```

**수용기준**

- 최소 1개 이상 대화가 있을 때 배열로 반환한다
- 각 항목에 id, title, created_at, updated_at이 포함된다
- 초기 정책상 user_id=1의 대화만 반환된다(3주차 인증 도입 시 요청자 user별로 변경)

---

### FR-03 대화 상세 조회

**Endpoint**: `GET /api/conversations/{id}`

**Response (200)**

```json
{
  "success": true,
  "data": {
    "id": "1",
    "title": "자바 equals와 == 차이",
    "created_at": "2026-02-03T21:00:00Z",
    "updated_at": "2026-02-03T21:30:00Z"
  }
}
```

**에러**

- 404: 없는 conversation id

**수용기준**

- 존재하는 id는 200으로 반환
- 존재하지 않으면 404 + 공통 에러 포맷

---

### FR-04 대화 메시지 조회

**Endpoint**: `GET /api/conversations/{id}/messages`

**Response (200)**

```json
{
  "success": true,
  "data": [
    {
      "id": "101",
      "role": "user",
      "content": "자바에서 equals와 == 차이가 뭐야?",
      "created_at": "2026-02-03T21:30:00Z"
    },
    {
      "id": "102",
      "role": "assistant",
      "content": "== 는 참조 비교, equals는 값 비교입니다...",
      "created_at": "2026-02-03T21:30:05Z"
    }
  ]
}
```

**에러**

- 404: 없는 conversation id

**수용기준**

- 메시지를 생성 시간(created_at) 오름차순으로 반환한다(프론트가 그대로 렌더 가능)
- role은 "user" 또는 "assistant"이다
- 각 메시지는 id, role, content, created_at을 포함한다

---

### FR-05 대화 삭제

**Endpoint**: `DELETE /api/conversations/{id}`

**Response (200)**

```json
{
  "success": true,
  "data": null
}
```

**에러**

- 404: 없는 conversation id

**수용기준**

- 200 + `{success:true, null}` 반환
- 삭제 후 동일 conversation_id로 GET 조회 시 404 반환
- 하위 messages는 연쇄 삭제된다(DB constraint 또는 로직으로 보장)

---

### FR-06 대화 제목 수정

**Endpoint**: `PATCH /api/conversations/{id}`

**Request Body**

```json
{
  "title": "새로운 대화 제목"
}
```

**Response (200)**

```json
{
  "success": true,
  "data": {
    "id": "1",
    "title": "새로운 대화 제목",
    "created_at": "2026-02-03T21:00:00Z",
    "updated_at": "2026-02-03T21:35:00Z"
  }
}
```

**에러**

- 400: title 누락 또는 빈 문자열
- 404: 없는 conversation id

**수용기준**

- title만 업데이트되고 다른 필드는 불변이다
- updated_at이 현재 시간으로 갱신된다
- PATCH이므로 부분 수정 의미를 명확히 한다(PUT과 달리 전체 리소스를 보낼 필요 없음)

---

### FR-07 컨텍스트 관리(최근 N개)

**설명**: 토큰/비용을 제어하면서도 대화 맥락을 유지한다.

**구현 정책**

- 최근 N개 메시지만 OpenAI 요청에 포함(기본값 N=10)
- N은 설정 파일(application.yml, 환경변수) 또는 상수로 변경 가능해야 함

**수용기준**

- 동일 conversation에서 "이전 발화"를 참조해야만 정확히 답할 수 있는 테스트 케이스 1개 이상 성공
- 컨텍스트 window 크기를 조정 가능하게 설계(하드코딩 금지)
- OpenAI 요청 시 messages 배열에 최근 N개가 포함되는 로직 + 로그 또는 Swagger로 증빙 가능

---

### FR-08 스트리밍 응답(SSE)

**Endpoint**: POST /api/chat/completions/stream

**응답 규격**

- `Content-Type: text/event-stream` web:563
- SSE 이벤트는 `event:` / `` 같은 필드 라인들로 구성되며, 빈 줄(개행 2번) 로 이벤트 블록을 구분한다. web:289web:4
- ``는 여러 줄로 올 수 있고, 클라이언트는 이를 하나의 data로 합쳐 처리한다(표준은 data buffer에 개행을 덧붙여 누적). web:289
- 종료 신호는 `event: done`으로 전달한다.

**Request Body**

FR-01과 동일

**예시 응답**

```
event: token
 {"text":"안녕"}

event: token
 {"text":"하세요"}

event: done
 {}
```

**처리 흐름**
1. `conversation_id`가 없으면 새 Conversation을 생성한다.
2. user 메시지를 저장한다.
3. OpenAI 응답을 토큰/청크 단위로 받아 `event: token`으로 즉시 전송한다.
4. 스트리밍이 끝나면 assistant 전체 응답을 저장하고 `event: done`을 전송한 뒤 연결을 종료한다.

**수용기준**

- 클라이언트가 응답을 "대기 후 일괄 수신"이 아니라, 생성 **중간부터 점진적으로 수신**한다
- 종료 이벤트(`event: done`)가 명확히 전달되어 연결이 정상 종료된다.
- 중간에 연결 끊김 시 재연결 로직 구현

---

### FR-09 API Key 인증

**적용 범위**: `/api/**` (단, `/health`, Swagger UI 제외)

**인증 방식**

- 요청 헤더: `X-API-Key: <key_value>`
- 키 유효성: 저장된 users.api_key와 일치 여부 확인

**처리**

- 키 없음: 401 + `{success:false, error:{code:"UNAUTHORIZED", message:"API Key is required"}}`
- 키 불일치: 401 + `{success:false, error:{code:"UNAUTHORIZED", message:"Invalid API Key"}}`
- 유효: 정상 처리

**OpenAPI 문서화**

- Swagger에서 `components.securitySchemes`에 `apiKey` 타입으로 정의
    - `in: header`, `name: X-API-Key`
- `security` 또는 operation별 `@SecurityRequirement` 적용
- Swagger UI에서 "Authorize" 버튼으로 API Key 입력 후 테스트 가능

**수용기준**

- 키 미제공/불일치 시 401 반환
- 유효 키로는 정상 호출
- Swagger UI에서 보호된 엔드포인트 테스트 가능
- OpenAPI 명세에 apiKey 보안 스킴 포함

---

### FR-10 Rate Limiting

**정책**: 시간 단위(분, 시간)로 요청 횟수 제한

**처리**

- 임계치 초과: 429 + `{success:false, error:{code:"RATE_LIMIT_EXCEEDED", message:"Too many requests"}}`
- 정상 범위: 영향 없음

**수용기준**

- 정상 트래픽(임계치 이하)은 200 반환
- 임계치 초과는 429 반환
- Rate limit 정보가 응답 헤더에 포함(선택: `X-RateLimit-Limit`, `X-RateLimit-Remaining` 등)

---

### FR-11 Swagger/OpenAPI 문서화

**도구**: springdoc-openapi-starter-webmvc-ui 또는 동등 라이브러리

**포함 사항**

- API 타이틀, 설명, 버전
- 각 엔드포인트의 설명, 요청/응답 예시, 파라미터 설명
- 에러 코드 및 의미(400, 401, 404, 429, 500)
- 인증 방식 문서화(API Key 헤더)
- Request/Response 스키마 자동 생성

**접근 경로**

- Swagger UI: `/swagger-ui.html` 또는 `/api-docs`
- OpenAPI JSON: `/v3/api-docs`

**수용기준**

- 모든 `/api/**` 엔드포인트가 Swagger UI에서 확인 가능
- 각 엔드포인트에서 "Try it out" 기능으로 바로 호출 테스트 가능
- API Key 인증 적용 시 Swagger에서 Authorize로 헤더 입력 후 테스트 가능

---

### FR-12 헬스체크

**Endpoint**: `GET /health`

**Response (200)**

```json
{
  "status": "UP",
  "timestamp": "2026-02-03T21:30:00Z"
}
```

**수용기준**

- 배포 환경에서 외부 접근 시 200 반환
- 모니터링/배포 자동화에서 활용 가능
- DB 연결 상태 등 간단한 상태 정보 포함(선택)

---

### FR-13 로깅 및 모니터링

**로깅 항목**

- 요청 단위: HTTP method, path, query string, status code, latency(ms)
- 에러: 스택 트레이스, 외부 API(OpenAI) 실패 원인 요약
- 구조화된 로그(JSON 또는 key=value) 권장

**수용기준**

- 모든 `/api/**` 요청이 표준 형식으로 로깅됨
- OpenAI 호출 실패 시 원인(네트워크, 타임아웃, API 오류 코드 등)이 기록됨
- 배포 환경에서 로그 수집/분석 가능(stdout 또는 파일)

---

### FR-14 Docker 및 배포

**구현**

- Dockerfile: Spring Boot 애플리케이션을 컨테이너화
- 환경변수: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `OPENAI_API_KEY` 등을 주입 가능
- 배포 플랫폼: Railway 또는 Render

**수용기준**

- 로컬에서 Docker로 빌드/실행 가능(`docker build` + `docker run`)
- 배포 환경에서 외부 URL로 접근 가능
- 환경변수로 DB/API Key 설정 가능(`.env.example` 또는 배포 대시보드에 명시)
- `/health` 엔드포인트로 서비스 상태 확인 가능

---

## 5. 데이터 요구사항 (ERD)

### 5.1 엔티티

- User: id, api_key
- Conversation: id, user_id, title, created_at, updated_at
- Message: id, conversation_id, role, content, created_at

### 5.2 관계

- User (1) : Conversation (N)
- Conversation (1) : Message (N)

### 5.3 데이터 정책

- Conversation 삭제 시 하위 Message는 함께 삭제되어야 한다(연쇄 삭제).
- Message.role은 user 또는 assistant 중 하나여야 한다.

### 테이블 정의

**users**

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL | 사용자 고유 ID |
| api_key | VARCHAR(255) | UNIQUE, NOT NULL | API 인증 키 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 시간 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 갱신 시간 |

**conversations**

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL | 대화 고유 ID |
| user_id | BIGINT | FK → [users.id](http://users.id/), NOT NULL | 대화 소유자 |
| title | VARCHAR(255) |  | 대화 제목 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 시간 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP, ON UPDATE CURRENT_TIMESTAMP | 갱신 시간 |

**messages**

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK, NOT NULL | 메시지 고유 ID |
| conversation_id | BIGINT | FK → [conversations.id](http://conversations.id/), NOT NULL | 소속 대화 |
| role | VARCHAR(50) | NOT NULL, ENUM('user', 'assistant') | 발화자 역할 |
| content | LONGTEXT | NOT NULL | 메시지 내용 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 시간 |

### 5.2 관계 및 제약

- **users : conversations = 1:N** (한 사용자가 여러 대화)
- **conversations : messages = 1:N** (한 대화가 여러 메시지)
- **Cascade Delete**: conversation 삭제 시 하위 messages 자동 삭제

### 5.3 인덱스 (최소)

```sql
-- 대화 조회 성능
CREATE INDEX idx_conversations_user_id_updated_at
ON conversations(user_id, updated_at DESC);

-- 메시지 조회 성능
CREATE INDEX idx_messages_conversation_id_created_at
ON messages(conversation_id, created_at ASC);
```

---

## 6. 제출 및 검증 기준

### 6.1 중간제출 (2주차 수요일, Pass/Fail)

**제출물**

1. **API 명세서 + ERD**: Notion 또는 Google Docs 링크 (FR-01~05, 데이터 테이블 모두 포함)
2. **시연 영상** (≤3분): Postman에서 아래 흐름 재현
    - `POST /api/chat/completions` 호출 (message, conversation_id 선택 지정)
    - 응답 확인 (success=true, data 포함)
    - DBeaver/psql에서 conversation/messages 저장 확인
3. **GitHub Public Repository**: 코드 공개, README.md에 실행 방법 포함

**합격 조건(Pass)**

- 3개 제출물 모두 완료
- API 호출 → DB 저장 흐름이 명확하게 확인됨
- 코드 실행 가능(로컬 또는 배포)

### 6.2 최종평가 (4주차)

**S등급 충족 조건**

- FR-07 (컨텍스트) 동작 확인
- FR-08 (스트리밍/SSE) 동작 확인
- FR-09 (API Key 인증) 동작 확인 + Swagger에 보안 스킴 포함
- FR-11 (Swagger) 완성도 높음 (모든 엔드포인트 테스트 가능)
- FR-14 (배포) 완료 (외부 URL + /health 동작)

**제출**

- GitHub Repository (최신 코드)
- 배포 URL 및 Swagger UI 링크
- 4주차 최종 시연 영상 (스트리밍, 인증, Swagger 테스트 포함)

# # 부록 - PostgreSQL 물리 스키마

## A.1 Enum 타입 (Message.role)

PostgreSQL enum은 `CREATE TYPE ... AS ENUM (...)`으로 먼저 타입을 만든 뒤, 컬럼 타입으로 사용합니다.

```markdown
CREATE TYPE message_role AS ENUM ('user', 'assistant');
```

---

## A.2 테이블 초안

```sql
-- users
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  api_key VARCHAR(255) UNIQUE NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- conversations
CREATE TABLE conversations (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  title VARCHAR(255),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- messages
CREATE TABLE messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  role message_role NOT NULL,
  content TEXT NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

```

---

## A.3 updated_at 자동 갱신 (DB 트리거 옵션)

PostgreSQL 트리거 함수는 인자 없이 만들고 `RETURNS trigger`로 선언하며, row-level BEFORE 트리거에서는 보통 `NEW`를 수정한 뒤 `RETURN NEW` 패턴을 사용합니다. 

```sql
-- updated_at 자동 세팅 함수
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- conversations.updated_at 트리거
CREATE TRIGGER trg_conversations_updated_at
BEFORE UPDATE ON conversations
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- users.updated_at 트리거(선택)
CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
```

---

## A.4 인덱스

```sql
-- 대화 목록: user별 최신 순 정렬 최적화
CREATE INDEX idx_conversations_user_id_updated_at
ON conversations(user_id, updated_at DESC);

-- 메시지 목록: 대화별 시간순 조회 최적화
CREATE INDEX idx_messages_conversation_id_created_at
ON messages(conversation_id, created_at ASC);
```

## 7. 기술 스택 (확정)

| 분야 | 기술 |
| --- | --- |
| 언어 | Java 11+ |
| 프레임워크 | Spring Boot 3.x |
| 웹 | Spring MVC |
| 데이터 | Spring Data JPA, PostgreSQL |
| 외부 API | OpenAI API (GPT-3.5-turbo / GPT-4) |
| 문서화 | springdoc-openapi-starter-webmvc-ui, Notion |
| 컨테이너 | Docker |
| 배포 | Railway |
| 빌드 | Gradle |

## MEMO) Swagger/OpenAPI 반영

Swagger(OpenAPI)에서는 응답 body의 포맷(미디어 타입)을 `responses.<code>.content.<media-type>`로 표현하므로 스트리밍 엔드포인트 응답은 `text/event-stream`을 media type으로 명시.

- Request(JSON): POST/PUT/PATCH는 requestBody.content.application/json에 schema(필수) 작성, example(선택)로 샘플 1개만 추가.
- Response(일반): responses.200.content.application/json에 schema/예시.
- Response(스트리밍): responses.200.content.text/event-stream 명시, 설명에 SSE 형식(event:/`` + 빈 줄, event: done)과 3줄 예시만 첨부.
- *기술 스택*
    
    
    | 분야 | 기술 | 선택 이유 |
    | --- | --- | --- |
    | 언어 | Java 11+ | JVM 기반, 타입 안전성, 엔터프라이즈 표준 |
    | 프레임워크 | Spring Boot 3.x | REST API 표준, 스프링 생태계, 산업 표준 |
    | 웹 프레임워크 | Spring MVC | Spring Boot 기본, RESTful API 구현 최적화 |
    | ORM | Spring Data JPA | 객체-DB 매핑 자동화, 보일러플레이트 감소, 트랜잭션 안전성 |
    | 데이터베이스 | PostgreSQL | 오픈소스, 높은 신뢰성, 한국 기업 광범위 지원 |
    | 외부 AI API | OpenAI API (GPT-3.5-turbo) | GPT AI와 상호작용 |
    | API 문서화 | Springdoc-openapi-starter-webmvc-ui (Swagger/OpenAPI) | 자동 생성, 실시간 테스트 가능, 산업 표준 |
    | 빌드 도구 | Gradle | 빠른 빌드 속도, 간결한 문법, 현대적 표준 |
    | 컨테이너 | Docker | 배포 환경 일관성, 포트폴리오 필수 기술 |
    | 배포 플랫폼 | Railway | 빠른 배포(~30초), 직관적 UI |
- *API 엔드포인트 설계*
    
    ## 1) API 기본 규약
    
    - Base path: `/api`
    - Content-Type: `application/json; charset=utf-8`
    - 공통 응답(성공/실패) 포맷은 문서를 그대로 사용
    - 인증(3주차~): `X-API-Key` 헤더 기반, Swagger(OpenAPI)에는 “apiKey in header” 보안 스킴으로 표현 가능
    
    ---
    
    ## 2) 엔드포인트 목록
    
    | 목적 | Method | Path | Auth | 비고 |
    | --- | --- | --- | --- | --- |
    | 채팅 응답 생성(비스트리밍) | POST | `/api/chat/completions` | Optional→Required | JSON 응답 |
    | 채팅 응답 생성(SSE 스트리밍) | POST | `/api/chat/completions/stream` | Optional→Required | `text/event-stream`(SSE) 응답 |
    | 대화 목록 조회 | GET | `/api/conversations` | Optional→Required | 최신순 |
    | 대화 상세 조회 | GET | `/api/conversations/{id}` | Optional→Required | 404 처리 |
    | 대화 메시지 목록 | GET | `/api/conversations/{id}/messages` | Optional→Required | 시간 오름차순 |
    | 대화 제목 수정 | PATCH | `/api/conversations/{id}` | Optional→Required | `{ "title": "..." }` |
    | 대화 삭제 | DELETE | `/api/conversations/{id}` | Optional→Required | 메시지 연쇄 삭제 |
    | 헬스체크 | GET | `/health` | No | 로드밸런서/모니터링용 |
    
    ---
    
    ## 3) 요청/응답 스키마
    
    ### 3.1 POST `/api/chat/completions`
    
    Request
    
    ```json
    {
      "message": "사용자 입력",
      "conversation_id": "선택"
    }
    ```
    
    Response 200
    
    ```json
    {
      "success": true,
      "data": {
        "conversation_id": "1",
        "message": {
          "id": "102",
          "role": "assistant",
          "content": "AI 응답",
          "created_at": "2026-02-03T21:30:05Z"
        }
      }
    }
    ```
    
    Error
    
    - 400: message 누락/빈값
    - 404: conversation_id 존재하지 않음
    - 500: OpenAI 호출 실패/DB 실패
    
     응답에 `conversation_id`를 항상 내려주면(신규 생성 케이스 포함) 프론트가 다음 호출을 연결하기 쉬워서 UX/구현 난이도가 내려감.
    
    ### 3.2 GET `/api/conversations`
    
    Response 200
    
    ```json
    {
      "success": true,
      "data": [
        {
          "id": "1",
          "title": "자바 equals와 == 차이",
          "created_at": "2026-02-03T21:00:00Z",
          "updated_at": "2026-02-03T21:30:00Z"
        }
      ]
    }
    ```
    
    - `limit`(기본 20), `cursor`(id 또는 updated_at 기반) 같은 페이지네이션은 3주차 이후 확장으로 두면 충분
    
    ### 3.3 GET `/api/conversations/{id}`
    
    Response 200: 단일 conversation
    Error: 404
    
    ### 3.4 GET `/api/conversations/{id}/messages`
    
    Response 200
    
    ```json
    {
      "success": true,
      "data": [
        { "id": "101", "role": "user", "content": "질문", "created_at": "..." },
        { "id": "102", "role": "assistant", "content": "답변", "created_at": "..." }
      ]
    }
    ```
    
    ### 3.5 PATCH `/api/conversations/{id}`
    
    Request
    
    ```json
    { "title": "새 제목" }
    ```
    
    Response 200: 업데이트된 conversation 반환
    
    Error: 400(빈 title), 404
    
    ### 3.6 DELETE `/api/conversations/{id}`
    
    Response 200
    
    ```json
    { "success": true, "data": null }
    ```
    
    Error: 404
    
    ### 3.7 GET `/api/health`
    
    Response 200
    
    ```json
    { "status": "UP", "timestamp": "2026-02-03T21:30:00Z" }
    ```
    
    ---
    
    ## 4) SSE 스트리밍 설계(FR-08)
    
    ### 4.1 POST `/api/chat/completions/stream`
    
    Request Body (JSON)
    
    ```json
    {
      "message": "사용자 입력",
      "conversation_id": "선택"
    }
    ```
    
    Response 헤더
    
    - `Content-Type: text/event-stream`
    
    SSE 이벤트 형식(표준)
    
    - 이벤트는 `event:` / `` 라인들로 구성되며, 빈 줄로 이벤트를 구분한다.
    - 종료 신호는 event: done으로 전달한다.
    
    예시)
    
    ```markdown
    event: token
     {"text":"안녕"}
    
    event: token
     {"text":"하세요"}
    
    event: done
     {}
    ```
    
    ## 5) OpenAI 연동(서버 내부)
    
    - 서버가 OpenAI API를 호출할 때는 OpenAI가 요구하는 방식대로 `Authorization: Bearer <OPENAI_API_KEY>` 헤더를 사용해야 함.
    
    ---
    
- DB 설계
    
    좋아. “복붙용 SQL”은 그대로 쓰되, 사람이 읽는 주석/섹션명만 한글로 바꿔줄게. (SQL 키워드/문법은 영어가 표준이라 `CREATE TABLE`, `PRIMARY KEY` 같은 건 그대로 두는 게 맞아.)
    
    ```sql
    -- =========================
    -- 테이블: users, conversations, messages
    -- =========================
    
    -- 0) 개발 환경 초기화용 드롭
    -- DROP TABLE IF EXISTS messages;
    -- DROP TABLE IF EXISTS conversations;
    -- DROP TABLE IF EXISTS users;
    -- DROP TYPE IF EXISTS message_role;
    
    -- 1) 메시지 역할 Enum 타입
    CREATE TYPE message_role AS ENUM ('user', 'assistant');
    
    -- 2) 사용자 테이블
    CREATE TABLE users (
      id BIGSERIAL PRIMARY KEY,
      api_key TEXT UNIQUE NOT NULL,
      created_at timestamptz NOT NULL DEFAULT now(),
      updated_at timestamptz NOT NULL DEFAULT now()
    );
    
    -- 3) 대화(Conversation) 테이블
    CREATE TABLE conversations (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- 사용자 삭제 시 대화도 함께 삭제
      title VARCHAR(255) NOT NULL,
      created_at timestamptz NOT NULL DEFAULT now(),
      updated_at timestamptz NOT NULL DEFAULT now()
    );
    
    -- 4) 메시지(Message) 테이블
    CREATE TABLE messages (
      id BIGSERIAL PRIMARY KEY,
      conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE, -- 대화 삭제 시 메시지도 함께 삭제
      role message_role NOT NULL,
      content TEXT NOT NULL,
      created_at timestamptz NOT NULL DEFAULT now()
    );
    
    -- 5) 값 검증 제약조건: 공백만 있는 값 방지
    ALTER TABLE messages
      ADD CONSTRAINT chk_messages_content_nonempty
      CHECK (length(btrim(content)) > 0);
    
    ALTER TABLE conversations
      ADD CONSTRAINT chk_conversations_title_nonempty
      CHECK (length(btrim(title)) > 0);
    
    -- 6) updated_at 자동 갱신 트리거 함수
    CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS trigger AS $$
    BEGIN
      NEW.updated_at = now();
      RETURN NEW;
    END;
    $$ LANGUAGE plpgsql;
    
    -- 7) updated_at 자동 갱신 트리거 등록
    CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
    
    CREATE TRIGGER trg_conversations_updated_at
    BEFORE UPDATE ON conversations
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
    
    -- 8) 인덱스(조회 성능)
    -- 대화 목록: user별 최신순 정렬 최적화
    CREATE INDEX idx_conversations_user_id_updated_at
      ON conversations(user_id, updated_at DESC);
    
    -- 메시지 목록: 대화별 시간순 조회 최적화
    CREATE INDEX idx_messages_conversation_id_created_at
      ON messages(conversation_id, created_at ASC);
    
    -- 9)초기 운영 시드 데이터
    -- INSERT INTO users (api_key) VALUES ('dev-api-key-please-change');
    ```
    
- ERD
    
    ![스크린샷 2026-02-04 오전 11.46.06.png](attachment:c436487d-8720-4d48-add1-5a829e7855df:스크린샷_2026-02-04_오전_11.46.06.png)
    
- 기술적 의사결정
    
    ## 스트리밍/비스트리밍 엔드포인트 분리
    
    - 상황: 동일한 “채팅 생성”이라도 클라이언트가 JSON(일괄)과 SSE(스트리밍)를 다르게 처리해야 함.
    - 대안: (A) 하나의 엔드포인트에서 옵션으로 처리 (B) 엔드포인트를 분리
    - 결정: (B) `POST /api/chat/completions`(JSON) 과 `POST /api/chat/completions/stream`(SSE)로 분리.
    - 이유: 채점/테스트(Postman/Swagger) 안정성과 클라이언트 구현 명확성이 높아짐.
    - 결과: API가 1개 늘지만, 문서/테스트/구현의 혼선이 줄어듦.
    
    ## 스트리밍은 POST + JSON Body, 응답만 SSE
    
    - 상황: message는 길어질 수 있고, 추후 옵션(모델, temperature, context 등)이 늘어날 수 있음.
    - 대안: (A) GET + query(EventSource 친화) (B) POST + JSON body(fetch 스트리밍)
    - 결정: (B)로 통일하고, 응답만 `Content-Type: text/event-stream`으로 스트리밍.
    - 이유: URL 길이/인코딩 리스크 감소, 요청 스키마 확장에 유리, 엔드포인트 설계 일관성 확보.
    - 결과: EventSource 기반 클라이언트는 Stretch(별도 GET 엔드포인트 추가)로 분리 가능.
    
    ## chat 응답에 conversation_id를 항상 반환
    
    - 상황: 신규 대화 생성/기존 대화 이어가기 두 케이스가 존재.
    - 대안: (A) 신규 생성 때만 conversation_id 반환 (B) 항상 반환
    - 결정: (B) 항상 `conversation_id`를 응답에 포함.
    - 이유: 클라이언트가 다음 요청을 동일 로직으로 연결 가능(분기 감소).
    - 결과: 응답 스키마가 약간 커지지만 UX/연동 난이도가 내려감.
    
    ## 컨텍스트는 ‘최근 N개’로 제한
    
    - 상황: 전체 이력을 다 보내면 토큰/비용이 증가하고 성능이 떨어질 수 있음.
    - 대안: (A) 전체 메시지 전송 (B) 최근 N개 (C) 요약+최근 N개
    - 결정: (B) 최근 N개(기본 N=10), N은 설정으로 변경 가능.
    - 이유: 구현 난이도 대비 효과가 크고(비용/성능), 요구사항(맥락 유지)을 충족.
    - 결과: 오래된 맥락은 약해질 수 있어, 필요 시 요약 전략을 Stretch로 둠.
    
    ## 인증은 3주차부터 X-API-Key로 단계적 도입
    
    - 상황: 2주차 중간제출은 핵심 기능 동작 증빙이 우선이고, 인증까지 동시에 하면 리스크가 커짐.
    - 대안: (A) 1주차부터 인증 강제 (B) 초기엔 단일 사용자 운영 후 단계적 도입
    - 결정: (B) 중간제출 전: user_id=1 고정, 3주차부터 `X-API-Key`로 사용자 분리.
    - 이유: 일정 리스크를 줄이면서도 데이터 모델은 처음부터 확장 가능하게 준비.
    - 결과: 운영 정책 전환 시 마이그레이션/시드 데이터가 필요(문서에 명시).
    
    ## DB는 3테이블 + FK/CASCADE로 정합성 보장
    
    - 상황: 대화 삭제 시 메시지 정리, 사용자별 데이터 분리가 필요.
    - 대안: (A) 앱 로직으로만 삭제/정합성 보장 (B) FK + ON DELETE CASCADE로 DB 레벨 보장
    - 결정: (B) conversations/messages FK + CASCADE 적용.
    - 이유: 코드 실수로 고아 데이터가 생기는 것을 방지, 요구사항을 DB가 강제.
    - 결과: 삭제 영향 범위가 명확해지고, 운영/디버깅이 쉬워짐.
    
    ## updated_at은 DB 트리거로 자동 갱신
    
    - 상황: 업데이트 시각은 여러 코드 경로에서 누락되기 쉬움.
    - 대안: (A) 애플리케이션에서만 세팅 (B) DB 트리거로 강제
    - 결정: (B) BEFORE UPDATE 트리거로 updated_at 갱신.
    - 이유: 공통 규칙을 DB가 보장해 누락/불일치 리스크 감소.
    - 결과: DB에 로직이 일부 들어가지만, 데이터 신뢰성이 올라감.
    
    ## Swagger(OpenAPI)를 계약의 단일 소스로 유지
    
    - 상황: 요구사항 정의서/엔드포인트 설계서/구현이 불일치하면 채점/연동에서 문제가 생김.
    - 대안: (A) 문서 수기 관리 (B) OpenAPI로 스키마/예시를 중심화
    - 결정: (B) springdoc-openapi로 API 스펙을 단일 소스로 유지.
    - 이유: “Try it out”로 즉시 검증 가능하고, 변경 시 영향 파악이 쉬움.
    - 결과: 스트리밍(SSE)은 `text/event-stream`로 예외 케이스만 명시해 운영.