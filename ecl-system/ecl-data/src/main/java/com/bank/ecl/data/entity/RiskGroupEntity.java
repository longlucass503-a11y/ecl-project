package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tbl_risk_group")
public class RiskGroupEntity {

    @TableId
    private String groupId;

    private String groupCode;

    private String schemeId;

    private String groupName;

    private Integer sortOrder;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
