package com.bank.ecl.parameter.scheme;

import com.bank.ecl.common.constant.SchemeStatus;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.util.UuidGenerator;
import com.bank.ecl.data.entity.EclSchemeEntity;
import com.bank.ecl.data.mapper.*;
import com.bank.ecl.parameter.copy.SchemeCopyService;
import com.bank.ecl.parameter.dict.DictService;
import com.bank.ecl.parameter.scheme.dto.SchemeCreateReq;
import com.bank.ecl.parameter.scheme.dto.SchemeDiffVO;
import com.bank.ecl.parameter.scheme.dto.SchemeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EclSchemeServiceImplTest {

    @Mock private EclSchemeMapper schemeMapper;
    @Mock private SchemeCopyService schemeCopyService;

    // Mappers needed by compareSchemes / deleteCascade
    @Mock private RiskGroupMapper riskGroupMapper;
    @Mock private RiskGroupDetailMapper riskGroupDetailMapper;
    @Mock private StageRuleMapper stageRuleMapper;
    @Mock private CrrRatingDropRuleMapper crrRatingDropRuleMapper;
    @Mock private PdScenarioMapper pdScenarioMapper;
    @Mock private PdCurveMapper pdCurveMapper;
    @Mock private LgdCurveMapper lgdCurveMapper;
    @Mock private LgdCollateralDiscountMapper lgdCollateralDiscountMapper;
    @Mock private LgdDepreciationMapper lgdDepreciationMapper;
    @Mock private CcfCurveMapper ccfCurveMapper;
    @Mock private OverlayRuleMapper overlayRuleMapper;

    @Mock private DictService dictService;

    @InjectMocks
    private EclSchemeServiceImpl schemeService;

    @Captor
    private ArgumentCaptor<EclSchemeEntity> entityCaptor;

    private EclSchemeEntity createTestEntity(String code, String status) {
        EclSchemeEntity entity = new EclSchemeEntity();
        entity.setSchemeId(UuidGenerator.uuid());
        entity.setSchemeCode(code);
        entity.setSchemeName("测试方案");
        entity.setSchemeVersion("v1.0");
        entity.setStatus(status);
        entity.setDiscountRate(new BigDecimal("0.0500"));
        entity.setDefaultCcf(BigDecimal.ZERO);
        entity.setDefaultLgd(new BigDecimal("0.4500"));
        entity.setCreatedBy("admin");
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    // ==================== createScheme ====================

    @Test
    void createScheme_ShouldReturnDraftScheme() {
        // Arrange
        SchemeCreateReq req = new SchemeCreateReq();
        req.setSchemeName("2026年Q3方案");
        req.setDescription("测试方案");

        when(schemeMapper.selectMaxSchemeSeq()).thenReturn(0);

        // Act
        SchemeVO result = schemeService.createScheme(req);

        // Assert
        assertNotNull(result);
        assertEquals("DRAFT", result.getStatus());
        assertEquals("草稿", result.getStatusDisplay());
        assertEquals("v1.0", result.getSchemeVersion());
        assertTrue(result.isEditable());

        verify(schemeMapper).insert(entityCaptor.capture());
        EclSchemeEntity captured = entityCaptor.getValue();
        assertNotNull(captured.getSchemeId());
        assertEquals("SCH_001", captured.getSchemeCode());
        assertEquals("DRAFT", captured.getStatus());
        assertEquals("2026年Q3方案", captured.getSchemeName());
        assertEquals("测试方案", captured.getDescription());
        assertEquals("system", captured.getCreatedBy());
        assertEquals(new BigDecimal("0.0500"), captured.getDiscountRate());
        assertEquals(BigDecimal.ZERO, captured.getDefaultCcf());
        assertEquals(new BigDecimal("0.4500"), captured.getDefaultLgd());
    }

    @Test
    void createScheme_WithNullDescription_ShouldHandleGracefully() {
        // Arrange
        SchemeCreateReq req = new SchemeCreateReq();
        req.setSchemeName("方案A");

        when(schemeMapper.selectMaxSchemeSeq()).thenReturn(5);

        // Act
        SchemeVO result = schemeService.createScheme(req);

        // Assert
        assertNotNull(result);
        assertEquals("DRAFT", result.getStatus());
        assertEquals("SCH_006", result.getSchemeCode());
        verify(schemeMapper).insert(entityCaptor.capture());
        assertNull(entityCaptor.getValue().getDescription());
    }

    // ==================== getScheme ====================

    @Test
    void getScheme_WhenExists_ShouldReturnIt() {
        EclSchemeEntity entity = createTestEntity("SCH_001", "DRAFT");
        when(schemeMapper.selectById("SCH_001")).thenReturn(entity);

        SchemeVO result = schemeService.getScheme("SCH_001");

        assertNotNull(result);
        assertEquals("SCH_001", result.getSchemeCode());
        assertEquals("DRAFT", result.getStatus());
    }

    @Test
    void getScheme_WhenNotExists_ShouldThrow() {
        when(schemeMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThrows(EclException.class, () -> schemeService.getScheme("NOT_EXIST"));
    }

    // ==================== getEffectiveScheme ====================

    @Test
    void getEffectiveScheme_WhenExists_ShouldReturnIt() {
        EclSchemeEntity entity = createTestEntity("SCH_001", "EFFECTIVE");
        when(schemeMapper.selectEffective()).thenReturn(entity);

        SchemeVO result = schemeService.getEffectiveScheme();

        assertNotNull(result);
        assertEquals("SCH_001", result.getSchemeCode());
        assertEquals("EFFECTIVE", result.getStatus());
    }

    @Test
    void getEffectiveScheme_WhenNotExists_ShouldThrowException() {
        when(schemeMapper.selectEffective()).thenReturn(null);

        assertThrows(EclException.class, () -> schemeService.getEffectiveScheme());
    }

    // ==================== copyFromEffective ====================

    @Test
    void copyFromEffective_ShouldCreateDraftWithIncrementedVersion() {
        // Arrange
        EclSchemeEntity effective = createTestEntity("SCH_001", "EFFECTIVE");
        effective.setSchemeVersion("v2.0");
        when(schemeMapper.selectEffective()).thenReturn(effective);
        when(schemeMapper.selectMaxSchemeSeq()).thenReturn(1);
        doNothing().when(schemeCopyService).copyAll(anyString(), anyString());

        // Act
        SchemeVO result = schemeService.copyFromEffective("基于 Q2 修改");

        // Assert
        assertNotNull(result);
        assertEquals("DRAFT", result.getStatus());
        assertEquals("v2.1", result.getSchemeVersion());
        assertTrue(result.getSchemeName().contains("副本"));
        assertTrue(result.getSchemeCode().startsWith("SCH_"));
        verify(schemeCopyService).copyAll(eq(effective.getSchemeId()), anyString());
        verify(schemeMapper).insert(entityCaptor.capture());
        assertEquals("基于 Q2 修改", entityCaptor.getValue().getDescription());
    }

    @Test
    void copyFromEffective_WhenNoEffective_ShouldThrow() {
        when(schemeMapper.selectEffective()).thenReturn(null);

        assertThrows(EclException.class, () -> schemeService.copyFromEffective("desc"));
    }

    @Test
    void copyFromEffective_WithNullDescription_ShouldUseDefault() {
        EclSchemeEntity effective = createTestEntity("SCH_001", "EFFECTIVE");
        effective.setSchemeVersion("v1.0");
        when(schemeMapper.selectEffective()).thenReturn(effective);
        when(schemeMapper.selectMaxSchemeSeq()).thenReturn(1);
        doNothing().when(schemeCopyService).copyAll(anyString(), anyString());

        SchemeVO result = schemeService.copyFromEffective(null);

        assertNotNull(result);
        verify(schemeMapper).insert(entityCaptor.capture());
        assertTrue(entityCaptor.getValue().getDescription().contains("SCH_001"));
    }

    @Test
    void copyFromScheme_ShouldCopySpecifiedSourceScheme() {
        EclSchemeEntity source = createTestEntity("SCH_003", "PUBLISHED");
        source.setSchemeId("source-scheme-id");
        source.setSchemeVersion("v2.1");
        source.setSchemeName("2026年Q3方案");
        when(schemeMapper.selectById("source-scheme-id")).thenReturn(source);
        when(schemeMapper.selectMaxSchemeSeq()).thenReturn(3);
        doNothing().when(schemeCopyService).copyAll(anyString(), anyString());

        SchemeVO result = schemeService.copyFromScheme("source-scheme-id", "基于本方案复制");

        assertNotNull(result);
        assertEquals("DRAFT", result.getStatus());
        assertEquals("SCH_004", result.getSchemeCode());
        assertEquals("v2.2", result.getSchemeVersion());
        assertTrue(result.getSchemeName().contains("副本"));
        verify(schemeMapper).insert(entityCaptor.capture());
        EclSchemeEntity inserted = entityCaptor.getValue();
        assertEquals("基于本方案复制", inserted.getDescription());
        verify(schemeCopyService).copyAll(eq("source-scheme-id"), eq(inserted.getSchemeId()));
    }

    @Test
    void copyFromScheme_WhenSourceNotExists_ShouldThrow() {
        when(schemeMapper.selectById("missing-scheme-id")).thenReturn(null);

        assertThrows(EclException.class,
                () -> schemeService.copyFromScheme("missing-scheme-id", "desc"));
        verify(schemeCopyService, never()).copyAll(anyString(), anyString());
    }

    // ==================== listSchemes ====================

    @Test
    void listSchemes_WithStatusFilter_ShouldReturnFiltered() {
        EclSchemeEntity draft = createTestEntity("SCH_001", "DRAFT");
        when(schemeMapper.selectList(any())).thenReturn(List.of(draft));

        List<SchemeVO> result = schemeService.listSchemes("DRAFT");

        assertEquals(1, result.size());
        assertEquals("DRAFT", result.get(0).getStatus());
    }

    @Test
    void listSchemes_WithoutStatus_ShouldReturnAll() {
        EclSchemeEntity draft = createTestEntity("SCH_001", "DRAFT");
        EclSchemeEntity effective = createTestEntity("SCH_002", "EFFECTIVE");
        when(schemeMapper.selectList(any())).thenReturn(List.of(draft, effective));

        List<SchemeVO> result = schemeService.listSchemes(null);

        assertEquals(2, result.size());
    }

    @Test
    void listSchemes_WhenEmpty_ShouldReturnEmptyList() {
        when(schemeMapper.selectList(any())).thenReturn(List.of());

        List<SchemeVO> result = schemeService.listSchemes(null);

        assertTrue(result.isEmpty());
    }

    // ==================== updateScheme ====================

    @Test
    void updateScheme_WhenDraft_ShouldUpdate() {
        EclSchemeEntity draft = createTestEntity("SCH_001", "DRAFT");
        when(schemeMapper.selectById("SCH_001")).thenReturn(draft);

        SchemeCreateReq req = new SchemeCreateReq();
        req.setSchemeName("新方案名");
        req.setDescription("新描述");

        SchemeVO result = schemeService.updateScheme("SCH_001", req);

        assertNotNull(result);
        assertEquals("新方案名", result.getSchemeName());
        verify(schemeMapper).updateById(entityCaptor.capture());
        assertEquals("新方案名", entityCaptor.getValue().getSchemeName());
        assertEquals("新描述", entityCaptor.getValue().getDescription());
    }

    @Test
    void updateScheme_WhenPublished_ShouldThrow() {
        EclSchemeEntity published = createTestEntity("SCH_001", "PUBLISHED");
        when(schemeMapper.selectById("SCH_001")).thenReturn(published);

        SchemeCreateReq req = new SchemeCreateReq();
        req.setSchemeName("new");

        assertThrows(EclException.class, () -> schemeService.updateScheme("SCH_001", req));
    }

    @Test
    void updateScheme_WhenEffective_ShouldThrow() {
        EclSchemeEntity effective = createTestEntity("SCH_001", "EFFECTIVE");
        when(schemeMapper.selectById("SCH_001")).thenReturn(effective);

        SchemeCreateReq req = new SchemeCreateReq();
        req.setSchemeName("new");

        assertThrows(EclException.class, () -> schemeService.updateScheme("SCH_001", req));
    }

    @Test
    void updateScheme_WhenNotExists_ShouldThrow() {
        when(schemeMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThrows(EclException.class, () -> schemeService.updateScheme("NOT_EXIST", new SchemeCreateReq()));
    }

    // ==================== deleteScheme ====================

    @Test
    void deleteScheme_WhenDraft_ShouldDelete() {
        EclSchemeEntity draft = createTestEntity("SCH_001", "DRAFT");
        when(schemeMapper.selectById("SCH_001")).thenReturn(draft);

        // Mock all cascade delete mappers
        when(riskGroupMapper.delete(any())).thenReturn(0);
        when(riskGroupDetailMapper.delete(any())).thenReturn(0);
        when(stageRuleMapper.delete(any())).thenReturn(0);
        when(crrRatingDropRuleMapper.delete(any())).thenReturn(0);
        when(pdScenarioMapper.delete(any())).thenReturn(0);
        when(pdCurveMapper.delete(any())).thenReturn(0);
        when(lgdCurveMapper.delete(any())).thenReturn(0);
        when(lgdCollateralDiscountMapper.delete(any())).thenReturn(0);
        when(lgdDepreciationMapper.delete(any())).thenReturn(0);
        when(ccfCurveMapper.delete(any())).thenReturn(0);
        when(overlayRuleMapper.delete(any())).thenReturn(0);

        schemeService.deleteScheme("SCH_001");

        verify(schemeMapper, times(1)).deleteById(isA(String.class));
        verify(riskGroupMapper).delete(any());
        verify(stageRuleMapper).delete(any());
        verify(pdScenarioMapper).delete(any());
        verify(overlayRuleMapper).delete(any());
    }

    @Test
    void deleteScheme_WhenEffective_ShouldThrow() {
        EclSchemeEntity effective = createTestEntity("SCH_001", "EFFECTIVE");
        when(schemeMapper.selectById("SCH_001")).thenReturn(effective);

        assertThrows(EclException.class, () -> schemeService.deleteScheme("SCH_001"));
        // deleteById should never be called when scheme is not DRAFT
        verify(schemeMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void deleteScheme_WhenExpired_ShouldThrow() {
        EclSchemeEntity expired = createTestEntity("SCH_001", "EXPIRED");
        when(schemeMapper.selectById("SCH_001")).thenReturn(expired);

        assertThrows(EclException.class, () -> schemeService.deleteScheme("SCH_001"));
    }

    @Test
    void deleteScheme_WhenNotExists_ShouldThrow() {
        when(schemeMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThrows(EclException.class, () -> schemeService.deleteScheme("NOT_EXIST"));
    }

    // ==================== compareSchemes ====================

    @Test
    void compareSchemes_WithSameData_ShouldReturnAllSame() {
        // Arrange
        EclSchemeEntity s1 = createTestEntity("SCH_001", "EFFECTIVE");
        EclSchemeEntity s2 = createTestEntity("SCH_002", "DRAFT");
        when(schemeMapper.selectById("SCH_001")).thenReturn(s1);
        when(schemeMapper.selectById("SCH_002")).thenReturn(s2);

        // All mappers return 0 counts (same)
        when(pdScenarioMapper.selectCount(any())).thenReturn(0L);
        when(pdCurveMapper.selectCount(any())).thenReturn(0L);
        when(lgdCurveMapper.selectCount(any())).thenReturn(0L);
        when(lgdCollateralDiscountMapper.selectCount(any())).thenReturn(0L);
        when(lgdDepreciationMapper.selectCount(any())).thenReturn(0L);
        when(ccfCurveMapper.selectCount(any())).thenReturn(0L);
        when(riskGroupMapper.selectCount(any())).thenReturn(0L);
        when(stageRuleMapper.selectCount(any())).thenReturn(0L);
        when(crrRatingDropRuleMapper.selectCount(any())).thenReturn(0L);
        when(overlayRuleMapper.selectCount(any())).thenReturn(0L);

        // Act
        List<SchemeDiffVO> diffs = schemeService.compareSchemes("SCH_001", "SCH_002");

        // Assert
        assertNotNull(diffs);
        assertTrue(diffs.stream().allMatch(SchemeDiffVO::isSame));
        // 6 modules: PD, LGD, CCF, RISK_GROUP, STAGE, OVERLAY
        assertEquals(6, diffs.size());
    }

    @Test
    void compareSchemes_WithDifferentData_ShouldDetectDifferences() {
        // Arrange
        EclSchemeEntity s1 = createTestEntity("SCH_001", "EFFECTIVE");
        EclSchemeEntity s2 = createTestEntity("SCH_002", "DRAFT");
        when(schemeMapper.selectById("SCH_001")).thenReturn(s1);
        when(schemeMapper.selectById("SCH_002")).thenReturn(s2);

        // Each mapper is called twice (schemeId1 then schemeId2).
        // Use sequential returns: first = schemeId1 count, second = schemeId2 count.
        when(pdScenarioMapper.selectCount(any())).thenReturn(3L, 0L);
        when(pdCurveMapper.selectCount(any())).thenReturn(1L, 0L);
        when(lgdCurveMapper.selectCount(any())).thenReturn(2L, 0L);
        when(lgdCollateralDiscountMapper.selectCount(any())).thenReturn(0L, 0L);
        when(lgdDepreciationMapper.selectCount(any())).thenReturn(0L, 0L);
        when(ccfCurveMapper.selectCount(any())).thenReturn(1L, 0L);
        when(riskGroupMapper.selectCount(any())).thenReturn(0L, 0L);
        when(stageRuleMapper.selectCount(any())).thenReturn(1L, 0L);
        when(crrRatingDropRuleMapper.selectCount(any())).thenReturn(0L, 0L);
        when(overlayRuleMapper.selectCount(any())).thenReturn(1L, 0L);

        // Act
        List<SchemeDiffVO> diffs = schemeService.compareSchemes("SCH_001", "SCH_002");

        // Assert
        assertNotNull(diffs);
        assertEquals(6, diffs.size());

        // PD has 4 items in S1, 0 in S2 -> different
        SchemeDiffVO pdDiff = diffs.stream().filter(d -> "PD".equals(d.getModule())).findFirst().orElseThrow();
        assertFalse(pdDiff.isSame());
        assertEquals(4, pdDiff.getChangedItems());

        // LGD has 2 items in S1, 0 in S2 -> different
        SchemeDiffVO lgdDiff = diffs.stream().filter(d -> "LGD".equals(d.getModule())).findFirst().orElseThrow();
        assertFalse(lgdDiff.isSame());
        assertEquals(2, lgdDiff.getChangedItems());

        // CCF different
        SchemeDiffVO ccfDiff = diffs.stream().filter(d -> "CCF".equals(d.getModule())).findFirst().orElseThrow();
        assertFalse(ccfDiff.isSame());

        // RISK_GROUP same (both 0)
        SchemeDiffVO rgDiff = diffs.stream().filter(d -> "RISK_GROUP".equals(d.getModule())).findFirst().orElseThrow();
        assertTrue(rgDiff.isSame());
    }

    @Test
    void compareSchemes_WhenOneNotExists_ShouldThrow() {
        when(schemeMapper.selectById("SCH_001")).thenReturn(null);
        when(schemeMapper.selectById("SCH_002")).thenReturn(createTestEntity("SCH_002", "DRAFT"));

        assertThrows(EclException.class, () -> schemeService.compareSchemes("SCH_001", "SCH_002"));
    }

    @Test
    void compareSchemes_WhenBothNotExist_ShouldThrow() {
        when(schemeMapper.selectById("SCH_001")).thenReturn(null);
        when(schemeMapper.selectById("SCH_002")).thenReturn(null);

        assertThrows(EclException.class, () -> schemeService.compareSchemes("SCH_001", "SCH_002"));
    }

    // ==================== SchemeVO.isEditable ====================

    @Test
    void schemeVO_isEditable_WhenDraft_ShouldBeTrue() {
        EclSchemeEntity entity = createTestEntity("SCH_001", "DRAFT");
        when(schemeMapper.selectById("SCH_001")).thenReturn(entity);

        SchemeVO vo = schemeService.getScheme("SCH_001");
        assertTrue(vo.isEditable());
    }

    @Test
    void schemeVO_isEditable_WhenNonDraft_ShouldBeFalse() {
        EclSchemeEntity entity = createTestEntity("SCH_001", "EFFECTIVE");
        when(schemeMapper.selectById("SCH_001")).thenReturn(entity);

        SchemeVO vo = schemeService.getScheme("SCH_001");
        assertFalse(vo.isEditable());
    }
}
