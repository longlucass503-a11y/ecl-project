package com.bank.ecl.parameter.scheme.dto;

import com.bank.ecl.common.constant.SchemeStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SchemeVO {
    private String schemeId;
    private String schemeCode;
    private String schemeName;
    private String schemeVersion;
    private String status;
    private String statusDisplay;
    private LocalDate effectiveDate;
    private LocalDateTime effectiveAt;
    private LocalDateTime expiredAt;
    private BigDecimal discountRate;
    private BigDecimal defaultCcf;
    private BigDecimal defaultLgd;
    private BigDecimal lgdFloor;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private String description;

    // Module statistics counts for scheme overview
    private Integer riskGroupCount;
    private Integer pdScenarioCount;
    private Integer lgdCurveCount;
    private Integer ccfCurveCount;
    private Integer overlayRuleCount;
    private Integer stageRuleCount;

    public boolean isEditable() {
        return SchemeStatus.DRAFT.name().equals(status);
    }
}
