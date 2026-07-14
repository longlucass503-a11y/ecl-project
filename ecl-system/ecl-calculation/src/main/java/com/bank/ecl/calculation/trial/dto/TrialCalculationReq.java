package com.bank.ecl.calculation.trial.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class TrialCalculationReq {

    @NotBlank(message = "schemeId 不能为空")
    private String schemeId;

    private String assetId;

    private LocalDate calcDate;
    private String scope = "SINGLE";

    private List<TrialLoanRowReq> loans = new ArrayList<>();
    private List<TrialFacilityRowReq> facilities = new ArrayList<>();
    private List<TrialRepaymentRowReq> repaymentSchedules = new ArrayList<>();
    private List<TrialCollateralRowReq> collaterals = new ArrayList<>();
    private List<TrialRatingRowReq> ratings = new ArrayList<>();
    private List<TrialHistoricalStageRowReq> historicalStages = new ArrayList<>();

    // === 6.1 风险分组入参 ===
    private String segment;
    private String customerType;
    private String productType;
    private String industryCode;
    private String regionCode;
    private String collateralType;

    // === 6.2 阶段划分入参 ===
    private String lastStage;         // STAGE_1 / STAGE_2 / STAGE_3
    private Integer overdueDays;
    private String crrRating;
    private String fiveCategory;
    private Boolean defaultFlag;
    private String mediaSentiment;
    private Integer ratingDropLevels;

    // === 6.3 PD 入参 ===
    private String ratingCode;
    private LocalDate maturityDate;

    // === 6.4 EAD 入参 ===
    /** 业务类型：ON_BS（表内）/ OFF_BS（表外） */
    private String businessType;
    private BigDecimal outstandingBalance;
    private BigDecimal accruedInterest;
    private BigDecimal totalLimit;
    private String commitmentType;
    private Integer commitmentDays;
    private BigDecimal amtFinancedCny;
    private String facilityCd;

    /** 多借据模式：按客户维度跑批 */
    private List<AssetInputReq> assets;
}
