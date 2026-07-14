package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tbl_dict_entry")
public class DictEntryEntity {
    @TableId
    private String entryId;
    private String categoryId;
    private String entryCode;
    private String entryName;
    private Integer sortOrder;
    @TableField("is_active")
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
