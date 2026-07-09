package com.bank.ecl.engine.pd;

import com.bank.ecl.data.entity.PdCurveEntity;
import com.bank.ecl.data.entity.PdScenarioEntity;
import com.bank.ecl.data.mapper.PdCurveMapper;
import com.bank.ecl.data.mapper.PdScenarioMapper;
import com.bank.ecl.engine.core.*;
import com.bank.ecl.engine.stage.StageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdEngineTest {

    @Mock private PdScenarioMapper scenarioMapper;
    @Mock private PdCurveMapper curveMapper;
    private PdEngine engine;

    @BeforeEach
    void setUp() { engine = new PdEngine(scenarioMapper, curveMapper); }

    // helpers
    private PdScenarioEntity scenario(Long id, String type, String name, double weight) {
        PdScenarioEntity s = new PdScenarioEntity();
        s.setScenarioId(id); s.setScenarioType(type); s.setScenarioName(name);
        s.setWeight(BigDecimal.valueOf(weight)); return s;
    }

    private PdCurveEntity curve(String groupId, String ratingCode, Long scenarioId, double pd) {
        PdCurveEntity c = new PdCurveEntity();
        c.setGroupId(groupId); c.setRatingCode(ratingCode);
        c.setScenarioId(scenarioId); c.setPdValue(BigDecimal.valueOf(pd));
        c.setRatingAgency("INTERNAL_CRR");
        return c;
    }

    private PdCurveEntity curve(String groupId, String ratingAgency, String ratingCode, Long scenarioId, double pd) {
        PdCurveEntity c = new PdCurveEntity();
        c.setGroupId(groupId); c.setRatingAgency(ratingAgency); c.setRatingCode(ratingCode);
        c.setScenarioId(scenarioId); c.setPdValue(BigDecimal.valueOf(pd));
        return c;
    }

    private AssetInput asset(String groupId, String ratingCode, Stage stage) {
        AssetInput a = new AssetInput();
        a.setAssetId("AST_001"); a.setGroupId(groupId); a.setRatingCode(ratingCode);
        a.setCrrFinal(ratingCode);
        a.setStageResult(new StageResult(stage, "test", false));
        a.setMaturityDate(LocalDate.of(2028, 6, 21));
        a.setCalcDate(LocalDate.of(2026, 6, 21));
        return a;
    }

    private JobContext ctx(String schemeId, AssetInput a) {
        JobContext c = new JobContext(); c.setSchemeId(schemeId);
        CustomerContext cust = new CustomerContext();
        cust.setCustomerId("CUST_001"); cust.setAssets(List.of(a));
        c.setCustomers(List.of(cust)); return c;
    }

    @Test
    void shouldMarkExceptionWhenNoScenarios() {
        when(scenarioMapper.selectList(any())).thenReturn(Collections.emptyList());
        AssetInput a = asset("GRP_001", "AAA", Stage.STAGE_1);
        engine.execute(ctx("SCH_001", a));
        assertEquals("ECL_001", a.getPdException());
    }

    @Test
    void shouldCalcWeightedPdForSingleScenario() {
        PdScenarioEntity s = scenario(1L, "BASELINE", "基准", 1.0);
        PdCurveEntity c = curve("GRP_001", "AAA", 1L, 0.02);
        when(scenarioMapper.selectList(any())).thenReturn(List.of(s));
        when(curveMapper.selectList(any())).thenReturn(List.of(c));

        AssetInput a = asset("GRP_001", "AAA", Stage.STAGE_1);
        engine.execute(ctx("SCH_001", a));

        assertEquals(0.02, a.getPd12m(), 0.0001);
        assertNull(a.getPdException());
        assertEquals(1, a.getPdDetails().size());
    }

    @Test
    void shouldCalcWeightedPdForThreeScenarios() {
        PdScenarioEntity s1 = scenario(1L, "OPTIMISTIC", "乐观", 0.2);
        PdScenarioEntity s2 = scenario(2L, "BASELINE", "基准", 0.6);
        PdScenarioEntity s3 = scenario(3L, "PESSIMISTIC", "悲观", 0.2);
        PdCurveEntity c1 = curve("GRP_001", "AAA", 1L, 0.01);
        PdCurveEntity c2 = curve("GRP_001", "AAA", 2L, 0.02);
        PdCurveEntity c3 = curve("GRP_001", "AAA", 3L, 0.05);
        when(scenarioMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));
        when(curveMapper.selectList(any())).thenReturn(List.of(c1, c2, c3));

        AssetInput a = asset("GRP_001", "AAA", Stage.STAGE_1);
        engine.execute(ctx("SCH_001", a));

        double expected = 0.01 * 0.2 + 0.02 * 0.6 + 0.05 * 0.2; // 0.024
        assertEquals(expected, a.getPd12m(), 0.0001);
        assertEquals(3, a.getPdDetails().size());
    }

    @Test
    void shouldReturnPd12mForStage1() {
        PdScenarioEntity s = scenario(1L, "BASELINE", "基准", 1.0);
        PdCurveEntity c = curve("GRP_001", "AAA", 1L, 0.03);
        when(scenarioMapper.selectList(any())).thenReturn(List.of(s));
        when(curveMapper.selectList(any())).thenReturn(List.of(c));

        AssetInput a = asset("GRP_001", "AAA", Stage.STAGE_1);
        engine.execute(ctx("SCH_001", a));

        assertEquals(a.getPd12m(), a.getPdLifetime(), 0.0001);
    }

    @Test
    void shouldCalcLifetimePdForStage2() {
        PdScenarioEntity s = scenario(1L, "BASELINE", "基准", 1.0);
        PdCurveEntity c = curve("GRP_001", "AAA", 1L, 0.05);
        when(scenarioMapper.selectList(any())).thenReturn(List.of(s));
        when(curveMapper.selectList(any())).thenReturn(List.of(c));

        AssetInput a = asset("GRP_001", "AAA", Stage.STAGE_2);
        // 剩余 24 个月 = 2 年
        engine.execute(ctx("SCH_001", a));

        double expected = 1 - Math.pow(1 - 0.05, 2.0); // = 0.0975
        assertEquals(expected, a.getPdLifetime(), 0.0001);
    }

    @Test
    void shouldReturnOneForStage3() {
        PdScenarioEntity s = scenario(1L, "BASELINE", "基准", 1.0);
        PdCurveEntity c = curve("GRP_001", "AAA", 1L, 0.05);
        when(scenarioMapper.selectList(any())).thenReturn(List.of(s));
        when(curveMapper.selectList(any())).thenReturn(List.of(c));

        AssetInput a = asset("GRP_001", "AAA", Stage.STAGE_3);
        engine.execute(ctx("SCH_001", a));

        assertEquals(1.0, a.getPdLifetime(), 0.0001);
    }

    @Test
    void shouldMarkExceptionWhenCurveMissing() {
        PdScenarioEntity s = scenario(1L, "BASELINE", "基准", 1.0);
        when(scenarioMapper.selectList(any())).thenReturn(List.of(s));
        when(curveMapper.selectList(any())).thenReturn(Collections.emptyList());

        AssetInput a = asset("GRP_001", "AAA", Stage.STAGE_1);
        engine.execute(ctx("SCH_001", a));

        assertEquals("ECL_001", a.getPdException());
    }

    @Test
    void shouldHandleMissingRating() {
        PdScenarioEntity s = scenario(1L, "BASELINE", "基准", 1.0);
        PdCurveEntity c = curve("GRP_001", "BBB", 1L, 0.02); // 不同评级
        when(scenarioMapper.selectList(any())).thenReturn(List.of(s));
        when(curveMapper.selectList(any())).thenReturn(List.of(c));

        AssetInput a = asset("GRP_001", "AAA", Stage.STAGE_1);
        engine.execute(ctx("SCH_001", a));

        assertEquals("ECL_001", a.getPdException());
    }

    @Test
    void shouldUseExternalRatingAgencyForExternalGroup() {
        AssetInput asset = asset("GRP_003", null, Stage.STAGE_1);
        asset.setExtRatingThisYear("A1");
        asset.setExtRatingCoThisYear("MOODY");

        PdCurveEntity curve = curve("GRP_003", "MOODY", "A1", 1L, 0.02);
        when(scenarioMapper.selectList(any())).thenReturn(List.of(scenario(1L, "BASE", "基准", 1.0)));
        when(curveMapper.selectList(any())).thenReturn(List.of(curve));

        engine.execute(ctx("SCH_001", asset));

        assertNull(asset.getPdException());
        assertEquals(0.02, asset.getPdScenarioResults().get(0).getPdValue(), 0.0001);
    }

    @Test
    void shouldBlockWhenMaturityDateMissing() {
        AssetInput asset = asset("GRP_001", "CRR5", Stage.STAGE_1);
        asset.setMaturityDate(null);

        PdScenarioEntity s = scenario(1L, "BASELINE", "基准", 1.0);
        PdCurveEntity c = curve("GRP_001", "CRR5", 1L, 0.02);
        when(scenarioMapper.selectList(any())).thenReturn(List.of(s));
        when(curveMapper.selectList(any())).thenReturn(List.of(c));

        engine.execute(ctx("SCH_001", asset));

        assertEquals("ECL_001", asset.getPdException());
    }

    @Test
    void shouldBlockWhenMaturityDateIsNotAfterCalcDateForAnyStage() {
        AssetInput asset = asset("GRP_001", "CRR5", Stage.STAGE_2);
        asset.setMaturityDate(LocalDate.of(2026, 6, 21));
        asset.setCalcDate(LocalDate.of(2026, 6, 22));

        when(scenarioMapper.selectList(any())).thenReturn(List.of(scenario(1L, "BASELINE", "基准", 1.0)));
        when(curveMapper.selectList(any())).thenReturn(Collections.emptyList());

        engine.execute(ctx("SCH_001", asset));

        assertEquals("ECL_001", asset.getPdException());
        assertTrue(asset.getPdScenarioResults().isEmpty());
    }

    @Test
    void shouldStoreStageAdjustedPdPerScenarioForStage2() {
        PdScenarioEntity base = scenario(1L, "BASELINE", "基准", 0.6);
        PdScenarioEntity pess = scenario(2L, "PESSIMISTIC", "悲观", 0.4);
        PdCurveEntity baseCurve = curve("GRP_001", "CRR5", 1L, 0.05);
        PdCurveEntity pessCurve = curve("GRP_001", "CRR5", 2L, 0.10);
        when(scenarioMapper.selectList(any())).thenReturn(List.of(base, pess));
        when(curveMapper.selectList(any())).thenReturn(List.of(baseCurve, pessCurve));

        AssetInput asset = asset("GRP_001", "CRR5", Stage.STAGE_2);
        asset.setCalcDate(LocalDate.of(2026, 6, 21));
        asset.setMaturityDate(LocalDate.of(2028, 6, 21));

        engine.execute(ctx("SCH_001", asset));

        double expectedBaseLifetime = 1 - Math.pow(1 - 0.05, 2.0);
        double expectedPessLifetime = 1 - Math.pow(1 - 0.10, 2.0);
        assertEquals(expectedBaseLifetime, asset.getPdScenarioResults().get(0).getPdValue(), 0.0001);
        assertEquals(expectedPessLifetime, asset.getPdScenarioResults().get(1).getPdValue(), 0.0001);
        assertEquals(expectedBaseLifetime * 0.6 + expectedPessLifetime * 0.4, asset.getPdLifetime(), 0.0001);
    }

    @Test
    void shouldReturn0ForStage3WithNoScenarios() {
        // STAGE_3 无情景时 pdLifetime 为 0.0
        AssetInput asset = asset("GRP_001", "CRR5", Stage.STAGE_3);
        when(scenarioMapper.selectList(any())).thenReturn(Collections.emptyList());

        engine.execute(ctx("SCH_001", asset));

        assertEquals(0.0, asset.getPdLifetime(), 0.0001);
        assertTrue(asset.getPdScenarioResults().isEmpty());
    }

    @Test
    void shouldHandleMultipleScenariosWithStage3() {
        // STAGE_3 多情景：每个情景 pd=1.0，按权重加权
        AssetInput asset = asset("GRP_001", "CRR5", Stage.STAGE_3);
        when(scenarioMapper.selectList(any())).thenReturn(List.of(
                scenario(1L, "BASE", "基准", 0.6),
                scenario(2L, "PESS", "悲观", 0.4)));
        when(curveMapper.selectList(any())).thenReturn(Collections.emptyList());

        engine.execute(ctx("SCH_001", asset));

        assertEquals(1.0, asset.getPdLifetime(), 0.0001);
        assertEquals(2, asset.getPdScenarioResults().size());
        assertEquals(1.0, asset.getPdScenarioResults().get(0).getPdValue(), 0.0001);
        assertEquals(1.0, asset.getPdScenarioResults().get(1).getPdValue(), 0.0001);
    }

}
