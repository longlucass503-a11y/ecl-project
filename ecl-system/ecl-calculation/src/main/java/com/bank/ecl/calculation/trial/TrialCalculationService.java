package com.bank.ecl.calculation.trial;

import com.bank.ecl.calculation.trial.dto.*;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.data.entity.EclCalcDetailEntity;
import com.bank.ecl.data.entity.EclJobEntity;
import com.bank.ecl.data.entity.EclSchemeEntity;
import com.bank.ecl.data.entity.RiskGroupEntity;
import com.bank.ecl.data.mapper.EclCalcDetailMapper;
import com.bank.ecl.data.mapper.EclJobMapper;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.data.mapper.RiskGroupMapper;
import com.bank.ecl.engine.core.*;
import com.bank.ecl.engine.stage.StageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
@RequiredArgsConstructor
public class TrialCalculationService {

    private static final Logger log = LoggerFactory.getLogger(TrialCalculationService.class);

    private final EngineDispatcher dispatcher;
    private final EclSchemeMapper eclSchemeMapper;
    private final RiskGroupMapper riskGroupMapper;
    private final EclJobMapper eclJobMapper;
    private final EclCalcDetailMapper eclCalcDetailMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public TrialCalculationResp runTrial(TrialCalculationReq req) {
        // 1. 校验方案
        EclSchemeEntity scheme = eclSchemeMapper.selectById(req.getSchemeId());
        if (scheme == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在: " + req.getSchemeId());
        }

        LocalDate calcDate = req.getCalcDate() != null ? req.getCalcDate() : LocalDate.now();
        long start = System.currentTimeMillis();

        // 2. 构建 JobContext + AssetInput
        String jobId = "TRIAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 27);
        JobContext ctx = buildJobContext(jobId, scheme, calcDate);
        AssetInput asset = buildAsset(req, calcDate);
        CustomerContext customer = new CustomerContext();
        customer.setCustomerId("TRIAL_CUST");
        customer.setAssets(List.of(asset));
        ctx.setCustomers(List.of(customer));

        // 3. 执行引擎链
        dispatcher.execute(ctx);
        long durationMs = System.currentTimeMillis() - start;

        // 4. 加载分组名称
        String groupLabel = buildGroupLabel(req.getSchemeId(), asset.getGroupId());

        // 5. 写任务记录（简洁版）
        writeJobRecord(jobId, req.getSchemeId(), calcDate, durationMs);

        // 6. 构建响应
        return buildResponse(jobId, asset, groupLabel, durationMs);
    }

    private JobContext buildJobContext(String jobId, EclSchemeEntity scheme, LocalDate calcDate) {
        JobContext ctx = new JobContext();
        ctx.setJobId(jobId);
        ctx.setSchemeId(scheme.getSchemeId());
        ctx.setTrialMode(true);
        ctx.setDiscountRate(scheme.getDiscountRate() != null ? scheme.getDiscountRate().doubleValue() : 0.05);
        ctx.setDefaultCcf(scheme.getDefaultCcf() != null ? scheme.getDefaultCcf().doubleValue() : 0.0);
        ctx.setDefaultLgd(scheme.getDefaultLgd() != null ? scheme.getDefaultLgd().doubleValue() : 0.45);
        return ctx;
    }

    private AssetInput buildAsset(TrialCalculationReq req, LocalDate calcDate) {
        AssetInput a = new AssetInput();
        a.setAssetId(req.getAssetId());
        // 6.1 入参
        a.setBusinessLine(req.getBusinessLine());
        a.setCustomerType(req.getCustomerType());
        a.setProductType(req.getProductType());
        a.setIndustryCode(req.getIndustryCode());
        a.setRegionCode(req.getRegionCode());
        a.setCollateralType(req.getCollateralType());
        // 6.2 入参
        if (req.getLastStage() != null) {
            a.setLastStage(Stage.valueOf(req.getLastStage()));
        }
        a.setOverdueDays(req.getOverdueDays());
        a.setCrrRating(req.getCrrRating());
        a.setFiveCategory(req.getFiveCategory());
        a.setDefaultFlag(req.getDefaultFlag());
        a.setMediaSentiment(req.getMediaSentiment());
        a.setRatingDropLevels(req.getRatingDropLevels());
        // 6.3 入参
        a.setRatingCode(req.getRatingCode());
        a.setMaturityDate(req.getMaturityDate());
        a.setCalcDate(calcDate);
        // 6.4 入参
        a.setOutstandingBalance(req.getOutstandingBalance());
        a.setAccruedInterest(req.getAccruedInterest());
        a.setTotalLimit(req.getTotalLimit());
        a.setCommitmentType(req.getCommitmentType());
        a.setCommitmentDays(req.getCommitmentDays());
        return a;
    }

    private String buildGroupLabel(String schemeId, String groupId) {
        if (groupId == null) return "未匹配";
        RiskGroupEntity group = riskGroupMapper.selectById(groupId);
        if (group != null) {
            return group.getGroupCode() + " " + group.getGroupName();
        }
        if ("GRP_DEFAULT".equals(groupId)) return "兜底分组 (GRP_DEFAULT)";
        return groupId;
    }

    private void writeJobRecord(String jobId, String schemeId, LocalDate calcDate, long durationMs) {
        try {
            EclJobEntity job = new EclJobEntity();
            job.setJobId(jobId);
            job.setSchemeId(schemeId);
            job.setCalcDate(calcDate);
            job.setTrialMode(true);
            job.setStatus("SUCCESS");
            job.setTotalAssets(1);
            job.setSuccessCount(1);
            job.setExceptionCount(0);
            job.setStartedAt(LocalDateTime.now().minusNanos(durationMs * 1_000_000));
            job.setFinishedAt(LocalDateTime.now());
            job.setDurationMs(durationMs);
            job.setErrorSummary("{}");
            eclJobMapper.insert(job);
        } catch (Exception e) {
            log.warn("failed to write job record", e);
        }
    }

    private TrialCalculationResp buildResponse(String jobId, AssetInput a, String groupLabel, long durationMs) {
        TrialCalculationResp resp = new TrialCalculationResp();
        resp.setJobId(jobId);
        resp.setStatus("SUCCESS");
        resp.setDurationMs(durationMs);
        resp.setAssetId(a.getAssetId());
        resp.setGroupId(a.getGroupId());
        resp.setGroupLabel(groupLabel);
        resp.setProductType(a.getProductType());
        resp.setRatingCode(a.getRatingCode());

        // Stage
        StageResult sr = a.getStageResult();
        resp.setStage(sr != null ? sr.getStage().getLabel() : "-");

        // EAD
        resp.setEad(formatMoney(a.getTotalEad()));

        // LGD
        resp.setLgd(formatPercent(a.getLgdValue()));

        // PD
        resp.setPd12m(formatPercent(a.getPd12m()));
        resp.setPdLifetime(formatPercent(a.getPdLifetime()));

        // ECL
        resp.setEclValue(formatMoney(a.getEclValue()));
        resp.setOverlayAmount(formatMoney(a.getOverlayAmount()));
        resp.setEclFinal(formatMoney(a.getEclFinal()));

        // Exception summary
        StringBuilder ex = new StringBuilder();
        if (a.getGroupException() != null) ex.append("分组:").append(a.getGroupException()).append(";");
        if (a.getPdException() != null) ex.append("PD:").append(a.getPdException()).append(";");
        if (a.getEadException() != null) ex.append("EAD:").append(a.getEadException()).append(";");
        if (a.getLgdException() != null) ex.append("LGD:").append(a.getLgdException()).append(";");
        resp.setExceptionSummary(ex.isEmpty() ? null : ex.toString());

        // Steps
        resp.setSteps(buildSteps(a, sr));

        return resp;
    }

    private List<TrialStepVO> buildSteps(AssetInput a, StageResult sr) {
        List<TrialStepVO> steps = new ArrayList<>();

        // Step 1: 风险分组
        TrialStepVO step1 = step("group", "① 风险分组",
                a.getGroupId() != null && !"GRP_DEFAULT".equals(a.getGroupId())
                        ? "命中分组 " + a.getGroupName() : "兜底分组 GRP_DEFAULT");
        step1.setMetrics(List.of(
                metric("业务条线", nvl(a.getBusinessLine())),
                metric("客户类型", nvl(a.getCustomerType())),
                metric("产品类型", nvl(a.getProductType())),
                metric("行业代码", nvl(a.getIndustryCode())),
                metric("地区代码", nvl(a.getRegionCode())),
                metric("担保类型", nvl(a.getCollateralType()))
        ));
        if (a.getGroupException() != null) step1.setNote("异常：命中兜底分组");
        steps.add(step1);

        // Step 2: 阶段判定
        TrialStepVO step2 = step("stage", "② 阶段判定",
                sr != null ? sr.getStage().getLabel() + " (触发: " +
                        (sr.getTriggerType() != null ? sr.getTriggerType() : "-") + ")" : "-");
        step2.setMetrics(List.of(
                metric("逾期天数", nvl(a.getOverdueDays())),
                metric("CRR 评级", nvl(a.getCrrRating())),
                metric("五级分类", nvl(a.getFiveCategory())),
                metric("违约标识", a.getDefaultFlag() != null && a.getDefaultFlag() ? "是" : "否")
        ));
        if (sr != null && sr.isExceptionFlag()) step2.setNote("异常：走兜底或被回跳阻断");
        steps.add(step2);

        // Step 3: PD
        TrialStepVO step3 = step("pd", "③ PD 取值", "PD_12M = " + formatPercent(a.getPd12m()));
        if (a.getPdDetails() != null) {
            List<TrialScenarioRowVO> rows = new ArrayList<>();
            for (var d : a.getPdDetails()) {
                rows.add(new TrialScenarioRowVO(
                        d.getScenarioName() + " (" + d.getScenarioType() + ")",
                        formatPercent(d.getWeight() != null ? d.getWeight().doubleValue() : 0),
                        formatPercent(d.getPdValue()),
                        formatPercent(d.getWeightedPd()),
                        "BASELINE".equals(d.getScenarioType())));
            }
            step3.setScenarioRows(rows);
        }
        if (a.getPdException() != null) step3.setNote("异常：" + a.getPdException());
        steps.add(step3);

        // Step 4: EAD
        TrialStepVO step4 = step("ead", "④ EAD 计算", "EAD = " + formatMoney(a.getTotalEad()));
        step4.setMetrics(List.of(
                metric("表内 EAD", formatMoney(a.getOnBsEad())),
                metric("表外 EAD", formatMoney(a.getOffBsEad())),
                metric("总 EAD", formatMoney(a.getTotalEad()))
        ));
        steps.add(step4);

        // Step 5: LGD
        TrialStepVO step5 = step("lgd", "⑤ LGD 取值", "LGD = " + formatPercent(a.getLgdValue()));
        if (a.getLgdException() != null) step5.setNote("警告：使用方案默认 LGD");
        steps.add(step5);

        // Step 6: ECL
        TrialStepVO step6 = step("ecl", "⑥ ECL 计算", "ECL = " + formatMoney(a.getEclValue()));
        step6.setMetrics(List.of(
                metric("PD (存续期)", formatPercent(a.getPdLifetime())),
                metric("LGD", formatPercent(a.getLgdValue())),
                metric("EAD", formatMoney(a.getTotalEad())),
                metric("ECL 加权", formatMoney(a.getEclValue()))
        ));
        steps.add(step6);

        // Step 7: Overlay
        TrialStepVO step7 = step("overlay", "⑦ 管理层叠加",
                a.getOverlayAmount() > 0 ? "+ " + formatMoney(a.getOverlayAmount())
                        : "无命中规则");
        step7.setMetrics(List.of(
                metric("叠加金额", formatMoney(a.getOverlayAmount())),
                metric("ECL 最终", formatMoney(a.getEclFinal()))
        ));
        steps.add(step7);

        return steps;
    }

    private TrialStepVO step(String key, String title, String summary) {
        TrialStepVO s = new TrialStepVO();
        s.setKey(key); s.setTitle(title); s.setSummary(summary);
        return s;
    }

    private TrialMetricVO metric(String label, String value) {
        return new TrialMetricVO(label, value, null);
    }

    private String nvl(Object v) { return v != null ? v.toString() : "-"; }

    private String formatMoney(double v) {
        return "¥" + BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPercent(double v) {
        return BigDecimal.valueOf(v * 100).setScale(4, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
