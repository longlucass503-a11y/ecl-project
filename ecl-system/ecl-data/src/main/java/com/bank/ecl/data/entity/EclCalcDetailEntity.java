package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("tbl_ecl_calc_detail")
public class EclCalcDetailEntity {

    @TableId(type = IdType.AUTO)
    private Long detailId;

    private String jobId;

    private String assetId;

    private String schemeId;

    private LocalDate calcDate;

    private String inputData;

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

    private String overlayException;

    private String calcStatus;

    private String errorSummary;
}
