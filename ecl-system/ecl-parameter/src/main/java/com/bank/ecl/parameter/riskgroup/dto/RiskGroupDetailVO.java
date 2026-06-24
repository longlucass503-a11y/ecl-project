package com.bank.ecl.parameter.riskgroup.dto;

import lombok.Data;

@Data
public class RiskGroupDetailVO {
    private Long detailId;
    private Integer priority;
    private String businessLine;
    private String productType;
    private String industryCode;
    private String collateralType;
}
