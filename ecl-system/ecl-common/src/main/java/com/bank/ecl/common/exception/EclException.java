package com.bank.ecl.common.exception;

import com.bank.ecl.common.exception.ErrorCode.Severity;

public class EclException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Severity severity;

    public EclException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.severity = errorCode.getSeverity();
    }

    public EclException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " : " + detail);
        this.errorCode = errorCode;
        this.severity = errorCode.getSeverity();
    }

    public EclException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage() + " : " + detail, cause);
        this.errorCode = errorCode;
        this.severity = errorCode.getSeverity();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Severity getSeverity() {
        return severity;
    }
}
