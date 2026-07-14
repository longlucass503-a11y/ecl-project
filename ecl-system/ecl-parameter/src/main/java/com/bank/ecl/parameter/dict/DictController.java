package com.bank.ecl.parameter.dict;

import com.bank.ecl.common.model.Result;
import com.bank.ecl.parameter.dict.dto.DictCategoryVO;
import com.bank.ecl.parameter.dict.dto.DictEntryVO;
import com.bank.ecl.parameter.dict.dto.SchemeDictSaveReq;
import com.bank.ecl.parameter.dict.dto.SchemeDictVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    // ======= 全局字典分类（带条目） =======
    @GetMapping("/categories")
    public Result<List<DictCategoryVO>> listCategories() {
        return Result.success(dictService.listCategories());
    }

    @GetMapping("/categories/{categoryId}")
    public Result<DictCategoryVO> getCategory(@PathVariable String categoryId) {
        return Result.success(dictService.getCategory(categoryId));
    }

    // ======= 字典条目 CRUD =======
    @PostMapping("/entries")
    public Result<DictEntryVO> createEntry(@RequestBody DictEntryVO req) {
        return Result.success(dictService.createEntry(req));
    }

    @PutMapping("/entries/{entryId}")
    public Result<DictEntryVO> updateEntry(@PathVariable String entryId, @RequestBody DictEntryVO req) {
        return Result.success(dictService.updateEntry(entryId, req));
    }

    @DeleteMapping("/entries/{entryId}")
    public Result<Void> deleteEntry(@PathVariable String entryId) {
        dictService.deleteEntry(entryId);
        return Result.success();
    }

    // ======= 查询方案指定分类的生效条目（供其他模块下拉使用） =======
    @GetMapping("/scheme/{schemeId}/effective")
    public Result<List<DictEntryVO>> getEffectiveEntries(
            @PathVariable String schemeId,
            @RequestParam String categoryCode) {
        return Result.success(dictService.getEffectiveEntries(schemeId, categoryCode));
    }

    // ======= 方案基础信息 =======
    @GetMapping("/scheme/{schemeId}")
    public Result<List<SchemeDictVO>> listSchemeDicts(@PathVariable String schemeId) {
        return Result.success(dictService.listSchemeDicts(schemeId));
    }

    @GetMapping("/scheme/{schemeId}/category/{categoryId}")
    public Result<SchemeDictVO> getSchemeDict(@PathVariable String schemeId,
                                              @PathVariable String categoryId) {
        return Result.success(dictService.getSchemeDict(schemeId, categoryId));
    }

    @PutMapping("/scheme/{schemeId}/category/{categoryId}")
    public Result<SchemeDictVO> saveSchemeDict(@PathVariable String schemeId,
                                               @PathVariable String categoryId,
                                               @RequestBody SchemeDictSaveReq req) {
        return Result.success(dictService.saveSchemeDict(schemeId, categoryId, req));
    }
}
