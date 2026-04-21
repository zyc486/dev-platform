package com.zhaoyichi.devplatformbackend.common;

import lombok.Data;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 统一 API 响应结果封装。
 */
@Data
public class Result<T> {
    private Integer code;
    private String errorCode;
    private String message;
    private T data;
    private String traceId;
    private LocalDateTime timestamp;
    private String path;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setErrorCode(ErrorCode.SUCCESS.name());
        result.setMessage("操作成功");
        result.setData(data);
        applyContext(result);
        return result;
    }

    public static <T> Result<T> successMsg(String message) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setErrorCode(ErrorCode.SUCCESS.name());
        result.setMessage(message);
        result.setData(null);
        applyContext(result);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return failure(ErrorCode.BUSINESS_ERROR, message);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String message) {
        Result<T> result = new Result<>();
        ErrorCode resolved = errorCode == null ? ErrorCode.INTERNAL_ERROR : errorCode;
        result.setCode(resolved.getCode());
        result.setErrorCode(resolved.name());
        result.setMessage(message == null || message.trim().isEmpty() ? resolved.getDefaultMessage() : message);
        applyContext(result);
        return result;
    }

    public static <T> Result<T> unauthorized(String message) {
        return failure(ErrorCode.UNAUTHORIZED, message);
    }

    public static <T> Result<T> forbidden(String message) {
        return failure(ErrorCode.FORBIDDEN, message);
    }

    public static <T> Result<T> validationError(String message) {
        return failure(ErrorCode.VALIDATION_ERROR, message);
    }

    public static <T> Result<T> systemError(String message) {
        return failure(ErrorCode.INTERNAL_ERROR, message);
    }

    private static void applyContext(Result<?> result) {
        result.setTimestamp(LocalDateTime.now());
        result.setTraceId(MDC.get("traceId"));
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
            result.setPath(request.getRequestURI());
        }
    }
}