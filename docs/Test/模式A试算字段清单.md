# 模式 A（单笔简易试算）字段清单

**用途**：去行内找真实借据数据时，按这份清单逐字段核对，能凑齐的就用真实数据替换 Excel 案例，凑不齐的字段可以留空（大部分为选填）。

**接口**：`POST /api/v1/ecl/calculate/trial`，请求体对应 `TrialCalculationReq`（`scope=SINGLE`，不传 `loans` 数组）。

---

## ⚠️ 先看这个：模式 A 测不了的场景

以下字段**接口完全不接受**，即使准备了数据也传不进去，对应的测试用例请跳过，留到模式B（完整多借据导入）再测：

| 缺失字段 | 影响的场景 | 对应用例 |
|---|---|---|
| `businessType`(ON_BS/OFF_BS) | 纯表外业务（如保函）不计表内 EAD 的场景 | 模式B专属用例（TC-17 不受影响，见下方勘误） |
| `normalConsecutiveDays`(正常连续天数) | STAGE_2/3 回跳到更低阶段的判定条件 | **TC-07、TC-08** |

> ⚠️ **勘误（2026-07-09）**：此前认为 TC-17（表外敞口）需要 `businessType` 才能测，经实测证伪——EAD 引擎对任何有 `totalLimit` 的借据都会**无条件**计算表外敞口（授信总额-已用额度）×CCF，`businessType` 只影响"是否叠加计算表内 EAD"（仅用于抑制纯表外项目如保函的表内部分）。因此 TC-17 在模式A下可以正常测试，已实测通过。

CRR 评级下降不受此限——但字段跟你可能设想的不一样，见下表"评级下降"那一行。

---

## 完整字段表

| 分类 | 字段名(接口) | 中文含义 | 类型 | 必填 | 取值/示例 |
|---|---|---|:---:|:---:|---|
| 基本信息 | schemeId | 方案ID | String | 必填 | 系统里已建好的方案ID，不用你找 |
| | assetId | 借据编号 | String | 必填 | 你行内的借据号，如 AST_C01 |
| | calcDate | 计量日期 | Date | 选填 | 不填默认当天，如 2026-07-01 |
| 风险分组用 | segment | 业务板块 | String | 选填 | `对公` / `零售` / `小微` |
| | customerType | 客户类型 | String | 选填 | 如 `企业客户`、`个人客户`（自由文本，不校验枚举）|
| | productType | 产品类型 | String | 选填 | `LC`信用证 / `IL`中长期贷款 / `ST`短期融资 / `PL`个人贷款 / `CL`个人消费贷 / `ML`小微贷款 |
| | industryCode | 行业代码 | String | 选填 | 如 `J`(金融)、`K`(房地产)、`L`(建筑) — 按行内行业代码表 |
| | regionCode | 地区代码 | String | 选填 | 如 `CN-SH` |
| | collateralType | 担保类型 | String | 选填 | `不动产` / `保证` / `信用` / `抵押` / `NONE`(无) |
| 阶段判定用 | lastStage | 上期阶段 | String | 选填 | `STAGE_1` / `STAGE_2` / `STAGE_3`，首次计算可不填 |
| | overdueDays | 逾期天数 | Integer | 选填 | 如 0、45、120 |
| | crrRating | 当前CRR评级 | String | 选填 | `CRR1`~`CRR8`，或外部评级如穆迪 `Aaa`/`Baa2` 等 |
| | fiveCategory | 五级分类 | String | 选填 | `正常` / `关注` / `次级` / `可疑` / `损失` |
| | defaultFlag | 违约标识 | Boolean | 选填 | true / false |
| | mediaSentiment | 舆情严重程度 | String | 选填 | 如 `轻度`/`中度`/`重度`（自由文本） |
| **评级下降** | ratingDropLevels | 评级下降级数 | Integer | 选填 | ⚠️直接告诉系统"降了几级"，不是传去年/今年两个评级让系统算，如降2级就填 `2` |
| PD用 | ratingCode | PD查询用评级代码 | String | 选填 | 需要能在方案的PD曲线矩阵里查到，通常同 crrRating |
| | maturityDate | 到期日 | Date | 选填 | 如 2028-06-21 |
| EAD用 | outstandingBalance | 未偿余额 | Decimal | 选填 | 如 95000 |
| | accruedInterest | 应计利息 | Decimal | 选填 | 如 500 |
| | totalLimit | 授信总额 | Decimal | 选填 | 如 200000 |
| | commitmentType | 承诺类型 | String | 选填 | `可撤销` / `不可撤销`（对应CCF曲线，可撤销CCF=0）|
| | commitmentDays | 承诺剩余天数 | Integer | 选填 | 用于匹配CCF曲线的天数区间，如 365 |
| | amtFinancedCny | 融资本金(人民币) | Decimal | 选填 | 如 100000 |
| | facilityCd | 关联授信编号 | String | 选填 | 仅作标识用，不影响计算 |

---

## 示例（来自 试算案例.xlsx，案例 C01）

```json
{
  "schemeId": "(当前方案ID)",
  "assetId": "AST_C01",
  "calcDate": "2026-07-01",
  "scope": "SINGLE",
  "segment": "对公",
  "customerType": "企业客户",
  "productType": "LC",
  "industryCode": "J",
  "collateralType": "不动产",
  "overdueDays": 0,
  "fiveCategory": "正常",
  "defaultFlag": false,
  "crrRating": "CRR3",
  "ratingCode": "CRR3",
  "maturityDate": "2028-06-21",
  "outstandingBalance": 95000,
  "accruedInterest": 500,
  "totalLimit": 200000,
  "commitmentType": "不可撤销",
  "commitmentDays": 365,
  "amtFinancedCny": 100000,
  "facilityCd": "FC001"
}
```

---

## 找真实数据时的建议

1. **优先找覆盖面广的借据**：一笔借据同时有逾期记录、评级变动、抵押物信息的，比"干净"的正常借据更有测试价值。
2. **表外/回跳场景不用特意找**：反正模式A传不了，等模式B阶段再补。
3. **行业代码、地区代码等编码类字段**：直接用行内真实编码即可，不需要跟 `完整减值方案.md` 里的示例值（J/K/L这种）对应，只要保证同一批测试数据内部一致、能在系统里配出对应的风险分组匹配规则即可。
4. **金额类字段**：用真实数值即可，系统按方案里配置的折现率/CCF/LGD 曲线动态计算，不需要凑出特定的"整数"结果。
