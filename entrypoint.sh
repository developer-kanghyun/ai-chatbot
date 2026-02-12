#!/bin/sh
set -eu

# 개별 환경 변수가 존재할 경우 JDBC URL 조립
if [ -n "${SPRING_DATASOURCE_HOST:-}" ] && [ -n "${SPRING_DATASOURCE_DATABASE:-}" ]; then
  DB_PORT="${SPRING_DATASOURCE_PORT:-5432}"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${SPRING_DATASOURCE_HOST}:${DB_PORT}/${SPRING_DATASOURCE_DATABASE}"
  echo "SPRING_DATASOURCE_URL generated: ${SPRING_DATASOURCE_URL}"
fi

# Spring Boot 3.2+ Layered JAR 실행 (PID 1 유지)
exec java ${JAVA_OPTS:-} org.springframework.boot.loader.launch.JarLauncher
