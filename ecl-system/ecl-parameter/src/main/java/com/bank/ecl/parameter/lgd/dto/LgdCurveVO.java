package com.bank.ecl.parameter.lgd.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LgdCurveVO {
    private Long curveId;
    private String schemeId;
    private String groupId;
    private String collateralType;
    private String collateralCategory;
    private String productType;
    private BigDecimal lgdBaseValue;
}
