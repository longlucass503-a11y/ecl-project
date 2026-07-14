package com.bank.ecl.engine.overlay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.EngineType;
import com.bank.ecl.data.entity.OverlayRuleEntity;
import com.bank.ecl.data.mapper.OverlayRuleMapper;
import com.bank.ecl.engine.core.*;
import com.bank.ecl.engine.stage.StageConditionEvaluator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OverlayEngine implements EclEngine {
    private static final Logger log = LoggerFactory.getLogger(OverlayEngine.class);
    private final OverlayRuleMapper overlayRuleMapper;

    @Override
    public EngineType getType() {
        return EngineType.OVERLAY;
    }

    @Override
    public void execute(JobContext ctx) {
        String schemeId = ctx.getSchemeId();
        log.info("[6.7 Overlay] start, schemeId={}", schemeId);
        if (schemeId == null || schemeId.isBlank()) {
            log.warn("[6.7 Overlay] skipping");
            return;
        }

        Map<String, List<OverlayRuleEntity>> rulesByGroup = loadRulesByGroup(schemeId);
        log.info("[6.7 Overlay] loaded {} rule groups", rulesByGroup.size());

        List<CustomerContext> customers = ctx.getCustomers();
        if (customers == null || customers.isEmpty()) {
            log.info("[6.7 Overlay] no customers");
            return;
        }

        LocalDate calcDate = ctx.getCalcDate();

        // 预计算同批次 EAD 均值（用于 EAD均值比 条件匹配）
        double totalEadSum = 0;
        int assetCount = 0;
        for (CustomerContext c : customers) {
            if (c == null || c.getAssets() == null) continue;
            for (AssetInput a : c.getAssets()) {
                if (a == null) continue;
                totalEadSum += a.getTotalEad();
                assetCount++;
            }
        }
        double batchEadAvg = assetCount > 0 ? totalEadSum / assetCount : 0;
        log.info("[6.7 Overlay] batchEadAvg={} (count={}, sum={})", batchEadAvg, assetCount, totalEadSum);

        for (CustomerContext c : customers) {
            if (c == null || c.getAssets() == null) continue;
            for (AssetInput a : c.getAssets()) {
                if (a == null) continue;
                a.setBatchEadAvg(batchEadAvg);
                processAsset(a, rulesByGroup, calcDate);
            }
        }
        log.info("[6.7 Overlay] complete");
    }

    private void processAsset(AssetInput a, Map<String, List<OverlayRuleEntity>> rulesByGroup, LocalDate calcDate) {
        double ecl = a.getEclValue();
        String groupId = a.getGroupId();
        // 包含全局规则（空groupId）和分组特定规则
        List<OverlayRuleEntity> globalRules = rulesByGroup.getOrDefault("", Collections.emptyList());
        List<OverlayRuleEntity> groupRules = rulesByGroup.getOrDefault(groupId, Collections.emptyList());
        List<OverlayRuleEntity> rules = new ArrayList<>(globalRules);
        rules.addAll(groupRules);

        OverlayRuleEntity bestRule = null;
        double bestRatio = Double.NEGATIVE_INFINITY;
        Integer bestPriority = null;

        for (OverlayRuleEntity rule : rules) {
            // 日期过滤：有效期早于计算日，或过期日早于计算日则跳过
            if (calcDate != null) {
                if (rule.getEffectiveDate() != null && rule.getEffectiveDate().isAfter(calcDate)) continue;
                if (rule.getExpiryDate() != null && rule.getExpiryDate().isBefore(calcDate)) continue;
            }

            if (StageConditionEvaluator.evaluate(rule.getConditions(), a, null)) {
                Double ratio = computeEquivalentRatio(rule, a);
                // FIXED 类型且 EAD <= 0 时不选此规则
                if (ratio == null) continue;

                int thisPriority = rule.getPriority() != null ? rule.getPriority() : Integer.MAX_VALUE;
                int currBestPriority = bestPriority != null ? bestPriority : Integer.MAX_VALUE;

                if (ratio > bestRatio || (ratio == bestRatio && thisPriority < currBestPriority)) {
                    bestRatio = ratio;
                    bestPriority = rule.getPriority();
                    bestRule = rule;
                }
            }
        }

        double overlay = 0.0;
        if (bestRule != null) {
            overlay = computeOverlay(bestRule, a);
            a.setSelectedOverlayId(bestRule.getRuleId());
        }
        a.setOverlayAmount(overlay);
        a.setEclFinal(ecl + overlay);
    }

    private Double computeEquivalentRatio(OverlayRuleEntity rule, AssetInput a) {
        double val = rule.getAdjustmentValue() != null ? rule.getAdjustmentValue().doubleValue() : 0.0;
        double ead = a.getTotalEad();
        return switch (rule.getAdjustmentType()) {
            case "ADDBP" -> val / 10000.0;
            case "PERCENTAGE" -> val;
            case "FIXED" -> ead > 0 ? val / ead : null;
            default -> 0.0;
        };
    }

    private double computeOverlay(OverlayRuleEntity rule, AssetInput a) {
        double val = rule.getAdjustmentValue() != null ? rule.getAdjustmentValue().doubleValue() : 0.0;
        String type = rule.getAdjustmentType();
        String target = rule.getOverlayType() != null ? rule.getOverlayType() : "ECL";

        double ead = a.getTotalEad();
        double lgd = a.getLgdValue();
        double ecl = a.getEclValue();

        if ("FIXED".equals(type)) {
            return val;
        }

        if ("PERCENTAGE".equals(type)) {
            return ecl * val;
        }

        if ("ADDBP".equals(type)) {
            double bps = val / 10000.0;
            return switch (target) {
                case "PD"  -> ead * bps * lgd;
                case "LGD" -> ead * getEffectivePd(a) * bps;
                case "EAD" -> ead * bps * getEffectivePd(a) * lgd;
                default    -> ead * bps;
            };
        }

        return 0.0;
    }

    private double getEffectivePd(AssetInput a) {
        double ead = a.getTotalEad();
        double lgd = a.getLgdValue();
        if (ead == 0 || lgd == 0) return 0;
        return a.getEclValue() / (lgd * ead);
    }


    private Map<String, List<OverlayRuleEntity>> loadRulesByGroup(String schemeId) {
        List<OverlayRuleEntity> rules = overlayRuleMapper.selectList(
                new LambdaQueryWrapper<OverlayRuleEntity>()
                        .eq(OverlayRuleEntity::getSchemeId, schemeId)
                        .orderByAsc(OverlayRuleEntity::getPriority));
        if (rules == null) return Collections.emptyMap();
        // null groupId 用空字符串替代，避免 groupingBy NPE
        return rules.stream().collect(Collectors.groupingBy(
                r -> r.getGroupId() == null ? "" : r.getGroupId(),
                LinkedHashMap::new,
                Collectors.toList()));
    }
}
