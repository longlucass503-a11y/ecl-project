package com.bank.ecl.parameter.scheme;

import com.bank.ecl.parameter.scheme.dto.SchemeCreateReq;
import com.bank.ecl.parameter.scheme.dto.SchemeDiffVO;
import com.bank.ecl.parameter.scheme.dto.SchemeDefaultParamReq;
import com.bank.ecl.parameter.scheme.dto.SchemeVO;

import java.util.List;

public interface EclSchemeService {
    SchemeVO createScheme(SchemeCreateReq req);

    SchemeVO copyFromEffective(String description);

    SchemeVO copyFromScheme(String sourceSchemeId, String description);

    SchemeVO getScheme(String schemeId);

    List<SchemeVO> listSchemes(String status);

    SchemeVO getEffectiveScheme();

    List<SchemeDiffVO> compareSchemes(String schemeId1, String schemeId2);

    SchemeVO updateScheme(String schemeId, SchemeCreateReq req);
    SchemeVO updateDefaultParams(String schemeId, SchemeDefaultParamReq req);

    void deleteScheme(String schemeId);
}
