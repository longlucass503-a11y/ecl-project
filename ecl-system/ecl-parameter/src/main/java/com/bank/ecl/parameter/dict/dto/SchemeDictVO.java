package com.bank.ecl.parameter.dict.dto;

import lombok.Data;
import java.util.List;

@Data
public class SchemeDictVO {
    private String id;
    private String schemeId;
    private String categoryId;
    private String categoryCode;
    private String categoryName;
    private String overrideType;   // INHERIT / CUSTOM
    private List<String> entryIds; // CUSTOM 模式下选中的条目ID
    private List<DictEntryVO> effectiveEntries; // 实际生效的条目列表
}
