package com.bank.ecl.parameter.dict.dto;

import lombok.Data;

@Data
public class DictEntryVO {
    private String entryId;
    private String categoryId;
    private String entryCode;
    private String entryName;
    private Integer sortOrder;
    private Boolean isActive;
}
