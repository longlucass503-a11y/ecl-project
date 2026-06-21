package com.bank.ecl.common.exception;

public enum ErrorCode {
    ECL_001("PD查询无结果", Severity.BLOCKING),
    ECL_002("EAD计算异常", Severity.BLOCKING),
    ECL_003("LGD查询无结果", Severity.WARN),
    ECL_004("方案状态异常", Severity.BLOCKING),
    ECL_005("对账校验不符", Severity.BLOCKING),
    ECL_006("参数校验失败", Severity.WARN),
    ECL_007("重复跑批", Severity.BLOCKING);

    private final String message;
    private final Severity severity;

    ErrorCode(String message, Severity severity) {
        this.message = message;
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public enum Severity {
        BLOCKING,
        WARN
    }
}
