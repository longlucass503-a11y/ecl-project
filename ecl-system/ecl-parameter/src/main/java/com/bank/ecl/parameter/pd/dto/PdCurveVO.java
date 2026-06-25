package com.bank.ecl.parameter.pd.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PdCurveVO {
    private Long curveId;
    private String schemeId;
    private String groupId;
    private Long scenarioId;
    private String ratingAgency;
    private String ratingCode;
    private BigDecimal pdValue;
}
