package com.bank.ecl.calculation.monitor.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EclJobDetailVO {
    private Long detailId;
    private String assetId;
    private String schemeId;
    private LocalDate calcDate;
    private String groupId;
    private String stageResult;
    private BigDecimal eadTotal;
    private BigDecimal lgdValue;
    private BigDecimal eclWeighted;
    private BigDecimal eclOverlayTotal;
    private BigDecimal eclFinal;
    private String calcStatus;
    private String errorSummary;
}
