package com.bank.ecl.engine.lgd;

import com.bank.ecl.data.entity.LgdCollateralDiscountEntity;
import com.bank.ecl.data.entity.LgdCurveEntity;
import com.bank.ecl.data.entity.LgdDepreciationEntity;
import com.bank.ecl.data.mapper.LgdCollateralDiscountMapper;
import com.bank.ecl.data.mapper.LgdCurveMapper;
import com.bank.ecl.data.mapper.LgdDepreciationMapper;
import com.bank.ecl.engine.core.*;
import org.junit.jupiter.api.BeforeEach; import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock; import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Collections; import java.util.List; import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LgdEngineTest {
    @Mock private LgdCurveMapper lgdCurveMapper;
    @Mock private LgdCollateralDiscountMapper discountMapper;
    @Mock private LgdDepreciationMapper depreciationMapper;
    private LgdEngine engine;
    @BeforeEach void setUp() { engine = new LgdEngine(lgdCurveMapper, discountMapper, depreciationMapper); }

    private AssetInput asset(String groupId, String collateralType, String productType) {
        AssetInput a = new AssetInput();
        a.setAssetId("AST_001"); a.setGroupId(groupId);
        a.setCollateralType(collateralType); a.setProductType(productType);
        return a;
    }

    private JobContext ctx(String schemeId, double defaultLgd, AssetInput a) {
        JobContext c = new JobContext(); c.setSchemeId(schemeId); c.setDefaultLgd(defaultLgd);
        CustomerContext cust = new CustomerContext(); cust.setCustomerId("CUST_001");
        cust.setAssets(List.of(a)); c.setCustomers(List.of(cust)); return c;
    }

    private LgdCurveEntity curve(String groupId, String collType, String prodType, double value) {
        LgdCurveEntity e = new LgdCurveEntity();
        e.setGroupId(groupId); e.setCollateralType(collType); e.setProductType(prodType);
        e.setLgdBaseValue(BigDecimal.valueOf(value)); return e;
    }

    private LgdCollateralDiscountEntity discount(String collateralCategory, String collateralType, double rate) {
        LgdCollateralDiscountEntity e = new LgdCollateralDiscountEntity();
        e.setCollateralCategory(collateralCategory);
        e.setCollateralType(collateralType);
        e.setDiscountRate(BigDecimal.valueOf(rate));
        return e;
    }

    private LgdDepreciationEntity depreciation(String collateralType, int yearOffset, double rate) {
        LgdDepreciationEntity e = new LgdDepreciationEntity();
        e.setCollateralType(collateralType);
        e.setYearOffset(yearOffset);
        e.setDepreciationRate(BigDecimal.valueOf(rate));
        return e;
    }

    private CollateralInput collateral(String collateralCategory, String collateralType, double appraisalValue) {
        CollateralInput c = new CollateralInput();
        c.setCollateralType(collateralType);
        c.setCollateralCategory(collateralCategory);
        c.setAppraisalValue(BigDecimal.valueOf(appraisalValue));
        return c;
    }

    @Test void shouldMatchExactCurve() {
        // 无押品池资产按贷款自己声明的担保方式(collateralType)查曲线
        LgdCurveEntity c = curve("GRP_001", "房产", "公司贷款", 0.35);
        when(lgdCurveMapper.selectList(any())).thenReturn(List.of(c));
        AssetInput a = asset("GRP_001", "房产", "公司贷款");
        engine.execute(ctx("SCH_001", 0.45, a));
        assertEquals(0.35, a.getLgdValue(), 0.0001);
        assertNull(a.getLgdException());
    }

    @Test void shouldFallbackToNoneWhenExactMissing() {
        LgdCurveEntity c = curve("GRP_001", "NONE", "公司贷款", 0.40);
        when(lgdCurveMapper.selectList(any())).thenReturn(List.of(c));
        AssetInput a = asset("GRP_001", "信用", "公司贷款"); // 曲线没有"信用"这一行，应退化到NONE
        engine.execute(ctx("SCH_001", 0.45, a));
        assertEquals(0.40, a.getLgdValue(), 0.0001);
    }

    @Test void shouldUseDefaultLgdWhenAllMissing() {
        when(lgdCurveMapper.selectList(any())).thenReturn(Collections.emptyList());
        AssetInput a = asset("GRP_001", "信用", "公司贷款");
        engine.execute(ctx("SCH_001", 0.45, a));
        assertEquals(0.45, a.getLgdValue(), 0.0001);
        assertEquals("WARN", a.getLgdException());
    }

    @Test void shouldHandleNullCollateralType() {
        LgdCurveEntity c = curve("GRP_001", "NONE", "公司贷款", 0.40);
        when(lgdCurveMapper.selectList(any())).thenReturn(List.of(c));
        AssetInput a = asset("GRP_001", null, "公司贷款");
        engine.execute(ctx("SCH_001", 0.45, a));
        assertEquals(0.40, a.getLgdValue(), 0.0001);
    }

    @Test
    void shouldCalculatePoolLgdFromCollateralNetValueAndPoolEad() {
        AssetInput asset = asset("GRP_001", "房产抵押", "公司贷款");
        asset.setCollateralPoolId("POOL_001");
        asset.setTotalEad(1000.0);

        JobContext ctx = ctx("SCH_001", 0.45, asset);
        ctx.setLgdFloor(0.10);
        ctx.setCollateralsByPool(Map.of("POOL_001", List.of(collateral("房产", "住宅", 800.0))));

        when(lgdCurveMapper.selectList(any())).thenReturn(List.of(curve("GRP_001", "房产抵押", "公司贷款", 0.45)));
        when(discountMapper.selectList(any())).thenReturn(List.of(discount("房产", "住宅", 0.20)));
        when(depreciationMapper.selectList(any())).thenReturn(List.of(depreciation("住宅", 0, 0.0)));

        engine.execute(ctx);

        assertEquals(0.226, asset.getLgdValue(), 0.01);
    }
}
