package com.bank.ecl.calculation.trial.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TrialCalculationReq {

    @NotBlank(message = "schemeId 不能为空")
    private String schemeId;

    @NotBlank(message = "assetId 不能为空")
    private String assetId;

    private LocalDate calcDate;
    private String scope = "SINGLE";

    // === 6.1 风险分组入参 ===
    private String businessLine;
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
    private BigDecimal outstandingBalance;
    private BigDecimal accruedInterest;
    private BigDecimal totalLimit;
    private String commitmentType;
    private Integer commitmentDays;
}
