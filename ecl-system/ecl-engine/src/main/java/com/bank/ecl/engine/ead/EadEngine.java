package com.bank.ecl.engine.ead;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.EngineType;
import com.bank.ecl.data.entity.CcfCurveEntity;
import com.bank.ecl.data.mapper.CcfCurveMapper;
import com.bank.ecl.engine.core.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EadEngine implements EclEngine {
    private static final Logger log = LoggerFactory.getLogger(EadEngine.class);
    private final CcfCurveMapper ccfCurveMapper;

    @Override public EngineType getType() { return EngineType.EAD; }

    @Override
    public void execute(JobContext ctx) {
        String schemeId = ctx.getSchemeId();
        log.info("[6.4 EAD] start, schemeId={}", schemeId);
        if (schemeId == null || schemeId.isBlank()) {
            log.warn("[6.4 EAD] schemeId null/blank, skipping"); return;
        }

        List<CcfCurveEntity> curves = buildCcfCache(schemeId);
        double defaultCcf = ctx.getDefaultCcf();
        double discountRate = ctx.getDiscountRate();
        log.info("[6.4 EAD] loaded {} CCF entries, defaultCcf={}", curves.size(), defaultCcf);

        List<CustomerContext> customers = ctx.getCustomers();
        if (customers == null || customers.isEmpty()) { log.info("[6.4 EAD] no customers"); return; }

        // Pass 1: On-balance EAD for all assets
        List<AssetInput> allAssets = new ArrayList<>();
        for (CustomerContext customer : customers) {
            if (customer == null || customer.getAssets() == null) continue;
            for (AssetInput asset : customer.getAssets()) {
                if (asset == null) continue;
                processOnBsEad(asset, discountRate);
                allAssets.add(asset);
            }
        }

        // Pass 2: Off-balance EAD
        processOffBsEad(ctx, allAssets, curves, defaultCcf);

        log.info("[6.4 EAD] complete");
    }

    // ========== On-balance EAD ==========

    private void processOnBsEad(AssetInput asset, double discountRate) {
        if (isOffBalance(asset)) {
            asset.setOnBsEad(0.0);
            asset.setEadBreakdown("{\"type\":\"OFF_BS\"}");
            return;
        }

        List<RepaymentScheduleInput> schedules = asset.getRepaymentSchedules();
        if (schedules != null && !schedules.isEmpty() && asset.getCalcDate() != null) {
            double sum = 0.0;
            int futurePeriods = 0;
            for (RepaymentScheduleInput s : schedules) {
                if (s == null || s.getDueDate() == null) continue;
                if (s.getDueDate().isAfter(asset.getCalcDate())) {
                    double principal = toDouble(s.getDuePrincipal());
                    double interest = toDouble(s.getDueInterest());
                    double amount = principal + interest;
                    double years = calcYearsAct365(asset.getCalcDate(), s.getDueDate());
                    double discounted = amount / Math.pow(1 + discountRate, years);
                    sum += discounted;
                    futurePeriods++;
                }
            }
            asset.setOnBsEad(sum);
            asset.setEadBreakdown("{\"futurePeriods\":" + futurePeriods + "}");
        } else {
            double onBsEad = toDouble(asset.getOutstandingBalance()) + toDouble(asset.getAccruedInterest());
            asset.setOnBsEad(onBsEad);
        }
    }

    // ========== Off-balance EAD ==========

    private void processOffBsEad(JobContext ctx, List<AssetInput> allAssets,
                                  List<CcfCurveEntity> curves, double defaultCcf) {
        // Build facility lookup map
        Map<String, FacilityInput> facilityMap = new HashMap<>();
        if (ctx.getFacilities() != null) {
            for (FacilityInput f : ctx.getFacilities()) {
                if (f != null && f.getFacilityCd() != null) {
                    facilityMap.put(f.getFacilityCd(), f);
                }
            }
        }

        // Track assets processed by facility allocation
        Set<AssetInput> facilityProcessed = new HashSet<>();

        // Group assets by facilityCd
        Map<String, List<AssetInput>> byFacility = allAssets.stream()
                .filter(a -> a.getFacilityCd() != null && !a.getFacilityCd().isBlank())
                .collect(Collectors.groupingBy(AssetInput::getFacilityCd));

        for (Map.Entry<String, List<AssetInput>> entry : byFacility.entrySet()) {
            String facilityCd = entry.getKey();
            List<AssetInput> groupAssets = entry.getValue();

            FacilityInput facility = facilityMap.get(facilityCd);
            if (facility == null) continue;

            // Calculate undrawn amount
            BigDecimal undrawnAmtCny = facility.getUndrawnAmtCny();
            double undrawn;
            if (undrawnAmtCny != null) {
                undrawn = Math.max(0.0, undrawnAmtCny.doubleValue());
            } else {
                undrawn = Math.max(0.0, toDouble(facility.getLimitAmtCny()) - toDouble(facility.getUsedLimit()));
            }

            // Sum amtFinancedCny across group
            double totalAmtFinanced = groupAssets.stream()
                    .mapToDouble(a -> toDouble(a.getAmtFinancedCny()))
                    .sum();
            if (totalAmtFinanced <= 0) continue;

            // Allocate off-balance EAD per asset
            for (AssetInput asset : groupAssets) {
                double ccf = findCcf(curves, asset, defaultCcf);
                double offBsPool = undrawn * ccf;
                double share = toDouble(asset.getAmtFinancedCny()) / totalAmtFinanced;
                double offBsEad = offBsPool * share;
                asset.setOffBsEad(offBsEad);
                asset.setTotalEad(asset.getOnBsEad() + offBsEad);
                facilityProcessed.add(asset);
            }
        }

        // Remaining assets (no facility or facility not found): use old method
        for (AssetInput asset : allAssets) {
            if (facilityProcessed.contains(asset)) continue;
            double undrawn = Math.max(0.0, toDouble(asset.getTotalLimit()) - toDouble(asset.getOutstandingBalance()));
            double ccf = findCcf(curves, asset, defaultCcf);
            double offBsEad = undrawn * ccf;
            asset.setOffBsEad(offBsEad);
            asset.setTotalEad(asset.getOnBsEad() + offBsEad);
        }
    }

    // ========== CCF lookup ==========

    private double findCcf(List<CcfCurveEntity> curves, AssetInput asset, double defaultCcf) {
        if (curves == null || curves.isEmpty()) return defaultCcf;

        String productType = asset.getProductType();
        String commitmentType = asset.getCommitmentType();
        Integer commitmentDays = asset.getCommitmentDays();
        if (productType == null || commitmentType == null) {
            return defaultCcf;
        }

        // If commitmentDays is set, prefer a curve whose days range covers it
        if (commitmentDays != null) {
            // 1. 精确匹配 (productType + commitmentType)
            Double result = matchCcf(curves, productType, commitmentType, commitmentDays);
            if (result != null) return result;
            // 2. 降级匹配 (只按 productType，忽略 commitmentType 空串)
            result = matchCcf(curves, productType, "", commitmentDays);
            if (result != null) return result;
        }

        // Fallback: first matching productType
        Double result = matchCcfFirst(curves, productType, commitmentType);
        if (result != null) return result;
        result = matchCcfFirst(curves, productType, "");
        if (result != null) return result;

        return defaultCcf;
    }

    /** 按 productType+commitmentType 精确匹配天数范围 */
    private Double matchCcf(List<CcfCurveEntity> curves, String productType, String commitmentType, int commitmentDays) {
        for (CcfCurveEntity c : curves) {
            if (productType.equals(c.getProductType()) && commitmentType.equals(nvl(c.getCommitmentType()))) {
                Integer min = c.getCommitmentDaysMin();
                Integer max = c.getCommitmentDaysMax();
                if (min != null && max != null && commitmentDays >= min && commitmentDays <= max) {
                    return c.getCcfValue() != null ? c.getCcfValue().doubleValue() : 0.0;
                }
            }
        }
        return null;
    }

    /** 按 productType+commitmentType 取第一条匹配 */
    private Double matchCcfFirst(List<CcfCurveEntity> curves, String productType, String commitmentType) {
        for (CcfCurveEntity c : curves) {
            if (productType.equals(c.getProductType()) && commitmentType.equals(nvl(c.getCommitmentType()))) {
                return c.getCcfValue() != null ? c.getCcfValue().doubleValue() : 0.0;
            }
        }
        return null;
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private boolean isOffBalance(AssetInput asset) {
        String businessType = asset.getBusinessType();
        return businessType != null && "OFF_BS".equalsIgnoreCase(businessType.trim());
    }

    private List<CcfCurveEntity> buildCcfCache(String schemeId) {
        List<CcfCurveEntity> curves = ccfCurveMapper.selectList(
                new LambdaQueryWrapper<CcfCurveEntity>()
                        .eq(CcfCurveEntity::getSchemeId, schemeId));
        return curves != null ? curves : Collections.emptyList();
    }

    private double toDouble(BigDecimal v) { return v != null ? v.doubleValue() : 0.0; }

    /**
     * ACT/365 天数惯例：实际天数 ÷ 365。
     * 年化利率折现时使用该惯例将天数转为年数，与年化复利公式 (1+r)^t 配合。
     */
    private double calcYearsAct365(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) / 365.0;
    }
}
