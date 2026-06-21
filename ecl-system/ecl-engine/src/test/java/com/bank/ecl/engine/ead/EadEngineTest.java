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
}
