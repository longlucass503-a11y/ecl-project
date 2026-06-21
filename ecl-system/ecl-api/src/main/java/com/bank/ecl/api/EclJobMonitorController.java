package com.bank.ecl.api;

import com.bank.ecl.calculation.monitor.EclJobMonitorService;
import com.bank.ecl.calculation.monitor.dto.EclJobVO;
import com.bank.ecl.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ecl/jobs")
@RequiredArgsConstructor
public class EclJobMonitorController {

    private final EclJobMonitorService eclJobMonitorService;

    @GetMapping
    public Result<List<EclJobVO>> listJobs() {
        return Result.success(eclJobMonitorService.listJobs());
    }

    @GetMapping("/{jobId}")
    public Result<EclJobVO> getJob(@PathVariable String jobId) {
        return Result.success(eclJobMonitorService.getJob(jobId));
    }
}
