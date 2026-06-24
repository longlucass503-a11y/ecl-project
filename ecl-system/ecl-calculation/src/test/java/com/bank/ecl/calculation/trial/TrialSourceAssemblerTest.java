package com.bank.ecl.calculation.trial;

import com.bank.ecl.calculation.trial.dto.TrialCalculationReq;
import com.bank.ecl.calculation.trial.dto.TrialLoanRowReq;
import com.bank.ecl.engine.core.AssetInput;
import com.bank.ecl.engine.core.CollateralInput;
import com.bank.ecl.engine.core.CustomerContext;
import com.bank.ecl.engine.core.FacilityInput;
import com.bank.ecl.engine.core.JobContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrialSourceAssemblerTest {

    @Test
    void shouldAcceptSourceTableRowsInTrialRequest() {
        TrialCalculationReq req = new TrialCalculationReq();
        req.setSchemeId("SCH_001");
        req.setCalcDate(LocalDate.of(2026, 6, 24));

        TrialLoanRowReq loan = new TrialLoanRowReq();
        loan.setId("LN_001");
        loan.setFacilityCd("FAC_001");
        loan.setCustomerNo("CUST_001");
        loan.setCustomerName("客户A");
        loan.setSegment("2 Loan");
        loan.setProductType("公司贷款");
        loan.setIndustryCn("制造业");
        loan.setGuaranteeType("房产抵押");
        loan.setCurrencyCd("CNY");
        loan.setAmtFinancedCny(new BigDecimal("1000000"));
        loan.setLoanBalCny(new BigDecimal("800000"));
        loan.setIntAccruedCny(new BigDecimal("1000"));
        loan.setInterestRate(new BigDecimal("0.045"));
        loan.setLoanStartDt(LocalDate.of(2025, 1, 1));
        loan.setLoanMaturityDt(LocalDate.of(2028, 1, 1));
        loan.setOverdueDays(0);
        loan.setIsNpl("N");
        loan.setNormalConsecutiveDays(200);
        loan.setBusinessType("ON_BS");

        req.setLoans(List.of(loan));

        assertEquals("LN_001", req.getLoans().get(0).getId());
        assertEquals("房产抵押", req.getLoans().get(0).getGuaranteeType());
    }

    @Test
    void shouldExposeExpandedEngineContextFieldsForTrialSourceMapping() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("LN_001");
        asset.setBusinessLine("2 Loan");
        asset.setIndustryCode("制造业");
        asset.setCollateralType("房产抵押");
        asset.setIsNpl("N");
        asset.setCrrFinal("CRR5");
        asset.setExtRatingCoThisYear("MOODY");
        asset.setFacilityCd("FAC_001");
        asset.setCollateralPoolId("POOL_001");

        CustomerContext customer = new CustomerContext();
        customer.setAssets(List.of(asset));

        FacilityInput facility = new FacilityInput();
        facility.setFacilityCd("FAC_001");
        facility.setCollateralPoolId("POOL_001");

        CollateralInput collateral = new CollateralInput();
        collateral.setCollateralPoolCode("POOL_001");

        JobContext ctx = new JobContext();
        ctx.setCustomers(List.of(customer));
        ctx.setFacilities(new ArrayList<>(List.of(facility)));
        ctx.setCollateralsByPool(new HashMap<>());
        ctx.getCollateralsByPool().put("POOL_001", List.of(collateral));

        AssetInput actualAsset = ctx.getCustomers().get(0).getAssets().get(0);
        assertEquals("LN_001", actualAsset.getAssetId());
        assertEquals("2 Loan", actualAsset.getBusinessLine());
        assertEquals("制造业", actualAsset.getIndustryCode());
        assertEquals("房产抵押", actualAsset.getCollateralType());
        assertEquals("N", actualAsset.getIsNpl());
        assertEquals("CRR5", actualAsset.getCrrFinal());
        assertEquals("MOODY", actualAsset.getExtRatingCoThisYear());
        assertEquals("FAC_001", actualAsset.getFacilityCd());
        assertEquals("POOL_001", actualAsset.getCollateralPoolId());

        assertEquals(1, ctx.getFacilities().size());
        assertEquals(1, ctx.getCollateralsByPool().get("POOL_001").size());
    }
}
