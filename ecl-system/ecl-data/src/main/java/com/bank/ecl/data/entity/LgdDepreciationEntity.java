package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("tbl_lgd_depreciation")
public class LgdDepreciationEntity {

    @TableId(type = IdType.AUTO)
    private Long depreciationId;

    private String schemeId;

    private String collateralType;

    private Integer yearOffset;

    private BigDecimal depreciationRate;
}
