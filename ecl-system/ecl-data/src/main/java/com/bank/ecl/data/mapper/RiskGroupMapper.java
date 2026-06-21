package com.bank.ecl.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ecl.data.entity.RiskGroupEntity;
import org.apache.ibatis.annotations.Select;

public interface RiskGroupMapper extends BaseMapper<RiskGroupEntity> {

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(group_code, 5) AS INTEGER)), 0) FROM tbl_risk_group")
    int selectMaxRiskGroupSeq();
}
