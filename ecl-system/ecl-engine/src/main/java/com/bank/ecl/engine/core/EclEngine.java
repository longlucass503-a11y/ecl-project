package com.bank.ecl.engine.core;

import com.bank.ecl.common.constant.EngineType;

/**
 * ECL 引擎统一接口。
 * 每个引擎负责计算一个独立参数，通过 execute(JobContext) 修改上下文中的借据中间结果。
 */
public interface EclEngine {

    /** 返回此引擎的类型枚举 */
    EngineType getType();

    /** 执行引擎逻辑，修改 ctx 中的 CustomerContext / AssetInput */
    void execute(JobContext ctx);
}
