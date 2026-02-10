# 3일차: 공통 응답 규격 및 보일러플레이트

## 작업 완료 항목

### 1. 공통 응답 객체
- `src/main/java/com/example/chatbot/common/ApiResponse.java`
- `src/main/java/com/example/chatbot/common/ErrorResponse.java`

### 2. 에러 처리 인프라
- `src/main/java/com/example/chatbot/exception/ErrorCode.java`
- `src/main/java/com/example/chatbot/exception/GlobalExceptionHandler.java`

### 3. Health API 리팩토링
- `src/main/java/com/example/chatbot/health/HealthController.java`
- 공통 응답 규격 적용 완료

## 검증

### Health API 테스트
```bash
curl http://localhost:8080/health
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "status": "UP",
    "timestamp": "2026-02-04T08:14:12.393645Z"
  }
}
```

### 에러 응답 테스트
존재하지 않는 엔드포인트 호출 시:
```json
{
  "success": false,
  "error": {
    "code": "INTERNAL_SERVER_ERROR",
    "message": "서버 내부 오류가 발생했습니다."
  }
}
```

## 다음 단계
- 4일차: 데이터 레이어 (Entity, Repository) 구현
- 5일차: 서비스 레이어 뼈대 구현
