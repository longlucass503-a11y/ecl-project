package com.bank.ecl.parameter.ccf;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.SchemeStatus;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.data.entity.CcfCurveEntity;
import com.bank.ecl.data.entity.EclSchemeEntity;
import com.bank.ecl.data.mapper.CcfCurveMapper;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.parameter.ccf.dto.CcfCurveCreateReq;
import com.bank.ecl.parameter.ccf.dto.CcfCurveVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CcfServiceImpl implements CcfService {

    private final CcfCurveMapper ccfCurveMapper;
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

    private void validateDaysRange(Integer min, Integer max) {
        if (min != null && max != null && min >= max) {
            throw new EclException(ErrorCode.ECL_006,
                    "commitmentDaysMin 必须小于 commitmentDaysMax，当前: min=" + min + ", max=" + max);
        }
    }

    // ======================== 查询 ========================

    @Override
    public List<CcfCurveVO> listCurves(String schemeId, String productType) {
        if (schemeId == null || schemeId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "schemeId 不能为空");
        }
        LambdaQueryWrapper<CcfCurveEntity> wrapper = new LambdaQueryWrapper<CcfCurveEntity>()
                .eq(CcfCurveEntity::getSchemeId, schemeId);
        // 空字符串表示全集，空值也查询全部
        if (productType != null) {
            wrapper.eq(CcfCurveEntity::getProductType, productType);
        }
        List<CcfCurveEntity> entities = ccfCurveMapper.selectList(wrapper);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    // ======================== 创建 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CcfCurveVO createCurve(CcfCurveCreateReq req) {
        checkSchemeDraft(req.getSchemeId());
        Integer daysMin = firstPresent(req.getCommitmentDaysMin(), req.getDaysMin());
        Integer daysMax = firstPresent(req.getCommitmentDaysMax(), req.getDaysMax());
        validateDaysRequired(daysMin, daysMax);
        validateDaysRange(daysMin, daysMax);

        CcfCurveEntity entity = buildEntity(req.getSchemeId(), req, daysMin, daysMax);
        ccfCurveMapper.insert(entity);
        return toVO(entity);
    }

    // ======================== 更新 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CcfCurveVO updateCurve(Long curveId, CcfCurveCreateReq req) {
        CcfCurveEntity entity = ccfCurveMapper.selectById(curveId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "CCF 曲线不存在: " + curveId);
        }
        checkSchemeDraft(entity.getSchemeId());

        // 更新字段
        if (req.getProductType() != null) {
            entity.setProductType(req.getProductType().isEmpty() ? "" : req.getProductType());
        }
        if (req.getCommitmentType() != null) {
            entity.setCommitmentType(req.getCommitmentType().isEmpty() ? "" : req.getCommitmentType());
        }
        Integer daysMin = firstPresent(req.getCommitmentDaysMin(), req.getDaysMin());
        Integer daysMax = firstPresent(req.getCommitmentDaysMax(), req.getDaysMax());
        if (daysMin != null) {
            entity.setCommitmentDaysMin(daysMin);
        }
        if (daysMax != null) {
            entity.setCommitmentDaysMax(daysMax);
        }
        if (req.getCcfValue() != null) {
            entity.setCcfValue(req.getCcfValue());
        }

        validateDaysRange(entity.getCommitmentDaysMin(), entity.getCommitmentDaysMax());
        ccfCurveMapper.updateById(entity);
        return toVO(entity);
    }

    // ======================== 删除 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurve(Long curveId) {
        CcfCurveEntity entity = ccfCurveMapper.selectById(curveId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "CCF 曲线不存在: " + curveId);
        }
        checkSchemeDraft(entity.getSchemeId());
        ccfCurveMapper.deleteById(curveId);
    }

    // ======================== 批量替换 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateCurves(String schemeId, List<CcfCurveCreateReq> curves) {
        checkSchemeDraft(schemeId);

        // 先删后插
        ccfCurveMapper.delete(
                new LambdaQueryWrapper<CcfCurveEntity>()
                        .eq(CcfCurveEntity::getSchemeId, schemeId));

        if (curves != null && !curves.isEmpty()) {
            for (CcfCurveCreateReq req : curves) {
                Integer daysMin = firstPresent(req.getCommitmentDaysMin(), req.getDaysMin());
                Integer daysMax = firstPresent(req.getCommitmentDaysMax(), req.getDaysMax());
                validateDaysRequired(daysMin, daysMax);
                validateDaysRange(daysMin, daysMax);
                CcfCurveEntity entity = buildEntity(schemeId, req, daysMin, daysMax);
                ccfCurveMapper.insert(entity);
            }
        }
    }

    // ======================== 辅助方法 ========================

    private CcfCurveVO toVO(CcfCurveEntity entity) {
        if (entity == null) return null;
        CcfCurveVO vo = new CcfCurveVO();
        vo.setCurveId(entity.getCurveId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setProductType(entity.getProductType());
        vo.setCommitmentType(entity.getCommitmentType());
        vo.setCommitmentDaysMin(entity.getCommitmentDaysMin());
        vo.setCommitmentDaysMax(entity.getCommitmentDaysMax());
        vo.setDaysMin(entity.getCommitmentDaysMin());
        vo.setDaysMax(entity.getCommitmentDaysMax());
        vo.setCcfValue(entity.getCcfValue());
        return vo;
    }

    private CcfCurveEntity buildEntity(String schemeId, CcfCurveCreateReq req, Integer daysMin, Integer daysMax) {
        CcfCurveEntity entity = new CcfCurveEntity();
        entity.setSchemeId(schemeId);
        // 空字符串表示全集，存储为空字符串以便查询
        entity.setProductType(req.getProductType() != null ? req.getProductType() : "");
        entity.setCommitmentType(req.getCommitmentType() != null ? req.getCommitmentType() : "");
        entity.setCommitmentDaysMin(daysMin);
        entity.setCommitmentDaysMax(daysMax);
        entity.setCcfValue(req.getCcfValue());
        return entity;
    }

    private Integer firstPresent(Integer primary, Integer fallback) {
        return primary != null ? primary : fallback;
    }

    private void validateDaysRequired(Integer min, Integer max) {
        if (min == null || max == null) {
            throw new EclException(ErrorCode.ECL_006, "期限上下限不能为空");
        }
    }
}
