package com.bank.ecl.parameter.scheme.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SchemeCreateReq {
    @NotBlank(message = "schemeName 不能为空")
    @Size(max = 100, message = "schemeName 长度不能超过 100 个字符")
    private String schemeName;
    private String description;
}
