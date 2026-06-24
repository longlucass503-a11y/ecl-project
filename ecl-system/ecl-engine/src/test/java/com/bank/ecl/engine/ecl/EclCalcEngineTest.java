package com.bank.ecl.engine.ecl;

import com.bank.ecl.engine.core.*;
import com.bank.ecl.engine.stage.StageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EclCalcEngineTest {
    private EclCalcEngine engine;

    @BeforeEach
    void setUp() {
        engine = new EclCalcEngine();
    }

    private AssetInput asset(double pd, double lgd, double ead) {
        AssetInput a = new AssetInput();
        a.setAssetId("AST_001");
        a.setPdLifetime(pd);
        a.setLgdValue(lgd);
        a.setTotalEad(ead);
        return a;
    }

    private JobContext ctx(AssetInput a) {
        JobContext c = new JobContext();
        CustomerContext cust = new CustomerContext();
        cust.setAssets(List.of(a));
        c.setCustomers(List.of(cust));
        return c;
    }

    @Test
    void shouldCalcEclNormal() {
        AssetInput a = asset(0.05, 0.45, 1000.0);
        engine.execute(ctx(a));
        assertEquals(22.5, a.getEclValue(), 0.01); // 0.05 x 0.45 x 1000
    }

    @Test
    void shouldCalcFullLossForPdOne() {
        AssetInput a = asset(1.0, 0.45, 1000.0);
        engine.execute(ctx(a));
        assertEquals(450.0, a.getEclValue(), 0.01);
    }

    @Test
    void shouldSkipWhenPdException() {
        AssetInput a = asset(0.05, 0.45, 1000.0);
        a.setPdException("ECL_001");
        engine.execute(ctx(a));
        assertEquals(0.0, a.getEclValue(), 0.01);
    }

    @Test
    void shouldHandleMultipleAssets() {
        AssetInput a1 = asset(0.02, 0.40, 500.0);
        AssetInput a2 = asset(0.05, 0.45, 1000.0);
        CustomerContext cust = new CustomerContext();
        cust.setAssets(List.of(a1, a2));
        JobContext c = new JobContext();
        c.setCustomers(List.of(cust));
        engine.execute(c);
        assertEquals(4.0, a1.getEclValue(), 0.01);   // 0.02 x 0.4 x 500
        assertEquals(22.5, a2.getEclValue(), 0.01);  // 0.05 x 0.45 x 1000
    }

    @Test
    void shouldCalculateWeightedEclFromPdScenarios() {
        AssetInput asset = asset(0.0, 0.45, 1000.0);

        PdScenarioResult base = new PdScenarioResult();
        base.setScenarioType("BASE");
        base.setScenarioName("基准");
        base.setWeight(BigDecimal.valueOf(0.6));
        base.setPdValue(0.02);

        PdScenarioResult pess = new PdScenarioResult();
        pess.setScenarioType("PESS");
        pess.setScenarioName("悲观");
        pess.setWeight(BigDecimal.valueOf(0.4));
        pess.setPdValue(0.05);

        asset.setPdScenarioResults(List.of(base, pess));

        engine.execute(ctx(asset));

        assertEquals(14.4, asset.getEclValue(), 0.01);
        assertEquals(2, asset.getEclScenarioResults().size());
    }
}
