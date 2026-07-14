package com.bank.ecl.parameter.scheme;

import com.bank.ecl.common.model.Result;
import com.bank.ecl.parameter.copy.publish.SchemePublishService;
import com.bank.ecl.parameter.scheme.dto.SchemeCreateReq;
import com.bank.ecl.parameter.scheme.dto.SchemeDefaultParamReq;
import com.bank.ecl.parameter.scheme.dto.SchemeDiffVO;
import com.bank.ecl.parameter.scheme.dto.SchemePublishReq;
import com.bank.ecl.parameter.scheme.dto.SchemeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schemes")
@RequiredArgsConstructor
public class EclSchemeController {

    private final EclSchemeService schemeService;
    private final SchemePublishService publishService;

    @GetMapping
    public Result<List<SchemeVO>> listSchemes(
            @RequestParam(required = false) String status) {
        return Result.success(schemeService.listSchemes(status));
    }

    @GetMapping("/effective")
    public Result<SchemeVO> getEffective() {
        return Result.success(schemeService.getEffectiveScheme());
    }

    @GetMapping("/{schemeId}")
    public Result<SchemeVO> getScheme(@PathVariable String schemeId) {
        return Result.success(schemeService.getScheme(schemeId));
    }

    @PostMapping
    public Result<SchemeVO> createScheme(@Valid @RequestBody SchemeCreateReq req) {
        return Result.success(schemeService.createScheme(req));
    }

    @PostMapping("/copy")
    public Result<SchemeVO> copyFromEffective(@RequestParam String description) {
        return Result.success(schemeService.copyFromEffective(description));
    }

    @PostMapping("/{schemeId}/copy")
    public Result<SchemeVO> copyFromScheme(@PathVariable String schemeId,
                                           @RequestParam(required = false) String description) {
        return Result.success(schemeService.copyFromScheme(schemeId, description));
    }

    @PutMapping("/{schemeId}")
    public Result<SchemeVO> updateScheme(@PathVariable String schemeId,
                                         @Valid @RequestBody SchemeCreateReq req) {
        return Result.success(schemeService.updateScheme(schemeId, req));
    }

    @PutMapping("/{schemeId}/default-params")
    public Result<SchemeVO> updateDefaultParams(@PathVariable String schemeId,
                                                  @Valid @RequestBody SchemeDefaultParamReq req) {
        return Result.success(schemeService.updateDefaultParams(schemeId, req));
    }

    @PutMapping("/{schemeId}/publish")
    public Result<SchemeVO> publishScheme(@PathVariable String schemeId,
                                          @Valid @RequestBody SchemePublishReq req) {
        return Result.success(publishService.publish(schemeId, req));
    }

    @GetMapping("/compare")
    public Result<List<SchemeDiffVO>> compareSchemes(
            @RequestParam String schemeId1,
            @RequestParam String schemeId2) {
        return Result.success(schemeService.compareSchemes(schemeId1, schemeId2));
    }

    @DeleteMapping("/{schemeId}")
    public Result<Void> deleteScheme(@PathVariable String schemeId) {
        schemeService.deleteScheme(schemeId);
        return Result.success();
    }
}
