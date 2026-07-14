package com.bank.ecl.parameter.copy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.data.entity.*;
import com.bank.ecl.data.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SchemeCopyService {

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

    @Transactional(rollbackFor = Exception.class)
    public void copyAll(String sourceSchemeId, String targetSchemeId) {
        if (sourceSchemeId.equals(targetSchemeId)) {
            throw new IllegalArgumentException("sourceSchemeId [" + sourceSchemeId + "] 与 targetSchemeId 相同，不允许自复制");
        }
        // 1. 复制 risk_group：建立 oldGroupId → newGroupId 映射
        List<RiskGroupEntity> groups = riskGroupMapper.selectList(
                new LambdaQueryWrapper<RiskGroupEntity>().eq(RiskGroupEntity::getSchemeId, sourceSchemeId));
        Map<String, String> groupIdMapping = new HashMap<>();
        for (RiskGroupEntity g : groups) {
            String oldId = g.getGroupId();
            String newId = java.util.UUID.randomUUID().toString().replace("-", "");
            groupIdMapping.put(oldId, newId);
            g.setGroupId(newId);
            g.setSchemeId(targetSchemeId);
            riskGroupMapper.insert(g);
        }

        // 2. 复制 risk_group_detail（用映射后的 groupId）
        List<RiskGroupDetailEntity> details = riskGroupDetailMapper.selectList(
                new LambdaQueryWrapper<RiskGroupDetailEntity>().eq(RiskGroupDetailEntity::getSchemeId, sourceSchemeId));
        for (RiskGroupDetailEntity d : details) {
            d.setDetailId(null);
            d.setSchemeId(targetSchemeId);
            d.setGroupId(groupIdMapping.get(d.getGroupId()));
            riskGroupDetailMapper.insert(d);
        }

        // 3. 复制 stage_rule（用映射后的 groupId）
        List<StageRuleEntity> stageRules = stageRuleMapper.selectList(
                new LambdaQueryWrapper<StageRuleEntity>().eq(StageRuleEntity::getSchemeId, sourceSchemeId));
        for (StageRuleEntity r : stageRules) {
            r.setRuleId(null);
            r.setSchemeId(targetSchemeId);
            r.setGroupId(groupIdMapping.get(r.getGroupId()));
            stageRuleMapper.insert(r);
        }

        // 4. 复制 crr_rating_drop_rule（用映射后的 groupId）
        List<CrrRatingDropRuleEntity> dropRules = crrRatingDropRuleMapper.selectList(
                new LambdaQueryWrapper<CrrRatingDropRuleEntity>().eq(CrrRatingDropRuleEntity::getSchemeId, sourceSchemeId));
        for (CrrRatingDropRuleEntity r : dropRules) {
            r.setDropRuleId(null);
            r.setSchemeId(targetSchemeId);
            r.setGroupId(groupIdMapping.get(r.getGroupId()));
            crrRatingDropRuleMapper.insert(r);
        }

        // 5. 复制 pd_scenario（保留原 scenario_id，建立 old→new 映射）
        List<PdScenarioEntity> scenarios = pdScenarioMapper.selectList(
                new LambdaQueryWrapper<PdScenarioEntity>().eq(PdScenarioEntity::getSchemeId, sourceSchemeId));
        Map<Long, Long> scenarioIdMapping = new HashMap<>();
        for (PdScenarioEntity s : scenarios) {
            Long oldId = s.getScenarioId();
            s.setScenarioId(null);
            s.setSchemeId(targetSchemeId);
            pdScenarioMapper.insert(s);
            scenarioIdMapping.put(oldId, s.getScenarioId());
        }

        // 6. 复制 pd_curve（用映射后的 groupId + scenarioId）
        List<PdCurveEntity> pdCurves = pdCurveMapper.selectList(
                new LambdaQueryWrapper<PdCurveEntity>().eq(PdCurveEntity::getSchemeId, sourceSchemeId));
        for (PdCurveEntity c : pdCurves) {
            c.setCurveId(null);
            c.setSchemeId(targetSchemeId);
            c.setGroupId(groupIdMapping.get(c.getGroupId()));
            c.setScenarioId(scenarioIdMapping.get(c.getScenarioId()));
            pdCurveMapper.insert(c);
        }

        // 7. 复制 lgd_curve（用映射后的 groupId）
        List<LgdCurveEntity> lgdCurves = lgdCurveMapper.selectList(
                new LambdaQueryWrapper<LgdCurveEntity>().eq(LgdCurveEntity::getSchemeId, sourceSchemeId));
        for (LgdCurveEntity c : lgdCurves) {
            c.setCurveId(null);
            c.setSchemeId(targetSchemeId);
            c.setGroupId(groupIdMapping.get(c.getGroupId()));
            lgdCurveMapper.insert(c);
        }

        // 8. 复制 lgd_collateral_discount
        List<LgdCollateralDiscountEntity> discounts = lgdCollateralDiscountMapper.selectList(
                new LambdaQueryWrapper<LgdCollateralDiscountEntity>().eq(LgdCollateralDiscountEntity::getSchemeId, sourceSchemeId));
        for (LgdCollateralDiscountEntity d : discounts) {
            d.setDiscountId(null);
            d.setSchemeId(targetSchemeId);
            lgdCollateralDiscountMapper.insert(d);
        }

        // 9. 复制 lgd_depreciation
        List<LgdDepreciationEntity> depreciations = lgdDepreciationMapper.selectList(
                new LambdaQueryWrapper<LgdDepreciationEntity>().eq(LgdDepreciationEntity::getSchemeId, sourceSchemeId));
        for (LgdDepreciationEntity d : depreciations) {
            d.setDepreciationId(null);
            d.setSchemeId(targetSchemeId);
            lgdDepreciationMapper.insert(d);
        }

        // 10. 复制 ccf_curve
        List<CcfCurveEntity> ccfCurves = ccfCurveMapper.selectList(
                new LambdaQueryWrapper<CcfCurveEntity>().eq(CcfCurveEntity::getSchemeId, sourceSchemeId));
        for (CcfCurveEntity c : ccfCurves) {
            c.setCurveId(null);
            c.setSchemeId(targetSchemeId);
            ccfCurveMapper.insert(c);
        }

        // 11. 复制 overlay_rule（用映射后的 groupId）
        List<OverlayRuleEntity> overlays = overlayRuleMapper.selectList(
                new LambdaQueryWrapper<OverlayRuleEntity>().eq(OverlayRuleEntity::getSchemeId, sourceSchemeId));
        for (OverlayRuleEntity o : overlays) {
            o.setRuleId(null);
            o.setSchemeId(targetSchemeId);
            // GLOBAL 规则的 groupId 是空字符串（非真实分组ID），不应查 groupIdMapping（会得到null，
            // 而 group_id 列是 NOT NULL），原样保留；只有非空的分组级规则才需要重映射到新分组ID。
            if (o.getGroupId() != null && !o.getGroupId().isBlank()) {
                o.setGroupId(groupIdMapping.get(o.getGroupId()));
            }
            overlayRuleMapper.insert(o);
        }
    }
}
