package com.bank.ecl.calculation.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EclJobLogVO {
    private String time;
    private String level;
    private String message;
}
