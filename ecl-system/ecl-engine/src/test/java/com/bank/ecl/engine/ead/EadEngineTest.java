package com.bank.ecl.engine.ead;

import com.bank.ecl.data.entity.CcfCurveEntity;
import com.bank.ecl.data.mapper.CcfCurveMapper;
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
class EadEngineTest {
    @Mock private CcfCurveMapper ccfMapper;
    private EadEngine engine;

    @BeforeEach void setUp() { engine = new EadEngine(ccfMapper); }

    private AssetInput asset(String productType, String commitmentType,
                             double balance, double interest, double limit) {
        AssetInput a = new AssetInput();
        a.setAssetId("AST_001"); a.setProductType(productType);
        a.setCommitmentType(commitmentType);
        a.setOutstandingBalance(BigDecimal.valueOf(balance));
        a.setAccruedInterest(BigDecimal.valueOf(interest));
        a.setTotalLimit(BigDecimal.valueOf(limit));
        return a;
    }

    private JobContext ctx(String schemeId, double defaultCcf, AssetInput a) {
        JobContext c = new JobContext(); c.setSchemeId(schemeId); c.setDefaultCcf(defaultCcf);
        CustomerContext cust = new CustomerContext(); cust.setCustomerId("CUST_001");
        cust.setAssets(List.of(a)); c.setCustomers(List.of(cust)); return c;
    }

    private CcfCurveEntity ccf(String productType, String commitmentType, double value) {
        CcfCurveEntity e = new CcfCurveEntity();
        e.setProductType(productType); e.setCommitmentType(commitmentType);
        e.setCcfValue(BigDecimal.valueOf(value)); return e;
    }

    // ========== new helpers for task 9 ==========

    private AssetInput asset(String assetId, String facilityCd, double amtFinancedCny) {
        AssetInput a = new AssetInput();
        a.setAssetId(assetId);
        a.setFacilityCd(facilityCd);
        a.setAmtFinancedCny(BigDecimal.valueOf(amtFinancedCny));
        a.setProductType("公司贷款");
        a.setCommitmentType("承诺");
        a.setOutstandingBalance(BigDecimal.ZERO);
        a.setAccruedInterest(BigDecimal.ZERO);
        a.setTotalLimit(BigDecimal.ZERO);
        return a;
    }

    private RepaymentScheduleInput schedule(LocalDate dueDate, double duePrincipal, double dueInterest) {
        RepaymentScheduleInput s = new RepaymentScheduleInput();
        s.setDueDate(dueDate);
        s.setDuePrincipal(BigDecimal.valueOf(duePrincipal));
        s.setDueInterest(BigDecimal.valueOf(dueInterest));
        return s;
    }

    private AssetInput assetWithSchedule(String assetId, String facilityCd) {
        AssetInput a = new AssetInput();
        a.setAssetId(assetId);
        a.setFacilityCd(facilityCd);
        a.setProductType("公司贷款");
        a.setCommitmentType("承诺");
        a.setOutstandingBalance(BigDecimal.valueOf(1000));
        a.setAccruedInterest(BigDecimal.ZERO);
        a.setTotalLimit(BigDecimal.valueOf(1000));
        return a;
    }

    private JobContext ctxWithFacility(AssetInput... assets) {
        FacilityInput facility = new FacilityInput();
        facility.setFacilityCd("FAC_001");
        facility.setUndrawnAmtCny(BigDecimal.valueOf(100));

        JobContext c = new JobContext();
        c.setSchemeId("SCH_001");
        c.setDefaultCcf(1.0);
        c.setDiscountRate(0.05);
        c.setCalcDate(LocalDate.of(2026, 6, 24));
        c.setFacilities(List.of(facility));

        CcfCurveEntity curve = ccf("公司贷款", "承诺", 1.0);
        when(ccfMapper.selectList(any())).thenReturn(List.of(curve));

        CustomerContext cust = new CustomerContext();
        cust.setCustomerId("CUST_001");
        cust.setAssets(List.of(assets));
        c.setCustomers(List.of(cust));

        return c;
    }

    // ========== new tests for task 9 ==========

    @Test
    void shouldDiscountOnlyFutureRepaymentPeriods() {
        AssetInput asset = assetWithSchedule("LN_001", "FAC_001");
        asset.setCalcDate(LocalDate.of(2026, 6, 24));
        asset.setRepaymentSchedules(List.of(
                schedule(LocalDate.of(2026, 1, 1), 100, 10),
                schedule(LocalDate.of(2027, 1, 1), 100, 10)
        ));

        engine.execute(ctxWithFacility(asset));

        assertTrue(asset.getOnBsEad() > 0);
        assertTrue(asset.getEadBreakdown().contains("\"futurePeriods\":1"));
    }

    @Test
    void shouldDiscountUsingAssetsOwnInterestRateWhenPresent() {
        // 借据自己利率(3%)明显低于方案统一折现率(ctxWithFacility里的5%)，
        // 折出来的表内EAD应该按3%算，不是5%——验证"优先借据自己利率"生效。
        AssetInput asset = assetWithSchedule("LN_001", "FAC_001");
        asset.setCalcDate(LocalDate.of(2026, 6, 24));
        asset.setInterestRate(BigDecimal.valueOf(0.03));
        asset.setRepaymentSchedules(List.of(
                schedule(LocalDate.of(2027, 6, 24), 1000, 0)
        ));

        engine.execute(ctxWithFacility(asset));

        double expected = 1000 / Math.pow(1.03, 1.0);
        assertEquals(expected, asset.getOnBsEad(), 0.5);
        assertTrue(asset.getEadBreakdown().contains("\"discountRate\":0.03"));
    }

    @Test
    void shouldNormalizePercentageStyleInterestRate() {
        // 源数据"利率(%)"字段存的是4.85这种百分数写法(不是0.0485)，
        // 必须先归一化成小数再折现，否则会被当成485%导致EAD被压得极低——
        // 这是2026-07-17实测UAT-L002时真实出现过的回归，此处锁定防止再犯。
        AssetInput asset = assetWithSchedule("LN_003", "FAC_001");
        asset.setCalcDate(LocalDate.of(2026, 6, 24));
        asset.setInterestRate(BigDecimal.valueOf(4.85));
        asset.setRepaymentSchedules(List.of(
                schedule(LocalDate.of(2027, 6, 24), 1000, 0)
        ));

        engine.execute(ctxWithFacility(asset));

        double expected = 1000 / Math.pow(1.0485, 1.0);
        assertEquals(expected, asset.getOnBsEad(), 0.5);
        // 4.85/100.0 在浮点下不是精确的0.0485(是0.048499999999999995)，
        // 断言用数值容差比对，不做字符串精确匹配
        assertTrue(asset.getEadBreakdown().contains("\"discountRate\":0.0484"));
    }

    @Test
    void shouldFallBackToSchemeDiscountRateWhenAssetHasNoInterestRate() {
        // 借据没填自己的利率(interestRate=null)，应退回方案统一折现率(5%)，不是0或报错。
        AssetInput asset = assetWithSchedule("LN_002", "FAC_001");
        asset.setCalcDate(LocalDate.of(2026, 6, 24));
        asset.setRepaymentSchedules(List.of(
                schedule(LocalDate.of(2027, 6, 24), 1000, 0)
        ));

        engine.execute(ctxWithFacility(asset));

        double expected = 1000 / Math.pow(1.05, 1.0);
        assertEquals(expected, asset.getOnBsEad(), 0.5);
        assertTrue(asset.getEadBreakdown().contains("\"discountRate\":0.05"));
    }

    @Test
    void shouldAllocateFacilityUndrawnAmountByAmtFinancedShare() {
        AssetInput a1 = asset("LN_001", "FAC_001", 600);
        AssetInput a2 = asset("LN_002", "FAC_001", 400);
        JobContext ctx = ctxWithFacility(a1, a2);

        engine.execute(ctx);

        assertEquals(60.0, a1.getOffBsEad(), 0.01);
        assertEquals(40.0, a2.getOffBsEad(), 0.01);
    }

    @Test void shouldCalcOnBsEadOnlyWhenNoUndrawn() {
        when(ccfMapper.selectList(any())).thenReturn(Collections.emptyList());
        AssetInput a = asset("公司贷款", "承诺", 500, 10, 500); // 全额提取
        engine.execute(ctx("SCH_001", 0.5, a));
        assertEquals(510.0, a.getTotalEad(), 0.01);
        assertEquals(510.0, a.getOnBsEad(), 0.01);
        assertEquals(0.0, a.getOffBsEad(), 0.01);
    }

    @Test void shouldCalcOnAndOffBsEad() {
        CcfCurveEntity c = ccf("公司贷款", "承诺", 0.5);
        when(ccfMapper.selectList(any())).thenReturn(List.of(c));
        AssetInput a = asset("公司贷款", "承诺", 500, 10, 1000); // 500 未提取
        engine.execute(ctx("SCH_001", 0.0, a));
        assertEquals(510.0, a.getOnBsEad(), 0.01);
        assertEquals(250.0, a.getOffBsEad(), 0.01); // 500 × 0.5
        assertEquals(760.0, a.getTotalEad(), 0.01);
    }

    @Test void shouldUseDefaultCcfWhenCurveMissing() {
        when(ccfMapper.selectList(any())).thenReturn(Collections.emptyList());
        AssetInput a = asset("公司贷款", "承诺", 500, 10, 1000);
        engine.execute(ctx("SCH_001", 0.3, a)); // defaultCcf=0.3
        assertEquals(150.0, a.getOffBsEad(), 0.01); // 500 × 0.3
        assertEquals(660.0, a.getTotalEad(), 0.01);
    }

    @Test void shouldHandleNullBalanceAndLimit() {
        when(ccfMapper.selectList(any())).thenReturn(Collections.emptyList());
        AssetInput a = asset("公司贷款", "承诺", 0, 0, 0);
        engine.execute(ctx("SCH_001", 0.5, a));
        assertEquals(0.0, a.getTotalEad(), 0.01);
    }

    @Test void shouldHandleCurveMismatchProduct() {
        CcfCurveEntity c = ccf("银团贷款", "承诺", 0.5);
        when(ccfMapper.selectList(any())).thenReturn(List.of(c));
        AssetInput a = asset("公司贷款", "承诺", 500, 10, 1000); // 不同 product
        engine.execute(ctx("SCH_001", 0.2, a));
        assertEquals(100.0, a.getOffBsEad(), 0.01); // 500 × 0.2 (default)
    }

    @Test
    void shouldCalculateOffBalanceBusinessOnlyFromUnusedFacility() {
        AssetInput asset = asset("LN_001", "FAC_001", 1000);
        asset.setBusinessType("OFF_BS");
        asset.setOutstandingBalance(BigDecimal.valueOf(500));
        asset.setAccruedInterest(BigDecimal.valueOf(10));

        engine.execute(ctxWithFacility(asset));

        assertEquals(0.0, asset.getOnBsEad(), 0.01);
        assertEquals(100.0, asset.getOffBsEad(), 0.01);
        assertEquals(100.0, asset.getTotalEad(), 0.01);
    }

    @Test
    void shouldUseDefaultCcfWhenProductOrCommitmentTypeMissing() {
        CcfCurveEntity c = ccf("公司贷款", "承诺", 0.5);
        when(ccfMapper.selectList(any())).thenReturn(List.of(c));
        AssetInput a = asset(null, null, 0, 0, 1000);

        engine.execute(ctx("SCH_001", 0.2, a));

        assertEquals(200.0, a.getOffBsEad(), 0.01);
    }
}
