package com.bank.ecl.engine.ecl;

import com.bank.ecl.common.constant.EngineType;
import com.bank.ecl.engine.core.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EclCalcEngine implements EclEngine {
    private static final Logger log = LoggerFactory.getLogger(EclCalcEngine.class);

    @Override
    public EngineType getType() {
        return EngineType.ECL;
    }

    @Override
    public void execute(JobContext ctx) {
        log.info("[6.6 ECL] start");
        List<CustomerContext> customers = ctx.getCustomers();
        if (customers == null || customers.isEmpty()) {
            log.info("[6.6 ECL] no customers");
            return;
        }

        for (CustomerContext c : customers) {
            if (c == null || c.getAssets() == null) continue;
            for (AssetInput a : c.getAssets()) {
                if (a == null) continue;
                // PD 异常则跳过
                if (a.getPdException() != null) continue;

                List<PdScenarioResult> scenarios = a.getPdScenarioResults();
                if (scenarios != null && !scenarios.isEmpty()) {
                    // 情景加权 ECL
                    List<EclScenarioResult> eclScenarios = new ArrayList<>();
                    double totalWeighted = 0.0;
                    double lgd = a.getLgdValue();
                    double ead = a.getTotalEad();

                    for (PdScenarioResult ps : scenarios) {
                        double scenarioEcl = ps.getPdValue() * lgd * ead;
                        double weight = ps.getWeight() != null ? ps.getWeight().doubleValue() : 0.0;
                        double weightedEcl = scenarioEcl * weight;
                        totalWeighted += weightedEcl;

                        EclScenarioResult es = new EclScenarioResult();
                        es.setScenarioCode(ps.getScenarioType());
                        es.setWeight(ps.getWeight());
                        es.setScenarioEcl(scenarioEcl);
                        es.setWeightedEcl(weightedEcl);
                        eclScenarios.add(es);
                    }
                    a.setEclScenarioResults(eclScenarios);
                    a.setEclValue(totalWeighted);
                } else {
                    // 无情景则使用简单公式
                    double ecl = a.getPdLifetime() * a.getLgdValue() * a.getTotalEad();
                    a.setEclValue(ecl);
                }
            }
        }
        log.info("[6.6 ECL] complete");
    }
}
