package com.bank.ecl.parameter.pd;

import com.bank.ecl.common.constant.SchemeStatus;
import com.bank.ecl.data.entity.EclSchemeEntity;
import com.bank.ecl.data.entity.PdCurveEntity;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.data.mapper.PdCurveMapper;
import com.bank.ecl.data.mapper.PdScenarioMapper;
import com.bank.ecl.parameter.pd.dto.PdCurveBatchReq;
import com.bank.ecl.parameter.pd.dto.PdCurveVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdServiceImplTest {

    @Mock private PdScenarioMapper pdScenarioMapper;
    @Mock private PdCurveMapper pdCurveMapper;
    @Mock private EclSchemeMapper eclSchemeMapper;

    @InjectMocks
    private PdServiceImpl pdService;

    @Captor private ArgumentCaptor<PdCurveEntity> curveCaptor;

    @Test
    void listCurves_WithScenarioId_ShouldOnlyReturnSelectedScenarioCurves() {
        PdCurveEntity entity = new PdCurveEntity();
        entity.setCurveId(10L);
        entity.setSchemeId("scheme-1");
        entity.setGroupId("group-1");
        entity.setScenarioId(2L);
        entity.setRatingCode("AAA");
        entity.setPdValue(new BigDecimal("0.0012"));
        when(pdCurveMapper.selectList(any())).thenReturn(List.of(entity));

        List<PdCurveVO> result = pdService.listCurves("scheme-1", "group-1", 2L);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getScenarioId());
        assertEquals("AAA", result.get(0).getRatingCode());
    }

    @Test
    void batchUpdateCurves_ShouldPersistSchemeGroupAndScenarioFromFrontendPayload() {
        when(eclSchemeMapper.selectById("scheme-1")).thenReturn(draftScheme());

        PdCurveBatchReq.PdCurveItem item = new PdCurveBatchReq.PdCurveItem();
        item.setScenarioId(2L);
        item.setRatingAgency("MOODY");
        item.setRatingCode("AAA");
        item.setPdValue(new BigDecimal("0.0012"));

        PdCurveBatchReq req = new PdCurveBatchReq();
        req.setSchemeId("scheme-1");
        req.setGroupId("group-1");
        req.setCurves(List.of(item));

        pdService.batchUpdateCurves(req);

        verify(pdCurveMapper).insert(curveCaptor.capture());
        PdCurveEntity inserted = curveCaptor.getValue();
        assertEquals("scheme-1", inserted.getSchemeId());
        assertEquals("group-1", inserted.getGroupId());
        assertEquals(2L, inserted.getScenarioId());
        assertEquals("MOODY", inserted.getRatingAgency());
        assertEquals(new BigDecimal("0.0012"), inserted.getPdValue());
    }

    private EclSchemeEntity draftScheme() {
        EclSchemeEntity scheme = new EclSchemeEntity();
        scheme.setSchemeId("scheme-1");
        scheme.setStatus(SchemeStatus.DRAFT.name());
        return scheme;
    }

    @Test
    void listCurves_WithoutScenarioId_ShouldReturnAllCurves() {
        PdCurveEntity e1 = new PdCurveEntity();
        e1.setCurveId(1L); e1.setSchemeId("scheme-1"); e1.setGroupId("group-1");
        e1.setScenarioId(1L); e1.setRatingCode("CRR1"); e1.setPdValue(new BigDecimal("0.01"));
        PdCurveEntity e2 = new PdCurveEntity();
        e2.setCurveId(2L); e2.setSchemeId("scheme-1"); e2.setGroupId("group-1");
        e2.setScenarioId(2L); e2.setRatingCode("CRR2"); e2.setPdValue(new BigDecimal("0.02"));
        when(pdCurveMapper.selectList(any())).thenReturn(List.of(e1, e2));

        List<PdCurveVO> result = pdService.listCurves("scheme-1", "group-1", null);

        assertEquals(2, result.size());
    }

    @Test
    void listCurves_ShouldSortByRatingOrder() {
        PdCurveEntity crr2 = new PdCurveEntity();
        crr2.setCurveId(1L); crr2.setSchemeId("S"); crr2.setGroupId("G");
        crr2.setScenarioId(1L); crr2.setRatingCode("CRR2"); crr2.setPdValue(new BigDecimal("0.02"));
        PdCurveEntity crr1 = new PdCurveEntity();
        crr1.setCurveId(2L); crr1.setSchemeId("S"); crr1.setGroupId("G");
        crr1.setScenarioId(1L); crr1.setRatingCode("CRR1"); crr1.setPdValue(new BigDecimal("0.01"));
        when(pdCurveMapper.selectList(any())).thenReturn(List.of(crr2, crr1));

        List<PdCurveVO> result = pdService.listCurves("S", "G", 1L);

        assertEquals(2, result.size());
        assertEquals("CRR1", result.get(0).getRatingCode());
        assertEquals("CRR2", result.get(1).getRatingCode());
    }

}
