package com.bank.ecl.engine.riskgroup;

import com.bank.ecl.data.entity.RiskGroupDetailEntity;
import com.bank.ecl.data.entity.RiskGroupEntity;
import com.bank.ecl.data.mapper.RiskGroupDetailMapper;
import com.bank.ecl.data.mapper.RiskGroupMapper;
import com.bank.ecl.engine.core.AssetInput;
import com.bank.ecl.engine.core.CustomerContext;
import com.bank.ecl.engine.core.JobContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskGroupEngineTest {

    @Mock
    private RiskGroupDetailMapper detailMapper;

    @Mock
    private RiskGroupMapper groupMapper;

    private RiskGroupEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RiskGroupEngine(detailMapper, groupMapper);
    }

    // ---- helpers ----

    private AssetInput asset(String businessLine, String customerType,
                             String productType, String industryCode,
                             String regionCode, String collateralType) {
        AssetInput a = new AssetInput();
        a.setAssetId("AST_001");
        a.setCustomerId("CUST_001");
        a.setBusinessLine(businessLine);
        a.setCustomerType(customerType);
        a.setProductType(productType);
        a.setIndustryCode(industryCode);
        a.setRegionCode(regionCode);
        a.setCollateralType(collateralType);
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

    private RiskGroupDetailEntity detail(String groupId, int priority,
                                         String businessLine, String productType) {
        RiskGroupDetailEntity d = new RiskGroupDetailEntity();
        d.setGroupId(groupId);
        d.setPriority(priority);
        d.setBusinessLine(businessLine);
        d.setProductType(productType);
        return d;
    }

    private RiskGroupEntity group(String groupId, String groupName) {
        RiskGroupEntity g = new RiskGroupEntity();
        g.setGroupId(groupId);
        g.setGroupName(groupName);
        return g;
    }

    @Test
    void shouldUseDefaultGroupWhenNoRules() {
        // given
        String schemeId = "SCH_001";
        when(detailMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(groupMapper.selectList(any())).thenReturn(Collections.emptyList());

        AssetInput asset = asset("非零售", "对公", "公司贷款", "J", "110000", "房产");
        JobContext ctx = jobCtx(schemeId, List.of(asset));

        // when
        engine.execute(ctx);

        // then
        assertEquals("GRP_DEFAULT", asset.getGroupId());
        assertEquals("Y", asset.getGroupException());
        assertNull(asset.getGroupName());
    }

    @Test
    void shouldMatchExactRule() {
        // given
        String schemeId = "SCH_001";
        RiskGroupDetailEntity rule = detail("GRP_001", 1, "非零售", "公司贷款");
        when(detailMapper.selectList(any())).thenReturn(List.of(rule));
        when(groupMapper.selectList(any())).thenReturn(List.of(group("GRP_001", "对公业务")));

        AssetInput asset = asset("非零售", "对公", "公司贷款", "J", "110000", "房产");
        JobContext ctx = jobCtx(schemeId, List.of(asset));

        // when
        engine.execute(ctx);

        // then
        assertEquals("GRP_001", asset.getGroupId());
        assertEquals("对公业务", asset.getGroupName());
        assertNull(asset.getGroupException());

        verify(detailMapper).selectList(any());
        verify(groupMapper).selectList(any());
    }

    @Test
    void shouldMatchWithWildcardDimension() {
        // given
        String schemeId = "SCH_001";
        // 规则中 productType=NULL → 通配
        RiskGroupDetailEntity rule = detail("GRP_001", 1, "非零售", null);
        when(detailMapper.selectList(any())).thenReturn(List.of(rule));
        when(groupMapper.selectList(any())).thenReturn(List.of(group("GRP_001", "对公业务")));

        AssetInput asset = asset("非零售", "对公", "公司贷款", "J", "110000", "房产");
        JobContext ctx = jobCtx(schemeId, List.of(asset));

        // when
        engine.execute(ctx);

        // then
        assertEquals("GRP_001", asset.getGroupId());
        assertNull(asset.getGroupException());
    }

    @Test
    void shouldMatchFirstRuleByPriority() {
        // given
        String schemeId = "SCH_001";
        RiskGroupDetailEntity rule1 = detail("GRP_001", 1, "非零售", null);
        RiskGroupDetailEntity rule2 = detail("GRP_002", 2, "非零售", null);
        when(detailMapper.selectList(any())).thenReturn(List.of(rule1, rule2));
        when(groupMapper.selectList(any())).thenReturn(List.of(
                group("GRP_001", "对公业务"),
                group("GRP_002", "非银金融业务")));

        AssetInput asset = asset("非零售", "对公", "公司贷款", "J", "110000", "房产");
        JobContext ctx = jobCtx(schemeId, List.of(asset));

        // when
        engine.execute(ctx);

        // then
        assertEquals("GRP_001", asset.getGroupId()); // 命中 priority=1 的
        assertEquals("对公业务", asset.getGroupName());
        assertNull(asset.getGroupException());
    }

    @Test
    void shouldUseDefaultGroupWhenNoRuleMatches() {
        // given
        String schemeId = "SCH_001";
        RiskGroupDetailEntity rule = detail("GRP_001", 1, "零售", "个消费贷");
        when(detailMapper.selectList(any())).thenReturn(List.of(rule));
        when(groupMapper.selectList(any())).thenReturn(List.of(group("GRP_001", "个人消费贷款")));

        // asset 的维度与规则完全不同
        AssetInput asset = asset("非零售", "对公", "公司贷款", "J", "110000", "房产");
        JobContext ctx = jobCtx(schemeId, List.of(asset));

        // when
        engine.execute(ctx);

        // then
        assertEquals("GRP_DEFAULT", asset.getGroupId());
        assertEquals("Y", asset.getGroupException());
    }

    @Test
    void shouldHandleMultipleAssetsWithMixedMatches() {
        // given
        String schemeId = "SCH_001";
        RiskGroupDetailEntity rule = detail("GRP_001", 1, "非零售", "公司贷款");
        when(detailMapper.selectList(any())).thenReturn(List.of(rule));
        when(groupMapper.selectList(any())).thenReturn(List.of(group("GRP_001", "对公业务")));

        AssetInput matchAsset = asset("非零售", "对公", "公司贷款", "J", "110000", "房产");
        AssetInput noMatchAsset = asset("零售", "个人", "个消费贷", "X", "510000", "信用");
        JobContext ctx = jobCtx(schemeId, List.of(matchAsset, noMatchAsset));

        // when
        engine.execute(ctx);

        // then
        assertEquals("GRP_001", matchAsset.getGroupId());
        assertEquals("对公业务", matchAsset.getGroupName());
        assertNull(matchAsset.getGroupException());

        assertEquals("GRP_DEFAULT", noMatchAsset.getGroupId());
        assertEquals("Y", noMatchAsset.getGroupException());
    }

    @Test
    void shouldHandleNullAssets() {
        // given
        String schemeId = "SCH_001";
        when(detailMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(groupMapper.selectList(any())).thenReturn(Collections.emptyList());

        JobContext ctx = new JobContext();
        ctx.setSchemeId(schemeId);
        CustomerContext customer = new CustomerContext();
        customer.setCustomerId("CUST_001");
        customer.setAssets(null);  // null assets
        ctx.setCustomers(List.of(customer));

        // when - should not throw
        assertDoesNotThrow(() -> engine.execute(ctx));
    }
}
