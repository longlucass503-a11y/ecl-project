package com.bank.ecl.parameter.scheme;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ecl.common.constant.SchemeStatus;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.common.util.UuidGenerator;
import com.bank.ecl.data.entity.*;
import com.bank.ecl.data.mapper.*;
import com.bank.ecl.parameter.copy.SchemeCopyService;
import com.bank.ecl.parameter.dict.DictService;
import com.bank.ecl.parameter.scheme.dto.SchemeCreateReq;
import com.bank.ecl.parameter.scheme.dto.SchemeDefaultParamReq;
import com.bank.ecl.parameter.scheme.dto.SchemeDiffVO;
import com.bank.ecl.parameter.scheme.dto.SchemeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EclSchemeServiceImpl implements EclSchemeService {

    private final EclSchemeMapper schemeMapper;
    private final SchemeCopyService schemeCopyService;
    private final DictService dictService;

    private final RiskGroupMapper riskGroupMapper;
    private final RiskGroupDetailMapper riskGroupDetailMapper;
    private final StageRuleMapper stageRuleMapper;
    private final CrrRatingDropRuleMapper crrRatingDropRuleMapper;
    private final PdScenarioMapper pdScenarioMapper;
    private final PdCurveMapper pdCurveMapper;
    private final LgdCurveMapper lgdCurveMapper;
    private final LgdCollateralDiscountMapper lgdCollateralDiscountMapper;
    private final LgdDepreciationMapper lgdDepreciationMapper;
    private final CcfCurveMapper ccfCurveMapper;
    private final OverlayRuleMapper overlayRuleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchemeVO createScheme(SchemeCreateReq req) {
        // 1. 生成 schemeId (UUID)、schemeCode (SCH_序列号)、版本号 v1.0
        int maxSeq = schemeMapper.selectMaxSchemeSeq();
        EclSchemeEntity entity = new EclSchemeEntity();
        entity.setSchemeId(UuidGenerator.uuid());
        entity.setSchemeCode(UuidGenerator.generateBizCode("SCH", maxSeq + 1));
        entity.setSchemeName(req.getSchemeName());
        entity.setSchemeVersion("v1.0");

        // 2. 状态=DRAFT，填充缺省值
        entity.setStatus(SchemeStatus.DRAFT.name());
        entity.setDiscountRate(new BigDecimal("0.0500"));
        entity.setDefaultCcf(BigDecimal.ZERO);
        entity.setDefaultLgd(new BigDecimal("0.4500"));
        entity.setCreatedBy("system");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDescription(req.getDescription());

        // 3. 保存并返回 VO
        schemeMapper.insert(entity);
        dictService.initSchemeDicts(entity.getSchemeId());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchemeVO copyFromEffective(String description) {
        // 1. 查当前 EFFECTIVE 方案
        EclSchemeEntity effective = schemeMapper.selectEffective();
        if (effective == null) {
            throw new EclException(ErrorCode.ECL_004, "无生效方案可供复制");
        }

        return copyFromSource(effective, description);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchemeVO copyFromScheme(String sourceSchemeId, String description) {
        EclSchemeEntity source = schemeMapper.selectById(sourceSchemeId);
        if (source == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在: " + sourceSchemeId);
        }

        return copyFromSource(source, description);
    }

    private SchemeVO copyFromSource(EclSchemeEntity source, String description) {
        int maxSeq = schemeMapper.selectMaxSchemeSeq();
        String newSchemeId = UuidGenerator.uuid();
        EclSchemeEntity newEntity = new EclSchemeEntity();
        newEntity.setSchemeId(newSchemeId);
        newEntity.setSchemeCode(UuidGenerator.generateBizCode("SCH", maxSeq + 1));
        newEntity.setSchemeName(source.getSchemeName() + "(副本)");

        // 3. 版本号 + 0.1
        String oldVersion = source.getSchemeVersion();
        if (oldVersion == null || !oldVersion.startsWith("v")) {
            oldVersion = "v1.0";
        }
        double verNum = Double.parseDouble(oldVersion.substring(1)) + 0.1;
        newEntity.setSchemeVersion("v" + String.format("%.1f", verNum));

        newEntity.setStatus(SchemeStatus.DRAFT.name());
        newEntity.setDiscountRate(source.getDiscountRate());
        newEntity.setDefaultCcf(source.getDefaultCcf());
        newEntity.setDefaultLgd(source.getDefaultLgd());
        newEntity.setLgdFloor(source.getLgdFloor());
        newEntity.setCreatedBy("system");
        newEntity.setCreatedAt(LocalDateTime.now());
        newEntity.setDescription(description != null ? description : "从 " + source.getSchemeCode() + " 复制");

        schemeMapper.insert(newEntity);

        // 2. 调用 SchemeCopyService.copyAll(sourceSchemeId, newSchemeId)
        schemeCopyService.copyAll(source.getSchemeId(), newSchemeId);

        // 4. 返回 VO
        return toVO(newEntity);
    }

    @Override
    public SchemeVO getScheme(String schemeId) {
        EclSchemeEntity entity = schemeMapper.selectById(schemeId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在: " + schemeId);
        }
        return toVO(entity);
    }

    @Override
    public List<SchemeVO> listSchemes(String status) {
        LambdaQueryWrapper<EclSchemeEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(EclSchemeEntity::getStatus, status);
        }
        wrapper.orderByDesc(EclSchemeEntity::getCreatedAt);
        return schemeMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public SchemeVO getEffectiveScheme() {
        EclSchemeEntity entity = schemeMapper.selectEffective();
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_004, "无生效方案");
        }
        return toVO(entity);
    }

    @Override
    public List<SchemeDiffVO> compareSchemes(String schemeId1, String schemeId2) {
        // 查询两个方案的版本号
        EclSchemeEntity s1 = schemeMapper.selectById(schemeId1);
        EclSchemeEntity s2 = schemeMapper.selectById(schemeId2);
        if (s1 == null || s2 == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在");
        }

        List<SchemeDiffVO> result = new ArrayList<>();

        // PD: 对比 tbl_pd_scenario + tbl_pd_curve
        result.add(buildDiff("PD", schemeId1, schemeId2, s1.getSchemeVersion(), s2.getSchemeVersion(),
                countByScheme(pdScenarioMapper, schemeId1) + countByScheme(pdCurveMapper, schemeId1),
                countByScheme(pdScenarioMapper, schemeId2) + countByScheme(pdCurveMapper, schemeId2)));

        // LGD: 对比 tbl_lgd_curve + discount + depreciation
        result.add(buildDiff("LGD", schemeId1, schemeId2, s1.getSchemeVersion(), s2.getSchemeVersion(),
                countByScheme(lgdCurveMapper, schemeId1)
                        + countByScheme(lgdCollateralDiscountMapper, schemeId1)
                        + countByScheme(lgdDepreciationMapper, schemeId1),
                countByScheme(lgdCurveMapper, schemeId2)
                        + countByScheme(lgdCollateralDiscountMapper, schemeId2)
                        + countByScheme(lgdDepreciationMapper, schemeId2)));

        // CCF: 对比 tbl_ccf_curve
        result.add(buildDiff("CCF", schemeId1, schemeId2, s1.getSchemeVersion(), s2.getSchemeVersion(),
                countByScheme(ccfCurveMapper, schemeId1),
                countByScheme(ccfCurveMapper, schemeId2)));

        // RISK_GROUP: 对比 tbl_risk_group
        result.add(buildDiff("RISK_GROUP", schemeId1, schemeId2, s1.getSchemeVersion(), s2.getSchemeVersion(),
                countByScheme(riskGroupMapper, schemeId1),
                countByScheme(riskGroupMapper, schemeId2)));

        // STAGE: 对比 tbl_stage_rule + crr_drop_rule
        result.add(buildDiff("STAGE", schemeId1, schemeId2, s1.getSchemeVersion(), s2.getSchemeVersion(),
                countByScheme(stageRuleMapper, schemeId1) + countByScheme(crrRatingDropRuleMapper, schemeId1),
                countByScheme(stageRuleMapper, schemeId2) + countByScheme(crrRatingDropRuleMapper, schemeId2)));

        // OVERLAY: 对比 tbl_overlay_rule
        result.add(buildDiff("OVERLAY", schemeId1, schemeId2, s1.getSchemeVersion(), s2.getSchemeVersion(),
                countByScheme(overlayRuleMapper, schemeId1),
                countByScheme(overlayRuleMapper, schemeId2)));

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchemeVO updateScheme(String schemeId, SchemeCreateReq req) {
        EclSchemeEntity entity = schemeMapper.selectById(schemeId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在: " + schemeId);
        }
        // 校验：仅 DRAFT 可修改
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new EclException(ErrorCode.ECL_004, "仅 DRAFT 状态的方案可修改");
        }
        // 更新 schemeName/description
        entity.setSchemeName(req.getSchemeName());
        entity.setDescription(req.getDescription());
        entity.setUpdatedAt(LocalDateTime.now());
        schemeMapper.updateById(entity);
        return toVO(entity);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchemeVO updateDefaultParams(String schemeId, SchemeDefaultParamReq req) {
        EclSchemeEntity entity = schemeMapper.selectById(schemeId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在: " + schemeId);
        }
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new EclException(ErrorCode.ECL_004, "仅 DRAFT 状态的方案可修改缺省参数");
        }
        entity.setDiscountRate(req.getDiscountRate());
        entity.setDefaultCcf(req.getDefaultCcf());
        entity.setDefaultLgd(req.getDefaultLgd());
        entity.setLgdFloor(req.getLgdFloor());
        entity.setUpdatedAt(LocalDateTime.now());
        schemeMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScheme(String schemeId) {
        EclSchemeEntity entity = schemeMapper.selectById(schemeId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在: " + schemeId);
        }
        // 校验：仅 DRAFT 可删除
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new EclException(ErrorCode.ECL_004, "仅 DRAFT 状态的方案可删除");
        }
        // 级联删除所有参数数据
        deleteCascade(schemeId);
        // 删除方案主表
        schemeMapper.deleteById(schemeId);
    }

    // ===== 辅助方法 =====

    private SchemeVO toVO(EclSchemeEntity entity) {
        if (entity == null) return null;
        SchemeVO vo = new SchemeVO();
        vo.setSchemeId(entity.getSchemeId());
        vo.setSchemeCode(entity.getSchemeCode());
        vo.setSchemeName(entity.getSchemeName());
        vo.setSchemeVersion(entity.getSchemeVersion());
        vo.setStatus(entity.getStatus());
        vo.setStatusDisplay(SchemeStatus.valueOf(entity.getStatus()).getDisplayName());
        vo.setEffectiveDate(entity.getEffectiveDate());
        vo.setEffectiveAt(entity.getEffectiveAt());
        vo.setExpiredAt(entity.getExpiredAt());
        vo.setDiscountRate(entity.getDiscountRate());
        vo.setDefaultCcf(entity.getDefaultCcf());
        vo.setDefaultLgd(entity.getDefaultLgd());
        vo.setLgdFloor(entity.getLgdFloor());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedBy(entity.getUpdatedBy());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setDescription(entity.getDescription());

        // Populate module statistics counts
        String schemeId = entity.getSchemeId();
        vo.setRiskGroupCount(Math.toIntExact(
                riskGroupMapper.selectCount(new LambdaQueryWrapper<RiskGroupEntity>()
                        .eq(RiskGroupEntity::getSchemeId, schemeId))));
        vo.setPdScenarioCount(Math.toIntExact(
                pdScenarioMapper.selectCount(new LambdaQueryWrapper<PdScenarioEntity>()
                        .eq(PdScenarioEntity::getSchemeId, schemeId))));
        vo.setLgdCurveCount(Math.toIntExact(
                lgdCurveMapper.selectCount(new LambdaQueryWrapper<LgdCurveEntity>()
                        .eq(LgdCurveEntity::getSchemeId, schemeId))));
        vo.setCcfCurveCount(Math.toIntExact(
                ccfCurveMapper.selectCount(new LambdaQueryWrapper<CcfCurveEntity>()
                        .eq(CcfCurveEntity::getSchemeId, schemeId))));
        vo.setOverlayRuleCount(Math.toIntExact(
                overlayRuleMapper.selectCount(new LambdaQueryWrapper<OverlayRuleEntity>()
                        .eq(OverlayRuleEntity::getSchemeId, schemeId))));
        vo.setStageRuleCount(Math.toIntExact(
                stageRuleMapper.selectCount(new LambdaQueryWrapper<StageRuleEntity>()
                        .eq(StageRuleEntity::getSchemeId, schemeId))));

        return vo;
    }

    private long countByScheme(BaseMapper<?> mapper, String schemeId) {
        // 使用 MyBatis-Plus 的 selectCount 统计某个 scheme_id 下的行数
        // 通过传入的 mapper 类型，构建 LambdaQueryWrapper
        if (mapper instanceof RiskGroupMapper) {
            return ((RiskGroupMapper) mapper).selectCount(
                    new LambdaQueryWrapper<RiskGroupEntity>().eq(RiskGroupEntity::getSchemeId, schemeId));
        }
        if (mapper instanceof RiskGroupDetailMapper) {
            return ((RiskGroupDetailMapper) mapper).selectCount(
                    new LambdaQueryWrapper<RiskGroupDetailEntity>().eq(RiskGroupDetailEntity::getSchemeId, schemeId));
        }
        if (mapper instanceof StageRuleMapper) {
            return ((StageRuleMapper) mapper).selectCount(
                    new LambdaQueryWrapper<StageRuleEntity>().eq(StageRuleEntity::getSchemeId, schemeId));
        }
        if (mapper instanceof CrrRatingDropRuleMapper) {
            return ((CrrRatingDropRuleMapper) mapper).selectCount(
                    new LambdaQueryWrapper<CrrRatingDropRuleEntity>().eq(CrrRatingDropRuleEntity::getSchemeId, schemeId));
        }
        if (mapper instanceof PdScenarioMapper) {
            return ((PdScenarioMapper) mapper).selectCount(
                    new LambdaQueryWrapper<PdScenarioEntity>().eq(PdScenarioEntity::getSchemeId, schemeId));
        }
        if (mapper instanceof PdCurveMapper) {
            return ((PdCurveMapper) mapper).selectCount(
                    new LambdaQueryWrapper<PdCurveEntity>().eq(PdCurveEntity::getSchemeId, schemeId));
        }
        if (mapper instanceof LgdCurveMapper) {
            return ((LgdCurveMapper) mapper).selectCount(
                    new LambdaQueryWrapper<LgdCurveEntity>().eq(LgdCurveEntity::getSchemeId, schemeId));
        }
        if (mapper instanceof LgdCollateralDiscountMapper) {
            return ((LgdCollateralDiscountMapper) mapper).selectCount(
                    new LambdaQueryWrapper<LgdCollateralDiscountEntity>().eq(LgdCollateralDiscountEntity::getSchemeId, schemeId));
        }
        if (mapper instanceof LgdDepreciationMapper) {
            return ((LgdDepreciationMapper) mapper).selectCount(
                    new LambdaQueryWrapper<LgdDepreciationEntity>().eq(LgdDepreciationEntity::getSchemeId, schemeId));
        }
        if (mapper instanceof CcfCurveMapper) {
            return ((CcfCurveMapper) mapper).selectCount(
                    new LambdaQueryWrapper<CcfCurveEntity>().eq(CcfCurveEntity::getSchemeId, schemeId));
        }
        if (mapper instanceof OverlayRuleMapper) {
            return ((OverlayRuleMapper) mapper).selectCount(
                    new LambdaQueryWrapper<OverlayRuleEntity>().eq(OverlayRuleEntity::getSchemeId, schemeId));
        }
        return 0;
    }

    private SchemeDiffVO buildDiff(String module, String id1, String id2,
                                   String verFrom, String verTo,
                                   long count1, long count2) {
        SchemeDiffVO diff = new SchemeDiffVO();
        diff.setModule(module);
        diff.setVersionFrom(verFrom);
        diff.setVersionTo(verTo);
        diff.setChangedItems((int) Math.abs(count1 - count2));
        diff.setSame(count1 == count2);
        return diff;
    }

    @Transactional(rollbackFor = Exception.class)
    protected void deleteCascade(String schemeId) {
        riskGroupMapper.delete(new LambdaQueryWrapper<RiskGroupEntity>().eq(RiskGroupEntity::getSchemeId, schemeId));
        riskGroupDetailMapper.delete(new LambdaQueryWrapper<RiskGroupDetailEntity>().eq(RiskGroupDetailEntity::getSchemeId, schemeId));
        stageRuleMapper.delete(new LambdaQueryWrapper<StageRuleEntity>().eq(StageRuleEntity::getSchemeId, schemeId));
        crrRatingDropRuleMapper.delete(new LambdaQueryWrapper<CrrRatingDropRuleEntity>().eq(CrrRatingDropRuleEntity::getSchemeId, schemeId));
        pdScenarioMapper.delete(new LambdaQueryWrapper<PdScenarioEntity>().eq(PdScenarioEntity::getSchemeId, schemeId));
        pdCurveMapper.delete(new LambdaQueryWrapper<PdCurveEntity>().eq(PdCurveEntity::getSchemeId, schemeId));
        lgdCurveMapper.delete(new LambdaQueryWrapper<LgdCurveEntity>().eq(LgdCurveEntity::getSchemeId, schemeId));
        lgdCollateralDiscountMapper.delete(new LambdaQueryWrapper<LgdCollateralDiscountEntity>().eq(LgdCollateralDiscountEntity::getSchemeId, schemeId));
        lgdDepreciationMapper.delete(new LambdaQueryWrapper<LgdDepreciationEntity>().eq(LgdDepreciationEntity::getSchemeId, schemeId));
        ccfCurveMapper.delete(new LambdaQueryWrapper<CcfCurveEntity>().eq(CcfCurveEntity::getSchemeId, schemeId));
        overlayRuleMapper.delete(new LambdaQueryWrapper<OverlayRuleEntity>().eq(OverlayRuleEntity::getSchemeId, schemeId));
    }
}
