package com.bank.ecl.engine.core;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户级上下文 —— 一个客户下的所有借据。
 * 后续引擎（6.4 EAD / 6.5 LGD）将扩展 facilities 和 collaterals 字段。
 */
@Data
public class CustomerContext {

    /** 客户 ID */
    private String customerId;

    /** 该客户下所有借据 */
    private List<AssetInput> assets = new ArrayList<>();
}
