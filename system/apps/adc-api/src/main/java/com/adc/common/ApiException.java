package com.adc.common;

/**
 * 业务规则被违反时抛出。由 {@link GlobalExceptionHandler} 统一转成 400/409/422 的 JSON。
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }

    /** 越权：患者 A 想动患者 B 的预约（UC-10 鉴权约束）。 */
    public static ApiException forbidden(String message) {
        return new ApiException(403, "FORBIDDEN", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, "CONFLICT", message);
    }

    public static ApiException unprocessable(String message) {
        return new ApiException(422, "BUSINESS_RULE_VIOLATED", message);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
