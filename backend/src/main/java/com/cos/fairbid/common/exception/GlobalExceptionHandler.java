package com.cos.fairbid.common.exception;


import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.domain.AuctionDuration;
import com.cos.fairbid.auction.domain.Category;
import com.cos.fairbid.bid.domain.BidType;
import com.cos.fairbid.common.response.ApiResponse;

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 공통 형식으로 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Enum 타입별 한글 설명 매핑
     * API 요청에서 잘못된 enum 값이 전달될 때 사용자 친화적인 에러 메시지 생성에 사용
     *
     * 지원하는 Enum 타입:
     * - Category: 경매 카테고리 (ELECTRONICS, FASHION, ...)
     * - AuctionDuration: 경매 기간 (HOURS_24, HOURS_48)
     * - BidType: 입찰 유형 (ONE_TOUCH, DIRECT)
     *
     * 새로운 Enum 타입 추가 시 이 맵에도 추가해야 한글 설명이 적용됨
     * 미등록 시 기본값 "값"으로 대체됨 (getOrDefault 사용)
     */
    private static final Map<Class<? extends Enum<?>>, String> ENUM_DESCRIPTIONS = Map.of(
            Category.class, "카테고리",
            AuctionDuration.class, "경매 기간",
            BidType.class, "입찰 유형"
    );

    // =====================================================
    // 도메인 예외 처리 (통합)
    // =====================================================

    /**
     * 모든 도메인 예외를 통합 처리
     * 각 예외가 정의한 HttpStatus를 사용
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex) {
        log.warn("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return errorResponse(ex.getStatus(), ex.getErrorCode(), ex.getMessage());
    }

    // =====================================================
    // Validation 예외 처리
    // =====================================================

    /**
     * @Valid 검증 실패 예외 처리
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", message);
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /**
     * Bean Validation 제약조건 위반 예외 처리
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException ex
    ) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", message);
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    // =====================================================
    // 요청 파싱 예외 처리
    // =====================================================

    /**
     * JSON 파싱 실패 예외 처리 (잘못된 enum 값 등)
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex
    ) {
        String message = "요청 본문을 파싱할 수 없습니다.";

        // enum 변환 실패 시 타입 기반으로 유효한 값 안내
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException invalidFormat) {
            Class<?> targetType = invalidFormat.getTargetType();
            if (targetType != null && targetType.isEnum()) {
                @SuppressWarnings("unchecked")
                Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) targetType;
                String description = ENUM_DESCRIPTIONS.getOrDefault(enumType, "값");
                String validValues = getEnumValidValues(enumType);
                message = "유효하지 않은 " + description + "입니다. 허용 값: " + validValues;
            }
        }

        log.warn("HttpMessageNotReadableException: {}", ex.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", message);
    }

    /**
     * 쿼리 파라미터 타입 변환 실패 예외 처리 (잘못된 enum 값 등)
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex
    ) {
        String paramName = ex.getName();
        String message = "'" + paramName + "' 파라미터 값이 유효하지 않습니다.";

        // enum 타입인 경우 유효한 값 안내
        Class<?> requiredType = ex.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) requiredType;
            String validValues = getEnumValidValues(enumType);
            message = "'" + paramName + "' 파라미터 값이 유효하지 않습니다. 허용 값: " + validValues;
        }

        log.warn("MethodArgumentTypeMismatchException: param={}, value={}",
                paramName, sanitizeLogValue(ex.getValue()));
        return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message);
    }

    // =====================================================
    // 데이터 무결성 예외 처리
    // =====================================================

    /**
     * DB 무결성 제약조건 위반 예외 처리 (UNIQUE 제약 등)
     * 동시 요청으로 인한 Race Condition 시 발생할 수 있음.
     * HTTP 409 Conflict
     *
     * 우선순위:
     * 1. Hibernate ConstraintViolationException의 constraintName 활용 (안정적)
     * 2. 예외 메시지 substring 파싱 (fallback)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex) {
        String message = "데이터 무결성 위반이 발생했습니다.";

        // 1. Hibernate ConstraintViolationException에서 constraintName 추출 시도
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException hibernateEx) {
            String constraintName = hibernateEx.getConstraintName();
            if (constraintName != null) {
                message = mapConstraintNameToMessage(constraintName);
                log.warn("DataIntegrityViolationException: constraint={}", constraintName);
                return errorResponse(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", message);
            }
        }

        // 2. Fallback: 예외 메시지에서 중복 필드 추출 시도
        String exceptionMessage = ex.getMostSpecificCause().getMessage();
        if (exceptionMessage != null) {
            if (exceptionMessage.contains("nickname")) {
                message = "이미 사용 중인 닉네임입니다.";
            } else if (exceptionMessage.contains("phone_number")) {
                message = "이미 등록된 전화번호입니다.";
            } else if (exceptionMessage.contains("Duplicate entry")) {
                message = "중복된 데이터가 존재합니다.";
            }
        }

        log.warn("DataIntegrityViolationException: {}", exceptionMessage);
        return errorResponse(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", message);
    }

    /**
     * DB 제약조건 이름을 사용자 친화적 메시지로 변환한다.
     *
     * @param constraintName DB 제약조건 이름 (예: uk_users_nickname)
     * @return 사용자 친화적 에러 메시지
     */
    private String mapConstraintNameToMessage(String constraintName) {
        if (constraintName == null) {
            return "데이터 무결성 위반이 발생했습니다.";
        }

        String lowerName = constraintName.toLowerCase();
        if (lowerName.contains("nickname")) {
            return "이미 사용 중인 닉네임입니다.";
        } else if (lowerName.contains("phone")) {
            return "이미 등록된 전화번호입니다.";
        } else if (lowerName.contains("email")) {
            return "이미 등록된 이메일입니다.";
        }

        return "중복된 데이터가 존재합니다.";
    }

    // =====================================================
    // 기타 예외 처리
    // =====================================================

    /**
     * 잘못된 인자 예외 처리 (유효하지 않은 enum 값 등)
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    /**
     * 잘못된 상태 예외 처리
     * HTTP 500 Internal Server Error
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException ex) {
        log.error("IllegalStateException: {}", ex.getMessage());
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");
    }

    /**
     * 그 외 예상치 못한 예외 처리
     * HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");
    }

    // =====================================================
    // 유틸리티 메서드
    // =====================================================

    /**
     * Enum의 모든 상수를 콤마로 연결한 문자열 반환
     *
     * @param enumClass Enum 클래스
     * @return "VALUE1, VALUE2, VALUE3" 형식의 문자열
     */
    private String getEnumValidValues(Class<? extends Enum<?>> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    /**
     * 에러 응답 생성 헬퍼 메서드
     *
     * @param status    HTTP 상태 코드
     * @param errorCode 에러 코드
     * @param message   에러 메시지
     * @return ResponseEntity 객체
     */
    private ResponseEntity<ApiResponse<Void>> errorResponse(HttpStatus status, String errorCode, String message) {
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(errorCode, message));
    }

    /**
     * 로그에 출력할 값을 정제한다.
     * - null이면 "null" 반환
     * - 50자 초과 시 잘라서 "..." 추가
     */
    private String sanitizeLogValue(Object value) {
        if (value == null) {
            return "null";
        }
        String str = value.toString();
        if (str.length() > 50) {
            return str.substring(0, 50) + "...";
        }
        return str;
    }
}
