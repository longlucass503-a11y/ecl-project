package com.bank.ecl.parameter.lgd.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LgdCurveCreateReq {

    @NotBlank(message = "schemeId 不能为空")
    private String schemeId;

    @NotBlank(message = "groupId 不能为空")
    private String groupId;

    @NotBlank(message = "collateralType 不能为空")
    private String collateralType;

    private String collateralCategory;

    private String productType;

    @NotNull(message = "lgdBaseValue 不能为空")
    @DecimalMin(value = "0.0", inclusive = true, message = "lgdBaseValue 最小为 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "lgdBaseValue 最大为 1")
    private BigDecimal lgdBaseValue;
}
