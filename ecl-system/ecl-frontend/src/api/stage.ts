import request from './request';

/** 阶段（Stage）基础模型 */
export interface StageVO {
  stageId: string;
  schemeId: string;
  stageCode: string;
  stageName: string;
  sortOrder: number;
  description?: string;
  createdAt: string;
  updatedAt?: string;
}

/** 阶段判定规则（FORWARD / ROLLBACK） */
export interface StageRuleVO {
  ruleId?: string;
  schemeId: string;
  groupId: string;
  ruleType: 'FORWARD' | 'ROLLBACK';
  sourceStage: string;
  targetStage: string;
  priority: number;
  observationDays?: number;
  conditions?: string;
  jsonCondition?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** 评级下降规则（CRR） */
export interface RatingDowngradeRuleVO {
  ruleId?: string;
  schemeId: string;
  groupId: string;
  ratingAgency?: string;
  currentRating: string;
  downgradeThreshold: number;
  createdAt?: string;
  updatedAt?: string;
}

export const stageApi = {
  /** 查询方案下所有阶段 */
  list: (schemeId: string) =>
    request.get<StageVO[]>('/v1/parameters/stages', { params: { schemeId } }),

  /** 查询单个阶段 */
  getById: (id: string) =>
    request.get<StageVO>(`/v1/parameters/stages/${id}`),

  /** 创建阶段 */
  create: (data: Partial<StageVO>) =>
    request.post<StageVO>('/v1/parameters/stages', data),

  /** 更新阶段 */
  update: (id: string, data: Partial<StageVO>) =>
    request.put<StageVO>(`/v1/parameters/stages/${id}`, data),

  /** 删除阶段 */
  delete: (id: string) =>
    request.delete(`/v1/parameters/stages/${id}`),

  // ─── 阶段判定规则 ───

  /** 查询某分组下的阶段判定规则 */
  getRulesByGroup: (schemeId: string, groupId: string) =>
    request.get<StageRuleVO[]>('/v1/parameters/stage-rules', { params: { schemeId, groupId } }),

  /** 创建阶段判定规则 */
  createRule: (data: Partial<StageRuleVO>) =>
    request.post<StageRuleVO>('/v1/parameters/stage-rules', data),

  /** 更新阶段判定规则 */
  updateRule: (id: string, data: Partial<StageRuleVO>) =>
    request.put<StageRuleVO>(`/v1/parameters/stage-rules/${id}`, data),

  /** 删除阶段判定规则 */
  deleteRule: (id: string) =>
    request.delete(`/v1/parameters/stage-rules/${id}`),

  // ─── 评级下降规则 ───

  /** 查询某分组下的评级下降规则 */
  getRatingRulesByGroup: (schemeId: string, groupId: string) =>
    request.get<RatingDowngradeRuleVO[]>('/v1/parameters/stage-rules/crr-drop', { params: { schemeId, groupId } }),

  /** 创建评级下降规则 */
  createRatingRule: (data: Partial<RatingDowngradeRuleVO>) =>
    request.post<RatingDowngradeRuleVO>('/v1/parameters/stage-rules/crr-drop', data),

  /** 更新评级下降规则 */
  updateRatingRule: (id: string, data: Partial<RatingDowngradeRuleVO>) =>
    request.put<RatingDowngradeRuleVO>(`/v1/parameters/stage-rules/crr-drop/${id}`, data),

  /** 删除评级下降规则 */
  deleteRatingRule: (id: string) =>
    request.delete(`/v1/parameters/stage-rules/crr-drop/${id}`),

  // ─── 兼容别名 ───
  getBySchemeAndGroup: (schemeId: string, groupId: string) =>
    request.get<{ stageRules: StageRuleVO[]; ratingRules: RatingDowngradeRuleVO[] }>(
      '/v1/parameters/stage-rules/by-group', { params: { schemeId, groupId } }
    ),
};
