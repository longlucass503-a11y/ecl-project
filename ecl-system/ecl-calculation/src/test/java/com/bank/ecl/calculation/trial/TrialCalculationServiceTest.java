package com.bank.ecl.calculation.trial;

import com.bank.ecl.calculation.trial.dto.AssetResult;
import com.bank.ecl.calculation.trial.dto.TrialMetricVO;
import com.bank.ecl.calculation.trial.dto.TrialScenarioRowVO;
import com.bank.ecl.calculation.trial.dto.TrialStepVO;
import com.bank.ecl.data.mapper.EclJobMapper;
import com.bank.ecl.data.mapper.EclSchemeMapper;
import com.bank.ecl.data.mapper.RiskGroupMapper;
import com.bank.ecl.engine.core.AssetInput;
import com.bank.ecl.engine.core.EclScenarioResult;
import com.bank.ecl.engine.core.EngineDispatcher;
import com.bank.ecl.engine.core.PdScenarioResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TrialCalculationServiceTest {

    @Mock
    private EngineDispatcher dispatcher;
    @Mock
    private EclSchemeMapper eclSchemeMapper;
    @Mock
    private RiskGroupMapper riskGroupMapper;
    @Mock
    private EclJobMapper eclJobMapper;
    @Mock
    private TrialSourceAssembler trialSourceAssembler;

    private TrialCalculationService service;

    @BeforeEach
    void setUp() {
        service = new TrialCalculationService(dispatcher, eclSchemeMapper, riskGroupMapper,
                eclJobMapper, trialSourceAssembler);
    }

    @Test
    void shouldExposeEadLgdAndScenarioDetailsInTrialSteps() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("LN_001");
        asset.setOnBsEad(800);
        asset.setOffBsEad(100);
        asset.setTotalEad(900);
        asset.setLgdValue(0.2);
        asset.setCollateralPoolId("POOL_001");
        asset.setEclValue(18);
        EclScenarioResult eclScenario = new EclScenarioResult();
        eclScenario.setScenarioCode("BASE");
        eclScenario.setWeight(BigDecimal.valueOf(0.6));
        eclScenario.setScenarioEcl(30);
        eclScenario.setWeightedEcl(18);
        asset.setEclScenarioResults(List.of(eclScenario));

        PdScenarioResult pdScenario = new PdScenarioResult();
        pdScenario.setScenarioType("BASE");
        pdScenario.setScenarioName("基准");
        pdScenario.setWeight(BigDecimal.valueOf(0.6));
        pdScenario.setPdValue(0.02);
        asset.setPdScenarioResults(List.of(pdScenario));

        AssetResult result = service.buildAssetResult(asset, "SCH_001");

        assertTrue(result.getSteps().stream().anyMatch(s -> s.getKey().equals("ead")));
        assertTrue(result.getSteps().stream().anyMatch(s -> s.getKey().equals("lgd")));
        assertTrue(result.getSteps().stream().anyMatch(s -> s.getKey().equals("ecl")));

        // Verify EAD step has detailed breakdown
        TrialStepVO eadStep = result.getSteps().stream()
                .filter(s -> s.getKey().equals("ead")).findFirst().orElseThrow();
        assertTrue(eadStep.getMetrics().stream()
                .anyMatch(m -> m.getLabel().contains("表内 EAD")));
        assertTrue(eadStep.getMetrics().stream()
                .anyMatch(m -> m.getLabel().contains("表外 EAD")));

        // Verify LGD step has collateral pool info
        TrialStepVO lgdStep = result.getSteps().stream()
                .filter(s -> s.getKey().equals("lgd")).findFirst().orElseThrow();
        assertTrue(lgdStep.getMetrics().stream()
                .anyMatch(m -> m.getLabel().contains("押品池")));

        // Verify ECL step has scenario rows
        TrialStepVO eclStep = result.getSteps().stream()
                .filter(s -> s.getKey().equals("ecl")).findFirst().orElseThrow();
        assertFalse(eclStep.getScenarioRows().isEmpty());

        // Verify PD step has scenario rows from PdScenarioResult
        TrialStepVO pdStep = result.getSteps().stream()
                .filter(s -> s.getKey().equals("pd")).findFirst().orElseThrow();
        assertFalse(pdStep.getScenarioRows().isEmpty());
    }

    @Test
    void shouldIncludeEadBreakdownAndLgdDetailsWhenPresent() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("LN_002");
        asset.setTotalEad(500);
        asset.setEadBreakdown("表内: 400; 表外: 100");
        asset.setLgdValue(0.3);
        asset.setCollateralPoolId("POOL_002");
        asset.setLgdDetails("LGD = 0.30 (基于押品类型)");

        AssetResult result = service.buildAssetResult(asset, "SCH_001");

        TrialStepVO eadStep = result.getSteps().stream()
                .filter(s -> s.getKey().equals("ead")).findFirst().orElseThrow();
        assertEquals("表内: 400; 表外: 100", eadStep.getNote());

        TrialStepVO lgdStep = result.getSteps().stream()
                .filter(s -> s.getKey().equals("lgd")).findFirst().orElseThrow();
        assertTrue(lgdStep.getNote().contains("LGD = 0.30"));
    }

    @Test
    void shouldHandleMissingScenarioResultsGracefully() {
        AssetInput asset = new AssetInput();
        asset.setAssetId("LN_003");
        asset.setTotalEad(500);
        asset.setLgdValue(0.3);

        AssetResult result = service.buildAssetResult(asset, "SCH_001");

        TrialStepVO pdStep = result.getSteps().stream()
                .filter(s -> s.getKey().equals("pd")).findFirst().orElseThrow();
        assertTrue(pdStep.getScenarioRows() == null || pdStep.getScenarioRows().isEmpty());

        TrialStepVO eclStep = result.getSteps().stream()
                .filter(s -> s.getKey().equals("ecl")).findFirst().orElseThrow();
        assertTrue(eclStep.getScenarioRows() == null || eclStep.getScenarioRows().isEmpty());
    }
}
