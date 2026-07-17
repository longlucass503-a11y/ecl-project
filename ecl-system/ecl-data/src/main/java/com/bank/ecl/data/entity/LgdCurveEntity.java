package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("tbl_lgd_curve")
public class LgdCurveEntity {

    @TableId(type = IdType.AUTO)
    private Long curveId;

    private String schemeId;

    private String groupId;

    private String collateralType;

    private String collateralCategory;

    private String productType;

    private BigDecimal lgdBaseValue;
}
