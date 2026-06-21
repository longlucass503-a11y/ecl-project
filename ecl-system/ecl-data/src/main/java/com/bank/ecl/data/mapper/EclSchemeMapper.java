package com.bank.ecl.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ecl.data.entity.EclSchemeEntity;
import org.apache.ibatis.annotations.Select;

public interface EclSchemeMapper extends BaseMapper<EclSchemeEntity> {

    @Select("SELECT * FROM tbl_ecl_scheme WHERE status = 'EFFECTIVE' ORDER BY effective_at DESC LIMIT 1")
    EclSchemeEntity selectEffective();

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(scheme_code, 5) AS SIGNED)), 0) FROM tbl_ecl_scheme")
    int selectMaxSchemeSeq();
}
