package com.bank.ecl.engine.model;

import com.bank.ecl.common.exception.ErrorCode;

/**
 * 引擎执行异常。
 * 当引擎内部发生不可恢复的错误时抛出（如参数数据缺失导致无法继续）。
 * 匹配性异常（如某笔借据未命中分组规则）不抛异常，以兜底值 + exception flag 处理。
 */
public class EngineException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String engineName;

    public EngineException(ErrorCode errorCode, String engineName, String detail) {
        super("[" + engineName + "] " + errorCode.getMessage() + " : " + detail);
        this.errorCode = errorCode;
        this.engineName = engineName;
    }

    public EngineException(ErrorCode errorCode, String engineName, String detail, Throwable cause) {
        super("[" + engineName + "] " + errorCode.getMessage() + " : " + detail, cause);
        this.errorCode = errorCode;
        this.engineName = engineName;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getEngineName() {
        return engineName;
    }
}
