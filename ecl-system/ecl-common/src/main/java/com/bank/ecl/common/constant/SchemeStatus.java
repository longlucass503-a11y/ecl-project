package com.bank.ecl.common.constant;

public enum SchemeStatus {
    DRAFT("草稿"),
    PUBLISHED("已发布"),
    EFFECTIVE("已生效"),
    EXPIRED("已失效");

    private final String displayName;

    SchemeStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean canTransitionTo(SchemeStatus target) {
        return switch (this) {
            case DRAFT -> target == PUBLISHED;
            case PUBLISHED -> target == EFFECTIVE;
            case EFFECTIVE -> target == EXPIRED;
            case EXPIRED -> false;
        };
    }
}
