#!/bin/sh
set -eu

# 개별 DB 환경 변수를 조합하여 JDBC URL 생성 (포트 미지정 시 5432 기본값)
if [ -n "${SPRING_DATASOURCE_HOST:-}" ] && [ -n "${SPRING_DATASOURCE_DATABASE:-}" ]; then
  DB_PORT="${SPRING_DATASOURCE_PORT:-5432}"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${SPRING_DATASOURCE_HOST}:${DB_PORT}/${SPRING_DATASOURCE_DATABASE}"
  echo "SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL}"
fi

# Java 애플리케이션 실행 (Layered JAR 방식)
exec java ${JAVA_OPTS:-} org.springframework.boot.loader.launch.JarLauncher
