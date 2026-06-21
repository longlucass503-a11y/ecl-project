package com.bank.ecl.common.constant;

public enum CalcStatus {
    PROCESSING("处理中"),
    SUCCESS("成功"),
    PARTIAL("部分成功"),
    FAILED("失败");

    private final String displayName;

    CalcStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
