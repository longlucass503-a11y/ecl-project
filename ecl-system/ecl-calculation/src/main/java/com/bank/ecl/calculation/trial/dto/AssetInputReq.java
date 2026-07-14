package com.bank.ecl.calculation.trial.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AssetInputReq {
    private String assetId;
    // 6.1
    private String segment;
    private String customerType;
    private String productType;
    private String industryCode;
    private String regionCode;
    private String collateralType;
    // 6.2
    private String lastStage;
    private Integer overdueDays;
    private String crrRating;
    private String fiveCategory;
    private Boolean defaultFlag;
    private String mediaSentiment;
    private Integer ratingDropLevels;
    // 6.3
    private String ratingCode;
    private LocalDate maturityDate;
    // 6.4
    /** 业务类型：ON_BS（表内）/ OFF_BS（表外） */
    private String businessType;
    private BigDecimal outstandingBalance;
    private BigDecimal accruedInterest;
    private BigDecimal totalLimit;
    private String commitmentType;
    private Integer commitmentDays;
    private BigDecimal amtFinancedCny;
    private String facilityCd;

    public static AssetInputReq from(TrialCalculationReq req) {
        AssetInputReq r = new AssetInputReq();
        r.setAssetId(req.getAssetId());
        r.setSegment(req.getSegment());
        r.setCustomerType(req.getCustomerType());
        r.setProductType(req.getProductType());
        r.setIndustryCode(req.getIndustryCode());
        r.setRegionCode(req.getRegionCode());
        r.setCollateralType(req.getCollateralType());
        r.setLastStage(req.getLastStage());
        r.setOverdueDays(req.getOverdueDays());
        r.setCrrRating(req.getCrrRating());
        r.setFiveCategory(req.getFiveCategory());
        r.setDefaultFlag(req.getDefaultFlag());
        r.setMediaSentiment(req.getMediaSentiment());
        r.setRatingDropLevels(req.getRatingDropLevels());
        r.setRatingCode(req.getRatingCode());
        r.setMaturityDate(req.getMaturityDate());
        r.setBusinessType(req.getBusinessType());
        r.setOutstandingBalance(req.getOutstandingBalance());
        r.setAccruedInterest(req.getAccruedInterest());
        r.setTotalLimit(req.getTotalLimit());
        r.setCommitmentType(req.getCommitmentType());
        r.setCommitmentDays(req.getCommitmentDays());
        r.setAmtFinancedCny(req.getAmtFinancedCny());
        r.setFacilityCd(req.getFacilityCd());
        return r;
    }
}
