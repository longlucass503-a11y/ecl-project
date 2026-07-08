package com.bank.ecl.engine.core;

/**
 * IFRS 9 三阶段枚举。
 * STAGE_1（第一阶段）→ STAGE_2（第二阶段）→ STAGE_3（第三阶段）。
 */
public enum Stage {
    STAGE_1("第一阶段"),
    STAGE_2("第二阶段"),
    STAGE_3("第三阶段");

    private final String label;

    Stage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
