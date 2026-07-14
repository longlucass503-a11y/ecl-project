# ECL v3.1 代码审计报告

**审计日期**: 2026-07-01  
**审计范围**: ecl-system 全部 Java 源码（126 个文件）、Liquibase SQL 迁移（16 个文件）、项目配置文件  
**审计人**: Codex AI

---

## 摘要

ECL v3.1 是 IFRS 9 预期信用损失计算系统，按「方案 - 风险分组 - 阶段判定 - PD - EAD - LGD - ECL - 叠加调整 - 输出」的引擎链架构设计。整体架构清晰，模块划分合理，但存在若干影响正确性、健壮性和可维护性的问题，其中 **P0（关键）5 项**、**P1（重要）9 项**、**P2（建议）7 项**。

---

## P0 - 关键问题（必须修复）

### P0-1. PD 引擎到期日校验逻辑有误

**文件**: [`PdEngine.java`](/ecl-system/ecl-engine/src/main/java/com/bank/ecl/engine/pd/PdEngine.java)

```java
// 当前逻辑（81-85行）:
if (asset.getMaturityDate() == null
    || asset.getCalcDate() == null
    || !asset.getMaturityDate().isAfter(asset.getCalcDate())) {
    asset.setPdException("ECL_001");
    return;  // ❌ 到期日 == 当日即阻断，但当日到期的借据仍应计算 ECL
}
```

**问题**: 使用 `!isAfter(calcDate)` 作为阻断条件，即 `maturityDate <= calcDate` 时全部返回异常。但实际上，到期日等于计算日的借据应正常计算，仅应阻断已过期（`isBefore`）的借据。

**影响**: 到期日恰为计量日的借据会被错误标记为 PD 异常，导致 ECL 计算结果不准确。

**建议修复**: 将条件改为 `!asset.getMaturityDate().isAfter(asset.getCalcDate())` → `asset.getMaturityDate().isBefore(asset.getCalcDate())`

---

### P0-2. 阶段规则回跳阻断逻辑缺失对 STAGE_2 → STAGE_1 的阻断检查

**文件**: [`StageEngine.java`](/ecl-system/ecl-engine/src/main/java/com/bank/ecl/engine/stage/StageEngine.java)

```java
// 第 110-118 行:
if (lastStage == Stage.STAGE_3 && targetStage == Stage.STAGE_1) {
    log.debug("[6.2 Stage] asset {} direct rollback STAGE_3 -> STAGE_1 blocked",
                asset.getAssetId());
    return new StageResult(lastStage, "ROLLBACK_BLOCKED", true);
}
```

**问题**: 代码仅硬编码禁止 `STAGE_3 → STAGE_1` 的直跳，但根据 IFRS 9 实务，`STAGE_3 → STAGE_2` 和 `STAGE_2 → STAGE_1` 的回跳同样需要 ROLLBACK 规则的精确匹配（129-139 行）。目前这段硬编码拦截在 ROLLBACK 校验之前，逻辑上没问题，但特殊在于：`STAGE_3 → STAGE_1` **即使有 ROLLBACK 规则也永远被拦截**，这可能过于严格——如果某借据质量显著改善，从 STAGE_3 恢复为 STAGE_1 是被 IFRS 9 允许的。

**影响**: 跨越式回跳被硬编码禁止，限制业务灵活性。

**建议修复**: 移除硬编码阻断，完全交由 ROLLBACK 规则表控制。如果业务确实需要禁止该路径，应在 ROLLBACK 规则中体现（即不配置 STAGE_3→STAGE_1 的回跳规则）。

---

### P0-3. EAD 引擎中 `processOnBsEad` 还款计划折现未排除过期期次

**文件**: [`EadEngine.java`](/ecl-system/ecl-engine/src/main/java/com/bank/ecl/engine/ead/EadEngine.java)

```java
// 第 67-79 行:
for (RepaymentScheduleInput s : schedules) {
    if (s == null || s.getDueDate() == null) continue;
    if (s.getDueDate().isAfter(asset.getCalcDate())) {  // ✅ 过滤掉已到期期次
        double amount = principal + interest;
        double years = ChronoUnit.DAYS.between(asset.getCalcDate(), s.getDueDate()) / 365.0;
        double discounted = amount / Math.pow(1 + discountRate, years);
        sum += discounted;
    }
}
```

**问题**: 虽然代码已过滤了未来期次，但 `DAYS / 365.0` 的折现年限计算存在精度问题—— 365 天作为一年是近似值，金融业惯例使用 365（实际/365）或 360。此外，此处未处理 `discountRate` 为年化利率时的折现期匹配（应使用与利率期限一致的天数惯例）。

**影响**: 折现年限的微小偏差在大规模资产池中会积累为显著金额差异。

**建议修复**: 
- 明确天数计算惯例为 Act/365 或 Act/360，并在方案级别配置
- 对浮点误差做边界保护

---

### P0-4. EAD 引擎空还款计划时重复加总

**文件**: [`EadEngine.java`](/ecl-system/ecl-engine/src/main/java/com/bank/ecl/engine/ead/EadEngine.java)

```java
// 第 81-83 行:
} else {
    double onBsEad = toDouble(asset.getOutstandingBalance()) + toDouble(asset.getAccruedInterest());
    asset.setOnBsEad(onBsEad);
}
```

**问题**: 当无还款计划时，使用 `outstandingBalance + accruedInterest` 作为表内 EAD。但后续 `processOffBsEad` 方法中计算 `totalEad = onBsEad + offBsEad`。如果该资产既有表外额度又有表内余额，EAD 可能出现重复——因为 `outstandingBalance` 可能已包含已提用部分的表内敞口，而表外 CCF 部分不应再包含已提用部分。

**影响**: 对有表外额度的资产可能高估 EAD。

**建议修复**: 计算表外部分时需明确区分「未提用额度 = 授信总额 - 已提用余额」，确保表内表外不重叠。

---

### P0-5. 试算服务 `runTrial` 方法中 `@Transactional` 可能引发大事务问题

**文件**: [`TrialCalculationService.java`](/ecl-system/ecl-calculation/src/main/java/com/bank/ecl/calculation/trial/TrialCalculationService.java)

```java
@Transactional(rollbackFor = Exception.class)
public TrialCalculationResp runTrial(TrialCalculationReq req) { ... }
```

**问题**: `runTrial` 包含全量引擎链执行 + 数据库写入（job 记录 + detail 记录）。当试算涉及大量借据时，事务持续时间长，可能引发数据库连接池耗尽和死锁风险。且引擎内使用 MyBatis-Plus 查询配置数据（通过 `@Transactional` 内嵌的读操作），在 MySQL 的 REPEATABLE READ 隔离级别下可能读到快照旧数据。

**影响**: 大批量试算时性能下降、事务冲突风险。

**建议修复**: 将「引擎执行」从事务中分离——仅在写入阶段开启事务；读取配置数据时使用 `@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)`。

---

## P1 - 重要问题（建议修复）

### P1-1. 引擎链无异常处理——单笔借据异常不应阻断整批

**文件**: `EngineDispatcher.java`、所有 `EclEngine` 实现

所有引擎 `execute()` 方法直接遍历 `CustomerContext` 列表，未对单个客户的异常做 try-catch 隔离。任何一笔借据的处理异常（如 NPE、数据格式错误）会导致整批计算失败。

**建议修复**: 在每个引擎的客户/借据循环内添加 try-catch，异常时对该借据标记异常状态并继续处理其他借据。

---

### P1-2. `OutputEngine` 逐条 INSERT 性能问题

**文件**: [`OutputEngine.java`](/ecl-system/ecl-engine/src/main/java/com/bank/ecl/engine/output/OutputEngine.java)

```java
for (EclCalcDetailEntity entity : batch) {
    calcDetailMapper.insert(entity);  // ❌ 逐条插入
}
```

**问题**: 大量借据（如数十万笔）时逐条 INSERT 性能极差，且未使用批量插入或 MyBatis-Plus 的 `saveBatch`。

**影响**: 大规模批次插入成为性能瓶颈。

**建议修复**: 使用 MyBatis-Plus 的批量插入或 JDBC batch 模式。

---

### P1-3. `StageConditionEvaluator` 使用反射访问字段，性能差且不安全

**文件**: [`StageConditionEvaluator.java`](/ecl-system/ecl-engine/src/main/java/com/bank/ecl/engine/stage/StageConditionEvaluator.java)

```java
private static final Map<String, java.lang.reflect.Field> FIELD_CACHE;
static {
    Map<String, java.lang.reflect.Field> cache = new HashMap<>();
    for (java.lang.reflect.Field field : AssetInput.class.getDeclaredFields()) {
        field.setAccessible(true);
        cache.put(field.getName(), field);
    }
    FIELD_CACHE = Collections.unmodifiableMap(cache);
}
```

**问题**: 
- 使用反射读字段绕过 getter，违反封装原则
- `field.setAccessible(true)` 在 JDK 17+ 模块化环境下可能抛出 `InaccessibleObjectException`
- JSON 键的 snake_case → camelCase 映射脆弱：`getField()` 的 `toCamelCase` 方法遇到非标准字段名（如 `mediaSentiment` 在 JSON 中是 `media_sentiment`）可能映射错误

**影响**: 在 JDK 17+ 环境运行可能报错；字段映射容错性差。

**建议修复**: 
- 将条件字段的取值逻辑改为使用 `AssetInput` 的 getter 方法加 Map 映射
- 或实现 `Map<String, Function<AssetInput, Object>>` 的函数式字段映射

---

### P1-4. `OverlayEngine` 日期过滤缺少时区处理

**文件**: [`OverlayEngine.java`](/ecl-system/ecl-engine/src/main/java/com/bank/ecl/engine/overlay/OverlayEngine.java)

```java
if (calcDate != null) {
    if (rule.getEffectiveDate() != null && rule.getEffectiveDate().isAfter(calcDate)) continue;
    if (rule.getExpiryDate() != null && rule.getExpiryDate().isBefore(calcDate)) continue;
}
```

`LocalDate` 不携带时区信息。如果系统时区与数据库时区不一致，日期比较可能产生偏差。

**建议修复**: 明确统一时区处理，使用 `LocalDate.now(zoneId)` 或统一 `ZoneId.of("Asia/Shanghai")`。

---

### P1-5. PD 引擎 `CurveCache` 构建的 Key 缺少 ratingAgency

**文件**: [`PdEngine.java`](/ecl-system/ecl-engine/src/main/java/com/bank/ecl/engine/pd/PdEngine.java)

```java
private Map<String, Double> buildCurveCache(String schemeId) {
    // ...
    return curves.stream().collect(Collectors.toMap(
        c -> c.getGroupId() + "|" + c.getScenarioId() + "|"
            + normalizeRatingAgency(c.getRatingAgency()) + "|" + c.getRatingCode(),
        c -> c.getPdValue() != null ? c.getPdValue().doubleValue() : 0.0,
        (a, b) -> a));
}
```

缓存使用了 `ratingAgency`，但在 `resolveRatingSource` 中，对外部评级的 agency 字段使用了 `asset.getExtRatingCoThisYear()` 而非标准化后的值。如果同一客户在不同年份的评级机构不同，可能导致缓存 miss。

**建议修复**: 确保缓存 key 中的 ratingAgency 与 `resolveRatingSource` 使用的逻辑一致。

---

### P1-6. `ConditionEvaluator` 与 `StageConditionEvaluator` 存在重复实现

**文件**: 
- `ConditionEvaluator.java`（ecl-common/util）
- `StageConditionEvaluator.java`（ecl-engine/stage）

两套条件评估引擎功能高度重叠，但实现路径不同，维护两份逻辑成本高且容易不一致。

**影响**: 修改条件格式时需同步修改两处，存在遗漏风险。

**建议修复**: 统一为同一套条件评估引擎，ecl-common 层提供通用实现，引擎层复用。

---

### P1-7. `tbl_ecl_calc_detail` 使用 `AUTO_INCREMENT` 可能导致分片问题

在分布式场景下使用自增主键存在分片键选择问题，且 `UNIQUE KEY uk_job_asset(job_id, asset_id)` 限制了同一任务下借据 ID 的唯一性，但在多批次重复运行场景下不够灵活。

**建议修复**: 考虑使用分布式 ID（雪花算法）替换自增主键。

---

### P1-8. 试算单资产入口（非 `loans` 路径）缺少 `amtFinancedCny` 和 `facilityCd`

**文件**: [`TrialCalculationService.java`](/ecl-system/ecl-calculation/src/main/java/com/bank/ecl/calculation/trial/TrialCalculationService.java)

`buildAssetFromReq` 方法未填充 `amtFinancedCny` 和 `facilityCd` 字段，导致通过简化模式（非 loan 表模式）发起的试算在 EAD 按授信分配时可能无法正确分配到资产。

**建议修复**: 补充填充缺失字段，或强制要求 loan 表模式。

---

### P1-9. `SchemeCopyService` 复制方案时未校验 source 和 target 方案 ID 相同

**文件**: `SchemeCopyService.java`

如果传入相同的 `sourceSchemeId == targetSchemeId`，可能造成数据覆盖或主键冲突。

**建议修复**: 复制前校验 sourceId != targetId。

---

## P2 - 建议改进

### P2-1. 引擎扩展性设计：新增引擎需修改 `EngineDispatcher`

当前 `EngineDispatcher` 硬编码了 8 个引擎实例的注入和调用顺序。新增引擎时需要修改调度器代码。

**建议修复**: 使用 `List<EclEngine>` 自动注入 + `@Order` 注解实现引擎链的声明式编排。

---

### P2-2. `EngineDispatcher` 不支持引擎级别的跳过/熔断

所有引擎链式执行，无跳过机制。如果风险分组引擎出错或组件依赖的数据不可用，后续引擎可能收到错误的中间数据。

**建议修复**: 添加引擎级别的前置条件检查和熔断机制。

---

### P2-3. `JobContext` 中的 `Map<String, List<CollateralInput>>` 类型不安全

```java
private Map<String, List<CollateralInput>> collateralsByPool = new HashMap<>();
```

Key 类型为字符串池编号，编译期无法校验。建议使用 `CollateralPoolId` 值对象包装。

---

### P2-4. LGD 引擎抵押品折旧/折扣率缓存 Key 中的 yearOffset 固定为 0

```java
// LgdEngine.java 第 137 行
String depKey = nullSafeKey(coll.getCollateralType(), 0);
```

折旧率查询固定使用 `yearOffset=0`，但押品可能有不同已使用年限，应该根据押品的实际使用年限计算。

**建议修复**: 根据押品启用日期到计量日的年限差动态计算 `yearOffset`。

---

### P2-5. `OverlayServiceImpl.testMatch` 方法中的 EAD 从 `fieldValues` 取不准确

`testMatch` 中的 `ead` 从 `fieldValues.get("ead")` 获取，但 `fieldValues` 来自 `req.getFieldValues()`。命中测试与实际引擎计算的 EAD 值可能不同，导致规则选择结果不一致。

---

### P2-6. 单元测试覆盖率不足

只有 ecl-engine 和 ecl-calculation 模块有单元测试，ecl-parameter 的 servie 层测试覆盖了基础 CRUD 但场景较少。ecl-engine 中复杂的条件评估逻辑（`StageConditionEvaluator`）没有单元测试。

---

### P2-7. 缺少集成测试

项目无集成测试配置，无法验证引擎链的整体正确性。

---

## 数据库 Schema 审计

### SQL 问题

1. **`tbl_risk_group_detail` 的 `UNIQUE KEY` 不包含 `segment`**  
   016 号迁移将 `business_line` 重命名为 `segment`，但唯一约束 `uk_scheme_group_priority (scheme_id, group_id, priority)` 未包含 `segment` 字段。按照匹配逻辑（4 维 AND），同一组下不同 `segment` 但同 `priority` 的规则可能冲突。

2. **ENGINE=InnoDB 缺失**  
   所有 DDL 未显式指定 ENGINE=InnoDB，依赖 MySQL 默认配置，可能因默认引擎不同导致外键约束失效。

3. **`tbl_ecl_calc_detail` 缺少 `INDEX`**  
   按 `jobId` 和 `schemeId` 查询的频率较高，但 `detail_id` 只有 `PRIMARY KEY` 和 `UNIQUE KEY (job_id, asset_id)`，缺少 `schemeId` 的独立索引。

---

## 安全检查

1. **请求日志可能包含敏感数据**  
   `EclJobEntity.requestPayload` 存储完整的试算请求 JSON（含客户名称等 PII），在日志和监控中暴露。

2. **无鉴权机制**  
  全局无 Spring Security 或拦截器，所有 API 端点公开可访问。

3. **无输入大小限制**  
  试算 API 无请求体大小限制，可能被超大请求攻击。

---

## 架构建议

### 1. 试算与正式跑批共享同一引擎链

当前试算和正式跑批使用完全相同的 `EngineDispatcher` 和引擎组件，只是通过 `JobContext.trialMode` 区分。试算模式中不应输出 `tbl_ecl_calc_detail`（当前 `OutputEngine` 无条件写入）。建议在 `OutputEngine` 中检查 `ctx.isTrialMode()`，试算模式跳过数据库写入。

### 2. 条件引擎统一

两套条件评估引擎重复，应统一为同一套设计，减少维护成本。

### 3. 配置管理

当前所有引擎参数（discountRate、defaultCcf、defaultLgd、lgdFloor）从 `tbl_ecl_scheme` 加载，但 `lgdFloor` 在试算中硬编码为 0.1，应从方案配置加载。

---

## 总结

| 严重级别 | 数量 | 关键发现 |
|---------|------|---------|
| P0 | 5 | PD 到期日判断错误、STAGE_3→STAGE_1 硬编码阻断、EAD 折现精度问题、表内外重复、大事务 |
| P1 | 9 | 引擎链异常隔离缺失、逐条 INSERT、反射安全问题、条件评估重复实现、字段映射缺失等 |
| P2 | 7 | 扩展性设计、折旧率 yearOffset 固定、测试覆盖不足等 |

**建议优先修复 P0 问题，尤其是 PD 到期日逻辑修正和试算事务拆分，这两项直接关系到计算结果的正确性和系统稳定性。**

