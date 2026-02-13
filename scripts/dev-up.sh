#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  echo "[ERROR] .env 파일이 없습니다. 프로젝트 루트에 .env를 생성하세요."
  exit 1
fi

set -a
source .env
set +a

: "${DB_PASSWORD:?DB_PASSWORD is required in .env}"
: "${OPENAI_API_KEY:?OPENAI_API_KEY is required in .env}"

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/chatbotdb}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-postgres}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$DB_PASSWORD}"
export SPRING_DATA_REDIS_URL="${SPRING_DATA_REDIS_URL:-redis://localhost:6379}"
export APP_CHAT_CONTEXT_SIZE="${APP_CHAT_CONTEXT_SIZE:-10}"

if lsof -tiTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[INFO] 8080 포트 사용 중 프로세스를 종료합니다."
  lsof -tiTCP:8080 -sTCP:LISTEN | xargs kill
fi

echo "[INFO] PostgreSQL/Redis 컨테이너를 시작합니다."
docker-compose up -d

echo "[INFO] 서버를 시작합니다. (http://localhost:8080)"
exec ./gradlew bootRun
