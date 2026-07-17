package com.bank.ecl.parameter.lgd;

import com.bank.ecl.common.model.Result;
import com.bank.ecl.parameter.lgd.dto.LgdCollateralDiscountCreateReq;
import com.bank.ecl.parameter.lgd.dto.LgdCollateralDiscountVO;
import com.bank.ecl.parameter.lgd.dto.LgdCurveBatchReq;
import com.bank.ecl.parameter.lgd.dto.LgdCurveCreateReq;
import com.bank.ecl.parameter.lgd.dto.LgdCurveVO;
import com.bank.ecl.parameter.lgd.dto.LgdDepreciationBatchReq;
import com.bank.ecl.parameter.lgd.dto.LgdDepreciationCreateReq;
import com.bank.ecl.parameter.lgd.dto.LgdDepreciationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/parameters/lgd")
@RequiredArgsConstructor
public class LgdController {

    private final LgdService lgdService;

    // ======================== 基准曲线 ========================

    @GetMapping("/curves")
    public Result<List<LgdCurveVO>> listCurves(@RequestParam String schemeId,
                                               @RequestParam String groupId) {
        return Result.success(lgdService.listCurves(schemeId, groupId));
    }

    @PostMapping("/curves/batch")
    public Result<Void> batchUpdateCurves(@Valid @RequestBody LgdCurveBatchReq req) {
        List<LgdCurveCreateReq> curves = req.getCurves().stream()
                .map(item -> {
                    LgdCurveCreateReq createReq = new LgdCurveCreateReq();
                    createReq.setSchemeId(req.getSchemeId());
                    createReq.setGroupId(req.getGroupId());
                    createReq.setCollateralType(item.getCollateralType());
                    createReq.setCollateralCategory(item.getCollateralCategory());
                    createReq.setProductType(item.getProductType());
                    createReq.setLgdBaseValue(item.getLgdBaseValue());
                    return createReq;
                })
                .collect(Collectors.toList());
        lgdService.batchUpdateCurves(req.getSchemeId(), req.getGroupId(), curves);
        return Result.success();
    }

    // ======================== 押品折扣率 ========================

    @GetMapping("/collateral-discounts")
    public Result<List<LgdCollateralDiscountVO>> listDiscounts(@RequestParam String schemeId) {
        return Result.success(lgdService.listDiscounts(schemeId));
    }

    @PostMapping("/collateral-discounts/batch")
    public Result<Void> batchUpdateDiscounts(@Valid @RequestBody List<LgdCollateralDiscountCreateReq> req) {
        // 从第一个元素取 schemeId（所有元素 schemeId 应一致）
        if (req == null || req.isEmpty()) {
            return Result.success();
        }
        String schemeId = req.get(0).getSchemeId();
        lgdService.batchUpdateDiscounts(schemeId, req);
        return Result.success();
    }

    // ======================== 押品折旧率 ========================

    @GetMapping("/depreciations")
    public Result<List<LgdDepreciationVO>> listDepreciations(@RequestParam String schemeId,
                                                              @RequestParam(required = false) String collateralType) {
        return Result.success(lgdService.listDepreciations(schemeId, collateralType));
    }

    @PostMapping("/depreciations/batch")
    public Result<Void> batchUpdateDepreciations(@Valid @RequestBody LgdDepreciationBatchReq req) {
        List<LgdDepreciationCreateReq> items = req.getItems().stream()
                .map(item -> {
                    LgdDepreciationCreateReq createReq = new LgdDepreciationCreateReq();
                    createReq.setSchemeId(req.getSchemeId());
                    createReq.setCollateralType(req.getCollateralType());
                    createReq.setYearOffset(item.getYearOffset());
                    createReq.setDepreciationRate(item.getDepreciationRate());
                    return createReq;
                })
                .collect(Collectors.toList());
        lgdService.batchUpdateDepreciations(req.getSchemeId(), req.getCollateralType(), items);
        return Result.success();
    }
}
