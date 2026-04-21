package com.zhaoyichi.devplatformbackend.common;

import org.springframework.http.HttpStatus;

/**
 * 统一错误码定义。
 */
public enum ErrorCode {
    SUCCESS(200, HttpStatus.OK, "操作成功"),
    BUSINESS_ERROR(400, HttpStatus.BAD_REQUEST, "业务处理失败"),
    UNAUTHORIZED(401, HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    FORBIDDEN(403, HttpStatus.FORBIDDEN, "无权限访问"),
    VALIDATION_ERROR(422, HttpStatus.UNPROCESSABLE_ENTITY, "请求参数不合法"),
    INTERNAL_ERROR(500, HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
