package com.springboot.global;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice   // REST 응답(JSON)에만 적용
public class GlobalRestExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handleResponseStatus(ResponseStatusException ex) {
        log.warn("ResponseStatusException: status={}, message={}",
                ex.getStatusCode(), ex.getReason());
        ErrorBody body = new ErrorBody(
                ex.getStatusCode().value(),
                ex.getReason()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("검증 실패: {}", msg);
        ErrorBody body = new ErrorBody(HttpStatus.BAD_REQUEST.value(), msg);
        return ResponseEntity.badRequest().body(body);
    }
    
    // 1) favicon 같은 정적 리소스 없는 경우는 404로 처리
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NotFoundException ex) {
        return ResponseEntity.notFound().build(); // status 404, body 없음
    }

    // 2) 나머지 진짜 서버 에러만 여기서 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        log.error("서버 내부 오류", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "서버 내부 오류가 발생했습니다.", "status", 500));
    }

    @Getter
    @AllArgsConstructor
    static class ErrorBody {
        private int status;
        private String message;
    }
}
