package com.bank.ecl.calculation.monitor.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class EclJobVO {
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
    private List<EclJobStepVO> steps = new ArrayList<>();
    private List<EclJobLogVO> logs = new ArrayList<>();
    private List<EclJobDetailVO> details = new ArrayList<>();
}
