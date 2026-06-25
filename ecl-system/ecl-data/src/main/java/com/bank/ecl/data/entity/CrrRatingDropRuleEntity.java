package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tbl_crr_rating_drop_rule")
public class CrrRatingDropRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long dropRuleId;

    private String schemeId;

    private String groupId;

    private String ratingAgency;

    private String currentRating;

    private Integer dropThreshold;
}
