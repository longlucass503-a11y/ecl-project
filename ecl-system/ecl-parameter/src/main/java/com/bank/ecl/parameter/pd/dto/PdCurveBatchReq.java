package com.bank.ecl.parameter.pd.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PdCurveBatchReq {

    @NotBlank(message = "schemeId 不能为空")
    private String schemeId;

    @NotBlank(message = "groupId 不能为空")
    private String groupId;

    @Valid
    private List<PdCurveItem> curves;

    @Data
    public static class PdCurveItem {
        private Long scenarioId;
        private String ratingAgency;
        private String ratingCode;
        private BigDecimal pdValue;
    }
}
