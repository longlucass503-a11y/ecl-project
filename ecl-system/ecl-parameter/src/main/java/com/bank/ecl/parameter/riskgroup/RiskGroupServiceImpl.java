package com.bank.ecl.parameter.riskgroup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.SchemeStatus;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.common.util.UuidGenerator;
import com.bank.ecl.data.entity.EclSchemeEntity;
import com.bank.ecl.data.entity.RiskGroupDetailEntity;
import com.bank.ecl.data.entity.RiskGroupEntity;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.data.mapper.RiskGroupDetailMapper;
import com.bank.ecl.data.mapper.RiskGroupMapper;
import com.bank.ecl.data.mapper.StageRuleMapper;
import com.bank.ecl.data.entity.StageRuleEntity;
import com.bank.ecl.parameter.riskgroup.dto.RiskGroupCreateReq;
import com.bank.ecl.parameter.riskgroup.dto.RiskGroupCreateReq.RiskGroupDetailReq;
import com.bank.ecl.parameter.riskgroup.dto.RiskGroupDetailVO;
import com.bank.ecl.parameter.riskgroup.dto.RiskGroupVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RiskGroupServiceImpl implements RiskGroupService {

    private final RiskGroupMapper riskGroupMapper;
    private final RiskGroupDetailMapper riskGroupDetailMapper;
    private final EclSchemeMapper eclSchemeMapper;
    private final StageRuleMapper stageRuleMapper;

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

    private void validateDetails(List<RiskGroupDetailReq> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        // 校验 4 维字段不全为空
        for (int i = 0; i < details.size(); i++) {
            RiskGroupDetailReq d = details.get(i);
            if (d.getSegment() == null && d.getProductType() == null
                    && d.getIndustryCode() == null && d.getCollateralType() == null) {
                throw new EclException(ErrorCode.ECL_006, "明细第 " + (i + 1) + " 行：4 维字段不可全为空，至少填写一个维度");
            }
        }
        // 校验 priority 唯一性（per scheme+group 在创建/更新时由调用方传入的 details 内校验）
        long distinctPriorities = details.stream()
                .map(RiskGroupDetailReq::getPriority)
                .filter(p -> p != null)
                .distinct()
                .count();
        long totalPriorities = details.stream()
                .map(RiskGroupDetailReq::getPriority)
                .filter(p -> p != null)
                .count();
        if (distinctPriorities != totalPriorities) {
            throw new EclException(ErrorCode.ECL_006, "明细中 priority 不能重复");
        }
    }

    // ======================== 查询 ========================

    @Override
    public List<RiskGroupVO> listByScheme(String schemeId) {
        if (schemeId == null || schemeId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "schemeId 不能为空");
        }
        // 查分组主表
        List<RiskGroupEntity> groups = riskGroupMapper.selectList(
                new LambdaQueryWrapper<RiskGroupEntity>()
                        .eq(RiskGroupEntity::getSchemeId, schemeId)
                        .orderByAsc(RiskGroupEntity::getSortOrder));
        if (groups.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量查明细
        List<String> groupIds = groups.stream().map(RiskGroupEntity::getGroupId).collect(Collectors.toList());
        List<RiskGroupDetailEntity> allDetails = riskGroupDetailMapper.selectList(
                new LambdaQueryWrapper<RiskGroupDetailEntity>()
                        .eq(RiskGroupDetailEntity::getSchemeId, schemeId)
                        .in(RiskGroupDetailEntity::getGroupId, groupIds)
                        .orderByAsc(RiskGroupDetailEntity::getPriority));
        // 按 groupId 分组
        return groups.stream().map(g -> {
            RiskGroupVO vo = toGroupVO(g);
            vo.setDetails(allDetails.stream()
                    .filter(d -> d.getGroupId().equals(g.getGroupId()))
                    .map(this::toDetailVO)
                    .collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public RiskGroupVO getGroup(String schemeId, String groupId) {
        RiskGroupEntity entity = riskGroupMapper.selectById(groupId);
        if (entity == null || !entity.getSchemeId().equals(schemeId)) {
            throw new EclException(ErrorCode.ECL_006, "风险分组不存在: " + groupId);
        }
        RiskGroupVO vo = toGroupVO(entity);
        List<RiskGroupDetailEntity> details = riskGroupDetailMapper.selectList(
                new LambdaQueryWrapper<RiskGroupDetailEntity>()
                        .eq(RiskGroupDetailEntity::getSchemeId, schemeId)
                        .eq(RiskGroupDetailEntity::getGroupId, groupId)
                        .orderByAsc(RiskGroupDetailEntity::getPriority));
        vo.setDetails(details.stream().map(this::toDetailVO).collect(Collectors.toList()));
        return vo;
    }

    // ======================== 创建 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RiskGroupVO createGroup(RiskGroupCreateReq req) {
        checkSchemeDraft(req.getSchemeId());

        // 生成 groupId 和 groupCode
        String groupId = UuidGenerator.uuid();
        String groupCode = req.getGroupCode();
        int maxSeq = 0;
        if (groupCode == null || groupCode.isBlank()) {
            maxSeq = riskGroupMapper.selectMaxRiskGroupSeq();
            groupCode = UuidGenerator.generateBizCode("GRP", maxSeq + 1);
        }
        // 校验 groupCode 在方案内唯一
        Long count = riskGroupMapper.selectCount(
            new LambdaQueryWrapper<RiskGroupEntity>()
                .eq(RiskGroupEntity::getSchemeId, req.getSchemeId())
                .eq(RiskGroupEntity::getGroupCode, groupCode));
        if (count > 0) {
            throw new EclException(ErrorCode.ECL_006, "分组编码 " + groupCode + " 已存在，请使用其他编码");
        }

        RiskGroupEntity entity = new RiskGroupEntity();
        entity.setGroupId(groupId);
        entity.setGroupCode(groupCode);
        entity.setSchemeId(req.getSchemeId());
        entity.setGroupName(req.getGroupName());
        entity.setDescription(req.getDescription());
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : nextSortOrder(req.getSchemeId(), maxSeq));
        entity.setCreatedAt(LocalDateTime.now());

        riskGroupMapper.insert(entity);

        // 创建明细
        if (req.getDetails() != null && !req.getDetails().isEmpty()) {
            validateDetails(req.getDetails());
            for (RiskGroupDetailReq detailReq : req.getDetails()) {
                RiskGroupDetailEntity detailEntity = buildDetailEntity(req.getSchemeId(), groupId, detailReq);
                riskGroupDetailMapper.insert(detailEntity);
            }
        }

        return getGroup(req.getSchemeId(), groupId);
    }

    private Integer nextSortOrder(String schemeId, int maxSeq) {
        if (maxSeq > 0) {
            return maxSeq + 1;
        }
        return Math.toIntExact(riskGroupMapper.selectCount(
                new LambdaQueryWrapper<RiskGroupEntity>().eq(RiskGroupEntity::getSchemeId, schemeId))) + 1;
    }

    // ======================== 更新 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RiskGroupVO updateGroup(String groupId, RiskGroupCreateReq req) {
        RiskGroupEntity entity = riskGroupMapper.selectById(groupId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "风险分组不存在: " + groupId);
        }
        // 校验方案 DRAFT
        checkSchemeDraft(entity.getSchemeId());

        // 更新主表
        if (req.getGroupCode() != null && !req.getGroupCode().isBlank()) {
            entity.setGroupCode(req.getGroupCode());
        }
        entity.setGroupName(req.getGroupName());
        entity.setDescription(req.getDescription());
        if (req.getSortOrder() != null) {
            entity.setSortOrder(req.getSortOrder());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        riskGroupMapper.updateById(entity);

        // 替换明细（先删后插）
        riskGroupDetailMapper.delete(
                new LambdaQueryWrapper<RiskGroupDetailEntity>()
                        .eq(RiskGroupDetailEntity::getSchemeId, entity.getSchemeId())
                        .eq(RiskGroupDetailEntity::getGroupId, groupId));

        if (req.getDetails() != null && !req.getDetails().isEmpty()) {
            validateDetails(req.getDetails());
            for (RiskGroupDetailReq detailReq : req.getDetails()) {
                RiskGroupDetailEntity detailEntity = buildDetailEntity(entity.getSchemeId(), groupId, detailReq);
                riskGroupDetailMapper.insert(detailEntity);
            }
        }

        return getGroup(entity.getSchemeId(), groupId);
    }

    // ======================== 删除 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(String schemeId, String groupId) {
        checkSchemeDraft(schemeId);
        RiskGroupEntity entity = riskGroupMapper.selectById(groupId);
        if (entity == null || !entity.getSchemeId().equals(schemeId)) {
            throw new EclException(ErrorCode.ECL_006, "风险分组不存在: " + groupId);
        }
        // 检查是否有关联阶段规则
        long stageRuleCount = stageRuleMapper.selectCount(
                new LambdaQueryWrapper<StageRuleEntity>()
                        .eq(StageRuleEntity::getSchemeId, schemeId)
                        .eq(StageRuleEntity::getGroupId, groupId));
        if (stageRuleCount > 0) {
            throw new EclException(ErrorCode.ECL_006, "分组已关联阶段规则，无法删除");
        }
        // 级联删除明细
        riskGroupDetailMapper.delete(
                new LambdaQueryWrapper<RiskGroupDetailEntity>()
                        .eq(RiskGroupDetailEntity::getSchemeId, schemeId)
                        .eq(RiskGroupDetailEntity::getGroupId, groupId));
        riskGroupMapper.deleteById(groupId);
    }

    // ======================== 替换明细 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDetails(String schemeId, String groupId, List<RiskGroupDetailReq> details) {
        checkSchemeDraft(schemeId);
        RiskGroupEntity entity = riskGroupMapper.selectById(groupId);
        if (entity == null || !entity.getSchemeId().equals(schemeId)) {
            throw new EclException(ErrorCode.ECL_006, "风险分组不存在: " + groupId);
        }
        // 先删后插
        riskGroupDetailMapper.delete(
                new LambdaQueryWrapper<RiskGroupDetailEntity>()
                        .eq(RiskGroupDetailEntity::getSchemeId, schemeId)
                        .eq(RiskGroupDetailEntity::getGroupId, groupId));

        if (details != null && !details.isEmpty()) {
            validateDetails(details);
            for (RiskGroupDetailReq detailReq : details) {
                RiskGroupDetailEntity detailEntity = buildDetailEntity(schemeId, groupId, detailReq);
                riskGroupDetailMapper.insert(detailEntity);
            }
        }
    }

    // ======================== 辅助方法 ========================

    private RiskGroupVO toGroupVO(RiskGroupEntity entity) {
        RiskGroupVO vo = new RiskGroupVO();
        vo.setGroupId(entity.getGroupId());
        vo.setGroupCode(entity.getGroupCode());
        vo.setSchemeId(entity.getSchemeId());
        vo.setGroupName(entity.getGroupName());
        vo.setSortOrder(entity.getSortOrder());
        vo.setDescription(entity.getDescription());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setDetails(new ArrayList<>());
        return vo;
    }

    private RiskGroupDetailVO toDetailVO(RiskGroupDetailEntity entity) {
        RiskGroupDetailVO vo = new RiskGroupDetailVO();
        vo.setDetailId(entity.getDetailId());
        vo.setPriority(entity.getPriority());
        vo.setSegment(entity.getSegment());
        vo.setProductType(entity.getProductType());
        vo.setIndustryCode(entity.getIndustryCode());
        vo.setCollateralType(entity.getCollateralType());
        return vo;
    }

    private RiskGroupDetailEntity buildDetailEntity(String schemeId, String groupId, RiskGroupDetailReq req) {
        RiskGroupDetailEntity entity = new RiskGroupDetailEntity();
        entity.setSchemeId(schemeId);
        entity.setGroupId(groupId);
        entity.setPriority(req.getPriority());
        entity.setSegment(req.getSegment());
        entity.setProductType(req.getProductType());
        entity.setIndustryCode(req.getIndustryCode());
        entity.setCollateralType(req.getCollateralType());
        return entity;
    }
}
