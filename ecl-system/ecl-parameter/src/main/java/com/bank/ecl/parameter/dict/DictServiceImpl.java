package com.bank.ecl.parameter.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.common.util.UuidGenerator;
import com.bank.ecl.data.entity.DictCategoryEntity;
import com.bank.ecl.data.entity.DictEntryEntity;
import com.bank.ecl.data.entity.SchemeDictEntity;
import com.bank.ecl.data.mapper.DictCategoryMapper;
import com.bank.ecl.data.mapper.DictEntryMapper;
import com.bank.ecl.data.mapper.SchemeDictMapper;
import com.bank.ecl.parameter.dict.dto.DictCategoryVO;
import com.bank.ecl.parameter.dict.dto.DictEntryVO;
import com.bank.ecl.parameter.dict.dto.SchemeDictSaveReq;
import com.bank.ecl.parameter.dict.dto.SchemeDictVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictCategoryMapper categoryMapper;
    private final DictEntryMapper entryMapper;
    private final SchemeDictMapper schemeDictMapper;
    private final ObjectMapper objectMapper;

    // ======================== 全局字典 ========================

    @Override
    public List<DictCategoryVO> listCategories() {
        List<DictCategoryEntity> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<DictCategoryEntity>()
                        .orderByAsc(DictCategoryEntity::getSortOrder));
        List<String> catIds = categories.stream()
                .map(DictCategoryEntity::getCategoryId).collect(Collectors.toList());
        List<DictEntryEntity> allEntries = entryMapper.selectList(
                new LambdaQueryWrapper<DictEntryEntity>()
                        .in(DictEntryEntity::getCategoryId, catIds)
                        .orderByAsc(DictEntryEntity::getSortOrder));
        Map<String, List<DictEntryEntity>> entryMap = allEntries.stream()
                .collect(Collectors.groupingBy(DictEntryEntity::getCategoryId));

        return categories.stream().map(cat -> {
            DictCategoryVO vo = toCategoryVO(cat);
            vo.setEntries(entryMap.getOrDefault(cat.getCategoryId(), Collections.emptyList())
                    .stream().map(this::toEntryVO).collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public DictCategoryVO getCategory(String categoryId) {
        DictCategoryEntity cat = categoryMapper.selectById(categoryId);
        if (cat == null) throw new EclException(ErrorCode.ECL_004, "字典分类不存在");
        DictCategoryVO vo = toCategoryVO(cat);
        vo.setEntries(entryMapper.selectList(
                new LambdaQueryWrapper<DictEntryEntity>()
                        .eq(DictEntryEntity::getCategoryId, categoryId)
                        .orderByAsc(DictEntryEntity::getSortOrder))
                .stream().map(this::toEntryVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictEntryVO createEntry(DictEntryVO req) {
        // 校验同分类下编码唯一
        Long count = entryMapper.selectCount(
                new LambdaQueryWrapper<DictEntryEntity>()
                        .eq(DictEntryEntity::getCategoryId, req.getCategoryId())
                        .eq(DictEntryEntity::getEntryCode, req.getEntryCode()));
        if (count > 0) {
            throw new EclException(ErrorCode.ECL_006, "该分类下编码 " + req.getEntryCode() + " 已存在");
        }
        DictEntryEntity entity = new DictEntryEntity();
        entity.setEntryId(UuidGenerator.uuid());
        entity.setCategoryId(req.getCategoryId());
        entity.setEntryCode(req.getEntryCode());
        entity.setEntryName(req.getEntryName());
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        entity.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
        entity.setCreatedAt(LocalDateTime.now());
        entryMapper.insert(entity);
        return toEntryVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictEntryVO updateEntry(String entryId, DictEntryVO req) {
        DictEntryEntity entity = entryMapper.selectById(entryId);
        if (entity == null) throw new EclException(ErrorCode.ECL_004, "字典条目不存在");
        if (req.getEntryName() != null) entity.setEntryName(req.getEntryName());
        if (req.getSortOrder() != null) entity.setSortOrder(req.getSortOrder());
        if (req.getIsActive() != null) entity.setIsActive(req.getIsActive());
        entity.setUpdatedAt(LocalDateTime.now());
        entryMapper.updateById(entity);
        return toEntryVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEntry(String entryId) {
        DictEntryEntity entity = entryMapper.selectById(entryId);
        if (entity == null) return;
        entryMapper.deleteById(entryId);
    }

    // ======================== 方案基础信息 ========================

    @Override
    public List<SchemeDictVO> listSchemeDicts(String schemeId) {
        // 查出所有分类
        List<DictCategoryEntity> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<DictCategoryEntity>()
                        .orderByAsc(DictCategoryEntity::getSortOrder));
        // 查出该方案已有的覆盖配置
        List<SchemeDictEntity> schemeDicts = schemeDictMapper.selectList(
                new LambdaQueryWrapper<SchemeDictEntity>()
                        .eq(SchemeDictEntity::getSchemeId, schemeId));

        Map<String, SchemeDictEntity> schemeDictMap = schemeDicts.stream()
                .collect(Collectors.toMap(SchemeDictEntity::getCategoryId, d -> d, (a, b) -> a));

        return categories.stream().map(cat -> {
            SchemeDictEntity sd = schemeDictMap.get(cat.getCategoryId());
            return buildSchemeDictVO(schemeId, cat, sd);
        }).collect(Collectors.toList());
    }

    @Override
    public SchemeDictVO getSchemeDict(String schemeId, String categoryId) {
        DictCategoryEntity cat = categoryMapper.selectById(categoryId);
        if (cat == null) throw new EclException(ErrorCode.ECL_004, "字典分类不存在");
        SchemeDictEntity sd = schemeDictMapper.selectOne(
                new LambdaQueryWrapper<SchemeDictEntity>()
                        .eq(SchemeDictEntity::getSchemeId, schemeId)
                        .eq(SchemeDictEntity::getCategoryId, categoryId));
        return buildSchemeDictVO(schemeId, cat, sd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchemeDictVO saveSchemeDict(String schemeId, String categoryId, SchemeDictSaveReq req) {
        // 查找或创建
        SchemeDictEntity sd = schemeDictMapper.selectOne(
                new LambdaQueryWrapper<SchemeDictEntity>()
                        .eq(SchemeDictEntity::getSchemeId, schemeId)
                        .eq(SchemeDictEntity::getCategoryId, categoryId));
        boolean isNew = (sd == null);
        if (isNew) {
            sd = new SchemeDictEntity();
            sd.setId(UuidGenerator.uuid());
            sd.setSchemeId(schemeId);
            sd.setCategoryId(categoryId);
            sd.setCreatedAt(LocalDateTime.now());
        }
        sd.setOverrideType(req.getOverrideType());
        if ("CUSTOM".equals(req.getOverrideType()) && req.getEntryIds() != null) {
            try {
                sd.setEntryIds(objectMapper.writeValueAsString(req.getEntryIds()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("序列化 entryIds 失败", e);
            }
        } else {
            sd.setEntryIds(null);
        }
        sd.setUpdatedAt(LocalDateTime.now());
        if (isNew) {
            schemeDictMapper.insert(sd);
        } else {
            schemeDictMapper.updateById(sd);
        }

        DictCategoryEntity cat = categoryMapper.selectById(categoryId);
        return buildSchemeDictVO(schemeId, cat, sd);
    }

    @Override
    public List<DictEntryVO> getEffectiveEntries(String schemeId, String categoryCode) {
        DictCategoryEntity cat = categoryMapper.selectOne(
                new LambdaQueryWrapper<DictCategoryEntity>()
                        .eq(DictCategoryEntity::getCategoryCode, categoryCode));
        if (cat == null) return Collections.emptyList();

        List<DictEntryEntity> allEntries = entryMapper.selectList(
                new LambdaQueryWrapper<DictEntryEntity>()
                        .eq(DictEntryEntity::getCategoryId, cat.getCategoryId())
                        .eq(DictEntryEntity::getIsActive, true)
                        .orderByAsc(DictEntryEntity::getSortOrder));

        SchemeDictEntity sd = schemeDictMapper.selectOne(
                new LambdaQueryWrapper<SchemeDictEntity>()
                        .eq(SchemeDictEntity::getSchemeId, schemeId)
                        .eq(SchemeDictEntity::getCategoryId, cat.getCategoryId()));

        if (sd != null && "CUSTOM".equals(sd.getOverrideType()) && sd.getEntryIds() != null) {
            try {
                List<String> ids = objectMapper.readValue(sd.getEntryIds(),
                        new TypeReference<List<String>>() {});
                Set<String> idSet = new HashSet<>(ids);
                return allEntries.stream()
                        .filter(e -> idSet.contains(e.getEntryId()))
                        .map(this::toEntryVO)
                        .collect(Collectors.toList());
            } catch (JsonProcessingException e) {
                return allEntries.stream().map(this::toEntryVO).collect(Collectors.toList());
            }
        }
        return allEntries.stream().map(this::toEntryVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initSchemeDicts(String schemeId) {
        List<DictCategoryEntity> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<DictCategoryEntity>()
                        .orderByAsc(DictCategoryEntity::getSortOrder));
        for (DictCategoryEntity cat : categories) {
            SchemeDictEntity sd = new SchemeDictEntity();
            sd.setId(UuidGenerator.uuid());
            sd.setSchemeId(schemeId);
            sd.setCategoryId(cat.getCategoryId());
            sd.setOverrideType("INHERIT");
            sd.setCreatedAt(LocalDateTime.now());
            schemeDictMapper.insert(sd);
        }
    }

    // ======================== 辅助方法 ========================

    private SchemeDictVO buildSchemeDictVO(String schemeId, DictCategoryEntity cat, SchemeDictEntity sd) {
        SchemeDictVO vo = new SchemeDictVO();
        vo.setSchemeId(schemeId);
        vo.setCategoryId(cat.getCategoryId());
        vo.setCategoryCode(cat.getCategoryCode());
        vo.setCategoryName(cat.getCategoryName());

        if (sd != null) {
            vo.setId(sd.getId());
            vo.setOverrideType(sd.getOverrideType());
            if ("CUSTOM".equals(sd.getOverrideType()) && sd.getEntryIds() != null) {
                try {
                    List<String> ids = objectMapper.readValue(sd.getEntryIds(),
                            new TypeReference<List<String>>() {});
                    vo.setEntryIds(ids);
                } catch (JsonProcessingException e) {
                    vo.setEntryIds(Collections.emptyList());
                }
            }
        } else {
            vo.setOverrideType("INHERIT");
            vo.setEntryIds(Collections.emptyList());
        }

        // 计算实际生效的条目
        List<DictEntryEntity> allEntries = entryMapper.selectList(
                new LambdaQueryWrapper<DictEntryEntity>()
                        .eq(DictEntryEntity::getCategoryId, cat.getCategoryId())
                        .orderByAsc(DictEntryEntity::getSortOrder));

        if (sd != null && "CUSTOM".equals(sd.getOverrideType()) && vo.getEntryIds() != null) {
            Set<String> idSet = new HashSet<>(vo.getEntryIds());
            vo.setEffectiveEntries(allEntries.stream()
                    .filter(e -> idSet.contains(e.getEntryId()))
                    .map(this::toEntryVO)
                    .collect(Collectors.toList()));
        } else {
            vo.setEffectiveEntries(allEntries.stream()
                    .filter(DictEntryEntity::getIsActive)
                    .map(this::toEntryVO)
                    .collect(Collectors.toList()));
        }
        return vo;
    }

    private DictCategoryVO toCategoryVO(DictCategoryEntity entity) {
        DictCategoryVO vo = new DictCategoryVO();
        vo.setCategoryId(entity.getCategoryId());
        vo.setCategoryCode(entity.getCategoryCode());
        vo.setCategoryName(entity.getCategoryName());
        vo.setDescription(entity.getDescription());
        vo.setIsSystem(entity.getIsSystem());
        vo.setSortOrder(entity.getSortOrder());
        return vo;
    }

    private DictEntryVO toEntryVO(DictEntryEntity entity) {
        DictEntryVO vo = new DictEntryVO();
        vo.setEntryId(entity.getEntryId());
        vo.setCategoryId(entity.getCategoryId());
        vo.setEntryCode(entity.getEntryCode());
        vo.setEntryName(entity.getEntryName());
        vo.setSortOrder(entity.getSortOrder());
        vo.setIsActive(entity.getIsActive());
        return vo;
    }
}
