package com.bank.ecl.parameter.overlay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.SchemeStatus;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.common.util.ConditionEvaluator;
import com.bank.ecl.data.entity.EclSchemeEntity;
import com.bank.ecl.data.entity.OverlayRuleEntity;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.data.mapper.OverlayRuleMapper;
import com.bank.ecl.parameter.overlay.dto.OverlayMatchTestReq;
import com.bank.ecl.parameter.overlay.dto.OverlayMatchTestResp;
import com.bank.ecl.parameter.overlay.dto.OverlayRuleCreateReq;
import com.bank.ecl.parameter.overlay.dto.OverlayRuleVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OverlayServiceImpl implements OverlayService {

    private final OverlayRuleMapper overlayRuleMapper;
    private final EclSchemeMapper eclSchemeMapper;
    private final ObjectMapper objectMapper;

    // ======================== 公共校验 ========================

    private EclSchemeEntity checkSchemeDraft(String schemeId) {
        EclSchemeEntity scheme = eclSchemeMapper.selectById(schemeId);
        if (scheme == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在: " + schemeId);
        }
        if (!SchemeStatus.DRAFT.name().equals(scheme.getStatus())) {
            throw new EclException(ErrorCode.ECL_006,
                    "仅 DRAFT 状态的方案可修改，当前状态: " + scheme.getStatus());
        }
        return scheme;
    }

    private void validateConditions(String conditionsJson) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(conditionsJson);
        } catch (JsonProcessingException e) {
            throw new EclException(ErrorCode.ECL_006,
                    "conditions JSON 格式错误: " + e.getMessage());
        }
    }

    private void validateDates(LocalDate effectiveDate, LocalDate expiryDate) {
        if (effectiveDate != null && expiryDate != null && !expiryDate.isAfter(effectiveDate)) {
            throw new EclException(ErrorCode.ECL_006,
                    "expiryDate 必须晚于 effectiveDate，当前: effectiveDate=" + effectiveDate
                            + ", expiryDate=" + expiryDate);
        }
    }

    // ======================== 查询 ========================

    @Override
    public List<OverlayRuleVO> listRules(String schemeId, String groupId) {
        if (schemeId == null || schemeId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "schemeId 不能为空");
        }
        if (groupId == null || groupId.isBlank()) {
            LambdaQueryWrapper<OverlayRuleEntity> wrapper = new LambdaQueryWrapper<OverlayRuleEntity>()
                    .eq(OverlayRuleEntity::getSchemeId, schemeId)
                    .orderByAsc(OverlayRuleEntity::getGroupId)
                    .orderByAsc(OverlayRuleEntity::getPriority);
            return overlayRuleMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
        }
        LambdaQueryWrapper<OverlayRuleEntity> wrapper = new LambdaQueryWrapper<OverlayRuleEntity>()
                .eq(OverlayRuleEntity::getSchemeId, schemeId)
                .eq(OverlayRuleEntity::getGroupId, groupId)
                .orderByAsc(OverlayRuleEntity::getPriority);
        List<OverlayRuleEntity> entities = overlayRuleMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    // ======================== 创建 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OverlayRuleVO createRule(OverlayRuleCreateReq req) {
        checkSchemeDraft(req.getSchemeId());

        // 业务校验：ADDBP 类型下 adjustmentValue 须大于 0
        if ("ADDBP".equals(req.getAdjustmentType())
                && (req.getAdjustmentValue() == null || req.getAdjustmentValue().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new EclException(ErrorCode.ECL_006,
                    "ADDBP 类型下 adjustmentValue 必须大于 0");
        }

        String conditions = normalizeInputConditions(req.getConditions());
        validateConditions(conditions);
        validateDates(req.getEffectiveDate(), req.getExpiryDate());

        OverlayRuleEntity entity = buildEntity(req, conditions, defaultEffectiveDate(req.getEffectiveDate()));
        overlayRuleMapper.insert(entity);
        return toVO(entity);
    }

    // ======================== 更新 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OverlayRuleVO updateRule(Long ruleId, OverlayRuleCreateReq req) {
        OverlayRuleEntity entity = overlayRuleMapper.selectById(ruleId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "管理层叠加规则不存在: " + ruleId);
        }
        checkSchemeDraft(entity.getSchemeId());

        // 业务校验：ADDBP 类型下 adjustmentValue 须大于 0
        if ("ADDBP".equals(req.getAdjustmentType())
                && (req.getAdjustmentValue() == null || req.getAdjustmentValue().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new EclException(ErrorCode.ECL_006,
                    "ADDBP 类型下 adjustmentValue 必须大于 0");
        }

        String conditions = normalizeInputConditions(req.getConditions());
        validateConditions(conditions);
        validateDates(req.getEffectiveDate(), req.getExpiryDate());

        // 更新字段
        if (req.getOverlayType() != null) {
            entity.setOverlayType(req.getOverlayType());
        }
        if (req.getAdjustmentTarget() != null) {
            entity.setAdjustmentTarget(req.getAdjustmentTarget());
        }
        if (req.getAdjustmentType() != null) {
            entity.setAdjustmentType(req.getAdjustmentType());
        }
        if (req.getAdjustmentValue() != null) {
            entity.setAdjustmentValue(req.getAdjustmentValue());
        }
        if (req.getPriority() != null) {
            entity.setPriority(req.getPriority());
        }
        entity.setConditions(conditions);
        entity.setEffectiveDate(defaultEffectiveDate(req.getEffectiveDate()));
        entity.setExpiryDate(req.getExpiryDate());

        overlayRuleMapper.updateById(entity);
        return toVO(entity);
    }

    // ======================== 删除 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long ruleId) {
        OverlayRuleEntity entity = overlayRuleMapper.selectById(ruleId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "管理层叠加规则不存在: " + ruleId);
        }
        checkSchemeDraft(entity.getSchemeId());
        overlayRuleMapper.deleteById(ruleId);
    }

    // ======================== 命中测试 ========================

    @Override
    public OverlayMatchTestResp testMatch(OverlayMatchTestReq req) {
        // 1. 加载所有规则
        // 查询规则：groupId 为空时查询方案下所有规则
        List<OverlayRuleEntity> allRules;
        if (req.getGroupId() != null && !req.getGroupId().isBlank()) {
            allRules = overlayRuleMapper.selectList(
                    new LambdaQueryWrapper<OverlayRuleEntity>()
                            .eq(OverlayRuleEntity::getSchemeId, req.getSchemeId())
                            .eq(OverlayRuleEntity::getGroupId, req.getGroupId()));
        } else {
            allRules = overlayRuleMapper.selectList(
                    new LambdaQueryWrapper<OverlayRuleEntity>()
                            .eq(OverlayRuleEntity::getSchemeId, req.getSchemeId()));
        }

        Map<String, Object> fieldValues = req.getFieldValues();
        if (fieldValues == null) {
            fieldValues = Map.of();
        }

        // 2. 逐条匹配
        List<OverlayRuleVO> matchedRules = new ArrayList<>();
        for (OverlayRuleEntity rule : allRules) {
            if (ConditionEvaluator.evaluate(normalizeJsonColumn(rule.getConditions()), fieldValues)) {
                matchedRules.add(toVO(rule));
            }
        }

        OverlayMatchTestResp resp = new OverlayMatchTestResp();
        resp.setMatchedRules(matchedRules);

        if (matchedRules.isEmpty()) {
            resp.setHasMatch(false);
            resp.setSelectedRule(null);
            resp.setEffectiveRatio(null);
            return resp;
        }

        // 3. 计算等效调整比例，选最高 1 条
        // 从 fieldValues 中取 EAD（用于 FIXED 类型）
        BigDecimal ead = fieldValues.containsKey("ead")
                ? new BigDecimal(fieldValues.get("ead").toString())
                : BigDecimal.ZERO;

        OverlayRuleVO bestRule = null;
        double bestRatio = Double.NEGATIVE_INFINITY;

        for (OverlayRuleVO rule : matchedRules) {
            double ratio = calcEffectiveRatio(rule, ead);
            if (ratio > bestRatio) {
                bestRatio = ratio;
                bestRule = rule;
            }
        }

        resp.setHasMatch(true);
        resp.setSelectedRule(bestRule);
        resp.setEffectiveRatio(bestRatio);
        return resp;
    }

    // ======================== 辅助方法 ========================

    private double calcEffectiveRatio(OverlayRuleVO rule, BigDecimal ead) {
        double value = rule.getAdjustmentValue().doubleValue();
        return switch (rule.getAdjustmentType()) {
            case "ADDBP" -> value / 10000.0;
            case "PERCENTAGE" -> value;
            case "FIXED" -> ead.compareTo(BigDecimal.ZERO) > 0 ? value / ead.doubleValue() : 0.0;
            default -> 0.0;
        };
    }

    private OverlayRuleVO toVO(OverlayRuleEntity entity) {
        if (entity == null) return null;
        OverlayRuleVO vo = new OverlayRuleVO();
        vo.setRuleId(entity.getRuleId());
        vo.setOverlayId(entity.getRuleId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setGroupId(entity.getGroupId());
        vo.setOverlayType(entity.getOverlayType());
        vo.setAdjustmentTarget(entity.getAdjustmentTarget());
        vo.setAdjustmentType(entity.getAdjustmentType());
        vo.setAdjustmentValue(entity.getAdjustmentValue());
        vo.setPriority(entity.getPriority());
        vo.setConditions(normalizeJsonColumn(entity.getConditions()));
        vo.setEffectiveDate(entity.getEffectiveDate());
        vo.setExpiryDate(entity.getExpiryDate());
        return vo;
    }

    private OverlayRuleEntity buildEntity(OverlayRuleCreateReq req, String conditions, LocalDate effectiveDate) {
        OverlayRuleEntity entity = new OverlayRuleEntity();
        entity.setSchemeId(req.getSchemeId());
        entity.setGroupId(req.getGroupId());
        entity.setOverlayType(req.getOverlayType());
        entity.setAdjustmentTarget(req.getAdjustmentTarget());
        entity.setAdjustmentType(req.getAdjustmentType());
        entity.setAdjustmentValue(req.getAdjustmentValue());
        entity.setPriority(req.getPriority());
        entity.setConditions(conditions);
        entity.setEffectiveDate(effectiveDate);
        entity.setExpiryDate(req.getExpiryDate());
        return entity;
    }

    private String normalizeInputConditions(String conditions) {
        return conditions == null || conditions.isBlank() ? "{}" : conditions;
    }

    private LocalDate defaultEffectiveDate(LocalDate effectiveDate) {
        return effectiveDate != null ? effectiveDate : LocalDate.now();
    }

    private String normalizeJsonColumn(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node.isTextual()) {
                return node.asText();
            }
        } catch (Exception ignored) {
            return value;
        }
        return value;
    }
}
