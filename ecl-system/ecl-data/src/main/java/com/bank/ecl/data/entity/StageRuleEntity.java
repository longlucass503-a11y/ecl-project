package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tbl_stage_rule")
public class StageRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long ruleId;

    private String schemeId;

    private String groupId;

    private String ruleType;

    private String stageFrom;

    private String stageTo;

    private Integer priority;

    private Integer observationDays;

    private String conditions;
}
