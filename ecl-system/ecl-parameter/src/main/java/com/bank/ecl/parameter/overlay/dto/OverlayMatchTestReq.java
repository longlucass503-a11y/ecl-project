package com.bank.ecl.parameter.overlay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class OverlayMatchTestReq {

    @NotBlank(message = "schemeId 不能为空")
    private String schemeId;

    private String groupId;

    private Map<String, Object> fieldValues;
}
