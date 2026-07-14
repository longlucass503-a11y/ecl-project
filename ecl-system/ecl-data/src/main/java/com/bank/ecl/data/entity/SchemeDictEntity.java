package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tbl_scheme_dict")
public class SchemeDictEntity {
    @TableId
    private String id;
    private String schemeId;
    private String categoryId;
    private String overrideType;
    private String entryIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
