package com.bank.ecl.calculation.trial;

import com.bank.ecl.calculation.trial.dto.AssetResult;
import com.bank.ecl.calculation.trial.dto.TrialMetricVO;
import com.bank.ecl.calculation.trial.dto.TrialScenarioRowVO;
import com.bank.ecl.calculation.trial.dto.TrialStepVO;
import com.bank.ecl.data.mapper.EclJobMapper;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.data.mapper.RiskGroupMapper;
import com.bank.ecl.engine.core.*;
import com.bank.ecl.engine.core.RepaymentScheduleInput;
import com.bank.ecl.engine.stage.StageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TrialCalculationServiceTest {

    @Mock
    private EngineDispatcher dispatcher;
    @Mock
    private EclSchemeMapper eclSchemeMapper;
    @Mock
    private RiskGroupMapper riskGroupMapper;
    @Mock
    private EclJobMapper eclJobMapper;
    @Mock
    private TrialSourceAssembler trialSourceAssembler;

    private TrialCalculationService service;

    @BeforeEach
    void setUp() {
        service = new TrialCalculationService(dispatcher, eclSchemeMapper, riskGroupMapper,
                eclJobMapper, trialSourceAssembler);
    }

    // ========================================================================
    //  风险分组 Step (Step 1)
    // ========================================================================

    @Test
    void step1_shouldShowNormalGroupWhenGroupMatched() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_001");
        asset.setGroupId("GRP_001");
        asset.setGroupName("对公业务");
        asset.setSegment("非零售");
        asset.setProductType("公司贷款");
        asset.setIndustryCode("J");
        asset.setCollateralType("房产抵押");

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "group");

        assertNotNull(step);
        assertTrue(step.getSummary().contains("对公业务"));
        assertFalse(step.getSummary().contains("兜底"));
        assertNull(asset.getGroupException());
    }

    @Test
    void step1_shouldShowDefaultGroupAndExceptionWhenDefault() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_002");
        asset.setGroupId("GRP_DEFAULT");
        asset.setGroupException("Y");

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "group");

        assertNotNull(step);
        assertTrue(step.getSummary().contains("兜底"));
        assertTrue(step.getNote().contains("异常"));
    }

    // ========================================================================
    //  阶段判定 Step (Step 2)
    // ========================================================================

    @Test
    void step2_shouldShowStageLabelAndTriggerType() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_003");
        asset.setOverdueDays(45);
        asset.setNormalConsecutiveDays(60);
        asset.setIsNpl("N");
        asset.setDefaultFlag(false);
        StageResult sr = new StageResult(Stage.STAGE_2, "overdue_days", false);
        asset.setStageResult(sr);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "stage");

        assertNotNull(step);
        assertTrue(step.getSummary().contains("第二阶段"));
        assertTrue(step.getSummary().contains("overdue_days"));

        // 展示全部6个维度（用 contains 避免编码差异）
        assertTrue(metricHasLabel(step, "逾期天数"));
        assertTrue(metricHasLabel(step, "是否不良"));
        assertTrue(metricHasLabel(step, "正常连续天数"));
        assertTrue(metricHasLabel(step, "CRR"));
        assertTrue(metricHasLabel(step, "五级分类"));
        assertTrue(metricHasLabel(step, "违约标识"));
    }

    @Test
    void step2_shouldShowExceptionNoteWhenRollbackBlocked() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_004");
        StageResult sr = new StageResult(Stage.STAGE_3, "ROLLBACK_BLOCKED", true);
        asset.setStageResult(sr);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "stage");

        assertEquals("第三阶段 · 触发: ROLLBACK_BLOCKED", step.getSummary());
        assertTrue(step.getNote().contains("异常"));
    }

    @Test
    void step2_shouldHandleNullStageResult() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_005");

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "stage");

        assertEquals("-", step.getSummary());
    }

    // ========================================================================
    //  PD 取值 Step (Step 3)
    // ========================================================================

    @Test
    void step3_shouldShow12mPdForStage1() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_006");
        asset.setPd12m(0.02);
        asset.setPdLifetime(0.02);
        asset.setRatingCode("CRR5");
        asset.setCrrFinal("CRR5");
        asset.setMaturityDate(java.time.LocalDate.of(2028, 6, 30));
        StageResult sr = new StageResult(Stage.STAGE_1, null, false);
        asset.setStageResult(sr);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "pd");

        assertNotNull(step);
        assertTrue(step.getSummary().contains("12M PD"));
        assertTrue(step.getMetrics().stream().anyMatch(m -> "评级代码".equals(m.getLabel())));
    }

    @Test
    void step3_shouldShowLifetimePdForStage2() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_007");
        asset.setPd12m(0.03);
        asset.setPdLifetime(0.07);
        StageResult sr = new StageResult(Stage.STAGE_2, null, false);
        asset.setStageResult(sr);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "pd");

        assertTrue(step.getSummary().contains("Lifetime PD"));
        assertTrue(step.getSummary().contains("7.0000%")); // pdLifetime=0.07
    }

    @Test
    void step3_shouldShow100PercentForStage3() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_008");
        asset.setPd12m(1.0);
        asset.setPdLifetime(1.0);
        StageResult sr = new StageResult(Stage.STAGE_3, null, false);
        asset.setStageResult(sr);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "pd");

        assertTrue(step.getSummary().contains("100%"));
    }

    @Test
    void step3_shouldIncludePdScenarioRows() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_009");
        asset.setPdScenarioResults(List.of(
                pdResult("BASE", "基准", 0.6, 0.02),
                pdResult("PESS", "悲观", 0.4, 0.05)));

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "pd");

        assertEquals(2, step.getScenarioRows().size());
        assertTrue(step.getScenarioRows().get(0).getScenario().contains("基准"));
    }

    @Test
    void step3_shouldShowPdExceptionNote() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_010");
        asset.setPdException("ECL_001");

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "pd");

        assertTrue(step.getNote().contains("ECL_001"));
    }

    // ========================================================================
    //  EAD 计算 Step (Step 4)
    // ========================================================================

    @Test
    void step4_shouldShowOnOffAndTotalEad() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_011");
        asset.setOnBsEad(500_000);
        asset.setOffBsEad(100_000);
        asset.setTotalEad(600_000);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "ead");

        assertNotNull(step);
        assertTrue(hasMetric(step, "表内 EAD"));
        assertTrue(hasMetric(step, "表外 EAD"));
        assertTrue(hasMetric(step, "总 EAD"));
        assertTrue(step.getSummary().contains("¥600000"));
    }

    @Test
    void step4_shouldShowRepaymentScheduleMethod() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_012");
        asset.setTotalEad(450_000);
        asset.setRepaymentSchedules(List.of(new RepaymentScheduleInput()));

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "ead");

        assertTrue(step.getSummary().contains("还款计划折现"));
    }

    @Test
    void step4_shouldShowBalanceMethodByDefault() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_013");
        asset.setTotalEad(300_000);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "ead");

        assertTrue(step.getSummary().contains("余额+利息"));
    }

    @Test
    void step4_shouldShowEadExceptionInNote() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_014");
        asset.setTotalEad(0);
        asset.setEadException("ECL_002");

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "ead");

        assertTrue(step.getNote().contains("ECL_002"));
    }

    // ========================================================================
    //  LGD 取值 Step (Step 5)
    // ========================================================================

    @Test
    void step5_shouldShowLgdAndCollateralPool() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_015");
        asset.setLgdValue(0.35);
        asset.setCollateralPoolId("POOL_001");

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "lgd");

        assertNotNull(step);
        assertTrue(step.getSummary().contains("35.0000%"));
        assertTrue(hasMetric(step, "押品池"));
    }

    @Test
    void step5_shouldParseLgdDetailJson() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_016");
        asset.setLgdValue(0.25);
        asset.setLgdDetails("{\"eadCovered\":80000,\"eadUncovered\":20000,\"collateralNetValue\":120000}");

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "lgd");

        // EAD 覆盖/未覆盖应从 JSON 解析
        assertTrue(hasMetric(step, "EAD 覆盖"));
        assertTrue(hasMetric(step, "押品净值"));
    }

    @Test
    void step5_shouldShowDefaultLgdWarning() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_017");
        asset.setLgdValue(0.45);
        asset.setLgdException("DEFAULT");

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "lgd");

        assertTrue(step.getNote().contains("方案默认 LGD"));
    }

    // ========================================================================
    //  ECL 计算 Step (Step 6)
    // ========================================================================

    @Test
    void step6_shouldShowEclFormula() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_018");
        asset.setPdLifetime(0.05);
        asset.setLgdValue(0.4);
        asset.setTotalEad(100_000);
        asset.setEclValue(2_000);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "ecl");

        assertNotNull(step);
        assertTrue(step.getSummary().contains("¥2000"));
        // 展示 4 个要素：PD/LGD/EAD/ECL
        assertEquals(4, step.getMetrics().size());
    }

    @Test
    void step6_shouldIncludeEclScenarioRows() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_019");
        asset.setEclScenarioResults(List.of(
                eclResult("BASE", 0.6, 1800, 1080),
                eclResult("PESS", 0.4, 3000, 1200)));

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "ecl");

        assertEquals(2, step.getScenarioRows().size());
        assertEquals("BASE", step.getScenarioRows().get(0).getScenario());
    }

    // ========================================================================
    //  管理层叠加 Step (Step 7)
    // ========================================================================

    @Test
    void step7_shouldShowOverlayAmountWhenHit() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_020");
        asset.setOverlayAmount(5000);
        asset.setEclFinal(25000);
        asset.setSelectedOverlayId(42L);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "overlay");

        assertNotNull(step);
        assertTrue(step.getSummary().contains("¥5000"));
        assertTrue(hasMetric(step, "叠加金额"));
        assertTrue(hasMetric(step, "ECL 最终"));
        assertTrue(hasMetric(step, "命中叠加规则 ID"));
    }

    @Test
    void step7_shouldShowNoMatchWhenNoOverlay() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_021");
        asset.setOverlayAmount(0);
        asset.setEclFinal(18000);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");
        TrialStepVO step = findStep(result, "overlay");

        assertTrue(step.getSummary().contains("无命中规则"));
        assertEquals("¥18000.00", step.getMetrics().stream()
                .filter(m -> "ECL 最终".equals(m.getLabel()))
                .findFirst().orElseThrow().getValue());
    }

    // ========================================================================
    //  异常汇总 & 全局场景
    // ========================================================================

    @Test
    void shouldCollectMultiEngineExceptionsInSummary() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_022");
        asset.setGroupException("Y");
        asset.setPdException("ECL_001");
        asset.setEadException("ECL_002");

        AssetResult result = service.buildAssetResult(asset, "SCH_001");

        assertTrue(result.getExceptionSummary().contains("分组:Y"));
        assertTrue(result.getExceptionSummary().contains("PD:ECL_001"));
        assertTrue(result.getExceptionSummary().contains("EAD:ECL_002"));
    }

    @Test
    void shouldProduceAllSevenStepsInOrder() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("AST_023");
        asset.setTotalEad(50000);
        asset.setLgdValue(0.3);
        asset.setPd12m(0.02);
        asset.setEclValue(900);
        asset.setEclFinal(900);
        StageResult sr = new StageResult(Stage.STAGE_1, null, false);
        asset.setStageResult(sr);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");

        List<TrialStepVO> steps = result.getSteps();
        assertEquals(7, steps.size());
        assertEquals("group", steps.get(0).getKey());
        assertEquals("stage", steps.get(1).getKey());
        assertEquals("pd", steps.get(2).getKey());
        assertEquals("ead", steps.get(3).getKey());
        assertEquals("lgd", steps.get(4).getKey());
        assertEquals("ecl", steps.get(5).getKey());
        assertEquals("overlay", steps.get(6).getKey());
    }

    @Test
    void shouldHandleFullCycleTrialResult() {
        // 模拟一个完整试算结果，一次验证 7 步均有输出
        AssetInput asset = new AssetInput();
        asset.setAssetId("LN_FULL");
        asset.setGroupId("GRP_001");
        asset.setGroupName("公司贷款");
        asset.setSegment("非零售");
        asset.setProductType("公司贷款");
        asset.setIndustryCode("J");
        asset.setCollateralType("房产抵押");
        asset.setOverdueDays(90);
        asset.setNormalConsecutiveDays(30);
        asset.setIsNpl("N");
        asset.setFiveCategory("次级");
        asset.setCrrRating("CRR6");
        asset.setCrrFinal("CRR6");
        asset.setRatingCode("CRR6");
        asset.setMaturityDate(java.time.LocalDate.of(2028, 6, 30));
        asset.setCalcDate(java.time.LocalDate.of(2026, 6, 24));
        StageResult sr = new StageResult(Stage.STAGE_2, "overdue_days", true);
        asset.setStageResult(sr);
        asset.setPd12m(0.035);
        asset.setPdLifetime(0.12);
        asset.setPdScenarioResults(List.of(
                pdResult("BASE", "基准", 0.6, 0.10),
                pdResult("PESS", "悲观", 0.4, 0.15)));
        asset.setFacilityCd("FAC_001");
        asset.setCommitmentType("不可撤销");
        asset.setOnBsEad(500_000);
        asset.setOffBsEad(50_000);
        asset.setTotalEad(550_000);
        asset.setCollateralPoolId("POOL_001");
        asset.setLgdValue(0.35);
        asset.setLgdDetails("{\"eadCovered\":400000,\"eadUncovered\":150000}");
        asset.setEclScenarioResults(List.of(
                eclResult("BASE", 0.6, 23100, 13860),
                eclResult("PESS", 0.4, 34650, 13860)));
        asset.setEclValue(27720);
        asset.setOverlayAmount(5000);
        asset.setEclFinal(32720);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");

        // 验证 7 个步骤
        List<TrialStepVO> steps = result.getSteps();
        assertEquals(7, steps.size());

        // Step 1: 风险分组
        TrialStepVO s1 = steps.get(0);
        assertEquals("group", s1.getKey());
        assertTrue(s1.getSummary().contains("公司贷款"));

        // Step 2: 阶段判定
        TrialStepVO s2 = steps.get(1);
        assertEquals("stage", s2.getKey());
        assertTrue(s2.getSummary().contains("第二阶段"));

        // Step 3: PD 取值 — 有情景行
        TrialStepVO s3 = steps.get(2);
        assertEquals("pd", s3.getKey());
        assertTrue(s3.getSummary().contains("12.0000%")); // pdLifetime=0.12
        assertEquals(2, s3.getScenarioRows().size());

        // Step 4: EAD
        TrialStepVO s4 = steps.get(3);
        assertEquals("ead", s4.getKey());
        assertTrue(s4.getSummary().contains("¥550000"));

        // Step 5: LGD
        TrialStepVO s5 = steps.get(4);
        assertEquals("lgd", s5.getKey());
        assertTrue(s5.getSummary().contains("35.0000%"));

        // Step 6: ECL
        TrialStepVO s6 = steps.get(5);
        assertEquals("ecl", s6.getKey());
        assertTrue(s6.getSummary().contains("¥27720"));
        assertEquals(2, s6.getScenarioRows().size());

        // Step 7: 管理层叠加
        TrialStepVO s7 = steps.get(6);
        assertEquals("overlay", s7.getKey());
        assertTrue(s7.getSummary().contains("¥5000"));

        // 异常汇总 — 因 exceptionFlag=true 会展示阶段异常
        // engine异常标记通过 isExceptionFlag 控制
        assertNull(result.getExceptionSummary());
    }

    // ========================================================================
    //  辅助方法
    // ========================================================================

    private TrialStepVO findStep(AssetResult result, String key) {
        return result.getSteps().stream()
                .filter(s -> s.getKey().equals(key))
                .findFirst().orElse(null);
    }

    private boolean hasMetric(TrialStepVO step, String label) {
        return step.getMetrics().stream()
                .anyMatch(m -> m.getLabel().equals(label));
    }

    private boolean metricHasLabel(TrialStepVO step, String label) {
        return step.getMetrics().stream()
                .anyMatch(m -> m.getLabel() != null && m.getLabel().contains(label));
    }

    // ========================================================================
    //  辅助构造方法（因 DTO 无 @AllArgsConstructor）
    // ========================================================================

    private static PdScenarioResult pdResult(String type, String name, double weight, double pdValue) {
        PdScenarioResult r = new PdScenarioResult();
        r.setScenarioType(type);
        r.setScenarioName(name);
        r.setWeight(BigDecimal.valueOf(weight));
        r.setPdValue(pdValue);
        return r;
    }

    private static EclScenarioResult eclResult(String code, double weight, double ecl, double weightedEcl) {
        EclScenarioResult r = new EclScenarioResult();
        r.setScenarioCode(code);
        r.setWeight(BigDecimal.valueOf(weight));
        r.setScenarioEcl(ecl);
        r.setWeightedEcl(weightedEcl);
        return r;
    }
}
