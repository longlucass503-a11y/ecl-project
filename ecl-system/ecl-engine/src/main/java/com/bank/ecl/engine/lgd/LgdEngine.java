package com.bank.ecl.engine.lgd;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.EngineType;
import com.bank.ecl.data.entity.LgdCollateralDiscountEntity;
import com.bank.ecl.data.entity.LgdCurveEntity;
import com.bank.ecl.data.entity.LgdDepreciationEntity;
import com.bank.ecl.data.mapper.LgdCollateralDiscountMapper;
import com.bank.ecl.data.mapper.LgdCurveMapper;
import com.bank.ecl.data.mapper.LgdDepreciationMapper;
import com.bank.ecl.engine.core.*;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class LgdEngine implements EclEngine {
    private static final Logger log = LoggerFactory.getLogger(LgdEngine.class);
    private final LgdCurveMapper lgdCurveMapper;
    private final LgdCollateralDiscountMapper discountMapper;
    private final LgdDepreciationMapper depreciationMapper;

    public LgdEngine(LgdCurveMapper lgdCurveMapper,
                     LgdCollateralDiscountMapper discountMapper,
                     LgdDepreciationMapper depreciationMapper) {
        this.lgdCurveMapper = lgdCurveMapper;
        this.discountMapper = discountMapper;
        this.depreciationMapper = depreciationMapper;
    }

    @Override public EngineType getType() { return EngineType.LGD; }

    @Override
    public void execute(JobContext ctx) {
        String schemeId = ctx.getSchemeId();
        log.info("[6.5 LGD] start, schemeId={}", schemeId);
        if (schemeId == null || schemeId.isBlank()) { log.warn("[6.5 LGD] schemeId null, skipping"); return; }

        Map<String, Double> curveCache = buildCurveCache(schemeId);
        double defaultLgd = ctx.getDefaultLgd();
        log.info("[6.5 LGD] loaded {} curve entries, defaultLgd={}", curveCache.size(), defaultLgd);

        // Load discount and depreciation data
        Map<String, Double> discountCache = buildDiscountCache(schemeId);
        Map<String, Double> depreciationCache = buildDepreciationCache(schemeId);
        log.info("[6.5 LGD] loaded {} discount entries, {} depreciation entries",
                discountCache.size(), depreciationCache.size());

        List<CustomerContext> customers = ctx.getCustomers();
        if (customers == null || customers.isEmpty()) { log.info("[6.5 LGD] no customers"); return; }

        // Collect all assets by pool for pool-level processing
        Map<String, List<AssetInput>> assetsByPool = new HashMap<>();
        List<AssetInput> nonPoolAssets = new ArrayList<>();

        for (CustomerContext c : customers) {
            if (c == null || c.getAssets() == null) continue;
            for (AssetInput a : c.getAssets()) {
                if (a == null) continue;
                if (a.getCollateralPoolId() != null && !a.getCollateralPoolId().isBlank()) {
                    assetsByPool.computeIfAbsent(a.getCollateralPoolId(), k -> new ArrayList<>()).add(a);
                } else {
                    nonPoolAssets.add(a);
                }
            }
        }

        // Process pool-level assets
        Map<String, List<CollateralInput>> collateralsByPool = ctx.getCollateralsByPool();
        if (collateralsByPool == null) collateralsByPool = Collections.emptyMap();

        for (Map.Entry<String, List<AssetInput>> entry : assetsByPool.entrySet()) {
            String poolId = entry.getKey();
            List<AssetInput> poolAssets = entry.getValue();
            processPool(poolId, poolAssets, collateralsByPool.get(poolId),
                    discountCache, depreciationCache, curveCache, defaultLgd, ctx.getLgdFloor());
        }

        // Process non-pool assets with original per-asset LGD lookup
        for (AssetInput a : nonPoolAssets) {
            processAsset(a, curveCache, defaultLgd);
        }

        log.info("[6.5 LGD] complete");
    }

    private void processPool(String poolId, List<AssetInput> poolAssets,
                             List<CollateralInput> collaterals,
                             Map<String, Double> discountCache,
                             Map<String, Double> depreciationCache,
                             Map<String, Double> curveCache,
                             double defaultLgd, double lgdFloor) {
        // Sum total EAD for the pool
        double eadTotal = poolAssets.stream()
                .mapToDouble(a -> a.getTotalEad())
                .sum();

        // Calculate each collateral's recognized net value and corresponding LGD
        AssetInput firstAsset = poolAssets.get(0);
        String groupId = firstAsset.getGroupId();

        List<double[]> collValues = new ArrayList<>(); // [netValue, lgd]
        if (collaterals != null) {
            for (CollateralInput coll : collaterals) {
                if (coll == null || coll.getAppraisalValue() == null) continue;
                double appVal = coll.getAppraisalValue().doubleValue();

                // Discount key: 押品大类(collateralCategory) + 押品类型(collateralType)，
                // 与 buildDiscountCache() 建缓存时的 key 结构保持一致(第248行)
                String collType = coll.getCollateralType();
                String discountKey = nullSafeKey(coll.getCollateralCategory(), collType);
                double discountRate = discountCache.getOrDefault(discountKey, 0.0); // 认可率,直接乘

                // Find depreciation rate by (collateralCategory, yearOffset=0)
                // 物理折旧本质是押品大类(房产/设备/车辆等)的属性，不是担保方式(押品类型)的属性，
                // 用押品大类去查才对得上"基础信息-押品大类"这套字典
                String depKey = nullSafeKey(coll.getCollateralCategory(), 0);
                double depreciationRate = depreciationCache.getOrDefault(depKey, 0.0);

                // 公式: 评估价值 × (1 + 折旧率) × 折价认可率
                // 折旧率是负数(减值),1+折旧率<1; 折扣率是认可率,直接乘
                double netValue = appVal * (1 + depreciationRate) * discountRate;

                // Look up LGD for this collateral type within the pool's group
                double collLgd = lookupLgdByType(groupId, collType, coll.getCollateralCategory(),
                        firstAsset.getProductType(), curveCache, defaultLgd);

                collValues.add(new double[]{netValue, collLgd});
            }
        }

        // Sort collaterals by LGD ascending (best collateral = lowest LGD covers first)
        collValues.sort((a, b) -> Double.compare(a[1], b[1]));

        // Allocate: each collateral covers part of EAD at its own LGD
        double remainingEad = eadTotal;
        double weightedLgdSum = 0.0;
        double totalCovered = 0.0;
        StringBuilder allocationDetail = new StringBuilder();

        for (int i = 0; i < collValues.size(); i++) {
            double[] cv = collValues.get(i);
            double netValue = cv[0];
            double collLgd = cv[1];
            double coverAmount = Math.min(netValue, remainingEad);
            if (coverAmount <= 0) continue;
            weightedLgdSum += coverAmount * collLgd;
            totalCovered += coverAmount;
            remainingEad -= coverAmount;
            if (i > 0) allocationDetail.append(", ");
            allocationDetail.append(String.format("tranche%d:covered=%.0f,lgd=%.4f", i + 1, coverAmount, collLgd));
        }

        // Uncovered portion uses weighted average LGD across all pool assets (by EAD)
        // 未覆盖部分 = 押品净值覆盖不到的EAD，名义上没有任何押品价值支撑，按"信用/无担保(CREDIT)"定价，
        // 不用贷款自己声明的担保方式(比如MORTGAGE)——那是这笔贷款整体的担保方式，不代表这一截未被覆盖的EAD还有抵押物撑着。
        double weightedLgdUncovered = 0.0;
        for (AssetInput a : poolAssets) {
            double assetLgd = lookupLgdUncoveredForPool(a, curveCache, defaultLgd);
            weightedLgdUncovered += a.getTotalEad() * assetLgd;
        }
        double lgdUncovered = eadTotal > 0 ? weightedLgdUncovered / eadTotal : defaultLgd;
        double uncovered = Math.max(0, remainingEad);
        weightedLgdSum += uncovered * lgdUncovered;

        double lgdPool;
        if (eadTotal > 0) {
            lgdPool = weightedLgdSum / eadTotal;
        } else {
            lgdPool = lgdUncovered;
        }
        // 2026-07-17改动：lgdFloor此前只是签名里收了个参数、方法体里没实际用过，
        // 方案配置的LGD楼层值(如10%)形同虚设。现在真正生效：加权结果不能低于楼层值。
        lgdPool = Math.max(lgdPool, lgdFloor);

        // Build JSON detail with allocation info
        String lgdDetails = String.format(
                "{\"poolId\":\"%s\",\"eadTotal\":%.2f,\"totalCovered\":%.2f,\"uncovered\":%.2f,\"lgdUncovered\":%.4f,\"lgdPool\":%.4f,\"allocation\":\"%s\"}",
                poolId, eadTotal, totalCovered, uncovered, lgdUncovered, lgdPool, allocationDetail.toString());

        // Set LGD for every asset in the pool
        for (AssetInput a : poolAssets) {
            a.setLgdValue(lgdPool);
            a.setLgdDetails(lgdDetails);
        }

        log.info("[6.5 LGD] pool={} eadTotal={} totalCovered={} uncovered={} lgdUncovered={} lgdPool={}",
                poolId, eadTotal, totalCovered, uncovered, lgdUncovered, lgdPool);
    }


    private void processAsset(AssetInput a, Map<String, Double> cache, double defaultLgd) {
        double lgd = lookupLgdForGroup(a, cache, defaultLgd);
        a.setLgdValue(lgd);
    }

    /**
     * 押品层级LGD查询（押品覆盖部分）：风险分组 + 押品自身担保类型 + 押品大类 + 产品类型，4维匹配，逐级降级。
     * 押品大类(collateralCategory)只有押品自己有，资产/贷款没有这个概念，所以只在这个方法里用，
     * 不影响 lookupLgdForGroup（资产层级/未覆盖部分）的3维匹配。
     */
    private double lookupLgdByType(String groupId, String collType, String category, String prodType,
                                    Map<String, Double> cache, double defaultLgd) {
        String c = category != null ? category : "";
        String p = prodType != null ? prodType : "";

        // 1. 精确匹配: groupId|collateralType|collateralCategory|productType
        Double lgd = cache.get(groupId + "|" + collType + "|" + c + "|" + p);

        // 2. 降级: 保留押品大类，忽略产品类型
        if (lgd == null) {
            lgd = cache.get(groupId + "|" + collType + "|" + c + "|");
        }

        // 3. 降级: 忽略押品大类，退回原来只按"担保类型+产品类型"配的曲线行（押品大类留空的那些行）
        if (lgd == null) {
            lgd = cache.get(groupId + "|" + collType + "||" + p);
        }

        // 4. 降级: 押品大类、产品类型都忽略
        if (lgd == null) {
            lgd = cache.get(groupId + "|" + collType + "||");
        }

        // 5. 方案兜底
        if (lgd == null) {
            lgd = defaultLgd;
        }
        return lgd;
    }

    /**
     * 2026-07-17改动回退：不再固定按CREDIT查，改回按贷款自己声明的担保类型
     * (a.getCollateralType())匹配——用户确认无押品池场景下应尊重贷款自己声明的担保方式，
     * 不强制视同信用/无担保。CREDIT只在NONE路径降级里作为口径兜底，不再是首选。
     */
    private double lookupLgdForGroup(AssetInput a, Map<String, Double> cache, double defaultLgd) {
        String groupId = a.getGroupId();
        String collType = a.getCollateralType();
        String prodType = a.getProductType();
        String p = prodType != null ? prodType : "";

        // 资产/贷款没有"押品大类"概念，固定按空白类别匹配曲线里"押品大类"留空的那些行，3维不变

        // 1. exact match: groupId|贷款自己声明的担保类型|productType
        Double lgd = cache.get(groupId + "|" + collType + "||" + p);

        // 2. NONE path
        if (lgd == null) {
            lgd = cache.get(groupId + "|NONE||" + p);
        }

        // 3. Fallback: 忽略 productType
        if (lgd == null && collType != null) {
            lgd = cache.get(groupId + "|" + collType + "||");
        }

        // 4. scheme default
        if (lgd == null) {
            lgd = defaultLgd;
            a.setLgdException("WARN");
        }

        return lgd;
    }

    /**
     * 押品池"未覆盖部分"专用：不用贷款自己声明的担保方式，固定按 CREDIT(信用/无担保) 查曲线，
     * 因为这部分EAD已经没有押品净值撑着，名义上就是无担保状态，跟贷款整体的担保方式(如MORTGAGE)是两回事。
     * 其余逻辑(3维、降级顺序)跟 lookupLgdForGroup 完全一致，只是担保类型固定成 CREDIT。
     */
    private double lookupLgdUncoveredForPool(AssetInput a, Map<String, Double> cache, double defaultLgd) {
        String groupId = a.getGroupId();
        String prodType = a.getProductType();
        String p = prodType != null ? prodType : "";

        // 1. exact match: groupId|CREDIT||productType
        Double lgd = cache.get(groupId + "|CREDIT||" + p);

        // 2. NONE path
        if (lgd == null) {
            lgd = cache.get(groupId + "|NONE||" + p);
        }

        // 3. Fallback: 忽略 productType
        if (lgd == null) {
            lgd = cache.get(groupId + "|CREDIT||");
        }

        // 4. scheme default
        if (lgd == null) {
            lgd = defaultLgd;
            a.setLgdException("WARN");
        }

        return lgd;
    }

    private Map<String, Double> buildCurveCache(String schemeId) {
        List<LgdCurveEntity> curves = lgdCurveMapper.selectList(
                new LambdaQueryWrapper<LgdCurveEntity>()
                        .eq(LgdCurveEntity::getSchemeId, schemeId));
        if (curves == null) return Collections.emptyMap();
        return curves.stream().collect(Collectors.toMap(
                c -> c.getGroupId() + "|" + c.getCollateralType() + "|"
                        + (c.getCollateralCategory() != null ? c.getCollateralCategory() : "") + "|"
                        + (c.getProductType() != null ? c.getProductType() : ""),
                c -> c.getLgdBaseValue() != null ? c.getLgdBaseValue().doubleValue() : 0.0,
                (a, b) -> a));
    }

    private Map<String, Double> buildDiscountCache(String schemeId) {
        List<LgdCollateralDiscountEntity> list = discountMapper.selectList(
                new LambdaQueryWrapper<LgdCollateralDiscountEntity>()
                        .eq(LgdCollateralDiscountEntity::getSchemeId, schemeId));
        if (list == null) return Collections.emptyMap();
        return list.stream().collect(Collectors.toMap(
                d -> nullSafeKey(d.getCollateralCategory(), d.getCollateralType()),
                d -> d.getDiscountRate() != null ? d.getDiscountRate().doubleValue() : 0.0,
                (a, b) -> a));
    }

    private Map<String, Double> buildDepreciationCache(String schemeId) {
        List<LgdDepreciationEntity> list = depreciationMapper.selectList(
                new LambdaQueryWrapper<LgdDepreciationEntity>()
                        .eq(LgdDepreciationEntity::getSchemeId, schemeId));
        if (list == null) return Collections.emptyMap();
        return list.stream().collect(Collectors.toMap(
                d -> nullSafeKey(d.getCollateralType(), d.getYearOffset()),
                d -> d.getDepreciationRate() != null ? d.getDepreciationRate().doubleValue() : 0.0,
                (a, b) -> a));
    }

    private static String nullSafeKey(String a, String b) {
        return (a != null ? a : "") + "|" + (b != null ? b : "");
    }

    private static String nullSafeKey(String a, Integer b) {
        return (a != null ? a : "") + "|" + (b != null ? b : 0);
    }
}
