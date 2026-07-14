package com.bank.ecl.parameter.lgd;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.SchemeStatus;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.data.entity.EclSchemeEntity;
import com.bank.ecl.data.entity.LgdCollateralDiscountEntity;
import com.bank.ecl.data.entity.LgdCurveEntity;
import com.bank.ecl.data.entity.LgdDepreciationEntity;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.data.mapper.LgdCollateralDiscountMapper;
import com.bank.ecl.data.mapper.LgdCurveMapper;
import com.bank.ecl.data.mapper.LgdDepreciationMapper;
import com.bank.ecl.parameter.lgd.dto.LgdCollateralDiscountCreateReq;
import com.bank.ecl.parameter.lgd.dto.LgdCollateralDiscountVO;
import com.bank.ecl.parameter.lgd.dto.LgdCurveCreateReq;
import com.bank.ecl.parameter.lgd.dto.LgdCurveVO;
import com.bank.ecl.parameter.lgd.dto.LgdDepreciationCreateReq;
import com.bank.ecl.parameter.lgd.dto.LgdDepreciationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LgdServiceImpl implements LgdService {

    private final LgdCurveMapper lgdCurveMapper;
    private final LgdCollateralDiscountMapper lgdCollateralDiscountMapper;
    private final LgdDepreciationMapper lgdDepreciationMapper;
    private final EclSchemeMapper eclSchemeMapper;

    // ======================== 公共校验 ========================

    private EclSchemeEntity checkSchemeDraft(String schemeId) {
        EclSchemeEntity scheme = eclSchemeMapper.selectById(schemeId);
        if (scheme == null) {
            throw new EclException(ErrorCode.ECL_004, "方案不存在: " + schemeId);
        }
        if (!SchemeStatus.DRAFT.name().equals(scheme.getStatus())) {
            throw new EclException(ErrorCode.ECL_006,
                    "仅 DRAFT 状态的方案可修改，当前状态: " + scheme.getStatus());
        }
        return scheme;
    }

    // ======================== 基准曲线 ========================

    @Override
    public List<LgdCurveVO> listCurves(String schemeId, String groupId) {
        if (schemeId == null || schemeId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "schemeId 不能为空");
        }
        if (groupId == null || groupId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "groupId 不能为空");
        }
        LambdaQueryWrapper<LgdCurveEntity> wrapper = new LambdaQueryWrapper<LgdCurveEntity>()
                .eq(LgdCurveEntity::getSchemeId, schemeId)
                .eq(LgdCurveEntity::getGroupId, groupId);
        List<LgdCurveEntity> entities = lgdCurveMapper.selectList(wrapper);
        return entities.stream().map(this::toCurveVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateCurves(String schemeId, String groupId, List<LgdCurveCreateReq> curves) {
        checkSchemeDraft(schemeId);

        // 先删后插
        lgdCurveMapper.delete(
                new LambdaQueryWrapper<LgdCurveEntity>()
                        .eq(LgdCurveEntity::getSchemeId, schemeId)
                        .eq(LgdCurveEntity::getGroupId, groupId));

        if (curves != null && !curves.isEmpty()) {
            for (LgdCurveCreateReq req : curves) {
                LgdCurveEntity entity = buildCurveEntity(schemeId, groupId, req);
                lgdCurveMapper.insert(entity);
            }
        }
    }

    // ======================== 押品折扣率 ========================

    @Override
    public List<LgdCollateralDiscountVO> listDiscounts(String schemeId) {
        if (schemeId == null || schemeId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "schemeId 不能为空");
        }
        LambdaQueryWrapper<LgdCollateralDiscountEntity> wrapper = new LambdaQueryWrapper<LgdCollateralDiscountEntity>()
                .eq(LgdCollateralDiscountEntity::getSchemeId, schemeId);
        List<LgdCollateralDiscountEntity> entities = lgdCollateralDiscountMapper.selectList(wrapper);
        return entities.stream().map(this::toDiscountVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateDiscounts(String schemeId, List<LgdCollateralDiscountCreateReq> discounts) {
        checkSchemeDraft(schemeId);

        // 先删后插
        lgdCollateralDiscountMapper.delete(
                new LambdaQueryWrapper<LgdCollateralDiscountEntity>()
                        .eq(LgdCollateralDiscountEntity::getSchemeId, schemeId));

        if (discounts != null && !discounts.isEmpty()) {
            for (LgdCollateralDiscountCreateReq req : discounts) {
                LgdCollateralDiscountEntity entity = buildDiscountEntity(schemeId, req);
                lgdCollateralDiscountMapper.insert(entity);
            }
        }
    }

    // ======================== 押品折旧率 ========================

    @Override
    public List<LgdDepreciationVO> listDepreciations(String schemeId, String collateralType) {
        if (schemeId == null || schemeId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "schemeId 不能为空");
        }
        LambdaQueryWrapper<LgdDepreciationEntity> wrapper = new LambdaQueryWrapper<LgdDepreciationEntity>()
                .eq(LgdDepreciationEntity::getSchemeId, schemeId);
        if (collateralType != null && !collateralType.isBlank()) {
            wrapper.eq(LgdDepreciationEntity::getCollateralType, collateralType);
        }
        wrapper.orderByAsc(LgdDepreciationEntity::getCollateralType)
                .orderByAsc(LgdDepreciationEntity::getYearOffset);
        List<LgdDepreciationEntity> entities = lgdDepreciationMapper.selectList(wrapper);
        return entities.stream().map(this::toDepreciationVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateDepreciations(String schemeId, String collateralType,
                                         List<LgdDepreciationCreateReq> items) {
        checkSchemeDraft(schemeId);

        // 校验折旧率必须为负值（折旧是资产价值递减，正值无意义）
        if (items != null) {
            for (LgdDepreciationCreateReq req : items) {
                if (req.getDepreciationRate() != null && req.getDepreciationRate().compareTo(java.math.BigDecimal.ZERO) >= 0) {
                    throw new com.bank.ecl.common.exception.EclException(
                            com.bank.ecl.common.exception.ErrorCode.ECL_006,
                            "depreciationRate 必须为负值，当前值: " + req.getDepreciationRate());
                }
            }
        }

        // 先删后插
        lgdDepreciationMapper.delete(
                new LambdaQueryWrapper<LgdDepreciationEntity>()
                        .eq(LgdDepreciationEntity::getSchemeId, schemeId)
                        .eq(LgdDepreciationEntity::getCollateralType, collateralType));

        if (items != null && !items.isEmpty()) {
            for (LgdDepreciationCreateReq req : items) {
                LgdDepreciationEntity entity = buildDepreciationEntity(schemeId, req);
                lgdDepreciationMapper.insert(entity);
            }
        }
    }

    // ======================== 辅助方法 ========================

    private LgdCurveVO toCurveVO(LgdCurveEntity entity) {
        if (entity == null) return null;
        LgdCurveVO vo = new LgdCurveVO();
        vo.setCurveId(entity.getCurveId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setGroupId(entity.getGroupId());
        vo.setCollateralType(entity.getCollateralType());
        vo.setProductType(entity.getProductType());
        vo.setLgdBaseValue(entity.getLgdBaseValue());
        return vo;
    }

    private LgdCurveEntity buildCurveEntity(String schemeId, String groupId, LgdCurveCreateReq req) {
        LgdCurveEntity entity = new LgdCurveEntity();
        entity.setSchemeId(schemeId);
        entity.setGroupId(groupId);
        entity.setCollateralType(req.getCollateralType());
        entity.setProductType(req.getProductType());
        entity.setLgdBaseValue(req.getLgdBaseValue());
        return entity;
    }

    private LgdCollateralDiscountVO toDiscountVO(LgdCollateralDiscountEntity entity) {
        if (entity == null) return null;
        LgdCollateralDiscountVO vo = new LgdCollateralDiscountVO();
        vo.setDiscountId(entity.getDiscountId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setCollateralCategory(entity.getCollateralCategory());
        vo.setCollateralType(entity.getCollateralType());
        vo.setDiscountRate(entity.getDiscountRate());
        return vo;
    }

    private LgdCollateralDiscountEntity buildDiscountEntity(String schemeId, LgdCollateralDiscountCreateReq req) {
        LgdCollateralDiscountEntity entity = new LgdCollateralDiscountEntity();
        entity.setSchemeId(schemeId);
        entity.setCollateralCategory(req.getCollateralCategory());
        entity.setCollateralType(req.getCollateralType());
        entity.setDiscountRate(req.getDiscountRate());
        return entity;
    }

    private LgdDepreciationVO toDepreciationVO(LgdDepreciationEntity entity) {
        if (entity == null) return null;
        LgdDepreciationVO vo = new LgdDepreciationVO();
        vo.setDepreciationId(entity.getDepreciationId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setCollateralType(entity.getCollateralType());
        vo.setYearOffset(entity.getYearOffset());
        vo.setDepreciationRate(entity.getDepreciationRate());
        return vo;
    }

    private LgdDepreciationEntity buildDepreciationEntity(String schemeId, LgdDepreciationCreateReq req) {
        LgdDepreciationEntity entity = new LgdDepreciationEntity();
        entity.setSchemeId(schemeId);
        entity.setCollateralType(req.getCollateralType());
        entity.setYearOffset(req.getYearOffset());
        entity.setDepreciationRate(req.getDepreciationRate());
        return entity;
    }
}
