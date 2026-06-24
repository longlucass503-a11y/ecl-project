package com.bank.ecl.engine.overlay;

import com.bank.ecl.data.entity.OverlayRuleEntity;
import com.bank.ecl.data.mapper.OverlayRuleMapper;
import com.bank.ecl.engine.core.*;
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
class OverlayEngineTest {
    @Mock
    private OverlayRuleMapper overlayRuleMapper;
    private OverlayEngine engine;

    @BeforeEach
    void setUp() {
        engine = new OverlayEngine(overlayRuleMapper);
    }

    private AssetInput asset(String groupId, double ecl, double ead) {
        AssetInput a = new AssetInput();
        a.setAssetId("AST_001");
        a.setGroupId(groupId);
        a.setEclValue(ecl);
        a.setTotalEad(ead);
        return a;
    }

    private JobContext ctx(String schemeId, AssetInput a) {
        JobContext c = new JobContext();
        c.setSchemeId(schemeId);
        CustomerContext cust = new CustomerContext();
        cust.setAssets(List.of(a));
        c.setCustomers(List.of(cust));
        return c;
    }

    private JobContext ctx(String schemeId, LocalDate calcDate, AssetInput a) {
        JobContext c = ctx(schemeId, a);
        c.setCalcDate(calcDate);
        return c;
    }

    private OverlayRuleEntity rule(String groupId, String adjType, double adjValue, int priority, String conditions) {
        OverlayRuleEntity r = new OverlayRuleEntity();
        r.setGroupId(groupId);
        r.setAdjustmentType(adjType);
        r.setAdjustmentValue(BigDecimal.valueOf(adjValue));
        r.setPriority(priority);
        r.setConditions(conditions);
        return r;
    }

    @Test
    void shouldOutputEclFinalWithoutOverlayWhenNoRules() {
        when(overlayRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        AssetInput a = asset("GRP_001", 100.0, 1000.0);
        engine.execute(ctx("SCH_001", a));
        assertEquals(0.0, a.getOverlayAmount(), 0.01);
        assertEquals(100.0, a.getEclFinal(), 0.01);
    }

    @Test
    void shouldApplyAddbpOverlay() {
        OverlayRuleEntity r = rule("GRP_001", "ADDBP", 50.0, 1, null); // null conditions = always match
        when(overlayRuleMapper.selectList(any())).thenReturn(List.of(r));
        AssetInput a = asset("GRP_001", 100.0, 1000.0);
        engine.execute(ctx("SCH_001", a));
        assertEquals(5.0, a.getOverlayAmount(), 0.01);  // 1000 x 50/10000
        assertEquals(105.0, a.getEclFinal(), 0.01);
    }

    @Test
    void shouldApplyPercentageOverlay() {
        OverlayRuleEntity r = rule("GRP_001", "PERCENTAGE", 0.10, 1, null);
        when(overlayRuleMapper.selectList(any())).thenReturn(List.of(r));
        AssetInput a = asset("GRP_001", 100.0, 1000.0);
        engine.execute(ctx("SCH_001", a));
        assertEquals(100.0, a.getOverlayAmount(), 0.01); // 1000 x 0.10
        assertEquals(200.0, a.getEclFinal(), 0.01);
    }

    @Test
    void shouldPickHighestRatioWhenMultiple() {
        OverlayRuleEntity r1 = rule("GRP_001", "ADDBP", 50.0, 1, null);   // ratio = 0.005
        OverlayRuleEntity r2 = rule("GRP_001", "PERCENTAGE", 0.02, 2, null); // ratio = 0.02
        when(overlayRuleMapper.selectList(any())).thenReturn(List.of(r1, r2));
        AssetInput a = asset("GRP_001", 100.0, 1000.0);
        engine.execute(ctx("SCH_001", a));
        assertEquals(20.0, a.getOverlayAmount(), 0.01);  // picked PERCENTAGE 0.02
        assertEquals(120.0, a.getEclFinal(), 0.01);
    }

    @Test
    void shouldUseLowestPriorityWhenEquivalentRatioTies() {
        OverlayRuleEntity r1 = rule("GRP_001", "PERCENTAGE", 0.01, 2, null);
        r1.setRuleId(100L);
        OverlayRuleEntity r2 = rule("GRP_001", "PERCENTAGE", 0.01, 1, null);
        r2.setRuleId(101L);
        when(overlayRuleMapper.selectList(any())).thenReturn(List.of(r1, r2));

        AssetInput a = asset("GRP_001", 100.0, 1000.0);
        engine.execute(ctx("SCH_001", LocalDate.of(2026, 6, 24), a));

        assertEquals(Long.valueOf(101L), a.getSelectedOverlayId());
        assertEquals(10.0, a.getOverlayAmount(), 0.01);  // r2 PERCENTAGE 0.01 * 1000
    }

    @Test
    void shouldSkipRulesOutsideDateRange() {
        LocalDate calcDate = LocalDate.of(2026, 6, 24);
        OverlayRuleEntity r1 = rule("GRP_001", "PERCENTAGE", 0.10, 1, null);
        r1.setExpiryDate(LocalDate.of(2026, 6, 1)); // expired
        OverlayRuleEntity r2 = rule("GRP_001", "PERCENTAGE", 0.01, 2, null);
        r2.setEffectiveDate(LocalDate.of(2026, 7, 1)); // not yet effective
        OverlayRuleEntity r3 = rule("GRP_001", "PERCENTAGE", 0.05, 3, null);
        r3.setEffectiveDate(LocalDate.of(2026, 6, 1));
        r3.setExpiryDate(LocalDate.of(2026, 7, 1));
        when(overlayRuleMapper.selectList(any())).thenReturn(List.of(r1, r2, r3));

        AssetInput a = asset("GRP_001", 100.0, 1000.0);
        engine.execute(ctx("SCH_001", calcDate, a));

        assertEquals(50.0, a.getOverlayAmount(), 0.01); // r3 PERCENTAGE 0.05 * 1000
    }

    @Test
    void shouldHandleFixedTypeWithZeroEad() {
        OverlayRuleEntity r1 = rule("GRP_001", "FIXED", 100.0, 1, null);
        OverlayRuleEntity r2 = rule("GRP_001", "PERCENTAGE", 0.01, 2, null);
        when(overlayRuleMapper.selectList(any())).thenReturn(List.of(r1, r2));

        AssetInput a = asset("GRP_001", 100.0, 0.0); // zero EAD
        a.setTotalEad(0.0);
        engine.execute(ctx("SCH_001", a));

        assertEquals(0.0, a.getOverlayAmount(), 0.01); // FIXED skipped, PERCENTAGE = 0 * 0.01 = 0
        assertEquals(100.0, a.getEclFinal(), 0.01);
    }
}
