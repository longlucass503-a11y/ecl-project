package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("tbl_pd_scenario")
public class PdScenarioEntity {

    @TableId(type = IdType.AUTO)
    private Long scenarioId;

    private String schemeId;

    private String scenarioType;

    private String scenarioName;

    private BigDecimal weight;
}
