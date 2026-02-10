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