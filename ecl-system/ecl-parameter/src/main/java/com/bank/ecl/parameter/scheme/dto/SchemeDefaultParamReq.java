package com.bank.ecl.parameter.scheme.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SchemeDefaultParamReq {

    @NotNull(message = "discountRate 不能为空")
    @DecimalMin(value = "0.0000", inclusive = true, message = "折扣率不能小于 0")
    @DecimalMax(value = "100.0000", inclusive = true, message = "折扣率不能超过 100")
    private BigDecimal discountRate;

    @NotNull(message = "defaultCcf 不能为空")
    @DecimalMin(value = "0.0000", inclusive = true, message = "默认 CCF 不能小于 0")
    @DecimalMax(value = "1.0000", inclusive = true, message = "默认 CCF 不能超过 1")
    private BigDecimal defaultCcf;

    @NotNull(message = "defaultLgd 不能为空")
    @DecimalMin(value = "0.0000", inclusive = true, message = "默认 LGD 不能小于 0")
    @DecimalMax(value = "1.0000", inclusive = true, message = "默认 LGD 不能超过 1")
    private BigDecimal defaultLgd;

    @NotNull(message = "lgdFloor 不能为空")
    @DecimalMin(value = "0.0000", inclusive = true, message = "LGD 下限不能小于 0")
    @DecimalMax(value = "1.0000", inclusive = true, message = "LGD 下限不能超过 1")
    private BigDecimal lgdFloor;
}
