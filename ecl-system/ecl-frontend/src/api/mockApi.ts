import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { Result } from './request';

type HttpMethod = 'get' | 'post' | 'put' | 'delete' | string;
type Payload = Record<string, unknown>;

const now = '2026-06-21 09:00:00';
const schemeId = 'mock-sch-003';
const groupId = 'mock-grp-001';

const schemes = [
  {
    schemeId,
    schemeCode: 'SCH_003',
    schemeName: '2026年Q3 v2.1',
    schemeVersion: '2.1',
    status: 'EFFECTIVE',
    statusDisplay: '已生效',
    effectiveDate: '2026-06-01',
    discountRate: 4.35,
    defaultCcf: 1,
    defaultLgd: 0.45,
    lgdFloor: 0.1,
    createdBy: '张工',
    createdAt: '2026-05-25 14:32',
    description: '基于 Q2 调整 PD 和 LGD',
    editable: false,
  },
  {
    schemeId: 'mock-sch-004',
    schemeCode: 'SCH_004',
    schemeName: '2026年Q3 v3.0 测试',
    schemeVersion: '3.0',
    status: 'DRAFT',
    statusDisplay: '草稿',
    effectiveDate: '',
    discountRate: 4.35,
    defaultCcf: 1,
    defaultLgd: 0.45,
    lgdFloor: 0.1,
    createdBy: '张工',
    createdAt: '2026-06-19 10:00',
    description: '试算 PD 新模型',
    editable: true,
  },
];

const riskGroups = [
  {
    groupId,
    schemeId,
    groupCode: 'GRP_001',
    groupName: '对公业务',
    sortOrder: 1,
    description: '对公客户与一般贷款资产',
    createdAt: now,
    details: [
      {
        detailId: 'mock-rgd-001',
        groupId,
        priority: 1,
        segment: '公司金融',
        customerType: '对公',
        productType: '流动资金贷款',
        industryCode: 'C',
        regionCode: '',
        collateralType: '抵押',
      },
      {
        detailId: 'mock-rgd-002',
        groupId,
        priority: 2,
        segment: '公司金融',
        customerType: '对公',
        productType: '',
        industryCode: 'J',
        regionCode: '',
        collateralType: '',
      },
    ],
  },
  {
    groupId: 'mock-grp-002',
    schemeId,
    groupCode: 'GRP_002',
    groupName: '银行同业',
    sortOrder: 2,
    description: '同业授信与同业资产',
    createdAt: now,
    details: [],
  },
];

const stageRules = [
  {
    ruleId: 'mock-stage-forward-1',
    schemeId,
    groupId,
    ruleType: 'FORWARD',
    sourceStage: '',
    targetStage: 'STAGE_3',
    priority: 1,
    jsonCondition: JSON.stringify({
      logic: 'OR',
      conditions: [
        { type: '逾期天数', operator: 'gte', value: 91 },
        { type: '五级分类', operator: 'in', values: ['次级', '可疑', '损失'] },
      ],
    }),
  },
  {
    ruleId: 'mock-stage-forward-2',
    schemeId,
    groupId,
    ruleType: 'FORWARD',
    sourceStage: '',
    targetStage: 'STAGE_2',
    priority: 2,
    jsonCondition: JSON.stringify({
      logic: 'OR',
      conditions: [
        { type: '逾期天数', operator: 'gte', value: 31 },
        { type: 'CRR 评级下降', operator: 'gte', value: 3 },
      ],
    }),
  },
  {
    ruleId: 'mock-stage-default',
    schemeId,
    groupId,
    ruleType: 'FORWARD',
    sourceStage: '',
    targetStage: 'STAGE_1',
    priority: 99,
    jsonCondition: '',
  },
  {
    ruleId: 'mock-stage-rollback-1',
    schemeId,
    groupId,
    ruleType: 'ROLLBACK',
    sourceStage: 'STAGE_3',
    targetStage: 'STAGE_2',
    priority: 1,
    observationDays: 180,
    jsonCondition: JSON.stringify({
      logic: 'AND',
      conditions: [
      ],
    }),
  },
];

const ratingRules = [
  { ruleId: 'mock-crr-1', schemeId, groupId, currentRating: 'CRR 1', downgradeThreshold: 5 },
  { ruleId: 'mock-crr-2', schemeId, groupId, currentRating: 'CRR 2', downgradeThreshold: 5 },
  { ruleId: 'mock-crr-5', schemeId, groupId, currentRating: 'CRR 5', downgradeThreshold: 3 },
  { ruleId: 'mock-crr-8', schemeId, groupId, currentRating: 'CRR 8', downgradeThreshold: 2 },
];

const scenarios = [
  { scenarioId: 'mock-scn-opt', schemeId, scenarioType: 'OPTIMISTIC', scenarioName: '乐观情景', weight: 0.2, createdAt: now },
  { scenarioId: 'mock-scn-base', schemeId, scenarioType: 'BASELINE', scenarioName: '基准情景', weight: 0.6, createdAt: now },
  { scenarioId: 'mock-scn-pes', schemeId, scenarioType: 'PESSIMISTIC', scenarioName: '悲观情景', weight: 0.2, createdAt: now },
];

const ratingCodes = ['CRR 1', 'CRR 2', 'CRR 3', 'CRR 4', 'CRR 5', 'CRR 6', 'CRR 7', 'CRR 8', 'CRR 9'];

const pdCurves = ratingCodes.map((ratingCode, index) => ({
  curveId: `mock-pd-${index + 1}`,
  scenarioId: 'mock-scn-base',
  ratingCode,
  pdValue: 0.00005 * Math.pow(2, index),
}));

const lgdCurves = [
  { curveId: 'mock-lgd-1', schemeId, groupId, collateralType: 'MORTGAGE', productType: 'LOAN', lgdBaseValue: 0.35 },
  { curveId: 'mock-lgd-2', schemeId, groupId, collateralType: 'PLEDGE', productType: 'LOAN', lgdBaseValue: 0.28 },
  { curveId: 'mock-lgd-3', schemeId, groupId, collateralType: 'NONE', productType: 'LOAN', lgdBaseValue: 0.65 },
];

const collateralDiscounts = [
  { discountId: 'mock-discount-1', schemeId, collateralType: 'MORTGAGE', discountRate: 0.75 },
  { discountId: 'mock-discount-2', schemeId, collateralType: 'PLEDGE', discountRate: 0.65 },
];

const depreciations = [
  { depreciationId: 'mock-dep-1', schemeId, collateralType: 'MORTGAGE', yearOffset: 0, depreciationRate: 0 },
  { depreciationId: 'mock-dep-2', schemeId, collateralType: 'MORTGAGE', yearOffset: 1, depreciationRate: 0.05 },
  { depreciationId: 'mock-dep-3', schemeId, collateralType: 'MORTGAGE', yearOffset: 2, depreciationRate: 0.1 },
];

const ccfCurves = [
  { curveId: 'mock-ccf-1', schemeId, productType: '授信额度', commitmentType: '可撤销', daysMin: 0, daysMax: 365, ccfValue: 0.2 },
  { curveId: 'mock-ccf-2', schemeId, productType: '授信额度', commitmentType: '不可撤销', daysMin: 0, daysMax: 365, ccfValue: 0.5 },
  { curveId: 'mock-ccf-3', schemeId, productType: '信用证', commitmentType: '表外承诺', daysMin: 366, daysMax: 99999, ccfValue: 0.75 },
];

const overlayRules = [
  {
    overlayId: 'mock-overlay-1',
    schemeId,
    groupId,
    ruleName: '房地产业专项',
    overlayType: 'SPECIAL',
    adjustmentType: 'PERCENTAGE',
    adjustmentValue: 0.01,
    priority: 1,
    effectiveDate: '2026-04-01',
    expiryDate: '2026-12-31',
    conditions: '{"industryCode":["J","K"]}',
    enabled: true,
  },
  {
    overlayId: 'mock-overlay-2',
    schemeId,
    groupId,
    ruleName: '模型校正',
    overlayType: 'CALIBRATION',
    adjustmentType: 'ADDBP',
    adjustmentValue: 50,
    priority: 2,
    effectiveDate: '2026-01-01',
    conditions: '{}',
    enabled: true,
  },
];

function readBody(data: unknown): Payload {
  if (!data) return {};
  if (typeof data === 'string') {
    try {
      return JSON.parse(data) as Payload;
    } catch {
      return {};
    }
  }
  if (typeof data === 'object') return data as Payload;
  return {};
}

function one<T extends { [key: string]: unknown }>(items: T[], key: string, value?: unknown): T | undefined {
  return items.find((item) => item[key] === value);
}

function createFromBody(config: InternalAxiosRequestConfig, idKey: string): Payload {
  const body = readBody(config.data);
  return {
    ...body,
    [idKey]: `mock-${Date.now()}`,
    createdAt: now,
  };
}

function payloadFor(config: InternalAxiosRequestConfig): unknown {
  const url = config.url || '';
  const method = (config.method || 'get').toLowerCase() as HttpMethod;
  const params = (config.params || {}) as Record<string, unknown>;

  if (method !== 'get') {
    if (url.includes('/test-match')) {
      return {
        hasMatch: true,
        selectedRule: overlayRules[0],
        effectiveRatio: 0.01,
        matchedRules: overlayRules,
      };
    }
    if (method === 'put' && url.includes('/default-params')) {
      const body = readBody(config.data);
      const sid = url.split('/').at(-2);
      const target = one(schemes, 'schemeId', sid);
      if (target) Object.assign(target, body);
      return { ...target, ...body };
    }

    if (method === 'post') {
      if (/\/v1\/schemes\/[^/]+\/copy$/.test(url)) {
        const sourceId = url.split('/').at(-2);
        const source = one(schemes, 'schemeId', sourceId) || schemes[0];
        return {
          ...source,
          schemeId: `mock-sch-${Date.now()}`,
          schemeCode: `SCH_${String(schemes.length + 1).padStart(3, '0')}`,
          schemeName: `${source.schemeName}(副本)`,
          status: 'DRAFT',
          statusDisplay: '草稿',
          editable: true,
          description: String(params.description || `基于 ${source.schemeCode} 复制`),
          createdAt: now,
        };
      }
      if (url.includes('/schemes')) return createFromBody(config, 'schemeId');
      if (url.includes('/risk-groups')) return createFromBody(config, 'groupId');
      if (url.includes('/stage-rules/crr-drop')) return createFromBody(config, 'ruleId');
      if (url.includes('/stage-rules')) return createFromBody(config, 'ruleId');
      if (url.includes('/pd/scenarios')) return createFromBody(config, 'scenarioId');
      if (url.includes('/lgd/curves')) return createFromBody(config, 'curveId');
      if (url.includes('/lgd/collateral-discounts')) return createFromBody(config, 'discountId');
      if (url.includes('/lgd/depreciations')) return createFromBody(config, 'depreciationId');
      if (url.includes('/ccf/curves')) return createFromBody(config, 'curveId');
      if (url.includes('/overlay/rules')) return createFromBody(config, 'overlayId');
    }
    return readBody(config.data);
  }

  if (url === '/v1/schemes') return schemes;
  if (url === '/v1/schemes/effective') return schemes[0];
  if (url.startsWith('/v1/schemes/compare')) {
    return [
      { field: 'discountRate', oldValue: '4.35', newValue: '4.50' },
      { field: 'defaultLgd', oldValue: '0.45', newValue: '0.42' },
    ];
  }
  if (url.startsWith('/v1/schemes/')) return one(schemes, 'schemeId', url.split('/').pop()) || schemes[0];

  if (url.includes('/risk-groups')) return riskGroups;

  if (url.includes('/parameters/stages')) {
    return [
      { stageId: 'mock-stage-1', schemeId, stageCode: 'STAGE_1', stageName: '阶段一', sortOrder: 1, createdAt: now },
      { stageId: 'mock-stage-2', schemeId, stageCode: 'STAGE_2', stageName: '阶段二', sortOrder: 2, createdAt: now },
      { stageId: 'mock-stage-3', schemeId, stageCode: 'STAGE_3', stageName: '阶段三', sortOrder: 3, createdAt: now },
    ];
  }
  if (url.includes('/stage-rules/by-group')) return { stageRules, ratingRules };
  if (url.includes('/stage-rules/crr-drop')) return ratingRules;
  if (url.includes('/stage-rules')) return stageRules.filter((rule) => rule.groupId === (params.groupId || groupId));

  if (url.includes('/pd/scenarios')) return scenarios;
  if (url.includes('/pd/curves')) return pdCurves.map((curve) => ({ ...curve, scenarioId: String(params.scenarioId || curve.scenarioId) }));
  if (url.includes('/pd/matrix-detail')) {
    return scenarios.flatMap((scenario) =>
      ratingCodes.map((ratingCode, index) => ({
        ratingCode,
        scenarioId: scenario.scenarioId,
        scenarioName: scenario.scenarioName,
        pdValue: 0.00005 * Math.pow(2, index) * (scenario.scenarioType === 'PESSIMISTIC' ? 1.8 : scenario.scenarioType === 'OPTIMISTIC' ? 0.6 : 1),
      }))
    );
  }
  if (url.includes('/pd/matrix')) return { riskGroups: ['对公业务'], stages: ['Stage 1', 'Stage 2', 'Stage 3'], matrix: { 对公业务: { 'Stage 1': 0.01, 'Stage 2': 0.08, 'Stage 3': 1 } } };

  if (url.includes('/lgd/curves')) return lgdCurves;
  if (url.includes('/lgd/collateral-discounts')) return collateralDiscounts;
  if (url.includes('/lgd/depreciations')) return depreciations.filter((item) => !params.collateralType || item.collateralType === params.collateralType);
  if (url.includes('/lgd/matrix')) return { riskGroups: ['对公业务'], stages: ['Stage 1'], matrix: { 对公业务: { 'Stage 1': 0.35 } } };

  if (url.includes('/ccf/curves')) return ccfCurves;
  if (url.includes('/ccf/matrix')) return { riskGroups: ['对公业务'], stages: ['Stage 1'], matrix: { 对公业务: { 'Stage 1': 0.5 } } };

  if (url.includes('/overlay/rules')) return overlayRules;

  return [];
}

export function createMockResponse(config: InternalAxiosRequestConfig): AxiosResponse<Result<unknown>> {
  const responseData: Result<unknown> = {
    code: '200',
    message: 'MOCK',
    data: payloadFor(config),
  };

  return {
    data: responseData,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  };
}
