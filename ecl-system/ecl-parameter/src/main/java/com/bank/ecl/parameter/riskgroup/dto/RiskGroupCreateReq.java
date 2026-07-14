package com.bank.ecl.parameter.riskgroup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class RiskGroupCreateReq {
    @NotBlank
    private String schemeId;

    @NotBlank
    private String groupName;

    // groupCode 可选，为空时后端自动生成
    @Size(max = 32, message = "groupCode 长度不能超过 32 个字符")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "groupCode 只允许字母、数字和下划线")
    private String groupCode;

    @Min(value = 0, message = "sortOrder 不能为负")
    private Integer sortOrder;

    private String description;

    private List<RiskGroupDetailReq> details;

    @Data
    public static class RiskGroupDetailReq {
        private Integer priority;
        private String segment;
        private String productType;
        private String industryCode;
        private String collateralType;
    }
}
