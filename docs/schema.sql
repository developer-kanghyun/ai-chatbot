-- =========================
-- 테이블: users, conversations, messages
-- =========================

-- 1) 메시지 역할 Enum 타입
-- PostgreSQL에서 ENUM 타입을 생성합니다.
DO $$ BEGIN
    CREATE TYPE message_role AS ENUM ('user', 'assistant');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 2) 사용자 테이블
-- API Key 기반 인증을 위한 사용자 정보를 저장합니다.
CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  api_key TEXT UNIQUE NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- 3) 대화(Conversation) 테이블
-- 사용자의 대화 세션을 관리합니다.
CREATE TABLE IF NOT EXISTS conversations (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- 4) 메시지(Message) 테이블
-- 대화 내의 개별 메시지들을 저장합니다.
CREATE TABLE IF NOT EXISTS messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  role message_role NOT NULL,
  content TEXT NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- 5) 제약조건 추가
-- 빈 문자열 저장을 방지하기 위한 체크 제약조건입니다.
DO $$ BEGIN
    ALTER TABLE messages
      ADD CONSTRAINT chk_messages_content_nonempty
      CHECK (length(btrim(content)) > 0);
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    ALTER TABLE conversations
      ADD CONSTRAINT chk_conversations_title_nonempty
      CHECK (length(btrim(title)) > 0);
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 6) updated_at 자동 갱신 트리거 함수
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 7) 트리거 등록
-- users 테이블 업데이트 시 updated_at 갱신
DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- conversations 테이블 업데이트 시 updated_at 갱신
DROP TRIGGER IF EXISTS trg_conversations_updated_at ON conversations;
CREATE TRIGGER trg_conversations_updated_at
BEFORE UPDATE ON conversations
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- 8) 인덱스 생성
-- 성능 최적화를 위한 인덱스입니다.
CREATE INDEX IF NOT EXISTS idx_conversations_user_id_updated_at
  ON conversations(user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_id_created_at
  ON messages(conversation_id, created_at ASC);

-- 9) 초기 시드 데이터 (개발용)
-- 초기 개발 시 사용할 테스트 유저 (ID: 1)
INSERT INTO users (api_key) 
VALUES ('test-api-key')
ON CONFLICT (api_key) DO NOTHING;
