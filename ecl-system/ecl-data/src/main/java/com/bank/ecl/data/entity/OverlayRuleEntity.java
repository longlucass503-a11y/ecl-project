package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("tbl_overlay_rule")
public class OverlayRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long ruleId;

    private String schemeId;

    private String groupId;

    private String overlayType;

    private String adjustmentTarget;

    private String adjustmentType;

    private BigDecimal adjustmentValue;

    private Integer priority;

    private String conditions;

    private LocalDate effectiveDate;

    private LocalDate expiryDate;
}
