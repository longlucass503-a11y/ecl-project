package com.bank.ecl.calculation.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.calculation.monitor.dto.EclJobDetailVO;
import com.bank.ecl.calculation.monitor.dto.EclJobVO;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.data.entity.EclCalcDetailEntity;
import com.bank.ecl.data.entity.EclJobEntity;
import com.bank.ecl.data.mapper.EclCalcDetailMapper;
import com.bank.ecl.data.mapper.EclJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EclJobMonitorService {

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
        vo.setRequestPayload(entity.getRequestPayload());
        return vo;
    }

    private EclJobDetailVO toDetailVO(EclCalcDetailEntity entity) {
        EclJobDetailVO vo = new EclJobDetailVO();
        vo.setDetailId(entity.getDetailId());
        vo.setAssetId(entity.getAssetId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setCalcDate(entity.getCalcDate());
        vo.setGroupId(entity.getGroupId());
        vo.setGroupException(entity.getGroupException());
        vo.setStageResult(entity.getStageResult());
        vo.setTriggerType(entity.getTriggerType());
        vo.setStageException(entity.getStageException());
        vo.setPdDetails(entity.getPdDetails());
        vo.setPdException(entity.getPdException());
        vo.setEadTotal(entity.getEadTotal());
        vo.setEadException(entity.getEadException());
        vo.setEadBreakdown(entity.getEadBreakdown());
        vo.setLgdValue(entity.getLgdValue());
        vo.setLgdException(entity.getLgdException());
        vo.setLgdDetails(entity.getLgdDetails());
        vo.setEclWeighted(entity.getEclWeighted());
        vo.setEclDetails(entity.getEclDetails());
        vo.setEclException(entity.getEclException());
        vo.setEclOverlayTotal(entity.getEclOverlayTotal());
        vo.setEclFinal(entity.getEclFinal());
        vo.setSelectedOverlayId(entity.getSelectedOverlayId());
        vo.setCalcStatus(entity.getCalcStatus());
        vo.setErrorSummary(entity.getErrorSummary());
        return vo;
    }

}
