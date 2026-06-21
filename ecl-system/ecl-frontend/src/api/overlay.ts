import request from './request';

export interface OverlayRuleVO {
  overlayId?: string;
  schemeId: string;
  groupId?: string;
  ruleId?: string;
  /** 调整类型（如 RISK_RATE / LGD / CCF / PD 等） */
  overlayType: string;
  /** 调整方式：ADDBP — 加点(基点), PERCENTAGE — 百分比, FIXED — 固定值 */
  adjustmentType: 'ADDBP' | 'PERCENTAGE' | 'FIXED';
  /** 调整值 */
  adjustmentValue: number;
  /** 优先级（数值越小优先级越高） */
  priority: number;
  /** 生效日期 */
  effectiveDate?: string;
  /** 失效日期 */
  expiryDate?: string;
  /** JSON 条件表达式 */
  conditions?: string;
  /** 规则名称（可选） */
  ruleName?: string;
  /** 是否启用 */
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

/** 命中测试请求 */
export interface OverlayMatchTestReq {
  schemeId: string;
  groupId?: string;
  fieldValues: Record<string, any>;
}

/** 命中测试响应 */
export interface OverlayMatchTestResp {
  /** 是否有匹配 */
  hasMatch: boolean;
  /** 选中的规则（等效比例最高） */
  selectedRule?: OverlayRuleVO;
  /** 等效比例 */
  effectiveRatio: number;
  /** 全部命中的规则列表 */
  matchedRules: OverlayRuleVO[];
}

export const overlayApi = {
  list: (schemeId: string, groupId?: string) =>
    request.get<OverlayRuleVO[]>('/v1/parameters/overlay/rules', { params: { schemeId, groupId } }),
  getById: (id: string) =>
    request.get<OverlayRuleVO>(`/v1/parameters/overlay/rules/${id}`),
  create: (data: Partial<OverlayRuleVO>) =>
    request.post<OverlayRuleVO>('/v1/parameters/overlay/rules', data),
  update: (id: string, data: Partial<OverlayRuleVO>) =>
    request.put<OverlayRuleVO>(`/v1/parameters/overlay/rules/${id}`, data),
  delete: (id: string) =>
    request.delete(`/v1/parameters/overlay/rules/${id}`),
  toggle: (id: string, enabled: boolean) =>
    request.put<OverlayRuleVO>(`/v1/parameters/overlay/rules/${id}/toggle`, { enabled }),

  /** 命中测试 */
  testMatch: (req: OverlayMatchTestReq) =>
    request.post<OverlayMatchTestResp>('/v1/parameters/overlay/rules/test-match', req),
};
