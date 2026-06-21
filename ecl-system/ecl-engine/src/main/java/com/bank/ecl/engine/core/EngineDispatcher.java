package com.bank.ecl.engine.core;

import com.bank.ecl.engine.ead.EadEngine;
import com.bank.ecl.engine.ecl.EclCalcEngine;
import com.bank.ecl.engine.lgd.LgdEngine;
import com.bank.ecl.engine.output.OutputEngine;
import com.bank.ecl.engine.overlay.OverlayEngine;
import com.bank.ecl.engine.pd.PdEngine;
import com.bank.ecl.engine.riskgroup.RiskGroupEngine;
import com.bank.ecl.engine.stage.StageEngine;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 引擎调度器 —— 按依赖链顺序执行 8 个引擎。
 *
 * <pre>
 * 6.1 风险分组 ─┬─ 6.3 PD ─┬─ 6.5 LGD ─ 6.6 ECL ─ 6.7 叠加 ─ 6.8 输出
 * 6.2 阶段划分 ─┘ 6.4 EAD ─┘
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class EngineDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EngineDispatcher.class);

    private final RiskGroupEngine riskGroup;
    private final StageEngine stage;
    private final PdEngine pd;
    private final EadEngine ead;
    private final LgdEngine lgd;
    private final EclCalcEngine ecl;
    private final OverlayEngine overlay;
    private final OutputEngine output;

    /**
     * 按依赖链顺序执行全部 8 个引擎。
     */
    public void execute(JobContext ctx) {
        log.info("[Dispatcher] starting engine chain for jobId={}", ctx.getJobId());

        riskGroup.execute(ctx);   // 6.1 风险分组
        stage.execute(ctx);        // 6.2 阶段划分
        pd.execute(ctx);           // 6.3 PD
        ead.execute(ctx);          // 6.4 EAD
        lgd.execute(ctx);         // 6.5 LGD
        ecl.execute(ctx);          // 6.6 ECL
        overlay.execute(ctx);      // 6.7 叠加
        output.execute(ctx);       // 6.8 输出

        log.info("[Dispatcher] engine chain complete for jobId={}", ctx.getJobId());
    }
}
