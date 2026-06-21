import request from './request';

export interface PdVO {
  pdId: string;
  schemeId: string;
  riskGroupId: string;
  stageId: string;
  pdValue: number;
  createdAt: string;
  updatedAt?: string;
}

export interface PdMatrixVO {
  riskGroups: string[];
  stages: string[];
  matrix: Record<string, Record<string, number>>;
}

/** 情景（Scenario） */
export interface ScenarioVO {
  scenarioId: string;
  schemeId: string;
  scenarioType: string;
  scenarioName: string;
  weight: number;
  createdAt: string;
  updatedAt?: string;
}

/** PD 曲线单行 */
export interface PdCurveVO {
  curveId?: string;
  scenarioId: string;
  ratingCode: string;
  pdValue: number;
}

/** 矩阵视图单元格 */
export interface PdMatrixCell {
  ratingCode: string;
  scenarioId: string;
  scenarioName: string;
  pdValue: number;
}

export const pdApi = {
  list: (schemeId: string) => request.get<PdVO[]>('/v1/parameters/pd', { params: { schemeId } }),
  getMatrix: (schemeId: string) => request.get<PdMatrixVO>('/v1/parameters/pd/matrix', { params: { schemeId } }),
  saveMatrix: (schemeId: string, matrix: PdMatrixVO) =>
    request.put('/v1/parameters/pd/matrix', { schemeId, ...matrix }),
  update: (id: string, data: Partial<PdVO>) => request.put<PdVO>(`/v1/parameters/pd/${id}`, data),

  // ─── 情景管理 ───
  listScenarios: (schemeId: string) =>
    request.get<ScenarioVO[]>('/v1/parameters/pd/scenarios', { params: { schemeId } }),
  createScenario: (data: { schemeId: string; scenarioType: string; scenarioName: string; weight: number }) =>
    request.post<ScenarioVO>('/v1/parameters/pd/scenarios', data),
  updateScenario: (id: string, data: Partial<ScenarioVO>) =>
    request.put<ScenarioVO>(`/v1/parameters/pd/scenarios/${id}`, data),
  deleteScenario: (id: string) =>
    request.delete(`/v1/parameters/pd/scenarios/${id}`),

  // ─── 曲线编辑 ───
  listCurves: (scenarioId: string) =>
    request.get<PdCurveVO[]>('/v1/parameters/pd/curves', { params: { scenarioId } }),
  batchUpdateCurves: (scenarioId: string, curves: Omit<PdCurveVO, 'scenarioId'>[]) =>
    request.put('/v1/parameters/pd/curves/batch', { scenarioId, curves }),

  // ─── 矩阵视图（增强：含情景列） ───
  getMatrixDetail: (schemeId: string) =>
    request.get<PdMatrixCell[]>('/v1/parameters/pd/matrix-detail', { params: { schemeId } }),
};
