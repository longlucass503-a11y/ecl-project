import request from './request';

export interface RiskGroupVO {
  groupId: string;
  schemeId: string;
  groupCode: string;
  groupName: string;
  sortOrder: number;
  description?: string;
  details?: RiskGroupDetailVO[];
  createdAt: string;
  updatedAt?: string;
}

/**
 * 匹配条件为 6 维：priority、businessLine、customerType、productType、industryCode、regionCode、collateralType
 * 至少填一个维度，不允许全 NULL
 */
export interface RiskGroupDetailVO {
  detailId?: string;
  groupId?: string;
  priority: number;
  businessLine?: string;
  customerType?: string;
  productType?: string;
  industryCode?: string;
  regionCode?: string;
  collateralType?: string;
}

export const riskGroupApi = {
  /** 按方案查询所有风险分组（含明细） */
  listByScheme: (schemeId: string) =>
    request.get<RiskGroupVO[]>('/v1/parameters/risk-groups', { params: { schemeId } }),

  /** 查询单个分组 */
  getById: (id: string) =>
    request.get<RiskGroupVO>(`/v1/parameters/risk-groups/${id}`),

  /** 新建分组 */
  create: (data: Partial<RiskGroupVO>) =>
    request.post<RiskGroupVO>('/v1/parameters/risk-groups', data),

  /** 更新分组基本信息 */
  update: (id: string, data: Partial<RiskGroupVO>) =>
    request.put<RiskGroupVO>(`/v1/parameters/risk-groups/${id}`, data),

  /** 删除分组（会级联删除匹配规则） */
  delete: (id: string) =>
    request.delete(`/v1/parameters/risk-groups/${id}`),

  /** 批量更新分组的匹配规则明细 */
  updateDetails: (schemeId: string, groupId: string, details: RiskGroupDetailVO[]) =>
    request.put(`/v1/parameters/risk-groups/${groupId}/details`, { schemeId, details }),
};
