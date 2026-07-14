package com.bank.ecl.calculation.trial;

import com.bank.ecl.calculation.trial.dto.*;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.common.util.JsonUtil;
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

        // 校验：非多笔模式时必须传入 assetId
        if ((req.getAssets() == null || req.getAssets().isEmpty())
                && (req.getLoans() == null || req.getLoans().isEmpty())
                && (req.getAssetId() == null || req.getAssetId().isBlank())) {
            throw new EclException(ErrorCode.ECL_006, "参数校验失败: 单笔模式必须传入 assetId");
        }

        LocalDate calcDate = req.getCalcDate() != null ? req.getCalcDate() : LocalDate.now();
        long start = System.currentTimeMillis();

        String jobId = "TRIAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26);
        JobContext ctx;
        List<AssetInput> assets;
        if (req.getLoans() != null && !req.getLoans().isEmpty()) {
            ctx = trialSourceAssembler.assemble(jobId, scheme.getSchemeId(), calcDate, req,
                    normalizeDiscountRate(scheme.getDiscountRate()),
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
        String requestJson = safeToJson(req);
        writeJobRecord(jobId, req.getSchemeId(), calcDate, durationMs, assets.size(), requestJson);

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
        a.setSegment(req.getSegment());
        a.setCustomerType(req.getCustomerType());
        a.setProductType(req.getProductType());
        a.setIndustryCode(req.getIndustryCode());
        a.setRegionCode(req.getRegionCode());
        a.setCollateralType(req.getCollateralType());
        if (req.getLastStage() != null) a.setLastStage(Stage.valueOf(req.getLastStage()));
        a.setOverdueDays(req.getOverdueDays());
        a.setCrrRating(req.getCrrRating());
        a.setCrrFinal(req.getRatingCode());
        a.setFiveCategory(req.getFiveCategory());
        a.setDefaultFlag(req.getDefaultFlag());
        a.setMediaSentiment(req.getMediaSentiment());
        a.setRatingDropLevels(req.getRatingDropLevels());
        a.setRatingCode(req.getRatingCode());
        a.setMaturityDate(req.getMaturityDate());
        a.setCalcDate(calcDate);
        a.setBusinessType(req.getBusinessType());
        a.setOutstandingBalance(req.getOutstandingBalance());
        a.setAccruedInterest(req.getAccruedInterest());
        a.setTotalLimit(req.getTotalLimit());
        a.setCommitmentType(req.getCommitmentType());
        a.setCommitmentDays(req.getCommitmentDays());
        a.setAmtFinancedCny(req.getAmtFinancedCny());
        a.setFacilityCd(req.getFacilityCd());
        return a;
    }

    private JobContext buildJobContext(String jobId, EclSchemeEntity scheme, LocalDate calcDate) {
        JobContext ctx = new JobContext();
        ctx.setJobId(jobId);
        ctx.setSchemeId(scheme.getSchemeId());
        ctx.setCalcDate(calcDate);
        ctx.setTrialMode(true);
        ctx.setDiscountRate(normalizeDiscountRate(scheme.getDiscountRate()));
        ctx.setDefaultCcf(scheme.getDefaultCcf() != null ? scheme.getDefaultCcf().doubleValue() : 0.0);
        ctx.setDefaultLgd(scheme.getDefaultLgd() != null ? scheme.getDefaultLgd().doubleValue() : 0.45);
        ctx.setLgdFloor(0.1);
        return ctx;
    }

    /**
     * 兼容百分比和小数两种存储格式的折现率。
     * 数据库可能存 5.0（表示5%）或 0.05（已转为小数），
     * 统一归一化为小数。
     */
    private double normalizeDiscountRate(BigDecimal rate) {
        if (rate == null) return 0.05;
        double val = rate.doubleValue();
        return val > 1 ? val / 100.0 : val;
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

    private void writeJobRecord(String jobId, String schemeId, LocalDate calcDate,
                                long durationMs, int count, String requestPayload) {
        try {
            EclJobEntity job = new EclJobEntity();
            job.setJobId(jobId); job.setSchemeId(schemeId); job.setCalcDate(calcDate);
            job.setTrialMode(true); job.setStatus("SUCCESS");
            job.setTotalAssets(count); job.setSuccessCount(count); job.setExceptionCount(0);
            job.setStartedAt(LocalDateTime.now().minusNanos(durationMs * 1_000_000));
            job.setFinishedAt(LocalDateTime.now()); job.setDurationMs(durationMs);
            job.setErrorSummary("{}");
            job.setRequestPayload(requestPayload);
            eclJobMapper.insert(job);
        } catch (Exception e) { log.warn("failed to write job record", e); }
    }

    private static String safeToJson(Object obj) {
        try {
            return JsonUtil.toJson(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // === Step builders ===

    private List<TrialStepVO> buildSteps(AssetInput a, StageResult sr) {
        List<TrialStepVO> steps = new ArrayList<>();

        // ──────────── Step 1: 风险分组 ────────────
        TrialStepVO s1 = step("group", "① 风险分组",
                a.getGroupId() != null && !"GRP_DEFAULT".equals(a.getGroupId())
                        ? "命中分组 " + a.getGroupName() : "兜底分组 GRP_DEFAULT");
        s1.setMetrics(List.of(
                metric("分组 ID", nvl(a.getGroupId())),
                metric("分组名称", nvl(a.getGroupName())),
                metric("segment", nvl(a.getSegment())),
                metric("产品类型", nvl(a.getProductType())),
                metric("行业代码", nvl(a.getIndustryCode())),
                metric("担保类型", nvl(a.getCollateralType()))));
        if (a.getGroupException() != null) s1.setNote("异常：命中兜底分组");
        steps.add(s1);

        // ──────────── Step 2: 阶段判定 ────────────
        String triggerInfo = sr != null ? sr.getStage().getLabel() : "-";
        if (sr != null && sr.getTriggerType() != null) triggerInfo += " · 触发: " + sr.getTriggerType();
        TrialStepVO s2 = step("stage", "② 阶段判定", triggerInfo);
        s2.setMetrics(List.of(
                metric("逾期天数", nvl(a.getOverdueDays())),
                metric("是否不良 (is_npl)", nvl(a.getIsNpl())),
                metric("正常连续天数", nvl(a.getNormalConsecutiveDays())),
                metric("上期阶段", a.getLastStage() != null ? a.getLastStage().getLabel() : "-"),
                metric("上年 CRR", nvl(a.getCrrIntLastYear())),
                metric("本年 CRR", nvl(a.getCrrIntThisYear() != null ? a.getCrrIntThisYear() : a.getCrrRating())),
                metric("评级下降", computeRatingDrop(a)),
                metric("五级分类", nvl(a.getFiveCategory())),
                metric("违约标识", a.getDefaultFlag() != null && a.getDefaultFlag() ? "是" : "否"),
                metric("其他风险信息", nvl(a.getOtherRiskInfo()))));
        if (sr != null && sr.isExceptionFlag()) s2.setNote("异常：走兜底或被回跳阻断");
        steps.add(s2);

        // ──────────── Step 3: PD 取值 ────────────
        String pdType = sr != null ? switch (sr.getStage()) {
            case STAGE_1 -> "12M PD";
            case STAGE_2 -> "Lifetime PD";
            case STAGE_3 -> "100%";
        } : "12M PD";
        TrialStepVO s3 = step("pd", "③ PD 取值",
                pdType + " = " + formatPercent(a.getPd12m())
                + (sr != null && sr.getStage() != Stage.STAGE_1 ? " | 存续期 = " + formatPercent(a.getPdLifetime()) : ""));
        s3.setMetrics(List.of(
                metric("评级体系", "INTERNAL_CRR"),
                metric("评级机构", nvl(a.getExtRatingCoThisYear() != null ? a.getExtRatingCoThisYear() : "INTERNAL_CRR")),
                metric("评级代码", nvl(a.getCrrFinal() != null ? a.getCrrFinal()
                        : a.getExtRatingThisYear() != null ? a.getExtRatingThisYear() : a.getRatingCode())),
                metric("PD 类型", pdType),
                metric("到期日", nvl(a.getMaturityDate()))));
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

        // ──────────── Step 4: EAD 计算 ────────────
        String eadMethod = (a.getRepaymentSchedules() != null && !a.getRepaymentSchedules().isEmpty())
                ? "还款计划折现" : "余额+利息";
        TrialStepVO s4 = step("ead", "④ EAD 计算",
                "EAD = " + formatMoney(a.getTotalEad()) + " (" + eadMethod + ")");
        List<TrialMetricVO> eadMetrics = new ArrayList<>();
        eadMetrics.add(metric("授信编码", nvl(a.getFacilityCd())));
        eadMetrics.add(metric("承诺类型", nvl(a.getCommitmentType())));
        eadMetrics.add(metric("表内 EAD", formatMoney(a.getOnBsEad())));
        eadMetrics.add(metric("表外 EAD", formatMoney(a.getOffBsEad())));
        eadMetrics.add(metric("总 EAD", formatMoney(a.getTotalEad())));
        eadMetrics.add(metric("计算方式", eadMethod));
        s4.setMetrics(eadMetrics);
        if (a.getEadBreakdown() != null) s4.setNote(a.getEadBreakdown());
        if (a.getEadException() != null) s4.setNote((s4.getNote() != null ? s4.getNote() + " | " : "") + "异常：" + a.getEadException());
        steps.add(s4);

        // ──────────── Step 5: LGD 取值 ────────────
        TrialStepVO s5 = step("lgd", "⑤ LGD 取值", "LGD = " + formatPercent(a.getLgdValue()));
        List<TrialMetricVO> lgdMetrics = new ArrayList<>();
        lgdMetrics.add(metric("押品池", nvl(a.getCollateralPoolId())));
        lgdMetrics.add(metric("LGD", formatPercent(a.getLgdValue())));

        // Parse LGD details JSON for covered/uncovered breakdown
        if (a.getLgdDetails() != null) {
            try {
                var lgdJson = JsonUtil.fromJson(a.getLgdDetails(), Map.class);
                if (lgdJson != null) {
                    if (lgdJson.get("eadCovered") != null)
                        lgdMetrics.add(metric("EAD 覆盖", formatMoney(((Number) lgdJson.get("eadCovered")).doubleValue())));
                    if (lgdJson.get("eadUncovered") != null)
                        lgdMetrics.add(metric("EAD 未覆盖", formatMoney(((Number) lgdJson.get("eadUncovered")).doubleValue())));
                    if (lgdJson.get("collateralNetValue") != null)
                        lgdMetrics.add(metric("押品净值", formatMoney(((Number) lgdJson.get("collateralNetValue")).doubleValue())));
                }
            } catch (Exception ignored) {}
        }
        s5.setMetrics(lgdMetrics);
        StringBuilder lgdNote = new StringBuilder();
        if (a.getLgdException() != null) lgdNote.append("警告：使用方案默认 LGD");
        if (a.getLgdDetails() != null) {
            if (lgdNote.length() > 0) lgdNote.append(" | ");
            lgdNote.append(a.getLgdDetails());
        }
        if (lgdNote.length() > 0) s5.setNote(lgdNote.toString());
        steps.add(s5);

        // ──────────── Step 6: ECL 计算 ────────────
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

        // ──────────── Step 7: 管理层叠加 ────────────
        TrialStepVO s7 = step("overlay", "⑦ 管理层叠加",
                a.getOverlayAmount() > 0 ? "+ " + formatMoney(a.getOverlayAmount()) : "无命中规则");
        List<TrialMetricVO> overlayMetrics = new ArrayList<>();
        overlayMetrics.add(metric("叠加金额", formatMoney(a.getOverlayAmount())));
        overlayMetrics.add(metric("ECL 最终", formatMoney(a.getEclFinal())));
        if (a.getSelectedOverlayId() != null)
            overlayMetrics.add(metric("命中叠加规则 ID", a.getSelectedOverlayId().toString()));
        s7.setMetrics(overlayMetrics);
        steps.add(s7);

        return steps;
    }

    private TrialStepVO step(String key, String title, String summary) {
        TrialStepVO s = new TrialStepVO(); s.setKey(key); s.setTitle(title); s.setSummary(summary); return s;
    }
    private TrialMetricVO metric(String label, String value) { return new TrialMetricVO(label, value, null); }
    private String nvl(Object v) { return v != null ? v.toString() : "-"; }

    private String computeRatingDrop(AssetInput a) {
        String last = a.getCrrIntLastYear();
        String curr = a.getCrrIntThisYear();
        if (last == null || curr == null) return "-";
        Integer lastRank = parseCrrRank(last);
        Integer currRank = parseCrrRank(curr);
        if (lastRank == null || currRank == null) return "-";
        int drop = currRank - lastRank;
        if (drop <= 0) return "无";
        return "降" + drop + "级";
    }

    private Integer parseCrrRank(String rating) {
        if (rating == null || rating.isBlank()) return null;
        String trimmed = rating.trim();
        if (trimmed.matches("(?i)CRR\\d+")) {
            return Integer.parseInt(trimmed.replaceAll("(?i)CRR", ""));
        }
        return null;
    }
    private String formatMoney(double v) {
        return "¥" + BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private String formatPercent(double v) {
        return BigDecimal.valueOf(v * 100).setScale(4, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
