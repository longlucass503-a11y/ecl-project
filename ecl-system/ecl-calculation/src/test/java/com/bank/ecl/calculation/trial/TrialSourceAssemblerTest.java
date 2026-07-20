package com.bank.ecl.calculation.trial;

import com.bank.ecl.calculation.trial.dto.TrialCalculationReq;
import com.bank.ecl.calculation.trial.dto.TrialCollateralRowReq;
import com.bank.ecl.calculation.trial.dto.TrialFacilityRowReq;
import com.bank.ecl.calculation.trial.dto.TrialHistoricalStageRowReq;
import com.bank.ecl.calculation.trial.dto.TrialLoanRowReq;
import com.bank.ecl.calculation.trial.dto.TrialRatingRowReq;
import com.bank.ecl.calculation.trial.dto.TrialRepaymentRowReq;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.engine.core.AssetInput;
import com.bank.ecl.engine.core.CollateralInput;
import com.bank.ecl.engine.core.CustomerContext;
import com.bank.ecl.engine.core.FacilityInput;
import com.bank.ecl.engine.core.JobContext;
import com.bank.ecl.engine.core.Stage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialSourceAssemblerTest {

    private final TrialSourceAssembler assembler = new TrialSourceAssembler();

    @Test
    void shouldRejectTrialWithoutLoanRows() {
        TrialCalculationReq req = new TrialCalculationReq();
        req.setLoans(List.of());

        EclException ex = assertThrows(EclException.class,
                () -> assembler.assemble("JOB_001", "SCH_001", LocalDate.of(2026, 6, 24), req,
                        0.05, 0.0, 0.45, 0.1));

        assertTrue(ex.getMessage().contains("借据信息表不能为空"));
    }

    @Test
    void shouldAssembleSourceTableRowsIntoEngineContext() {
        LocalDate calcDate = LocalDate.of(2026, 6, 24);
        TrialCalculationReq req = new TrialCalculationReq();
        req.setSchemeId("SCH_001");
        req.setCalcDate(calcDate);

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
        loan.setLoanClassifCd("正常");
        loan.setNormalConsecutiveDays(200);
        loan.setBusinessType("ON_BS");
        req.setLoans(List.of(loan));

        TrialFacilityRowReq facility = new TrialFacilityRowReq();
        facility.setFacilityCd("FAC_001");
        facility.setCollateralPoolId("POOL_001");
        facility.setLimitAmtCny(new BigDecimal("1200000"));
        facility.setIsRevolving("Y");
        facility.setFacilityStartDate(LocalDate.of(2025, 1, 1));
        facility.setFacilityMaturityDate(LocalDate.of(2028, 1, 1));
        req.setFacilities(List.of(facility));

        TrialCollateralRowReq collateral = new TrialCollateralRowReq();
        collateral.setCollateralPoolCode("POOL_001");
        collateral.setCollateralType("房产抵押");
        req.setCollaterals(List.of(collateral));

        TrialRatingRowReq rating = new TrialRatingRowReq();
        rating.setCifNo("CUST_001");
        rating.setCrrFinal("CRR5");
        rating.setExtRatingCoThisYear("MOODY");
        req.setRatings(List.of(rating));

        TrialRepaymentRowReq repayment = new TrialRepaymentRowReq();
        repayment.setLoanReceiptNo("LN_001");
        repayment.setPeriodNo(1);
        repayment.setDueDate(LocalDate.of(2026, 12, 24));
        req.setRepaymentSchedules(List.of(repayment));

        TrialHistoricalStageRowReq historicalStage = new TrialHistoricalStageRowReq();
        historicalStage.setAssetId("LN_001");
        historicalStage.setCalcDate(calcDate);
        historicalStage.setStageResult("STAGE_2");
        req.setHistoricalStages(List.of(historicalStage));

        JobContext ctx = assembler.assemble("JOB_001", "SCH_001", calcDate, req,
                0.05, 0.0, 0.45, 0.1);

        assertEquals("JOB_001", ctx.getJobId());
        assertEquals("SCH_001", ctx.getSchemeId());
        assertEquals(calcDate, ctx.getCalcDate());
        assertTrue(ctx.isTrialMode());
        assertEquals(0.1, ctx.getLgdFloor());

        AssetInput actualAsset = ctx.getCustomers().get(0).getAssets().get(0);
        assertEquals("LN_001", actualAsset.getAssetId());
        assertEquals("2 Loan", actualAsset.getSegment());
        assertEquals("公司贷款", actualAsset.getProductType());
        assertEquals("制造业", actualAsset.getIndustryCode());
        assertEquals("房产抵押", actualAsset.getCollateralType());
        assertEquals("N", actualAsset.getIsNpl());
        assertEquals("正常", actualAsset.getFiveCategory());
        assertEquals("CRR5", actualAsset.getCrrFinal());
        assertEquals("MOODY", actualAsset.getExtRatingCoThisYear());
        assertEquals("FAC_001", actualAsset.getFacilityCd());
        assertEquals("POOL_001", actualAsset.getCollateralPoolId());
        assertEquals(new BigDecimal("1200000"), actualAsset.getTotalLimit());
        assertEquals(new BigDecimal("0.045"), actualAsset.getInterestRate());
        assertEquals("不可撤销", actualAsset.getCommitmentType());
        assertTrue(actualAsset.getCommitmentDays() > 0);
        assertEquals(Stage.STAGE_2, actualAsset.getLastStage());
        assertEquals(1, actualAsset.getRepaymentSchedules().size());

        assertEquals(1, ctx.getFacilities().size());
        assertEquals(1, ctx.getCollateralsByPool().get("POOL_001").size());
    }

    @Test
    void shouldExposeExpandedEngineContextFieldsForTrialSourceMapping() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("LN_001");
        asset.setSegment("2 Loan");
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
        assertEquals("2 Loan", actualAsset.getSegment());
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
