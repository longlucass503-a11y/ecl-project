package com.bank.ecl.engine.stage;

import com.bank.ecl.data.entity.CrrRatingDropRuleEntity;
import com.bank.ecl.data.entity.StageRuleEntity;
import com.bank.ecl.data.mapper.CrrRatingDropRuleMapper;
import com.bank.ecl.data.mapper.StageRuleMapper;
import com.bank.ecl.engine.core.AssetInput;
import com.bank.ecl.engine.core.CustomerContext;
import com.bank.ecl.engine.core.JobContext;
import com.bank.ecl.engine.core.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StageEngineTest {

    @Mock
    private StageRuleMapper stageRuleMapper;

    @Mock
    private CrrRatingDropRuleMapper crrDropRuleMapper;

    private StageEngine engine;

    @BeforeEach
    void setUp() {
        engine = new StageEngine(stageRuleMapper, crrDropRuleMapper);
    }

    // ---- helpers ----

    private AssetInput asset(String groupId, Stage lastStage) {
        AssetInput a = new AssetInput();
        a.setAssetId("AST_001");
        a.setGroupId(groupId);
        a.setLastStage(lastStage);
        return a;
    }

    private AssetInput asset(String groupId, Stage lastStage,
                             Integer overdueDays, String crrRating,
                             String fiveCategory, Boolean defaultFlag,
                             String mediaSentiment, Integer ratingDropLevels) {
        AssetInput a = new AssetInput();
        a.setAssetId("AST_001");
        a.setGroupId(groupId);
        a.setLastStage(lastStage);
        a.setOverdueDays(overdueDays);
        a.setCrrRating(crrRating);
        a.setFiveCategory(fiveCategory);
        a.setDefaultFlag(defaultFlag);
        a.setMediaSentiment(mediaSentiment);
        a.setRatingDropLevels(ratingDropLevels);
        return a;
    }

    private JobContext jobCtx(String schemeId, List<AssetInput> assets) {
        JobContext ctx = new JobContext();
        ctx.setSchemeId(schemeId);
        CustomerContext customer = new CustomerContext();
        customer.setCustomerId("CUST_001");
        customer.setAssets(assets);
        ctx.setCustomers(List.of(customer));
        return ctx;
    }

    /**
     * 创建一条 FORWARD 规则：逾期 31-90 天 → Stage 2。
     */
    private StageRuleEntity forwardRule(String groupId, int priority,
                                        String stageTo, String conditions) {
        StageRuleEntity r = new StageRuleEntity();
        r.setGroupId(groupId);
        r.setRuleType("FORWARD");
        r.setPriority(priority);
        r.setStageTo(stageTo);
        r.setConditions(conditions);
        return r;
    }

    /**
     * 创建一条 ROLLBACK 规则。
     */
    private StageRuleEntity rollbackRule(String groupId, String stageFrom,
                                         String stageTo, String conditions) {
        StageRuleEntity r = new StageRuleEntity();
        r.setGroupId(groupId);
        r.setRuleType("ROLLBACK");
        r.setStageFrom(stageFrom);
        r.setStageTo(stageTo);
        r.setConditions(conditions);
        return r;
    }

    private CrrRatingDropRuleEntity crrDropRule(String groupId,
                                                String currentRating,
                                                int dropThreshold) {
        CrrRatingDropRuleEntity r = new CrrRatingDropRuleEntity();
        r.setGroupId(groupId);
        r.setCurrentRating(currentRating);
        r.setDropThreshold(dropThreshold);
        return r;
    }

    private CrrRatingDropRuleEntity crrDropRule(String groupId,
                                                String ratingAgency,
                                                String currentRating,
                                                int dropThreshold) {
        CrrRatingDropRuleEntity r = crrDropRule(groupId, currentRating, dropThreshold);
        r.setRatingAgency(ratingAgency);
        return r;
    }

    // ======================== Tests ========================

    @Test
    void shouldReturnStage1WhenNoForwardRules() {
        String schemeId = "SCH_001";
        when(stageRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(crrDropRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        AssetInput a = asset("GRP_001", null, 45, null, null, null, null, null);
        JobContext ctx = jobCtx(schemeId, List.of(a));

        engine.execute(ctx);

        assertEquals(Stage.STAGE_1, a.getStageResult().getStage());
        assertTrue(a.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldMatchForwardRule() {
        String schemeId = "SCH_001";
        String conditions = "{\"overdue_days\":{\"min\":31,\"max\":90}}";
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_2", conditions);
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 逾期 45 天 → 命中 Stage 2 规则
        AssetInput a = asset("GRP_001", Stage.STAGE_1, 45, null, null, null, null, null);
        JobContext ctx = jobCtx(schemeId, List.of(a));

        engine.execute(ctx);

        assertEquals(Stage.STAGE_2, a.getStageResult().getStage());
        assertFalse(a.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldMatchEditorFormatGreaterThanOverdueRule() {
        String schemeId = "SCH_001";
        String conditions = "{\"logic\":\"OR\",\"conditions\":[{\"type\":\"逾期天数\",\"operator\":\"gt\",\"value\":30}]}";
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_2", conditions);
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        AssetInput a = asset("GRP_001", Stage.STAGE_1, 31, null, null, null, null, null);
        JobContext ctx = jobCtx(schemeId, List.of(a));

        engine.execute(ctx);

        assertEquals(Stage.STAGE_2, a.getStageResult().getStage());
        assertFalse(a.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldMatchEditorFormatLessThanOverdueRule() {
        String schemeId = "SCH_001";
        String conditions = "{\"logic\":\"OR\",\"conditions\":[{\"type\":\"逾期天数\",\"operator\":\"lt\",\"value\":30}]}";
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_2", conditions);
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        AssetInput a = asset("GRP_001", Stage.STAGE_1, 29, null, null, null, null, null);
        JobContext ctx = jobCtx(schemeId, List.of(a));

        engine.execute(ctx);

        assertEquals(Stage.STAGE_2, a.getStageResult().getStage());
        assertFalse(a.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldMatchFirstForwardRuleByPriority() {
        String schemeId = "SCH_001";
        String stage3Cond = "{\"overdue_days\":{\"min\":91}}";
        String stage2Cond = "{\"overdue_days\":{\"min\":31,\"max\":90}}";
        // Stage 3 规则 priority=1（高优先级），Stage 2 规则 priority=2
        StageRuleEntity rule3 = forwardRule("GRP_001", 1, "STAGE_3", stage3Cond);
        StageRuleEntity rule2 = forwardRule("GRP_001", 2, "STAGE_2", stage2Cond);
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule3, rule2));
        when(crrDropRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 逾期 120 天 → 同时满足两条规则，但应命中 priority=1 的 Stage 3
        AssetInput a = asset("GRP_001", Stage.STAGE_1, 120, null, null, null, null, null);
        JobContext ctx = jobCtx(schemeId, List.of(a));

        engine.execute(ctx);

        assertEquals(Stage.STAGE_3, a.getStageResult().getStage());
    }

    @Test
    void shouldUseStage1WhenNoForwardRuleMatches() {
        String schemeId = "SCH_001";
        // 规则匹配逾期 31-90 → Stage 2
        String conditions = "{\"overdue_days\":{\"min\":31,\"max\":90}}";
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_2", conditions);
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 逾期仅 5 天 → 不满足任何规则
        AssetInput a = asset("GRP_001", Stage.STAGE_1, 5, null, null, null, null, null);
        JobContext ctx = jobCtx(schemeId, List.of(a));

        engine.execute(ctx);

        assertEquals(Stage.STAGE_1, a.getStageResult().getStage());
        assertTrue(a.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldAllowRollbackWhenConditionsMet() {
        String schemeId = "SCH_001";
        // FORWARD: 逾期 < 31 → Stage 1（没有匹配的 forward 规则时兜底也是 Stage 1）
        String forwardCond = "{\"overdue_days\":{\"min\":31,\"max\":90}}";
        StageRuleEntity fRule = forwardRule("GRP_001", 1, "STAGE_2", forwardCond);

        // ROLLBACK: Stage 2 → Stage 1，条件为观察期满足
        String rollbackCond = "{\"overdue_days\":{\"max\":30}}";
        StageRuleEntity rRule = rollbackRule("GRP_001", "STAGE_2", "STAGE_1", rollbackCond);

        when(stageRuleMapper.selectList(any())).thenReturn(List.of(fRule, rRule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 上期 Stage 2，本期逾期降到 5 天（满足 forward=无命中=Stage 1，也满足 rollback）
        AssetInput a = asset("GRP_001", Stage.STAGE_2, 5, null, null, null, null, null);
        JobContext ctx = jobCtx(schemeId, List.of(a));

        engine.execute(ctx);

        // FORWARD 无命中 → target=STAGE_1，但阶段改善（1<2），需 ROLLBACK 校验
        // ROLLBACK 条件满足（逾期≤30）→ 允许回跳
        assertEquals(Stage.STAGE_1, a.getStageResult().getStage());
        assertFalse(a.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldBlockRollbackWhenConditionsNotMet() {
        String schemeId = "SCH_001";
        // FORWARD: 逾期 < 31 → 无命中（兜底 Stage 1）
        String forwardCond = "{\"overdue_days\":{\"min\":31,\"max\":90}}";
        StageRuleEntity fRule = forwardRule("GRP_001", 1, "STAGE_2", forwardCond);

        // ROLLBACK: Stage 2 → Stage 1，要求逾期≤0（不可能满足）
        String rollbackCond = "{\"overdue_days\":{\"max\":0}}";
        StageRuleEntity rRule = rollbackRule("GRP_001", "STAGE_2", "STAGE_1", rollbackCond);

        when(stageRuleMapper.selectList(any())).thenReturn(List.of(fRule, rRule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 上期 Stage 2，逾期 5 天
        AssetInput a = asset("GRP_001", Stage.STAGE_2, 5, null, null, null, null, null);
        JobContext ctx = jobCtx(schemeId, List.of(a));

        engine.execute(ctx);

        // FORWARD 无命中 → target=STAGE_1，但 ROLLBACK 不满足 → 保持 STAGE_2
        assertEquals(Stage.STAGE_2, a.getStageResult().getStage());
        assertTrue(a.getStageResult().isExceptionFlag());
        assertEquals("ROLLBACK_BLOCKED", a.getStageResult().getTriggerType());
    }

    @Test
    void shouldSkipRollbackWhenStageWorsens() {
        String schemeId = "SCH_001";
        String forwardCond = "{\"overdue_days\":{\"min\":91}}";
        StageRuleEntity fRule = forwardRule("GRP_001", 1, "STAGE_3", forwardCond);
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(fRule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 上期 Stage 1，本期逾期 120 天 → Stage 3（恶化，不校验 ROLLBACK）
        AssetInput a = asset("GRP_001", Stage.STAGE_1, 120, null, null, null, null, null);
        JobContext ctx = jobCtx(schemeId, List.of(a));

        engine.execute(ctx);

        assertEquals(Stage.STAGE_3, a.getStageResult().getStage());
        assertFalse(a.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldUseIsNplForStage3DefaultRule() {
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_3", "{\"is_npl\":\"Y\"}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());

        AssetInput asset = asset("GRP_001", Stage.STAGE_1);
        asset.setIsNpl("Y");

        engine.execute(jobCtx("SCH_001", List.of(asset)));

        assertEquals(Stage.STAGE_3, asset.getStageResult().getStage());
    }

    @Test
    void shouldBlockStage3DirectRollbackToStage1() {
        StageRuleEntity rollback = rollbackRule("GRP_001", "STAGE_3", "STAGE_1", "{\"normal_consecutive_days\":{\"min\":180}}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rollback));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());

        AssetInput asset = asset("GRP_001", Stage.STAGE_3);
        asset.setNormalConsecutiveDays(90);

        engine.execute(jobCtx("SCH_001", List.of(asset)));

        assertEquals(Stage.STAGE_3, asset.getStageResult().getStage());
        assertEquals("ROLLBACK_BLOCKED", asset.getStageResult().getTriggerType());
    }

    @Test
    void shouldTriggerCrrDropWithCompositeKey() {
        CrrRatingDropRuleEntity dropRule = crrDropRule("GRP_001", "CRR3", 2);
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of(dropRule));
        when(stageRuleMapper.selectList(any())).thenReturn(List.of());

        AssetInput asset = asset("GRP_001", Stage.STAGE_1, 0, "CRR3",
                null, null, null, 3);
        JobContext ctx = jobCtx("SCH_001", List.of(asset));

        engine.execute(ctx);

        // CRR drop of 3 levels >= threshold of 2, but no forward rule matches.
        // Composite key lookup (groupId|INTERNAL_CRR|CRR3) must work.
        assertEquals(Stage.STAGE_1, asset.getStageResult().getStage());
        assertTrue(asset.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldEvaluateCrrDropBySelectedExternalRatingAgency() {
        CrrRatingDropRuleEntity moodyRule = crrDropRule("GRP_003",
                "MOODY", "Baa3", 2);
        CrrRatingDropRuleEntity spRule = crrDropRule("GRP_003",
                "S&P", "Baa3", 5);
        StageRuleEntity forward = forwardRule("GRP_003", 1, "STAGE_2", "{\"crr_drop\":true}");
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of(moodyRule, spRule));
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(forward));

        AssetInput asset = asset("GRP_003", Stage.STAGE_1);
        asset.setExtRatingCoThisYear("MOODY");
        asset.setExtRatingCoLastYear("MOODY");
        asset.setExtRatingLastYear("A1");
        asset.setExtRatingThisYear("Baa3");

        engine.execute(jobCtx("SCH_001", List.of(asset)));

        assertEquals(Stage.STAGE_2, asset.getStageResult().getStage());
        assertFalse(asset.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldEvaluateOrConditionArrays() {
        StageRuleEntity forward = forwardRule("GRP_001", 1, "STAGE_2",
                "{\"or\":[{\"overdue_days\":{\"min\":31}},{\"is_npl\":\"Y\"}]}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(forward));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());

        AssetInput asset = asset("GRP_001", Stage.STAGE_1);
        asset.setIsNpl("Y");

        engine.execute(jobCtx("SCH_001", List.of(asset)));

        assertEquals(Stage.STAGE_2, asset.getStageResult().getStage());
        assertFalse(asset.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldTreatNullLastStageAsStage1() {
        String schemeId = "SCH_001";
        String stage3Cond = "{\"overdue_days\":{\"min\":91}}";
        StageRuleEntity rule3 = forwardRule("GRP_001", 1, "STAGE_3", stage3Cond);
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule3));
        when(crrDropRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        // lastStage = null，逾期 120 天
        AssetInput a = asset("GRP_001", null, 120, null, null, null, null, null);
        JobContext ctx = jobCtx(schemeId, List.of(a));

        engine.execute(ctx);

        assertEquals(Stage.STAGE_3, a.getStageResult().getStage());
    }

    // ======================== 逾期天数范围 测试 ========================

    @Test
    void shouldMatchRangeExclusiveLeftInclusiveRight() {
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_2",
                "{\"logic\":\"AND\",\"conditions\":[{\"type\":\"逾期天数范围\",\"min\":30,\"minExclusive\":true,\"max\":90,\"maxExclusive\":false}]}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());
        AssetInput a = asset("GRP_001", Stage.STAGE_1, 45, null, null, null, null, null);
        engine.execute(jobCtx("SCH_001", List.of(a)));
        assertEquals(Stage.STAGE_2, a.getStageResult().getStage());
    }

    @Test
    void shouldRejectAtExclusiveLeftBoundary() {
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_2",
                "{\"logic\":\"AND\",\"conditions\":[{\"type\":\"逾期天数范围\",\"min\":30,\"minExclusive\":true,\"max\":90,\"maxExclusive\":false}]}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());
        AssetInput a = asset("GRP_001", Stage.STAGE_1, 30, null, null, null, null, null);
        engine.execute(jobCtx("SCH_001", List.of(a)));
        assertEquals(Stage.STAGE_1, a.getStageResult().getStage());
        assertTrue(a.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldAcceptExclusiveRightBoundaryEdge() {
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_2",
                "{\"logic\":\"AND\",\"conditions\":[{\"type\":\"逾期天数范围\",\"min\":30,\"minExclusive\":false,\"max\":90,\"maxExclusive\":true}]}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());
        AssetInput a = asset("GRP_001", Stage.STAGE_1, 89, null, null, null, null, null);
        engine.execute(jobCtx("SCH_001", List.of(a)));
        assertEquals(Stage.STAGE_2, a.getStageResult().getStage());
    }

    @Test
    void shouldRejectAtExclusiveRightBoundary() {
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_2",
                "{\"logic\":\"AND\",\"conditions\":[{\"type\":\"逾期天数范围\",\"min\":30,\"minExclusive\":false,\"max\":90,\"maxExclusive\":true}]}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());
        AssetInput a = asset("GRP_001", Stage.STAGE_1, 90, null, null, null, null, null);
        engine.execute(jobCtx("SCH_001", List.of(a)));
        assertEquals(Stage.STAGE_1, a.getStageResult().getStage());
    }

    @Test
    void shouldRejectRangeWhenMinGreaterThanMax() {
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_2",
                "{\"logic\":\"AND\",\"conditions\":[{\"type\":\"逾期天数范围\",\"min\":90,\"max\":30}]}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());
        AssetInput a = asset("GRP_001", Stage.STAGE_1, 45, null, null, null, null, null);
        engine.execute(jobCtx("SCH_001", List.of(a)));
        assertEquals(Stage.STAGE_1, a.getStageResult().getStage());
    }

    @Test
    void shouldNotAffectOriginalRangeWithoutExclusiveFlags() {
        StageRuleEntity rule = forwardRule("GRP_001", 1, "STAGE_2",
                "{\"overdue_days\":{\"min\":31,\"max\":90}}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());
        AssetInput a = asset("GRP_001", Stage.STAGE_1, 31, null, null, null, null, null);
        engine.execute(jobCtx("SCH_001", List.of(a)));
        assertEquals(Stage.STAGE_2, a.getStageResult().getStage());
        AssetInput b = asset("GRP_001", Stage.STAGE_1, 90, null, null, null, null, null);
        engine.execute(jobCtx("SCH_001", List.of(b)));
        assertEquals(Stage.STAGE_2, b.getStageResult().getStage());
    }

    @Test
    void shouldBlockStage3ToStage2RollbackWhenConditionNotMet() {
        // STAGE_3 → STAGE_2 回跳条件不满足时应阻断
        StageRuleEntity forward = forwardRule("GRP_001", 1, "STAGE_2", "{\"overdue_days\":{\"min\":31}}");
        StageRuleEntity rollback = rollbackRule("GRP_001", "STAGE_3", "STAGE_2", "{\"normal_consecutive_days\":{\"min\":180}}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(forward, rollback));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());

        AssetInput asset = asset("GRP_001", Stage.STAGE_3);
        asset.setOverdueDays(90);
        asset.setNormalConsecutiveDays(90);

        engine.execute(jobCtx("SCH_001", List.of(asset)));

        assertEquals(Stage.STAGE_3, asset.getStageResult().getStage());
        assertEquals("ROLLBACK_BLOCKED", asset.getStageResult().getTriggerType());
    }

    @Test
    void shouldAllowStage3DirectRollbackToStage2WhenConditionsMet() {
        // STAGE_3 → STAGE_2，回跳条件满足时应允许
        // FORWARD: 逾期≥31天 → STAGE_2，asset 逾期 90 天 → 命中
        StageRuleEntity forward = forwardRule("GRP_001", 1, "STAGE_2", "{\"overdue_days\":{\"min\":31}}");
        StageRuleEntity rollback = rollbackRule("GRP_001", "STAGE_3", "STAGE_2", "{\"normal_consecutive_days\":{\"min\":180}}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(forward, rollback));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());

        AssetInput asset = asset("GRP_001", Stage.STAGE_3);
        asset.setOverdueDays(90);
        asset.setNormalConsecutiveDays(365);

        engine.execute(jobCtx("SCH_001", List.of(asset)));

        assertEquals(Stage.STAGE_2, asset.getStageResult().getStage());
        assertFalse(asset.getStageResult().isExceptionFlag());
    }

    @Test
    void shouldHandleMultipleForwardRulesFromDifferentGroups() {
        // 多分组规则：资产只能匹配自己分组的规则
        StageRuleEntity fRule1 = forwardRule("GRP_001", 1, "STAGE_2", "{\"overdue_days\":{\"min\":31}}");
        StageRuleEntity fRule2 = forwardRule("GRP_002", 1, "STAGE_3", "{\"overdue_days\":{\"min\":61}}");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(fRule1, fRule2));
        when(crrDropRuleMapper.selectList(any())).thenReturn(List.of());

        AssetInput asset = asset("GRP_001", Stage.STAGE_1);
        asset.setOverdueDays(90);

        engine.execute(jobCtx("SCH_001", List.of(asset)));

        // GRP_001 只有 STAGE_2 规则，不应命中 GRP_002 的 STAGE_3 规则
        assertEquals(Stage.STAGE_2, asset.getStageResult().getStage());
    }

}
