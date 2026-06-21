package com.bank.ecl.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ecl.data.entity.EclJobEntity;
import org.apache.ibatis.annotations.Update;

public interface EclJobMapper extends BaseMapper<EclJobEntity> {

    @Update("UPDATE tbl_ecl_job SET status=#{status}, successCount=#{successCount}, exceptionCount=#{exceptionCount}, finishedAt=CURRENT_TIMESTAMP, durationMs=#{durationMs}, errorSummary=#{errorSummary} WHERE jobId=#{jobId}")
    void updateJobResult(EclJobEntity entity);
}
