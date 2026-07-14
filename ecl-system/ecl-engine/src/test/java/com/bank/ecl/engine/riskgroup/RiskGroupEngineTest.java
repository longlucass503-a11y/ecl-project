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

    private AssetInput asset(String segment, String customerType,
                             String productType, String industryCode,
                             String regionCode, String collateralType) {
        AssetInput a = new AssetInput();
        a.setAssetId("AST_001");
        a.setCustomerId("CUST_001");
        a.setSegment(segment);
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

    private AssetInput asset(String segment, String productType,
                             String industryCode, String collateralType) {
        AssetInput a = new AssetInput();
        a.setAssetId("AST_001");
        a.setCustomerId("CUST_001");
        a.setSegment(segment);
        a.setProductType(productType);
        a.setIndustryCode(industryCode);
        a.setCollateralType(collateralType);
        return a;
    }

    private RiskGroupDetailEntity detail(String groupId, int priority,
                                         String segment, String productType) {
        RiskGroupDetailEntity d = new RiskGroupDetailEntity();
        d.setGroupId(groupId);
        d.setPriority(priority);
        d.setSegment(segment);
        d.setProductType(productType);
        return d;
    }

    private RiskGroupDetailEntity detail(String groupId, int priority,
                                         String segment, String productType,
                                         String industryCode, String collateralType) {
        RiskGroupDetailEntity d = new RiskGroupDetailEntity();
        d.setGroupId(groupId);
        d.setPriority(priority);
        d.setSegment(segment);
        d.setProductType(productType);
        d.setIndustryCode(industryCode);
        d.setCollateralType(collateralType);
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

    @Test
    void shouldNotMatchNonBlankRuleWhenAssetValueBlank() {
        // given
        String schemeId = "SCH_001";
        RiskGroupDetailEntity rule = detail("GRP_001", 1, "2 Loan", "公司贷款", "制造业", "房产抵押");
        when(detailMapper.selectList(any())).thenReturn(List.of(rule));
        when(groupMapper.selectList(any())).thenReturn(List.of(group("GRP_001", "贷款")));

        AssetInput asset = asset("2 Loan", "公司贷款", null, "房产抵押");
        JobContext ctx = jobCtx(schemeId, List.of(asset));

        // when
        engine.execute(ctx);

        // then — industryCode=null in asset, but rule has "制造业" → should NOT match
        assertEquals("GRP_DEFAULT", asset.getGroupId());
    }

    @Test
    void shouldIgnoreCustomerTypeAndRegionCode() {
        // given
        String schemeId = "SCH_001";
        RiskGroupDetailEntity rule = detail("GRP_001", 1, "2 Loan", "公司贷款", "制造业", "房产抵押");
        when(detailMapper.selectList(any())).thenReturn(List.of(rule));
        when(groupMapper.selectList(any())).thenReturn(List.of(group("GRP_001", "贷款")));

        AssetInput asset = asset("2 Loan", "公司贷款", "制造业", "房产抵押");
        asset.setCustomerType("不应参与");
        asset.setRegionCode("不应参与");
        JobContext ctx = jobCtx(schemeId, List.of(asset));

        // when
        engine.execute(ctx);

        // then — customerType/regionCode should be completely ignored; only 4 dims matter
        assertEquals("GRP_001", asset.getGroupId());
    }

    @Test
    void shouldSelectGroupBySortOrderWhenMultipleMatch() {
        // 多个规则匹配时按分组 sortOrder 选最优，而非规则 priority
        String schemeId = "SCH_001";
        RiskGroupDetailEntity rule1 = detail("GRP_001", 1, "非零售", "公司贷款");
        RiskGroupDetailEntity rule2 = detail("GRP_002", 2, "非零售", "公司贷款");
        RiskGroupEntity group1 = group("GRP_001", "对公业务");
        group1.setSortOrder(10);
        RiskGroupEntity group2 = group("GRP_002", "战略客户");
        group2.setSortOrder(5);
        when(detailMapper.selectList(any())).thenReturn(List.of(rule1, rule2));
        when(groupMapper.selectList(any())).thenReturn(List.of(group1, group2));

        AssetInput asset = asset("非零售", "对公", "公司贷款", "J", "110000", "房产");
        engine.execute(jobCtx(schemeId, List.of(asset)));

        assertEquals("GRP_002", asset.getGroupId()); // sortOrder=5 < 10
        assertEquals("战略客户", asset.getGroupName());
        assertNull(asset.getGroupException());
    }

    @Test
    void shouldMatchAsteriskAsWildcard() {
        // 规则值 "*" 应视为通配
        String schemeId = "SCH_001";
        RiskGroupDetailEntity rule = detail("GRP_001", 1, "非零售", "*");
        when(detailMapper.selectList(any())).thenReturn(List.of(rule));
        when(groupMapper.selectList(any())).thenReturn(List.of(group("GRP_001", "对公")));

        AssetInput asset = asset("非零售", "对公", "个消费贷", "J", "110000", "房产");
        engine.execute(jobCtx(schemeId, List.of(asset)));

        assertEquals("GRP_001", asset.getGroupId());
    }

    @Test
    void shouldSetGroupCodeWhenGroupMatch() {
        // 命中分组后应补充 groupCode
        String schemeId = "SCH_001";
        RiskGroupDetailEntity rule = detail("GRP_001", 1, "非零售", "公司贷款");
        RiskGroupEntity group = group("GRP_001", "对公业务");
        group.setGroupCode("GRP_003");
        when(detailMapper.selectList(any())).thenReturn(List.of(rule));
        when(groupMapper.selectList(any())).thenReturn(List.of(group));

        AssetInput asset = asset("非零售", "对公", "公司贷款", "J", "110000", "房产");
        engine.execute(jobCtx(schemeId, List.of(asset)));

        assertEquals("GRP_003", asset.getGroupCode());
    }

}
