package com.bank.ecl.engine.lgd;

import com.bank.ecl.data.entity.LgdCurveEntity;
import com.bank.ecl.data.mapper.LgdCurveMapper;
import com.bank.ecl.engine.core.*;
import org.junit.jupiter.api.BeforeEach; import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock; import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Collections; import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LgdEngineTest {
    @Mock private LgdCurveMapper lgdCurveMapper;
    private LgdEngine engine;
    @BeforeEach void setUp() { engine = new LgdEngine(lgdCurveMapper); }

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

    @Test void shouldMatchExactCurve() {
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
        AssetInput a = asset("GRP_001", "信用", "公司贷款"); // "信用" 无匹配 → NONE
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
}
