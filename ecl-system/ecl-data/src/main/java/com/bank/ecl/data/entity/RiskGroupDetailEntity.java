package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tbl_risk_group_detail")
public class RiskGroupDetailEntity {

    @TableId(type = IdType.AUTO)
    private Long detailId;

    private String schemeId;

    private String groupId;

    private Integer priority;

    private String businessLine;

    private String customerType;

    private String productType;

    private String industryCode;

    private String regionCode;

    private String collateralType;
}
