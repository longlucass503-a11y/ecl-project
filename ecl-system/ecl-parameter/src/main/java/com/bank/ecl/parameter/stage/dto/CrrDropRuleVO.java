package com.bank.ecl.parameter.stage.dto;

import lombok.Data;

@Data
public class CrrDropRuleVO {
    private Long dropRuleId;
    private Long ruleId;
    private String schemeId;
    private String groupId;
    private String ratingAgency;
    private String currentRating;
    private Integer dropThreshold;
    private Integer downgradeThreshold;
}
