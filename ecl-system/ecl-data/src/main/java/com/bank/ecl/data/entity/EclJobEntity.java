package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("tbl_ecl_job")
public class EclJobEntity {

    @TableId
    private String jobId;

    private String schemeId;

    private LocalDate calcDate;

    private Boolean trialMode;

    private String status;

    private Integer totalAssets;

    private Integer successCount;

    private Integer exceptionCount;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    private String errorSummary;
}
