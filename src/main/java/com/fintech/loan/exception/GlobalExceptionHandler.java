package com.fintech.loan.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 잘못된 요청 파라미터 (Loan 존재하지 않음 등) */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
    	e.printStackTrace();
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    /** Redis 연결 문제 */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<?> handleRedisError(RedisConnectionFailureException e) {
    	e.printStackTrace();
        return ResponseEntity.status(503).body("🚨 Redis 서버 연결 실패");
    }

    /** DB 오류 (SQL, JPA 관련) */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> handleDatabaseError(DataAccessException e) {
    	e.printStackTrace();
        return ResponseEntity.internalServerError().body("🚨 데이터베이스 접근 중 오류 발생");
    }

    /** 그 외 예외 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception e) {
    	e.printStackTrace();
        return ResponseEntity.internalServerError().body("🚨 서버 내부 오류 발생");
    }
}
