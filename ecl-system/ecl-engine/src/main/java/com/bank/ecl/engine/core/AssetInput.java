package com.bank.ecl.engine.core;

import lombok.Data;

/**
 * 借据输入及引擎逐层填充的中间结果。
 * 各引擎在 execute() 中读取/写入对应字段。
 */
@Data
public class AssetInput {

    // ========== 入参字段（上游数据层填充）==========

    /** 借据 ID */
    private String assetId;

    /** 所属客户 ID */
    private String customerId;

    /** 业务条线 */
    private String businessLine;

    /** 客户类型 */
    private String customerType;

    /** 产品类型 */
    private String productType;

    /** 行业代码 */
    private String industryCode;

    /** 地区代码 */
    private String regionCode;

    /** 担保类型 */
    private String collateralType;

    // ========== 6.1 风险分组引擎输出 ==========

    /** 匹配到的分组 ID */
    private String groupId;

    /** 分组名称 */
    private String groupName;

    /** "Y" = 异常（命中兜底分组 GRP_DEFAULT） */
    private String groupException;

    // ========== 预留字段（后续引擎使用）==========
    // lastStage, stageResult, pdDetails, totalEad, lgdValue, eclValue ...
}
