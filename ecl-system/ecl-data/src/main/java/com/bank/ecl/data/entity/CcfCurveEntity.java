package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("tbl_ccf_curve")
public class CcfCurveEntity {

    @TableId(type = IdType.AUTO)
    private Long curveId;

    private String schemeId;

    private String productType;

    private String commitmentType;

    private Integer commitmentDaysMin;

    private Integer commitmentDaysMax;

    private BigDecimal ccfValue;
}
