package com.bank.ecl.calculation.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.calculation.monitor.dto.EclJobDetailVO;
import com.bank.ecl.calculation.monitor.dto.EclJobLogVO;
import com.bank.ecl.calculation.monitor.dto.EclJobStepVO;
import com.bank.ecl.calculation.monitor.dto.EclJobVO;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.data.entity.EclCalcDetailEntity;
import com.bank.ecl.data.entity.EclJobEntity;
import com.bank.ecl.data.mapper.EclCalcDetailMapper;
import com.bank.ecl.data.mapper.EclJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EclJobMonitorService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final EclJobMapper eclJobMapper;
    private final EclCalcDetailMapper eclCalcDetailMapper;

    public List<EclJobVO> listJobs() {
        return eclJobMapper.selectList(new LambdaQueryWrapper<EclJobEntity>()
                        .orderByDesc(EclJobEntity::getStartedAt))
                .stream()
                .map(this::toJobVO)
                .collect(Collectors.toList());
    }

    public EclJobVO getJob(String jobId) {
        EclJobEntity job = eclJobMapper.selectById(jobId);
        if (job == null) {
            throw new EclException(ErrorCode.ECL_006, "计算任务不存在: " + jobId);
        }
        EclJobVO vo = toJobVO(job);
        List<EclCalcDetailEntity> details = eclCalcDetailMapper.selectList(new LambdaQueryWrapper<EclCalcDetailEntity>()
                .eq(EclCalcDetailEntity::getJobId, jobId)
                .orderByAsc(EclCalcDetailEntity::getDetailId));
        vo.setDetails(details.stream().map(this::toDetailVO).collect(Collectors.toList()));
        vo.setSteps(defaultSteps(job));
        vo.setLogs(defaultLogs(job));
        return vo;
    }

    private EclJobVO toJobVO(EclJobEntity entity) {
        EclJobVO vo = new EclJobVO();
        vo.setJobId(entity.getJobId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setCalcDate(entity.getCalcDate());
        vo.setTrialMode(entity.getTrialMode());
        vo.setStatus(entity.getStatus());
        vo.setTotalAssets(entity.getTotalAssets());
        vo.setSuccessCount(entity.getSuccessCount());
        vo.setExceptionCount(entity.getExceptionCount());
        vo.setStartedAt(entity.getStartedAt());
        vo.setFinishedAt(entity.getFinishedAt());
        vo.setDurationMs(entity.getDurationMs());
        vo.setErrorSummary(entity.getErrorSummary());
        return vo;
    }

    private EclJobDetailVO toDetailVO(EclCalcDetailEntity entity) {
        EclJobDetailVO vo = new EclJobDetailVO();
        vo.setDetailId(entity.getDetailId());
        vo.setAssetId(entity.getAssetId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setCalcDate(entity.getCalcDate());
        vo.setGroupId(entity.getGroupId());
        vo.setStageResult(entity.getStageResult());
        vo.setEadTotal(entity.getEadTotal());
        vo.setLgdValue(entity.getLgdValue());
        vo.setEclWeighted(entity.getEclWeighted());
        vo.setEclOverlayTotal(entity.getEclOverlayTotal());
        vo.setEclFinal(entity.getEclFinal());
        vo.setCalcStatus(entity.getCalcStatus());
        vo.setErrorSummary(entity.getErrorSummary());
        return vo;
    }

    private List<EclJobStepVO> defaultSteps(EclJobEntity job) {
        long total = job.getDurationMs() != null && job.getDurationMs() > 0 ? job.getDurationMs() : 100L;
        return List.of(
                new EclJobStepVO("6.1 风险分组匹配", Math.max(1, total * 18 / 100), 18),
                new EclJobStepVO("6.2 阶段判定", Math.max(1, total * 16 / 100), 16),
                new EclJobStepVO("6.3 PD 取值", Math.max(1, total * 22 / 100), 22),
                new EclJobStepVO("6.4/6.5 EAD+LGD", Math.max(1, total * 20 / 100), 20),
                new EclJobStepVO("7.3~7.6 计算层", Math.max(1, total * 16 / 100), 16),
                new EclJobStepVO("写表+汇总", Math.max(1, total * 8 / 100), 8)
        );
    }

    private List<EclJobLogVO> defaultLogs(EclJobEntity job) {
        String start = job.getStartedAt() != null ? job.getStartedAt().format(TIME_FORMATTER) : "--:--:--";
        String end = job.getFinishedAt() != null ? job.getFinishedAt().format(TIME_FORMATTER) : "--:--:--";
        return List.of(
                new EclJobLogVO(start, "INFO", "ECL 计算任务启动"),
                new EclJobLogVO(start, "INFO", "加载方案参数: " + job.getSchemeId()),
                new EclJobLogVO(start, "INFO", "风险分组、阶段、PD、EAD、LGD、ECL 计算链路执行"),
                new EclJobLogVO(end, "INFO", "任务结束，状态: " + job.getStatus())
        );
    }
}
