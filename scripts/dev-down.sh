#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if lsof -tiTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[INFO] 8080 포트 서버를 종료합니다."
  lsof -tiTCP:8080 -sTCP:LISTEN | xargs kill
else
  echo "[INFO] 8080 포트 서버가 실행 중이 아닙니다."
fi

echo "[INFO] 컨테이너를 정리합니다."
docker-compose down
