package com.bank.ecl.parameter.dict;

import com.bank.ecl.parameter.dict.dto.DictCategoryVO;
import com.bank.ecl.parameter.dict.dto.DictEntryVO;
import com.bank.ecl.parameter.dict.dto.SchemeDictVO;
import com.bank.ecl.parameter.dict.dto.SchemeDictSaveReq;

import java.util.List;

public interface DictService {

    // ======= 全局字典 =======
    List<DictCategoryVO> listCategories();
    DictCategoryVO getCategory(String categoryId);
    DictEntryVO createEntry(DictEntryVO req);
    DictEntryVO updateEntry(String entryId, DictEntryVO req);
    void deleteEntry(String entryId);

    // ======= 方案基础信息 =======
    List<SchemeDictVO> listSchemeDicts(String schemeId);
    SchemeDictVO getSchemeDict(String schemeId, String categoryId);
    SchemeDictVO saveSchemeDict(String schemeId, String categoryId, SchemeDictSaveReq req);
    /** 查询方案指定分类的生效条目（供其他模块下拉使用） */
    List<DictEntryVO> getEffectiveEntries(String schemeId, String categoryCode);

    /** 初始化方案字典（新建方案时调用） */
    void initSchemeDicts(String schemeId);
}
