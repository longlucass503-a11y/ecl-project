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
    private String groupException;
    private String stageResult;
    private String triggerType;
    private String stageException;
    private String pdDetails;
    private String pdException;
    private BigDecimal eadTotal;
    private String eadException;
    private String eadBreakdown;
    private BigDecimal lgdValue;
    private String lgdException;
    private String lgdDetails;
    private BigDecimal eclWeighted;
    private String eclDetails;
    private String eclException;
    private BigDecimal eclOverlayTotal;
    private BigDecimal eclFinal;
    private Long selectedOverlayId;
    private String calcStatus;
    private String errorSummary;
}
