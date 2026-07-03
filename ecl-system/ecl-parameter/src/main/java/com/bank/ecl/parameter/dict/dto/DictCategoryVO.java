package com.bank.ecl.parameter.dict.dto;

import lombok.Data;
import java.util.List;

@Data
public class DictCategoryVO {
    private String categoryId;
    private String categoryCode;
    private String categoryName;
    private String description;
    private Boolean isSystem;
    private Integer sortOrder;
    private List<DictEntryVO> entries;
}
