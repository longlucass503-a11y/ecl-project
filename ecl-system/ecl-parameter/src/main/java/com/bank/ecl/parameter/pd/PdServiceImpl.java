package com.bank.ecl.parameter.pd;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.ecl.common.constant.SchemeStatus;
import com.bank.ecl.common.exception.EclException;
import com.bank.ecl.common.exception.ErrorCode;
import com.bank.ecl.data.entity.EclSchemeEntity;
import com.bank.ecl.data.entity.PdCurveEntity;
import com.bank.ecl.data.entity.PdScenarioEntity;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.data.mapper.PdCurveMapper;
import com.bank.ecl.data.mapper.PdScenarioMapper;
import com.bank.ecl.parameter.pd.dto.PdCurveBatchReq;
import com.bank.ecl.parameter.pd.dto.PdCurveVO;
import com.bank.ecl.parameter.pd.dto.PdMatrixVO;
import com.bank.ecl.parameter.pd.dto.PdScenarioCreateReq;
import com.bank.ecl.parameter.pd.dto.PdScenarioVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PdServiceImpl implements PdService {

    private final PdScenarioMapper pdScenarioMapper;
    private final PdCurveMapper pdCurveMapper;
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

    private void checkWeightSumNotExceed(String schemeId, Long excludeScenarioId, BigDecimal newWeight) {
        List<PdScenarioEntity> existing = pdScenarioMapper.selectList(
                new LambdaQueryWrapper<PdScenarioEntity>()
                        .eq(PdScenarioEntity::getSchemeId, schemeId));
        BigDecimal sum = existing.stream()
                .filter(s -> excludeScenarioId == null || !s.getScenarioId().equals(excludeScenarioId))
                .map(PdScenarioEntity::getWeight)
                .filter(w -> w != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.add(newWeight).compareTo(new BigDecimal("1.0")) > 0) {
            throw new EclException(ErrorCode.ECL_006,
                    "同一方案下 weight 总和不能超过 1.0，当前已有总和: " + sum + "，新增: " + newWeight);
        }
    }

    // ======================== 情景管理 ========================

    @Override
    public List<PdScenarioVO> listScenarios(String schemeId) {
        if (schemeId == null || schemeId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "schemeId 不能为空");
        }
        List<PdScenarioEntity> entities = pdScenarioMapper.selectList(
                new LambdaQueryWrapper<PdScenarioEntity>()
                        .eq(PdScenarioEntity::getSchemeId, schemeId));
        return entities.stream().map(this::toScenarioVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdScenarioVO createScenario(PdScenarioCreateReq req) {
        checkSchemeDraft(req.getSchemeId());

        // 校验 scenarioType 唯一性
        long count = pdScenarioMapper.selectCount(
                new LambdaQueryWrapper<PdScenarioEntity>()
                        .eq(PdScenarioEntity::getSchemeId, req.getSchemeId())
                        .eq(PdScenarioEntity::getScenarioType, req.getScenarioType()));
        if (count > 0) {
            throw new EclException(ErrorCode.ECL_006,
                    "同一方案下 scenarioType 已存在: " + req.getScenarioType());
        }

        // 校验 weight 总和不超过 1.0
        checkWeightSumNotExceed(req.getSchemeId(), null, req.getWeight());

        PdScenarioEntity entity = new PdScenarioEntity();
        entity.setSchemeId(req.getSchemeId());
        entity.setScenarioType(req.getScenarioType());
        entity.setScenarioName(req.getScenarioName());
        entity.setWeight(req.getWeight());
        pdScenarioMapper.insert(entity);
        return toScenarioVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdScenarioVO updateScenario(Long scenarioId, PdScenarioCreateReq req) {
        PdScenarioEntity entity = pdScenarioMapper.selectById(scenarioId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "情景不存在: " + scenarioId);
        }
        checkSchemeDraft(entity.getSchemeId());

        // 校验 scenarioType 唯一性（排除自身）
        if (req.getScenarioType() != null && !req.getScenarioType().equals(entity.getScenarioType())) {
            long count = pdScenarioMapper.selectCount(
                    new LambdaQueryWrapper<PdScenarioEntity>()
                            .eq(PdScenarioEntity::getSchemeId, entity.getSchemeId())
                            .eq(PdScenarioEntity::getScenarioType, req.getScenarioType()));
            if (count > 0) {
                throw new EclException(ErrorCode.ECL_006,
                        "同一方案下 scenarioType 已存在: " + req.getScenarioType());
            }
        }

        // 校验 weight 总和不超过 1.0
        if (req.getWeight() != null) {
            checkWeightSumNotExceed(entity.getSchemeId(), scenarioId, req.getWeight());
            entity.setWeight(req.getWeight());
        }

        if (req.getScenarioType() != null) {
            entity.setScenarioType(req.getScenarioType());
        }
        if (req.getScenarioName() != null) {
            entity.setScenarioName(req.getScenarioName());
        }
        pdScenarioMapper.updateById(entity);
        return toScenarioVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScenario(Long scenarioId) {
        PdScenarioEntity entity = pdScenarioMapper.selectById(scenarioId);
        if (entity == null) {
            throw new EclException(ErrorCode.ECL_006, "情景不存在: " + scenarioId);
        }
        checkSchemeDraft(entity.getSchemeId());

        // 级联删除关联的曲线
        pdCurveMapper.delete(
                new LambdaQueryWrapper<PdCurveEntity>()
                        .eq(PdCurveEntity::getScenarioId, scenarioId));
        pdScenarioMapper.deleteById(scenarioId);
    }

    // ======================== 曲线管理 ========================

    @Override
    public List<PdCurveVO> listCurves(String schemeId, String groupId, Long scenarioId) {
        if (schemeId == null || schemeId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "schemeId 不能为空");
        }
        if (groupId == null || groupId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "groupId 不能为空");
        }
        LambdaQueryWrapper<PdCurveEntity> wrapper = new LambdaQueryWrapper<PdCurveEntity>()
                .eq(PdCurveEntity::getSchemeId, schemeId)
                .eq(PdCurveEntity::getGroupId, groupId);
        if (scenarioId != null) {
            wrapper.eq(PdCurveEntity::getScenarioId, scenarioId);
        }
        List<PdCurveEntity> entities = pdCurveMapper.selectList(wrapper);
        return entities.stream().map(this::toCurveVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateCurves(PdCurveBatchReq req) {
        checkSchemeDraft(req.getSchemeId());

        // 先删后插：删除该方案+分组下所有曲线
        pdCurveMapper.delete(
                new LambdaQueryWrapper<PdCurveEntity>()
                        .eq(PdCurveEntity::getSchemeId, req.getSchemeId())
                        .eq(PdCurveEntity::getGroupId, req.getGroupId()));

        if (req.getCurves() != null && !req.getCurves().isEmpty()) {
            for (PdCurveBatchReq.PdCurveItem item : req.getCurves()) {
                PdCurveEntity entity = new PdCurveEntity();
                entity.setSchemeId(req.getSchemeId());
                entity.setGroupId(req.getGroupId());
                entity.setScenarioId(item.getScenarioId());
                entity.setRatingAgency(item.getRatingAgency());
                entity.setRatingCode(item.getRatingCode());
                entity.setPdValue(item.getPdValue());
                pdCurveMapper.insert(entity);
            }
        }
    }

    @Override
    public PdMatrixVO getMatrix(String schemeId, String groupId) {
        if (schemeId == null || schemeId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "schemeId 不能为空");
        }
        if (groupId == null || groupId.isBlank()) {
            throw new EclException(ErrorCode.ECL_006, "groupId 不能为空");
        }

        // 1. 查询所有情景
        List<PdScenarioEntity> scenarioEntities = pdScenarioMapper.selectList(
                new LambdaQueryWrapper<PdScenarioEntity>()
                        .eq(PdScenarioEntity::getSchemeId, schemeId));
        List<PdScenarioVO> scenarios = scenarioEntities.stream()
                .map(this::toScenarioVO).collect(Collectors.toList());

        // 2. 查询所有曲线
        List<PdCurveEntity> curveEntities = pdCurveMapper.selectList(
                new LambdaQueryWrapper<PdCurveEntity>()
                        .eq(PdCurveEntity::getSchemeId, schemeId)
                        .eq(PdCurveEntity::getGroupId, groupId));

        // 3. 提取有序的 ratingCodes（按出现顺序去重）
        LinkedHashSet<String> ratingCodeSet = new LinkedHashSet<>();
        for (PdCurveEntity c : curveEntities) {
            ratingCodeSet.add(c.getRatingCode());
        }
        List<String> ratingCodes = new ArrayList<>(ratingCodeSet);

        // 4. 构建矩阵：Map<ratingCode, Map<scenarioId, pdValue>>
        Map<String, Map<Long, BigDecimal>> valueMap = curveEntities.stream()
                .collect(Collectors.groupingBy(
                        PdCurveEntity::getRatingCode,
                        Collectors.toMap(PdCurveEntity::getScenarioId, PdCurveEntity::getPdValue)));

        // 5. 填充矩阵 [row][col]
        List<List<BigDecimal>> matrix = new ArrayList<>();
        for (String rc : ratingCodes) {
            List<BigDecimal> row = new ArrayList<>();
            Map<Long, BigDecimal> rowMap = valueMap.getOrDefault(rc, Map.of());
            for (PdScenarioEntity s : scenarioEntities) {
                row.add(rowMap.getOrDefault(s.getScenarioId(), BigDecimal.ZERO));
            }
            matrix.add(row);
        }

        PdMatrixVO vo = new PdMatrixVO();
        vo.setSchemeId(schemeId);
        vo.setGroupId(groupId);
        vo.setRatingCodes(ratingCodes);
        vo.setScenarios(scenarios);
        vo.setMatrix(matrix);
        return vo;
    }

    // ======================== 转换方法 ========================

    private PdScenarioVO toScenarioVO(PdScenarioEntity entity) {
        if (entity == null) return null;
        PdScenarioVO vo = new PdScenarioVO();
        vo.setScenarioId(entity.getScenarioId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setScenarioType(entity.getScenarioType());
        vo.setScenarioName(entity.getScenarioName());
        vo.setWeight(entity.getWeight());
        return vo;
    }

    private PdCurveVO toCurveVO(PdCurveEntity entity) {
        if (entity == null) return null;
        PdCurveVO vo = new PdCurveVO();
        vo.setCurveId(entity.getCurveId());
        vo.setSchemeId(entity.getSchemeId());
        vo.setGroupId(entity.getGroupId());
        vo.setScenarioId(entity.getScenarioId());
        vo.setRatingAgency(entity.getRatingAgency());
        vo.setRatingCode(entity.getRatingCode());
        vo.setPdValue(entity.getPdValue());
        return vo;
    }
}
