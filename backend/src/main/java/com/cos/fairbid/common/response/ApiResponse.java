package com.cos.fairbid.common.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

/**
 * 공통 API 응답 형식
 *
 * 성공 응답:
 * {
 *   "success": true,
 *   "data": { ... },
 *   "serverTime": "2026-01-06T12:00:00",
 *   "error": null
 * }
 *
 * 실패 응답:
 * {
 *   "success": false,
 *   "data": null,
 *   "serverTime": "2026-01-06T12:00:00",
 *   "error": {
 *     "code": "ERROR_CODE",
 *     "message": "에러 메시지"
 *   }
 * }
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final LocalDateTime serverTime;
    private final ErrorDetail error;

    private ApiResponse(boolean success, T data, ErrorDetail error) {
        this.success = success;
        this.data = data;
        this.serverTime = LocalDateTime.now();
        this.error = error;
    }

    /**
     * 성공 응답 생성 (데이터 포함)
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 성공 응답 생성 (데이터 없음)
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * 실패 응답 생성
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message));
    }

    @Getter
    public static class ErrorDetail {
        private final String code;
        private final String message;

        public ErrorDetail(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
