package com.bank.ecl.calculation.trial;

import com.bank.ecl.calculation.trial.dto.TrialCalculationReq;
import com.bank.ecl.calculation.trial.dto.TrialCollateralRowReq;
import com.bank.ecl.calculation.trial.dto.TrialFacilityRowReq;
import com.bank.ecl.calculation.trial.dto.TrialHistoricalStageRowReq;
import com.bank.ecl.calculation.trial.dto.TrialLoanRowReq;
import com.bank.ecl.calculation.trial.dto.TrialRatingRowReq;
import com.bank.ecl.calculation.trial.dto.TrialRepaymentRowReq;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.engine.core.AssetInput;
import com.bank.ecl.engine.core.CollateralInput;
import com.bank.ecl.engine.core.CustomerContext;
import com.bank.ecl.engine.core.FacilityInput;
import com.bank.ecl.engine.core.JobContext;
import com.bank.ecl.engine.core.RepaymentScheduleInput;
import com.bank.ecl.engine.core.Stage;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TrialSourceAssembler {

    public JobContext assemble(String jobId, String schemeId, LocalDate calcDate, TrialCalculationReq req,
                               double discountRate, double defaultCcf, double defaultLgd, double lgdFloor) {
        if (req.getLoans() == null || req.getLoans().isEmpty()) {
            throw new EclException(ErrorCode.ECL_005, "借据信息表不能为空");
        }

        JobContext ctx = buildContext(jobId, schemeId, calcDate, discountRate, defaultCcf, defaultLgd, lgdFloor);
        ctx.setFacilities(mapFacilities(req.getFacilities()));
        ctx.setCollateralsByPool(groupCollaterals(req.getCollaterals()));

        Map<String, FacilityInput> facilitiesByCode = ctx.getFacilities().stream()
                .filter(f -> f.getFacilityCd() != null)
                .collect(Collectors.toMap(FacilityInput::getFacilityCd, Function.identity(), (left, right) -> left));
        Map<String, TrialRatingRowReq> ratingsByCustomer = mapRatings(req.getRatings());
        Map<String, List<RepaymentScheduleInput>> repaymentsByLoan = groupRepayments(req.getRepaymentSchedules());
        Map<String, Stage> historicalStagesByAsset = mapHistoricalStages(req.getHistoricalStages(), calcDate);

        List<AssetInput> assets = req.getLoans().stream()
                .map(loan -> mapAsset(loan, calcDate, facilitiesByCode, ratingsByCustomer,
                        repaymentsByLoan, historicalStagesByAsset))
                .collect(Collectors.toList());

        CustomerContext customer = new CustomerContext();
        customer.setCustomerId(assets.get(0).getCustomerId());
        customer.setAssets(assets);
        ctx.setCustomers(List.of(customer));
        return ctx;
    }

    private JobContext buildContext(String jobId, String schemeId, LocalDate calcDate, double discountRate,
                                    double defaultCcf, double defaultLgd, double lgdFloor) {
        JobContext ctx = new JobContext();
        ctx.setJobId(jobId);
        ctx.setSchemeId(schemeId);
        ctx.setCalcDate(calcDate);
        ctx.setTrialMode(true);
        ctx.setDiscountRate(discountRate);
        ctx.setDefaultCcf(defaultCcf);
        ctx.setDefaultLgd(defaultLgd);
        ctx.setLgdFloor(lgdFloor);
        return ctx;
    }

    private List<FacilityInput> mapFacilities(List<TrialFacilityRowReq> rows) {
        if (rows == null) {
            return new ArrayList<>();
        }
        return rows.stream().map(this::mapFacility).collect(Collectors.toCollection(ArrayList::new));
    }

    private FacilityInput mapFacility(TrialFacilityRowReq row) {
        FacilityInput facility = new FacilityInput();
        BeanUtils.copyProperties(row, facility);
        return facility;
    }

    private Map<String, List<CollateralInput>> groupCollaterals(List<TrialCollateralRowReq> rows) {
        Map<String, List<CollateralInput>> grouped = new LinkedHashMap<>();
        if (rows == null) {
            return grouped;
        }
        for (TrialCollateralRowReq row : rows) {
            CollateralInput collateral = new CollateralInput();
            BeanUtils.copyProperties(row, collateral);
            grouped.computeIfAbsent(collateral.getCollateralPoolCode(), key -> new ArrayList<>()).add(collateral);
        }
        return grouped;
    }

    private Map<String, TrialRatingRowReq> mapRatings(List<TrialRatingRowReq> rows) {
        if (rows == null) {
            return Map.of();
        }
        return rows.stream()
                .filter(r -> r.getCifNo() != null)
                .collect(Collectors.toMap(TrialRatingRowReq::getCifNo, Function.identity(), (left, right) -> left));
    }

    private Map<String, List<RepaymentScheduleInput>> groupRepayments(List<TrialRepaymentRowReq> rows) {
        Map<String, List<RepaymentScheduleInput>> grouped = new LinkedHashMap<>();
        if (rows == null) {
            return grouped;
        }
        for (TrialRepaymentRowReq row : rows) {
            RepaymentScheduleInput repayment = new RepaymentScheduleInput();
            BeanUtils.copyProperties(row, repayment);
            grouped.computeIfAbsent(row.getLoanReceiptNo(), key -> new ArrayList<>()).add(repayment);
        }
        return grouped;
    }

    private Map<String, Stage> mapHistoricalStages(List<TrialHistoricalStageRowReq> rows, LocalDate calcDate) {
        if (rows == null) {
            return Map.of();
        }
        Map<String, Stage> stages = new LinkedHashMap<>();
        for (TrialHistoricalStageRowReq row : rows) {
            if (row.getAssetId() == null || !Objects.equals(row.getCalcDate(), calcDate)) {
                continue;
            }
            Stage stage = parseStage(row.getStageResult());
            if (stage != null) {
                stages.putIfAbsent(row.getAssetId(), stage);
            }
        }
        return stages;
    }

    private AssetInput mapAsset(TrialLoanRowReq loan, LocalDate calcDate, Map<String, FacilityInput> facilitiesByCode,
                                Map<String, TrialRatingRowReq> ratingsByCustomer,
                                Map<String, List<RepaymentScheduleInput>> repaymentsByLoan,
                                Map<String, Stage> historicalStagesByAsset) {
        AssetInput asset = new AssetInput();
        asset.setAssetId(loan.getId());
        asset.setFacilityCd(loan.getFacilityCd());
        asset.setCustomerNo(loan.getCustomerNo());
        asset.setCustomerId(loan.getCustomerNo());
        asset.setCustomerName(loan.getCustomerName());
        asset.setSegment(loan.getSegment());
        asset.setProductType(loan.getProductType());
        asset.setIndustryCode(loan.getIndustryCn());
        asset.setCollateralType(loan.getGuaranteeType());
        asset.setIsNpl(loan.getIsNpl());
        asset.setFiveCategory(loan.getLoanClassifCd());
        asset.setNormalConsecutiveDays(loan.getNormalConsecutiveDays());
        asset.setOtherRiskInfo(loan.getOtherRiskInfo());
        asset.setBusinessType(loan.getBusinessType());
        asset.setAmtFinancedCny(loan.getAmtFinancedCny());
        asset.setOutstandingBalance(loan.getLoanBalCny());
        asset.setAccruedInterest(loan.getIntAccruedCny());
        asset.setInterestRate(loan.getInterestRate());
        asset.setOverduePrincipal(loan.getOverduePrincipal());
        asset.setOverdueInterest(loan.getOverdueInterest());
        asset.setMaturityDate(loan.getLoanMaturityDt());
        asset.setCalcDate(loan.getReportDt() != null ? loan.getReportDt() : calcDate);
        asset.setOverdueDays(loan.getOverdueDays());
        asset.setRepaymentSchedules(repaymentsByLoan.getOrDefault(loan.getId(), new ArrayList<>()));
        asset.setLastStage(historicalStagesByAsset.get(loan.getId()));

        applyFacility(asset, facilitiesByCode.get(loan.getFacilityCd()));
        applyRating(asset, ratingsByCustomer.get(loan.getCustomerNo()));
        return asset;
    }

    private void applyFacility(AssetInput asset, FacilityInput facility) {
        if (facility == null) {
            return;
        }
        asset.setCollateralPoolId(facility.getCollateralPoolId());
        asset.setTotalLimit(facility.getLimitAmtCny());
        asset.setCommitmentType(isTruthy(facility.getIsRevolving()) ? "不可撤销" : "可撤销");
        if (facility.getFacilityStartDate() != null && facility.getFacilityMaturityDate() != null) {
            asset.setCommitmentDays(Math.toIntExact(ChronoUnit.DAYS.between(
                    facility.getFacilityStartDate(), facility.getFacilityMaturityDate())));
        }
    }

    private void applyRating(AssetInput asset, TrialRatingRowReq rating) {
        if (rating == null) {
            return;
        }
        asset.setCrrIntLastYear(rating.getCrrIntLastYear());
        asset.setCrrIntThisYear(rating.getCrrIntThisYear());
        asset.setCrrFinal(rating.getCrrFinal());
        asset.setCrrRating(rating.getCrrFinal());
        asset.setRatingCode(rating.getCrrFinal());
        asset.setExtRatingCoLastYear(rating.getExtRatingCoLastYear());
        asset.setExtRatingLastYear(rating.getExtRatingLastYear());
        asset.setExtRatingCoThisYear(rating.getExtRatingCoThisYear());
        asset.setExtRatingThisYear(rating.getExtRatingThisYear());
    }

    private boolean isTruthy(String value) {
        return value != null && ("Y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value));
    }

    private Stage parseStage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Stage stage : Stage.values()) {
            if (stage.name().equalsIgnoreCase(value) || stage.getLabel().equals(value)) {
                return stage;
            }
        }
        return null;
    }
}
