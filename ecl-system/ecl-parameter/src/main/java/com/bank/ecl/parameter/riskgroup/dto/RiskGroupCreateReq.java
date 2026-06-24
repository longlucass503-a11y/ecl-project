package com.bank.ecl.parameter.riskgroup.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class RiskGroupCreateReq {
    @NotBlank
    private String schemeId;

    @NotBlank
    private String groupName;

    private String groupCode;

    private Integer sortOrder;

    private String description;

    private List<RiskGroupDetailReq> details;

    @Data
    public static class RiskGroupDetailReq {
        private Integer priority;
        private String businessLine;
        private String productType;
        private String industryCode;
        private String collateralType;
    }
}
