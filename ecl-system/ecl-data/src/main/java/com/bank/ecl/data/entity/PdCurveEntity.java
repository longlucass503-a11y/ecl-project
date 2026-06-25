package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("tbl_pd_curve")
public class PdCurveEntity {

    @TableId(type = IdType.AUTO)
    private Long curveId;

    private String schemeId;

    private String groupId;

    private Long scenarioId;

    private String ratingAgency;

    private String ratingCode;

    private BigDecimal pdValue;
}
