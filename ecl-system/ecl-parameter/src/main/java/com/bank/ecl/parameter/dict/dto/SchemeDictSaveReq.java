package com.bank.ecl.parameter.dict.dto;

import lombok.Data;
import java.util.List;

@Data
public class SchemeDictSaveReq {
    private String overrideType;       // INHERIT / CUSTOM
    private List<String> entryIds;     // CUSTOM 模式下的条目 ID 列表
}
