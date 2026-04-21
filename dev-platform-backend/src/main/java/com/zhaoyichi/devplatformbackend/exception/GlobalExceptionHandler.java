package com.zhaoyichi.devplatformbackend.exception;

import com.zhaoyichi.devplatformbackend.common.ErrorCode;
import com.zhaoyichi.devplatformbackend.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * 全局异常处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Result<Void>> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("业务异常: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity
                .status(Objects.requireNonNull(errorCode.getHttpStatus(), "errorCode.httpStatus"))
                .body(Result.failure(errorCode, ex.getMessage()));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("请求参数异常: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity
                .status(Objects.requireNonNull(ErrorCode.VALIDATION_ERROR.getHttpStatus(), "ErrorCode.VALIDATION_ERROR.httpStatus"))
                .body(Result.validationError(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex, HttpServletRequest request) {
        log.error("系统异常: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity
                .status(Objects.requireNonNull(ErrorCode.INTERNAL_ERROR.getHttpStatus(), "ErrorCode.INTERNAL_ERROR.httpStatus"))
                .body(Result.systemError(ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
