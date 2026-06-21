package com.bank.ecl.engine.core;

import com.bank.ecl.engine.pd.PdDetail;
import com.bank.ecl.engine.stage.StageResult;
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

    // ========== 6.2 阶段划分引擎入参（上游数据层填充）==========

    /** 上期计算的阶段（首次计算时为 null，引擎内视为 STAGE_1） */
    private Stage lastStage;

    /** 逾期天数 */
    private Integer overdueDays;

    /** CRR/国际评级（如 CRR1~CRR8） */
    private String crrRating;

    /** 五级分类（正常/关注/次级/可疑/损失） */
    private String fiveCategory;

    /** 是否违约 */
    private Boolean defaultFlag;

    /** 舆情严重程度（轻度/中度/重度） */
    private String mediaSentiment;

    /** 评级下降级数（上游预计算） */
    private Integer ratingDropLevels;

    // ========== 6.2 阶段划分引擎输出 ==========

    /** 阶段判定结果 */
    private StageResult stageResult;

    // ========== 6.3 PD 计算引擎入参 ==========

    /** 评级代码 */
    private String ratingCode;

    /** 到期日 */
    private java.time.LocalDate maturityDate;

    /** 计算日期 */
    private java.time.LocalDate calcDate;

    // ========== 6.3 PD 计算引擎输出 ==========

    /** 各情景 PD 明细 */
    private java.util.List<PdDetail> pdDetails;

    /** 12 个月加权 PD */
    private double pd12m;

    /** 存续期 PD */
    private double pdLifetime;

    /** 异常码（ECL_001 等，查不到即阻断） */
    private String pdException;

    // ========== 6.4 EAD 计算引擎入参 ==========

    /** 未偿余额 */
    private java.math.BigDecimal outstandingBalance;

    /** 应计利息 */
    private java.math.BigDecimal accruedInterest;

    /** 授信总额 */
    private java.math.BigDecimal totalLimit;

    /** 承诺类型 */
    private String commitmentType;

    /** 承诺期限（天） */
    private Integer commitmentDays;

    // ========== 6.4 EAD 计算引擎输出 ==========

    /** 表内 EAD */
    private double onBsEad;

    /** 表外 EAD */
    private double offBsEad;

    /** 总 EAD */
    private double totalEad;

    /** 异常码 */
    private String eadException;

    // ========== 预留字段（后续引擎使用）==========
    // lgdValue, eclValue ...
}
