package com.bank.ecl.parameter.stage;

import com.bank.ecl.common.constant.SchemeStatus;
import com.bank.ecl.data.entity.CrrRatingDropRuleEntity;
import com.bank.ecl.data.entity.EclSchemeEntity;
import com.bank.ecl.data.entity.StageRuleEntity;
import com.bank.ecl.data.mapper.CrrRatingDropRuleMapper;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.data.mapper.StageRuleMapper;
import com.bank.ecl.parameter.stage.dto.CrrDropRuleCreateReq;
import com.bank.ecl.parameter.stage.dto.CrrDropRuleVO;
import com.bank.ecl.parameter.stage.dto.StageRuleCreateReq;
import com.bank.ecl.parameter.stage.dto.StageRuleVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StageRuleServiceImplTest {

    @Mock private StageRuleMapper stageRuleMapper;
    @Mock private CrrRatingDropRuleMapper crrRatingDropRuleMapper;
    @Mock private EclSchemeMapper eclSchemeMapper;

    @InjectMocks
    private StageRuleServiceImpl stageRuleService;

    @Captor private ArgumentCaptor<StageRuleEntity> stageRuleCaptor;
    @Captor private ArgumentCaptor<CrrRatingDropRuleEntity> crrRuleCaptor;

    @Test
    void createStageRule_WithFrontendFieldNames_ShouldPersistAndReturnAliases() {
        when(eclSchemeMapper.selectById("scheme-1")).thenReturn(draftScheme());
        when(stageRuleMapper.insert(any(StageRuleEntity.class))).thenAnswer(invocation -> {
            StageRuleEntity entity = invocation.getArgument(0);
            entity.setRuleId(100L);
            return 1;
        });

        StageRuleCreateReq req = new StageRuleCreateReq();
        req.setSchemeId("scheme-1");
        req.setGroupId("group-1");
        req.setRuleType("FORWARD");
        req.setSourceStage("");
        req.setTargetStage("STAGE_2");
        req.setPriority(1);
        req.setJsonCondition("{\"logic\":\"OR\",\"conditions\":[]}");

        StageRuleVO result = stageRuleService.createStageRule(req);

        verify(stageRuleMapper).insert(stageRuleCaptor.capture());
        StageRuleEntity inserted = stageRuleCaptor.getValue();
        assertEquals("", inserted.getStageFrom());
        assertEquals("STAGE_2", inserted.getStageTo());
        assertEquals("{\"logic\":\"OR\",\"conditions\":[]}", inserted.getConditions());
        assertEquals("STAGE_2", result.getTargetStage());
        assertEquals("{\"logic\":\"OR\",\"conditions\":[]}", result.getJsonCondition());
    }

    @Test
    void listStageRules_WhenJsonColumnReturnsTextNode_ShouldReturnPlainJsonString() {
        StageRuleEntity entity = new StageRuleEntity();
        entity.setRuleId(100L);
        entity.setSchemeId("scheme-1");
        entity.setGroupId("group-1");
        entity.setRuleType("FORWARD");
        entity.setStageFrom("");
        entity.setStageTo("STAGE_2");
        entity.setPriority(1);
        entity.setConditions("\"{\\\"logic\\\":\\\"OR\\\",\\\"conditions\\\":[]}\"");
        when(stageRuleMapper.selectList(any())).thenReturn(List.of(entity));

        List<StageRuleVO> result = stageRuleService.listStageRules("scheme-1", "group-1");

        assertEquals("{\"logic\":\"OR\",\"conditions\":[]}", result.get(0).getJsonCondition());
        assertEquals("{\"logic\":\"OR\",\"conditions\":[]}", result.get(0).getConditions());
    }

    @Test
    void createCrrDropRule_WithFrontendDowngradeThreshold_ShouldPersistAndReturnRuleIdAlias() {
        when(eclSchemeMapper.selectById("scheme-1")).thenReturn(draftScheme());
        when(crrRatingDropRuleMapper.insert(any(CrrRatingDropRuleEntity.class))).thenAnswer(invocation -> {
            CrrRatingDropRuleEntity entity = invocation.getArgument(0);
            entity.setDropRuleId(200L);
            return 1;
        });

        CrrDropRuleCreateReq req = new CrrDropRuleCreateReq();
        req.setSchemeId("scheme-1");
        req.setGroupId("group-1");
        req.setRatingAgency("MOODY");
        req.setCurrentRating("CRR 3");
        req.setDowngradeThreshold(4);

        CrrDropRuleVO result = stageRuleService.createCrrDropRule(req);

        verify(crrRatingDropRuleMapper).insert(crrRuleCaptor.capture());
        assertEquals("MOODY", crrRuleCaptor.getValue().getRatingAgency());
        assertEquals(4, crrRuleCaptor.getValue().getDropThreshold());
        assertEquals(200L, result.getRuleId());
        assertEquals("MOODY", result.getRatingAgency());
        assertEquals(4, result.getDowngradeThreshold());
    }

    private EclSchemeEntity draftScheme() {
        EclSchemeEntity scheme = new EclSchemeEntity();
        scheme.setSchemeId("scheme-1");
        scheme.setStatus(SchemeStatus.DRAFT.name());
        return scheme;
    }
}
