package com.bank.ecl.calculation.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EclJobStepVO {
    private String name;
    private Long durationMs;
    private Integer percent;
}
