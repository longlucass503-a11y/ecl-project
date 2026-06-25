package com.bank.ecl.calculation.trial;

import com.bank.ecl.calculation.trial.dto.*;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.data.entity.EclJobEntity;
import com.bank.ecl.data.entity.EclSchemeEntity;
import com.bank.ecl.data.entity.RiskGroupEntity;
import com.bank.ecl.data.mapper.EclJobMapper;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.data.mapper.RiskGroupMapper;
import com.bank.ecl.engine.core.*;
import com.bank.ecl.engine.stage.StageResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrialCalculationService {

    private static final Logger log = LoggerFactory.getLogger(TrialCalculationService.class);

    private final EngineDispatcher dispatcher;
    private final EclSchemeMapper eclSchemeMapper;
    private final RiskGroupMapper riskGroupMapper;
    private final EclJobMapper eclJobMapper;
    private final TrialSourceAssembler trialSourceAssembler;

    @Transactional(rollbackFor = Exception.class)
    public TrialCalculationResp runTrial(TrialCalculationReq req) {
        EclSchemeEntity scheme = eclSchemeMapper.selectById(req.getSchemeId());
        if (scheme == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在: " + req.getSchemeId());
        }

        LocalDate calcDate = req.getCalcDate() != null ? req.getCalcDate() : LocalDate.now();
        long start = System.currentTimeMillis();

        String jobId = "TRIAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26);
        JobContext ctx;
        List<AssetInput> assets;
        if (req.getLoans() != null && !req.getLoans().isEmpty()) {
            ctx = trialSourceAssembler.assemble(jobId, scheme.getSchemeId(), calcDate, req,
                    scheme.getDiscountRate() != null ? scheme.getDiscountRate().doubleValue() : 0.05,
                    scheme.getDefaultCcf() != null ? scheme.getDefaultCcf().doubleValue() : 0.0,
                    scheme.getDefaultLgd() != null ? scheme.getDefaultLgd().doubleValue() : 0.45,
                    0.1);
            assets = ctx.getCustomers().stream()
                    .flatMap(c -> c.getAssets().stream())
                    .collect(Collectors.toList());
        } else {
            ctx = buildJobContext(jobId, scheme, calcDate);

            // Build assets — multi-asset or single-asset
            if (req.getAssets() != null && !req.getAssets().isEmpty()) {
                assets = req.getAssets().stream()
                        .map(r -> buildAssetFromReq(r, calcDate))
                        .collect(Collectors.toList());
            } else {
                assets = List.of(buildAssetFromReq(AssetInputReq.from(req), calcDate));
            }

            CustomerContext customer = new CustomerContext();
            customer.setCustomerId("TRIAL_CUST");
            customer.setAssets(assets);
            ctx.setCustomers(List.of(customer));
        }

        // Execute
        dispatcher.execute(ctx);
        long durationMs = System.currentTimeMillis() - start;

        // Build per-asset results
        List<AssetResult> assetResults = assets.stream()
                .map(a -> buildAssetResult(a, req.getSchemeId()))
                .collect(Collectors.toList());

        // Write job record
        writeJobRecord(jobId, req.getSchemeId(), calcDate, durationMs, assets.size());

        // Build response
        TrialCalculationResp resp = new TrialCalculationResp();
        resp.setJobId(jobId);
        resp.setStatus("SUCCESS");
        resp.setDurationMs(durationMs);

        // Single-asset compat fields (from first asset)
        AssetInput first = assets.get(0);
        resp.setAssetId(first.getAssetId());
        AssetResult firstResult = assetResults.get(0);
        resp.setGroupId(firstResult.getGroupId());
        resp.setGroupLabel(firstResult.getGroupLabel());
        resp.setProductType(firstResult.getProductType());
        resp.setRatingCode(firstResult.getRatingCode());
        resp.setStage(firstResult.getStage());
        resp.setEad(firstResult.getEad());
        resp.setLgd(firstResult.getLgd());
        resp.setPd12m(firstResult.getPd12m());
        resp.setPdLifetime(firstResult.getPdLifetime());
        resp.setEclValue(firstResult.getEclValue());
        resp.setOverlayAmount(firstResult.getOverlayAmount());
        resp.setEclFinal(firstResult.getEclFinal());
        resp.setExceptionSummary(firstResult.getExceptionSummary());
        resp.setSteps(firstResult.getSteps());

        resp.setAssetResults(assetResults);
        return resp;
    }

    private AssetInput buildAssetFromReq(AssetInputReq req, LocalDate calcDate) {
        AssetInput a = new AssetInput();
        a.setAssetId(req.getAssetId());
        a.setBusinessLine(req.getBusinessLine());
        a.setCustomerType(req.getCustomerType());
        a.setProductType(req.getProductType());
        a.setIndustryCode(req.getIndustryCode());
        a.setRegionCode(req.getRegionCode());
        a.setCollateralType(req.getCollateralType());
        if (req.getLastStage() != null) a.setLastStage(Stage.valueOf(req.getLastStage()));
        a.setOverdueDays(req.getOverdueDays());
        a.setCrrRating(req.getCrrRating());
        a.setFiveCategory(req.getFiveCategory());
        a.setDefaultFlag(req.getDefaultFlag());
        a.setMediaSentiment(req.getMediaSentiment());
        a.setRatingDropLevels(req.getRatingDropLevels());
        a.setRatingCode(req.getRatingCode());
        a.setMaturityDate(req.getMaturityDate());
        a.setCalcDate(calcDate);
        a.setOutstandingBalance(req.getOutstandingBalance());
        a.setAccruedInterest(req.getAccruedInterest());
        a.setTotalLimit(req.getTotalLimit());
        a.setCommitmentType(req.getCommitmentType());
        a.setCommitmentDays(req.getCommitmentDays());
        return a;
    }

    private JobContext buildJobContext(String jobId, EclSchemeEntity scheme, LocalDate calcDate) {
        JobContext ctx = new JobContext();
        ctx.setJobId(jobId);
        ctx.setSchemeId(scheme.getSchemeId());
        ctx.setCalcDate(calcDate);
        ctx.setTrialMode(true);
        ctx.setDiscountRate(scheme.getDiscountRate() != null ? scheme.getDiscountRate().doubleValue() : 0.05);
        ctx.setDefaultCcf(scheme.getDefaultCcf() != null ? scheme.getDefaultCcf().doubleValue() : 0.0);
        ctx.setDefaultLgd(scheme.getDefaultLgd() != null ? scheme.getDefaultLgd().doubleValue() : 0.45);
        ctx.setLgdFloor(0.1);
        return ctx;
    }

    AssetResult buildAssetResult(AssetInput a, String schemeId) {
        AssetResult r = new AssetResult();
        r.setAssetId(a.getAssetId());
        r.setGroupId(a.getGroupId());
        r.setGroupLabel(buildGroupLabel(schemeId, a.getGroupId()));
        r.setProductType(a.getProductType());
        r.setRatingCode(a.getRatingCode());
        StageResult sr = a.getStageResult();
        r.setStage(sr != null ? sr.getStage().getLabel() : "-");
        r.setEad(formatMoney(a.getTotalEad()));
        r.setLgd(formatPercent(a.getLgdValue()));
        r.setPd12m(formatPercent(a.getPd12m()));
        r.setPdLifetime(formatPercent(a.getPdLifetime()));
        r.setEclValue(formatMoney(a.getEclValue()));
        r.setOverlayAmount(formatMoney(a.getOverlayAmount()));
        r.setEclFinal(formatMoney(a.getEclFinal()));
        StringBuilder ex = new StringBuilder();
        if (a.getGroupException() != null) ex.append("分组:").append(a.getGroupException()).append(";");
        if (a.getPdException() != null) ex.append("PD:").append(a.getPdException()).append(";");
        if (a.getEadException() != null) ex.append("EAD:").append(a.getEadException()).append(";");
        if (a.getLgdException() != null) ex.append("LGD:").append(a.getLgdException()).append(";");
        r.setExceptionSummary(ex.isEmpty() ? null : ex.toString());
        r.setSteps(buildSteps(a, sr));
        return r;
    }

    private String buildGroupLabel(String schemeId, String groupId) {
        if (groupId == null) return "未匹配";
        RiskGroupEntity group = riskGroupMapper.selectById(groupId);
        if (group != null) return group.getGroupCode() + " " + group.getGroupName();
        if ("GRP_DEFAULT".equals(groupId)) return "兜底分组 (GRP_DEFAULT)";
        return groupId;
    }

    private void writeJobRecord(String jobId, String schemeId, LocalDate calcDate, long durationMs, int count) {
        try {
            EclJobEntity job = new EclJobEntity();
            job.setJobId(jobId); job.setSchemeId(schemeId); job.setCalcDate(calcDate);
            job.setTrialMode(true); job.setStatus("SUCCESS");
            job.setTotalAssets(count); job.setSuccessCount(count); job.setExceptionCount(0);
            job.setStartedAt(LocalDateTime.now().minusNanos(durationMs * 1_000_000));
            job.setFinishedAt(LocalDateTime.now()); job.setDurationMs(durationMs);
            job.setErrorSummary("{}");
            eclJobMapper.insert(job);
        } catch (Exception e) { log.warn("failed to write job record", e); }
    }

    // === Step builders ===

    private List<TrialStepVO> buildSteps(AssetInput a, StageResult sr) {
        List<TrialStepVO> steps = new ArrayList<>();

        // Step 1: group
        TrialStepVO s1 = step("group", "① 风险分组",
                a.getGroupId() != null && !"GRP_DEFAULT".equals(a.getGroupId())
                        ? "命中分组 " + a.getGroupName() : "兜底分组 GRP_DEFAULT");
        s1.setMetrics(List.of(
                metric("业务条线", nvl(a.getBusinessLine())),
                metric("产品类型", nvl(a.getProductType())),
                metric("行业代码", nvl(a.getIndustryCode())),
                metric("担保类型", nvl(a.getCollateralType()))));
        if (a.getGroupException() != null) s1.setNote("异常：命中兜底分组");
        steps.add(s1);

        // Step 2: stage
        TrialStepVO s2 = step("stage", "② 阶段判定",
                sr != null ? sr.getStage().getLabel() + " (触发: " + nvl(sr.getTriggerType()) + ")" : "-");
        s2.setMetrics(List.of(
                metric("逾期天数", nvl(a.getOverdueDays())),
                metric("CRR 评级", nvl(a.getCrrRating())),
                metric("五级分类", nvl(a.getFiveCategory())),
                metric("违约标识", a.getDefaultFlag() != null && a.getDefaultFlag() ? "是" : "否")));
        if (sr != null && sr.isExceptionFlag()) s2.setNote("异常：走兜底或被回跳阻断");
        steps.add(s2);

        // Step 3: PD
        TrialStepVO s3 = step("pd", "③ PD 取值", "PD_12M = " + formatPercent(a.getPd12m()));
        if (a.getPdScenarioResults() != null && !a.getPdScenarioResults().isEmpty()) {
            List<TrialScenarioRowVO> rows = new ArrayList<>();
            for (var p : a.getPdScenarioResults()) {
                rows.add(new TrialScenarioRowVO(
                        p.getScenarioName() + " (" + p.getScenarioType() + ")",
                        formatPercent(p.getWeight() != null ? p.getWeight().doubleValue() : 0),
                        formatPercent(p.getPdValue()),
                        formatPercent(p.getPdValue()),
                        "BASELINE".equals(p.getScenarioType())));
            }
            s3.setScenarioRows(rows);
        } else if (a.getPdDetails() != null) {
            List<TrialScenarioRowVO> rows = new ArrayList<>();
            for (var d : a.getPdDetails()) {
                rows.add(new TrialScenarioRowVO(
                        d.getScenarioName() + " (" + d.getScenarioType() + ")",
                        formatPercent(d.getWeight() != null ? d.getWeight().doubleValue() : 0),
                        formatPercent(d.getPdValue()), formatPercent(d.getWeightedPd()),
                        "BASELINE".equals(d.getScenarioType())));
            }
            s3.setScenarioRows(rows);
        }
        if (a.getPdException() != null) s3.setNote("异常：" + a.getPdException());
        steps.add(s3);

        // Step 4: EAD
        TrialStepVO s4 = step("ead", "④ EAD 计算", "EAD = " + formatMoney(a.getTotalEad()));
        s4.setMetrics(List.of(
                metric("表内 EAD", formatMoney(a.getOnBsEad())),
                metric("表外 EAD", formatMoney(a.getOffBsEad())),
                metric("总 EAD", formatMoney(a.getTotalEad()))));
        if (a.getEadBreakdown() != null) s4.setNote(a.getEadBreakdown());
        steps.add(s4);

        // Step 5: LGD
        TrialStepVO s5 = step("lgd", "⑤ LGD 取值", "LGD = " + formatPercent(a.getLgdValue()));
        s5.setMetrics(List.of(
                metric("押品池", nvl(a.getCollateralPoolId())),
                metric("LGD", formatPercent(a.getLgdValue()))));
        StringBuilder lgdNote = new StringBuilder();
        if (a.getLgdException() != null) lgdNote.append("警告：使用方案默认 LGD");
        if (a.getLgdDetails() != null) {
            if (lgdNote.length() > 0) lgdNote.append(" | ");
            lgdNote.append(a.getLgdDetails());
        }
        if (lgdNote.length() > 0) s5.setNote(lgdNote.toString());
        steps.add(s5);

        // Step 6: ECL
        TrialStepVO s6 = step("ecl", "⑥ ECL 计算", "ECL = " + formatMoney(a.getEclValue()));
        s6.setMetrics(List.of(
                metric("PD (存续期)", formatPercent(a.getPdLifetime())),
                metric("LGD", formatPercent(a.getLgdValue())),
                metric("EAD", formatMoney(a.getTotalEad())),
                metric("ECL 加权", formatMoney(a.getEclValue()))));
        if (a.getEclScenarioResults() != null && !a.getEclScenarioResults().isEmpty()) {
            List<TrialScenarioRowVO> rows = new ArrayList<>();
            for (var e : a.getEclScenarioResults()) {
                rows.add(new TrialScenarioRowVO(
                        e.getScenarioCode() != null ? e.getScenarioCode() : "DEFAULT",
                        formatPercent(e.getWeight() != null ? e.getWeight().doubleValue() : 0),
                        formatMoney(e.getScenarioEcl()),
                        formatMoney(e.getWeightedEcl()),
                        false));
            }
            s6.setScenarioRows(rows);
        }
        steps.add(s6);

        // Step 7: Overlay
        TrialStepVO s7 = step("overlay", "⑦ 管理层叠加",
                a.getOverlayAmount() > 0 ? "+ " + formatMoney(a.getOverlayAmount()) : "无命中规则");
        s7.setMetrics(List.of(
                metric("叠加金额", formatMoney(a.getOverlayAmount())),
                metric("ECL 最终", formatMoney(a.getEclFinal()))));
        steps.add(s7);

        return steps;
    }

    private TrialStepVO step(String key, String title, String summary) {
        TrialStepVO s = new TrialStepVO(); s.setKey(key); s.setTitle(title); s.setSummary(summary); return s;
    }
    private TrialMetricVO metric(String label, String value) { return new TrialMetricVO(label, value, null); }
    private String nvl(Object v) { return v != null ? v.toString() : "-"; }
    private String formatMoney(double v) {
        return "¥" + BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private String formatPercent(double v) {
        return BigDecimal.valueOf(v * 100).setScale(4, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
