package com.zhaoyichi.devplatformbackend.exception;

import com.zhaoyichi.devplatformbackend.common.ErrorCode;

/**
 * 业务异常，交给全局异常处理统一返回。
 */
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode == null ? ErrorCode.BUSINESS_ERROR : errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
