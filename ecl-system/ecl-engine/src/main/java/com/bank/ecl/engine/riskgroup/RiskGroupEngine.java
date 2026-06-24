package com.bank.ecl.engine.riskgroup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.EngineType;
import com.bank.ecl.data.entity.RiskGroupDetailEntity;
import com.bank.ecl.data.entity.RiskGroupEntity;
import com.bank.ecl.data.mapper.RiskGroupDetailMapper;
import com.bank.ecl.data.mapper.RiskGroupMapper;
import com.bank.ecl.engine.core.AssetInput;
import com.bank.ecl.engine.core.CustomerContext;
import com.bank.ecl.engine.core.EclEngine;
import com.bank.ecl.engine.core.JobContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 6.1 风险分组引擎 —— 根据借据的 4 维属性将每笔借据匹配到对应的风险分组。
 *
 * <p>规则一次性加载到内存，按 priority ASC 排序后逐借据在内存中匹配。
 * 匹配失败以 GRP_DEFAULT 兜底，不抛异常。
 */
@Component
@RequiredArgsConstructor
public class RiskGroupEngine implements EclEngine {

    private static final Logger log = LoggerFactory.getLogger(RiskGroupEngine.class);
    private static final String DEFAULT_GROUP_ID = "GRP_DEFAULT";

    private final RiskGroupDetailMapper detailMapper;
    private final RiskGroupMapper groupMapper;

    @Override
    public EngineType getType() {
        return EngineType.RISK_GROUP;
    }

    @Override
    public void execute(JobContext ctx) {
        String schemeId = ctx.getSchemeId();
        if (schemeId == null || schemeId.isBlank()) {
            log.warn("[6.1 RiskGroup] schemeId is null or blank, skipping engine");
            return;
        }
        log.info("[6.1 RiskGroup] start, schemeId={}", schemeId);

        // 1. 一次性加载规则和分组主数据
        List<RiskGroupDetailEntity> rules = loadRules(schemeId);
        Map<String, RiskGroupEntity> groupMap = loadGroupMap(schemeId);
        log.info("[6.1 RiskGroup] loaded {} rules, {} groups", rules.size(), groupMap.size());

        // 2. 逐客户逐借据匹配
        List<CustomerContext> customers = ctx.getCustomers();
        if (customers == null || customers.isEmpty()) {
            log.info("[6.1 RiskGroup] no customers to process");
            return;
        }
        for (CustomerContext customer : customers) {
            if (customer == null) {
                continue;
            }
            if (customer.getAssets() == null) {
                continue;
            }
            for (AssetInput asset : customer.getAssets()) {
                if (asset == null) {
                    continue;
                }
                String groupId = matchGroup(asset, rules);
                asset.setGroupId(groupId);

                if (DEFAULT_GROUP_ID.equals(groupId)) {
                    asset.setGroupException("Y");
                    log.debug("[6.1 RiskGroup] asset {} -> default group", asset.getAssetId());
                }

                // 补充 groupName
                RiskGroupEntity group = groupMap.get(groupId);
                if (group != null) {
                    asset.setGroupName(group.getGroupName());
                }
            }
        }

        log.info("[6.1 RiskGroup] complete");
    }

    // ======================== 数据加载 ========================

    private List<RiskGroupDetailEntity> loadRules(String schemeId) {
        List<RiskGroupDetailEntity> rules = detailMapper.selectList(
                new LambdaQueryWrapper<RiskGroupDetailEntity>()
                        .eq(RiskGroupDetailEntity::getSchemeId, schemeId)
                        .orderByAsc(RiskGroupDetailEntity::getPriority));
        return rules != null ? rules : Collections.emptyList();
    }

    private Map<String, RiskGroupEntity> loadGroupMap(String schemeId) {
        List<RiskGroupEntity> groups = groupMapper.selectList(
                new LambdaQueryWrapper<RiskGroupEntity>()
                        .eq(RiskGroupEntity::getSchemeId, schemeId));
        if (groups == null) {
            return Collections.emptyMap();
        }
        return groups.stream().collect(Collectors.toMap(
                RiskGroupEntity::getGroupId, Function.identity(),
                (existing, replacement) -> {
                    log.warn("[6.1 RiskGroup] duplicate groupId '{}' in scheme, using first entry", existing.getGroupId());
                    return existing;
                }));
    }

    // ======================== 匹配逻辑 ========================

    /**
     * 逐条规则匹配：4 维 AND，NULL 或空字符串 = 通配。
     * 规则按 priority ASC 已排序，命中第一条即返回。
     */
    private String matchGroup(AssetInput asset, List<RiskGroupDetailEntity> rules) {
        for (RiskGroupDetailEntity rule : rules) {
            if (matchDimension(asset.getBusinessLine(), rule.getBusinessLine())
                    && matchDimension(asset.getProductType(), rule.getProductType())
                    && matchDimension(asset.getIndustryCode(), rule.getIndustryCode())
                    && matchDimension(asset.getCollateralType(), rule.getCollateralType())) {
                return rule.getGroupId();
            }
        }
        return DEFAULT_GROUP_ID;
    }

    /**
     * 单维度匹配：规则值 NULL 或空 → 通配（不限制此维度）；
     * 资产值 NULL 或空 → 通配（不限制此维度）；
     * 否则精确匹配。
     */
    private boolean matchDimension(String assetValue, String ruleValue) {
        if (ruleValue == null || ruleValue.isBlank()) {
            return true;
        }
        if (assetValue == null || assetValue.isBlank()) {
            return true;
        }
        return ruleValue.equals(assetValue);
    }
}
