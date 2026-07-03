package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tbl_dict_category")
public class DictCategoryEntity {
    @TableId
    private String categoryId;
    private String categoryCode;
    private String categoryName;
    private String description;
    @TableField("is_system")
    private Boolean isSystem;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
