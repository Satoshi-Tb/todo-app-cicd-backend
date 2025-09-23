package com.example.taskapp.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 Not Found
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ex.getMessage(), List.of()));
    }

    // 409 Conflict（楽観ロック）
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ApiError> handleOptimistic(OptimisticLockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getMessage(), List.of()));
    }

    // 400 Bad Request（Bean Validation - @Valid ボディ）
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<Violation> violations = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            violations.add(new Violation(fe.getField(), fe.getDefaultMessage()));
        }
        String msg = "入力値が不正です";
        return ResponseEntity.badRequest().body(new ApiError(msg, violations));
    }

    // 400 Bad Request（Bean Validation - @RequestParam 等）
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<Violation> violations = ex.getConstraintViolations().stream()
                .map(this::toViolation)
                .collect(Collectors.toList());
        return ResponseEntity.badRequest().body(new ApiError("入力値が不正です", violations));
    }

    // 400 Bad Request（ヘッダ不足 If-Match 等）
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        String field = ex.getHeaderName();
        String msg = String.format("必須ヘッダが不足しています: %s", field);
        return ResponseEntity.badRequest().body(new ApiError(msg, List.of(new Violation(field, msg))));
    }

    // 400 Bad Request（JSON不正など）
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiError("リクエストボディが不正です", List.of()));
    }

    // 400 Bad Request（型不一致）
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String field = ex.getName();
        String msg = String.format("パラメータの型が不正です: %s", field);
        return ResponseEntity.badRequest().body(new ApiError(msg, List.of(new Violation(field, msg))));
    }

    private Violation toViolation(ConstraintViolation<?> cv) {
        String field = (cv.getPropertyPath() != null) ? cv.getPropertyPath().toString() : "";
        return new Violation(field, cv.getMessage());
    }

    // シンプルなエラー応答
    public static record ApiError(String message, List<Violation> errors) {}
    public static record Violation(String field, String message) {}
}

