package com.bank.ecl.parameter.stage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.SchemeStatus;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StageRuleServiceImpl implements StageRuleService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StageRuleMapper stageRuleMapper;
    private final CrrRatingDropRuleMapper crrRatingDropRuleMapper;
    private final EclSchemeMapper eclSchemeMapper;

    // ======================== 公共校验 ========================

    private EclSchemeEntity checkSchemeDraft(String schemeId) {
        EclSchemeEntity scheme = eclSchemeMapper.selectById(schemeId);
        if (scheme == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在: " + schemeId);
        }
        if (!SchemeStatus.DRAFT.name().equals(scheme.getStatus())) {
            throw new EclException(ErrorCode.ECL_006, "仅 DRAFT 状态的方案可修改，当前状态: " + scheme.getStatus());
        }
        return scheme;
    }

    /**
     * 验证 conditions JSON 格式是否合法。
     * 使用 Jackson ObjectMapper 直接解析校验。
     */
    private void validateConditions(String conditions) {
        if (conditions == null || conditions.isBlank()) {
            return;
        }
        try {
            OBJECT_MAPPER.readTree(conditions);
        } catch (Exception e) {
            throw new EclException(ErrorCode.ECL_006, "conditions JSON 格式错误: " + e.getMessage());
        }
    }

    // ======================== 阶段规则 — 查询 ========================

    @Override
    public List<StageRuleVO> listStageRules(String schemeId, String groupId) {
        LambdaQueryWrapper<StageRuleEntity> wrapper = new LambdaQueryWrapper<StageRuleEntity>()
                .eq(StageRuleEntity::getSchemeId, schemeId)
                .eq(StageRuleEntity::getGroupId, groupId)
                .orderByAsc(StageRuleEntity::getPriority);
        List<StageRuleEntity> list = stageRuleMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toStageRuleVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StageRuleVO createStageRule(StageRuleCreateReq req) {
        checkSchemeDraft(req.getSchemeId());
        String conditions = firstPresent(req.getJsonCondition(), req.getConditions());
        validateConditions(conditions);

        StageRuleEntity entity = new StageRuleEntity();
        entity.setSchemeId(req.getSchemeId());
        entity.setGroupId(req.getGroupId());
        entity.setRuleType(req.getRuleType());
        entity.setStageFrom(firstPresent(req.getStageFrom(), req.getSourceStage()));
        entity.setStageTo(firstPresent(req.getStageTo(), req.getTargetStage()));
        entity.setPriority(req.getPriority());
        entity.setObservationDays(req.getObservationDays());
        entity.setConditions(conditions);

        stageRuleMapper.insert(entity);
        return toStageRuleVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StageRuleVO updateStageRule(Long ruleId, StageRuleCreateReq req) {
        StageRuleEntity entity = stageRuleMapper.selectById(ruleId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "阶段规则不存在: " + ruleId);
        }
        checkSchemeDraft(entity.getSchemeId());
        String conditions = firstPresent(req.getJsonCondition(), req.getConditions());
        validateConditions(conditions);

        entity.setRuleType(req.getRuleType());
        entity.setStageFrom(firstPresent(req.getStageFrom(), req.getSourceStage()));
        entity.setStageTo(firstPresent(req.getStageTo(), req.getTargetStage()));
        entity.setPriority(req.getPriority());
        entity.setObservationDays(req.getObservationDays());
        entity.setConditions(conditions);

        stageRuleMapper.updateById(entity);
        return toStageRuleVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStageRule(Long ruleId) {
        StageRuleEntity entity = stageRuleMapper.selectById(ruleId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "阶段规则不存在: " + ruleId);
        }
        checkSchemeDraft(entity.getSchemeId());
        stageRuleMapper.deleteById(ruleId);
    }

    // ======================== CRR 评级下降规则 — 查询 ========================

    @Override
    public List<CrrDropRuleVO> listCrrDropRules(String schemeId, String groupId) {
        LambdaQueryWrapper<CrrRatingDropRuleEntity> wrapper = new LambdaQueryWrapper<CrrRatingDropRuleEntity>()
                .eq(CrrRatingDropRuleEntity::getSchemeId, schemeId)
                .eq(CrrRatingDropRuleEntity::getGroupId, groupId)
                .orderByAsc(CrrRatingDropRuleEntity::getCurrentRating);
        List<CrrRatingDropRuleEntity> list = crrRatingDropRuleMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toCrrDropRuleVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrrDropRuleVO createCrrDropRule(CrrDropRuleCreateReq req) {
        checkSchemeDraft(req.getSchemeId());

        CrrRatingDropRuleEntity entity = new CrrRatingDropRuleEntity();
        entity.setSchemeId(req.getSchemeId());
        entity.setGroupId(req.getGroupId());
        entity.setRatingAgency(normalizeRatingAgency(req.getRatingAgency()));
        entity.setCurrentRating(req.getCurrentRating());
        entity.setDropThreshold(firstPresent(req.getDropThreshold(), req.getDowngradeThreshold()));

        crrRatingDropRuleMapper.insert(entity);
        return toCrrDropRuleVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrrDropRuleVO updateCrrDropRule(Long ruleId, CrrDropRuleCreateReq req) {
        CrrRatingDropRuleEntity entity = crrRatingDropRuleMapper.selectById(ruleId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "CRR 评级下降规则不存在: " + ruleId);
        }
        checkSchemeDraft(entity.getSchemeId());

        entity.setRatingAgency(normalizeRatingAgency(req.getRatingAgency()));
        entity.setCurrentRating(req.getCurrentRating());
        entity.setDropThreshold(firstPresent(req.getDropThreshold(), req.getDowngradeThreshold()));

        crrRatingDropRuleMapper.updateById(entity);
        return toCrrDropRuleVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCrrDropRule(Long ruleId) {
        CrrRatingDropRuleEntity entity = crrRatingDropRuleMapper.selectById(ruleId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "CRR 评级下降规则不存在: " + ruleId);
        }
        checkSchemeDraft(entity.getSchemeId());
        crrRatingDropRuleMapper.deleteById(ruleId);
    }

    // ======================== 辅助方法 ========================

    private StageRuleVO toStageRuleVO(StageRuleEntity entity) {
        StageRuleVO vo = new StageRuleVO();
        vo.setRuleId(entity.getRuleId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setGroupId(entity.getGroupId());
        vo.setRuleType(entity.getRuleType());
        vo.setStageFrom(entity.getStageFrom());
        vo.setStageTo(entity.getStageTo());
        vo.setSourceStage(entity.getStageFrom());
        vo.setTargetStage(entity.getStageTo());
        vo.setPriority(entity.getPriority());
        vo.setObservationDays(entity.getObservationDays());
        String conditions = normalizeJsonColumn(entity.getConditions());
        vo.setConditions(conditions);
        vo.setJsonCondition(conditions);
        return vo;
    }

    private CrrDropRuleVO toCrrDropRuleVO(CrrRatingDropRuleEntity entity) {
        CrrDropRuleVO vo = new CrrDropRuleVO();
        vo.setDropRuleId(entity.getDropRuleId());
        vo.setRuleId(entity.getDropRuleId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setGroupId(entity.getGroupId());
        vo.setRatingAgency(entity.getRatingAgency());
        vo.setCurrentRating(entity.getCurrentRating());
        vo.setDropThreshold(entity.getDropThreshold());
        vo.setDowngradeThreshold(entity.getDropThreshold());
        return vo;
    }

    private String normalizeRatingAgency(String ratingAgency) {
        return ratingAgency != null && !ratingAgency.isBlank() ? ratingAgency : "INTERNAL_CRR";
    }

    private String firstPresent(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private Integer firstPresent(Integer primary, Integer fallback) {
        return primary != null ? primary : fallback;
    }

    private String normalizeJsonColumn(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(value);
            if (node.isTextual()) {
                return node.asText();
            }
        } catch (Exception ignored) {
            return value;
        }
        return value;
    }
}
