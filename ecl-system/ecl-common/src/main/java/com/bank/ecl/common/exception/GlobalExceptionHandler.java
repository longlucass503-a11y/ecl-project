package com.bank.ecl.common.exception;

import com.bank.ecl.common.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler that converts EclException and other exceptions
 * into structured {@link Result} responses.
 */
@RestControllerAdvice(basePackages = "com.bank.ecl")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EclException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleEclException(EclException ex) {
        log.warn("ECL business exception: code={}, severity={}, detail={}",
                ex.getErrorCode(), ex.getSeverity(), ex.getMessage());
        return Result.error(ex.getErrorCode().name(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", msg);
        return Result.error("ECL_006", "参数校验失败: " + msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return Result.error(ErrorCode.ECL_006.name(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return Result.error("INTERNAL_ERROR", "服务器内部错误: " + ex.getMessage());
    }
}
