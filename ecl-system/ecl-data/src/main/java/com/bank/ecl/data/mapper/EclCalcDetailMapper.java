package com.bank.ecl.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ecl.data.entity.EclCalcDetailEntity;
import org.apache.ibatis.annotations.Insert;

public interface EclCalcDetailMapper extends BaseMapper<EclCalcDetailEntity> {

    @Insert({"INSERT INTO tbl_ecl_calc_detail(job_id, asset_id, scheme_id, calc_date, input_data, " +
            "group_id, group_exception, stage_result, trigger_type, stage_exception, " +
            "pd_details, pd_exception, ead_total, ead_exception, lgd_value, lgd_exception, " +
            "ecl_weighted, ecl_details, ecl_exception, ecl_overlay_total, ecl_final, " +
            "selected_overlay_id, overlay_exception, calc_status, error_summary) " +
            "VALUES(#{jobId}, #{assetId}, #{schemeId}, #{calcDate}, #{inputData}, " +
            "#{groupId}, #{groupException}, #{stageResult}, #{triggerType}, #{stageException}, " +
            "#{pdDetails}, #{pdException}, #{eadTotal}, #{eadException}, #{lgdValue}, #{lgdException}, " +
            "#{eclWeighted}, #{eclDetails}, #{eclException}, #{eclOverlayTotal}, #{eclFinal}, " +
            "#{selectedOverlayId}, #{overlayException}, #{calcStatus}, #{errorSummary}) " +
            "ON DUPLICATE KEY UPDATE " +
            "group_id=VALUES(group_id), group_exception=VALUES(group_exception), " +
            "stage_result=VALUES(stage_result), trigger_type=VALUES(trigger_type), " +
            "stage_exception=VALUES(stage_exception), pd_details=VALUES(pd_details), " +
            "pd_exception=VALUES(pd_exception), ead_total=VALUES(ead_total), " +
            "ead_exception=VALUES(ead_exception), lgd_value=VALUES(lgd_value), " +
            "lgd_exception=VALUES(lgd_exception), ecl_weighted=VALUES(ecl_weighted), " +
            "ecl_details=VALUES(ecl_details), ecl_exception=VALUES(ecl_exception), " +
            "ecl_overlay_total=VALUES(ecl_overlay_total), ecl_final=VALUES(ecl_final), " +
            "selected_overlay_id=VALUES(selected_overlay_id), overlay_exception=VALUES(overlay_exception), " +
            "calc_status=VALUES(calc_status), error_summary=VALUES(error_summary), " +
            "input_data=VALUES(input_data)"})
    void upsert(EclCalcDetailEntity entity);
}
