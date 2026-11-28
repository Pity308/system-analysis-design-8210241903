package com.adc.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一异常出口。
 * <p>
 * 对应报告 6.12 的系统级设计约束：所有失败都以固定结构的 JSON 返回，
 * 前端（员工端 B/S 与患者端小程序）只需按 {@code error.code} 分支，
 * 不需要解析自然语言提示。
 * <p>
 * 响应结构：
 * <pre>
 * {
 *   "code": "BUSINESS_RULE_VIOLATED",
 *   "message": "该时段已被占用",
 *   "timestamp": "2026-10-22T15:01:02+08:00",
 *   "fieldErrors": { "patientName": "不能为空" }   // 仅参数校验错误时出现
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务层主动抛出的可预期错误：建档查重、时段冲突、状态不允许等。 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        log.warn("业务异常 {} {}: {}", ex.getStatus(), ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(body(ex.getCode(), ex.getMessage(), null));
    }

    /** @Valid 校验失败：逐字段返回，前端可直接标红。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String message = fields.isEmpty() ? "请求参数不合法" : fields.values().iterator().next();
        return ResponseEntity.badRequest()
                .body(body("VALIDATION_FAILED", message, fields));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
                       MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadParam(Exception ex) {
        return ResponseEntity.badRequest()
                .body(body("BAD_REQUEST", ex.getMessage(), null));
    }

    /**
     * 框架层主动抛出的状态异常（例如 {@code ResponseStatusException}）保留原状态码，
     * 不要被最后的兜底分支改写成 500。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        String code = ex.getStatusCode().is4xxClientError() ? "BAD_REQUEST" : "INTERNAL_ERROR";
        if (ex.getStatusCode().value() == 404) {
            code = "NOT_FOUND";
        } else if (ex.getStatusCode().value() == 409) {
            code = "CONFLICT";
        }
        return ResponseEntity.status(ex.getStatusCode())
                .body(body(code, ex.getReason() == null ? ex.getMessage() : ex.getReason(), null));
    }

    /** 兜底：不把堆栈暴露给前端，但服务端留完整日志。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("未预期异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "服务器内部错误，请稍后重试或联系管理员", null));
    }

    private Map<String, Object> body(String code, String message, Map<String, String> fieldErrors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        m.put("timestamp", OffsetDateTime.now().toString());
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            m.put("fieldErrors", fieldErrors);
        }
        return m;
    }
}
