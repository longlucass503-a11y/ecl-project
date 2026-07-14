package com.bank.ecl.parameter.overlay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OverlayRuleCreateReq {

    @NotBlank(message = "schemeId 不能为空")
    private String schemeId;

    private String groupId;

    @NotBlank(message = "overlayType 不能为空")
    private String overlayType;

    private String adjustmentTarget = "ECL_FINAL";

    @NotBlank(message = "adjustmentType 不能为空")
    @Pattern(regexp = "^(ADDBP|PERCENTAGE|FIXED)$", message = "adjustmentType 仅允许 ADDBP / PERCENTAGE / FIXED")
    private String adjustmentType;

    @NotNull(message = "adjustmentValue 不能为空")
    @DecimalMin(value = "0.0", inclusive = true, message = "adjustmentValue 最小为 0")
    private BigDecimal adjustmentValue;

    @NotNull(message = "priority 不能为空")
    private Integer priority;

    private String conditions;

    private LocalDate effectiveDate;

    private LocalDate expiryDate;
}
