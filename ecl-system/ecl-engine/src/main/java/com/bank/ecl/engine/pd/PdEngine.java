package com.bank.ecl.engine.pd;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.EngineType;
import com.bank.ecl.data.entity.PdCurveEntity;
import com.bank.ecl.data.entity.PdScenarioEntity;
import com.bank.ecl.data.mapper.PdCurveMapper;
import com.bank.ecl.data.mapper.PdScenarioMapper;
import com.bank.ecl.engine.core.*;
import com.bank.ecl.engine.stage.StageResult;
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
public class PdEngine implements EclEngine {

    private static final Logger log = LoggerFactory.getLogger(PdEngine.class);
    private final PdScenarioMapper scenarioMapper;
    private final PdCurveMapper curveMapper;

    @Override
    public EngineType getType() { return EngineType.PD; }

    @Override
    public void execute(JobContext ctx) {
        String schemeId = ctx.getSchemeId();
        log.info("[6.3 PD] start, schemeId={}", schemeId);
        if (schemeId == null || schemeId.isBlank()) {
            log.warn("[6.3 PD] schemeId is null or blank, skipping");
            return;
        }

        List<PdScenarioEntity> scenarios = loadScenarios(schemeId);
        if (scenarios.isEmpty()) {
            markAll(ctx, "ECL_001");
            return;
        }
        Map<String, Double> curveCache = buildCurveCache(schemeId);
        log.info("[6.3 PD] loaded {} scenarios, {} curve entries", scenarios.size(), curveCache.size());

        List<CustomerContext> customers = ctx.getCustomers();
        if (customers == null || customers.isEmpty()) { log.info("[6.3 PD] no customers"); return; }

        for (CustomerContext customer : customers) {
            if (customer == null || customer.getAssets() == null) continue;
            for (AssetInput asset : customer.getAssets()) {
                if (asset == null) continue;
                processAsset(asset, scenarios, curveCache);
            }
        }
        log.info("[6.3 PD] complete");
    }

    private void processAsset(AssetInput asset, List<PdScenarioEntity> scenarios,
                              Map<String, Double> cache) {
        String groupId = asset.getGroupId();
        StageResult sr = asset.getStageResult();
        Stage stage = sr != null ? sr.getStage() : Stage.STAGE_1;

        // 到期日为空或不晚于计量日时阻断；Stage3 也必须先通过期限校验。
        if (asset.getMaturityDate() == null
                || asset.getCalcDate() == null
                || !asset.getMaturityDate().isAfter(asset.getCalcDate())) {
            asset.setPdException("ECL_001");
            return;
        }

        // Resolve rating source based on group
        RatingSelection rating = resolveRatingSource(asset);
        String ratingAgency = rating.ratingAgency;
        String ratingCode = rating.ratingCode;

        List<PdDetail> details = new ArrayList<>();
        List<PdScenarioResult> scenarioResults = new ArrayList<>();
        double pd12m = 0.0;
        double pdLifetime = 0.0;
        boolean hasMissing = false;

        for (PdScenarioEntity s : scenarios) {
            double rawPdValue;
            double effectivePdValue;

            if (stage == Stage.STAGE_3) {
                // Stage 3: default to 1.0 per scenario, no curve lookup
                rawPdValue = 1.0;
                effectivePdValue = 1.0;
            } else {
                String key = groupId + "|" + s.getScenarioId() + "|"
                        + normalizeRatingAgency(ratingAgency) + "|" + ratingCode;
                Double cached = cache.get(key);
                if (cached == null) {
                    hasMissing = true;
                    continue;
                }
                rawPdValue = cached;
                effectivePdValue = convertByStage(rawPdValue, stage, asset.getMaturityDate(), asset.getCalcDate());
            }

            double weight = s.getWeight() != null ? s.getWeight().doubleValue() : 0.0;
            pd12m += rawPdValue * weight;
            double weighted = effectivePdValue * weight;
            pdLifetime += weighted;
            details.add(new PdDetail(s.getScenarioType(), s.getScenarioName(),
                    s.getWeight(), effectivePdValue, weighted));

            // 供 ECL 引擎使用的场景 PD 已按阶段调整。
            PdScenarioResult psr = new PdScenarioResult();
            psr.setScenarioType(s.getScenarioType());
            psr.setScenarioName(s.getScenarioName());
            psr.setWeight(s.getWeight());
            psr.setPdValue(effectivePdValue);
            scenarioResults.add(psr);
        }

        asset.setPdDetails(details);
        asset.setPdScenarioResults(scenarioResults);
        asset.setPd12m(pd12m);

        if (hasMissing || details.isEmpty()) {
            asset.setPdException("ECL_001");
        }

        asset.setPdLifetime(pdLifetime);
    }

    private record RatingSelection(String ratingAgency, String ratingCode) {}

    private RatingSelection resolveRatingSource(AssetInput asset) {
        String groupId = asset.getGroupId();
        if (Set.of("GRP_003", "GRP_004").contains(groupId)) {
            return new RatingSelection(
                    asset.getExtRatingCoThisYear(),
                    asset.getExtRatingThisYear());
        }
        return new RatingSelection(
                "INTERNAL_CRR",
                asset.getCrrFinal());
    }

    private double convertByStage(double pd12m, Stage stage, LocalDate maturity, LocalDate calcDate) {
        return switch (stage) {
            case STAGE_1 -> pd12m;
            case STAGE_2 -> {
                long months = ChronoUnit.MONTHS.between(calcDate, maturity);
                double t = Math.max(months / 12.0, 1.0);
                yield 1 - Math.pow(1 - pd12m, t);
            }
            case STAGE_3 -> 1.0;
        };
    }

    private List<PdScenarioEntity> loadScenarios(String schemeId) {
        List<PdScenarioEntity> list = scenarioMapper.selectList(
                new LambdaQueryWrapper<PdScenarioEntity>()
                        .eq(PdScenarioEntity::getSchemeId, schemeId));
        return list != null ? list : Collections.emptyList();
    }

    private Map<String, Double> buildCurveCache(String schemeId) {
        List<PdCurveEntity> curves = curveMapper.selectList(
                new LambdaQueryWrapper<PdCurveEntity>()
                        .eq(PdCurveEntity::getSchemeId, schemeId));
        if (curves == null) return Collections.emptyMap();
        return curves.stream().collect(Collectors.toMap(
                c -> c.getGroupId() + "|" + c.getScenarioId() + "|"
                        + normalizeRatingAgency(c.getRatingAgency()) + "|" + c.getRatingCode(),
                c -> c.getPdValue() != null ? c.getPdValue().doubleValue() : 0.0,
                (a, b) -> a));
    }

    private String normalizeRatingAgency(String ratingAgency) {
        return ratingAgency != null && !ratingAgency.isBlank() ? ratingAgency : "INTERNAL_CRR";
    }

    private void markAll(JobContext ctx, String exceptionCode) {
        if (ctx.getCustomers() == null) return;
        for (CustomerContext c : ctx.getCustomers()) {
            if (c == null || c.getAssets() == null) continue;
            for (AssetInput a : c.getAssets()) {
                if (a != null) a.setPdException(exceptionCode);
            }
        }
    }
}
