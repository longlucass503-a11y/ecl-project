package com.bank.ecl.parameter.ccf.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CcfCurveCreateReq {

    private String schemeId;

    private String productType;

    private String commitmentType;

    @Min(value = 0, message = "commitmentDaysMin 不能为负数")
    private Integer commitmentDaysMin;

    @Min(value = 0, message = "commitmentDaysMax 不能为负数")
    private Integer commitmentDaysMax;

    @Min(value = 0, message = "daysMin 不能为负数")
    private Integer daysMin;

    @Min(value = 0, message = "daysMax 不能为负数")
    private Integer daysMax;

    @NotNull(message = "ccfValue 不能为空")
    @DecimalMin(value = "0.0", inclusive = true, message = "ccfValue 最小为 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "ccfValue 最大为 1")
    private BigDecimal ccfValue;
}
