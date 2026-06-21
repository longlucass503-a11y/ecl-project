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

        Map<String, Double> ccfCache = buildCcfCache(schemeId);
        double defaultCcf = ctx.getDefaultCcf();
        log.info("[6.4 EAD] loaded {} CCF entries, defaultCcf={}", ccfCache.size(), defaultCcf);

        List<CustomerContext> customers = ctx.getCustomers();
        if (customers == null || customers.isEmpty()) { log.info("[6.4 EAD] no customers"); return; }

        for (CustomerContext customer : customers) {
            if (customer == null || customer.getAssets() == null) continue;
            for (AssetInput asset : customer.getAssets()) {
                if (asset == null) continue;
                processAsset(asset, ccfCache, defaultCcf);
            }
        }
        log.info("[6.4 EAD] complete");
    }

    private void processAsset(AssetInput asset, Map<String, Double> ccfCache, double defaultCcf) {
        double balance = toDouble(asset.getOutstandingBalance());
        double interest = toDouble(asset.getAccruedInterest());
        double limit = toDouble(asset.getTotalLimit());

        double onBsEad = balance + interest;
        asset.setOnBsEad(onBsEad);

        double undrawn = Math.max(0.0, limit - balance);
        String ccfKey = asset.getProductType() + "|" + asset.getCommitmentType();
        double ccf = ccfCache.getOrDefault(ccfKey, defaultCcf);
        double offBsEad = undrawn * ccf;
        asset.setOffBsEad(offBsEad);

        asset.setTotalEad(onBsEad + offBsEad);
    }

    private Map<String, Double> buildCcfCache(String schemeId) {
        List<CcfCurveEntity> curves = ccfCurveMapper.selectList(
                new LambdaQueryWrapper<CcfCurveEntity>()
                        .eq(CcfCurveEntity::getSchemeId, schemeId));
        if (curves == null) return Collections.emptyMap();
        return curves.stream().collect(Collectors.toMap(
                c -> c.getProductType() + "|" + c.getCommitmentType(),
                c -> c.getCcfValue() != null ? c.getCcfValue().doubleValue() : 0.0,
                (a, b) -> a));
    }

    private double toDouble(BigDecimal v) { return v != null ? v.doubleValue() : 0.0; }
}
