package com.bank.ecl.engine.core;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务级上下文 —— 贯穿一次 ECL 计算任务的全部引擎。
 */
@Data
public class JobContext {

    /** 任务 ID */
    private String jobId;

    /** 绑定的 ECL 方案 ID */
    private String schemeId;

    /** true=试算，false=正式跑批 */
    private boolean trialMode;

    /** 方案 CCF 缺省值（由 tbl_ecl_scheme.default_ccf 加载） */
    private double defaultCcf;

    /** 方案 LGD 缺省值（由 tbl_ecl_scheme.default_lgd 加载） */
    private double defaultLgd;

    /** 折现率（由 tbl_ecl_scheme.discount_rate 加载） */
    private double discountRate;

    /** 按客户分批的上下文列表 */
    private List<CustomerContext> customers = new ArrayList<>();
}
