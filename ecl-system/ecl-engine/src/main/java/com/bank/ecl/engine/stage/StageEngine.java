package com.bank.ecl.engine.stage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.EngineType;
import com.bank.ecl.data.entity.CrrRatingDropRuleEntity;
import com.bank.ecl.data.entity.StageRuleEntity;
import com.bank.ecl.data.mapper.CrrRatingDropRuleMapper;
import com.bank.ecl.data.mapper.StageRuleMapper;
import com.bank.ecl.engine.core.AssetInput;
import com.bank.ecl.engine.core.CustomerContext;
import com.bank.ecl.engine.core.EclEngine;
import com.bank.ecl.engine.core.JobContext;
import com.bank.ecl.engine.core.Stage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 6.2 阶段划分引擎 —— FORWARD（向前判定）+ ROLLBACK（回跳校验）两步法判定借据的 IFRS 9 三阶段。
 *
 * <p>规则按 group_id 过滤后一次性加载到内存，在内存中逐借据匹配。
 * 判定失败以 STAGE_1 兜底 + exceptionFlag=true 标记，不抛异常。
 */
@Component
@RequiredArgsConstructor
public class StageEngine implements EclEngine {

    private static final Logger log = LoggerFactory.getLogger(StageEngine.class);

    private final StageRuleMapper stageRuleMapper;
    private final CrrRatingDropRuleMapper crrDropRuleMapper;

    @Override
    public EngineType getType() {
        return EngineType.STAGE;
    }

    @Override
    public void execute(JobContext ctx) {
        String schemeId = ctx.getSchemeId();
        if (schemeId == null || schemeId.isBlank()) {
            log.warn("[6.2 Stage] schemeId is null or blank, skipping engine");
            return;
        }
        log.info("[6.2 Stage] start, schemeId={}", schemeId);

        // 1. 一次性加载全部规则（所有 group）
        List<StageRuleEntity> allRules = loadRules(schemeId);
        Map<String, List<StageRuleEntity>> forwardByGroup = partitionByGroup(
                allRules.stream()
                        .filter(r -> "FORWARD".equals(r.getRuleType()) || "CRR_DROP".equals(r.getRuleType()))
                        .collect(Collectors.toList()));
        Map<String, List<StageRuleEntity>> rollbackByGroup = partitionByGroup(
                allRules.stream()
                        .filter(r -> "ROLLBACK".equals(r.getRuleType()))
                        .collect(Collectors.toList()));

        // 2. 一次性加载 CRR 下降阈值（所有 group）
        List<CrrRatingDropRuleEntity> crrRules = loadCrrRules(schemeId);
        Map<String, Map<String, Integer>> crrDropByGroup = crrRules.stream()
                .collect(Collectors.groupingBy(
                        CrrRatingDropRuleEntity::getGroupId,
                        Collectors.toMap(
                                r -> ratingDropKey(
                                        r.getGroupId(),
                                        r.getRatingAgency(),
                                        r.getCurrentRating()),
                                CrrRatingDropRuleEntity::getDropThreshold,
                                (a, b) -> a)));

        log.info("[6.2 Stage] loaded {} forward groups, {} rollback groups, {} crr groups",
                forwardByGroup.size(), rollbackByGroup.size(), crrDropByGroup.size());

        // 3. 逐客户逐借据判定
        List<CustomerContext> customers = ctx.getCustomers();
        if (customers == null || customers.isEmpty()) {
            log.info("[6.2 Stage] no customers to process");
            return;
        }
        for (CustomerContext customer : customers) {
            if (customer == null || customer.getAssets() == null) {
                continue;
            }
            for (AssetInput asset : customer.getAssets()) {
                if (asset == null) {
                    continue;
                }
                String groupId = asset.getGroupId() != null ? asset.getGroupId() : "GRP_DEFAULT";
                StageResult result = determineStage(
                        asset,
                        forwardByGroup.getOrDefault(groupId, Collections.emptyList()),
                        rollbackByGroup.getOrDefault(groupId, Collections.emptyList()),
                        crrDropByGroup.getOrDefault(groupId, Collections.emptyMap()));
                asset.setStageResult(result);
            }
        }

        log.info("[6.2 Stage] complete");
    }

    // ======================== 两步判定 ========================

    /**
     * Step 1: FORWARD 向前判定 → 确定 targetStage
     * Step 2: ROLLBACK 回跳校验 → 若阶段改善，验证是否允许回跳
     */
    private StageResult determineStage(AssetInput asset,
                                       List<StageRuleEntity> forwardRules,
                                       List<StageRuleEntity> rollbackRules,
                                       Map<String, Integer> crrDropMap) {

        Stage lastStage = asset.getLastStage() != null ? asset.getLastStage() : Stage.STAGE_1;

        // ═══ Step 1: FORWARD 向前判定 ═══
        Stage targetStage = Stage.STAGE_1;
        String triggerType = null;
        boolean exceptionFlag = true;

        for (StageRuleEntity rule : forwardRules) {
            boolean matched = StageConditionEvaluator.evaluate(rule.getConditions(), asset, crrDropMap);
            log.debug("[6.2 Stage] asset={} ruleId={} stageTo={} conditions={} → matched={}",
                    asset.getAssetId(), rule.getRuleId(), rule.getStageTo(),
                    rule.getConditions(), matched);
            if (matched) {
                String stageTo = rule.getStageTo();
                if (stageTo == null) {
                    continue;
                }
                try {
                    targetStage = Stage.valueOf(stageTo);
                } catch (IllegalArgumentException e) {
                    log.error("[6.2 Stage] invalid stageTo '{}' in rule id={}", stageTo, rule.getRuleId());
                    continue;
                }
                triggerType = extractTriggerType(rule.getConditions());
                exceptionFlag = false;
                break;
            }
        }

        // ═══ Step 2: ROLLBACK 回跳校验 ═══
        // 检查 FORWARD 目标阶段是否有对应的 ROLLBACK 规则：
        //   - 无规则 → 路径不受限，允许回跳
        //   - 有规则，条件+观察期都满足 → 允许回跳
        //   - 有规则，条件或观察期不满足 → 退到 lastStage-1

        if (targetStage.ordinal() < lastStage.ordinal()) {
            // 查找从 lastStage → targetStage 的 ROLLBACK 规则
            StageRuleEntity matchedRule = null;
            for (StageRuleEntity rule : rollbackRules) {
                if (lastStage.name().equals(rule.getStageFrom())
                        && targetStage.name().equals(rule.getStageTo())) {
                    matchedRule = rule;
                    break;
                }
            }

            if (matchedRule != null) {
                // 有规则，检查条件 + 观察期
                boolean conditionMet = matchedRule.getConditions() != null
                        ? StageConditionEvaluator.evaluate(matchedRule.getConditions(), asset, crrDropMap)
                        : true;
                boolean observationMet = matchedRule.getObservationDays() == null
                        || asset.getNormalConsecutiveDays() == null
                        || asset.getNormalConsecutiveDays() >= matchedRule.getObservationDays();

                if (conditionMet && observationMet) {
                    // 条件和观察期都满足 → 允许回跳
                    exceptionFlag = false;
                } else {
                    // 不满足 → 回退到 lastStage-1（改善路径的中间级）
                    int fallbackOrdinal = Math.max(0, lastStage.ordinal() - 1);
                    Stage fallbackStage = Stage.values()[fallbackOrdinal];
                    log.debug("[6.2 Stage] asset {} rollback denied: {} -> {} failed, fallback to {}",
                            asset.getAssetId(), lastStage, targetStage, fallbackStage);
                    targetStage = fallbackStage;
                    exceptionFlag = false;
                }
            } else {
                // 无规则 → 路径不受限，允许回跳
                exceptionFlag = false;
            }
        }

        return new StageResult(targetStage, triggerType, exceptionFlag);
    }

    // ======================== 数据加载 ========================

    private List<StageRuleEntity> loadRules(String schemeId) {
        List<StageRuleEntity> rules = stageRuleMapper.selectList(
                new LambdaQueryWrapper<StageRuleEntity>()
                        .eq(StageRuleEntity::getSchemeId, schemeId)
                        .orderByAsc(StageRuleEntity::getPriority));
        return rules != null ? rules : Collections.emptyList();
    }

    private List<CrrRatingDropRuleEntity> loadCrrRules(String schemeId) {
        List<CrrRatingDropRuleEntity> rules = crrDropRuleMapper.selectList(
                new LambdaQueryWrapper<CrrRatingDropRuleEntity>()
                        .eq(CrrRatingDropRuleEntity::getSchemeId, schemeId));
        return rules != null ? rules : Collections.emptyList();
    }

    private Map<String, List<StageRuleEntity>> partitionByGroup(List<StageRuleEntity> rules) {
        return rules.stream().collect(Collectors.groupingBy(StageRuleEntity::getGroupId));
    }

    // ======================== 辅助方法 ========================

    /**
     * 从 conditions JSON 中提取触发规则类型的简短描述。
     * 目前简单返回第一个 key 名（如 "overdue_days" / "five_category"）。
     */
    private String extractTriggerType(String conditionsJson) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return "default";
        }
        try {
            // 取 JSON 的第一个顶层 key 作为 trigger 类型
            int colonIdx = conditionsJson.indexOf(':');
            if (colonIdx > 0) {
                String firstKey = conditionsJson.substring(0, colonIdx)
                        .replaceAll("[\"\\{\\}\\s]", "");
                return firstKey;
            }
        } catch (Exception e) {
            log.debug("[6.2 Stage] failed to extract trigger type from: {}", conditionsJson);
        }
        return "rule_match";
    }

    /**
     * 构建包含评级来源/评级机构的评级下降 key。
     */
    private String ratingDropKey(String groupId, String ratingAgency, String currentRating) {
        return String.join("|",
                nvl(groupId),
                nvl(ratingAgency, "INTERNAL_CRR"),
                nvl(currentRating));
    }

    private static String nvl(String value) {
        return value != null ? value : "";
    }

    private static String nvl(String value, String defaultVal) {
        return value != null ? value : defaultVal;
    }
}
