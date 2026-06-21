import request from './request';

export interface CcfVO {
  ccfId: string;
  schemeId: string;
  riskGroupId: string;
  stageId: string;
  ccfValue: number;
  createdAt: string;
  updatedAt?: string;
}

export interface CcfMatrixVO {
  riskGroups: string[];
  stages: string[];
  matrix: Record<string, Record<string, number>>;
}

/** CCF 曲线（按产品类型+承诺类型+期限区间） */
export interface CcfCurveVO {
  curveId?: string;
  schemeId: string;
  productType: string;
  commitmentType: string;
  daysMin: number;
  daysMax: number;
  ccfValue: number;
  createdAt?: string;
  updatedAt?: string;
}

export const ccfApi = {
  list: (schemeId: string) => request.get<CcfVO[]>('/v1/parameters/ccf', { params: { schemeId } }),
  getMatrix: (schemeId: string) => request.get<CcfMatrixVO>('/v1/parameters/ccf/matrix', { params: { schemeId } }),
  saveMatrix: (schemeId: string, matrix: CcfMatrixVO) =>
    request.put('/v1/parameters/ccf/matrix', { schemeId, ...matrix }),
  update: (id: string, data: Partial<CcfVO>) => request.put<CcfVO>(`/v1/parameters/ccf/${id}`, data),

  // ─── CCF 曲线 CRUD ───
  listCurves: (schemeId: string) =>
    request.get<CcfCurveVO[]>('/v1/parameters/ccf/curves', { params: { schemeId } }),
  createCurve: (data: Omit<CcfCurveVO, 'curveId' | 'createdAt' | 'updatedAt'>) =>
    request.post<CcfCurveVO>('/v1/parameters/ccf/curves', data),
  updateCurve: (id: string, data: Partial<CcfCurveVO>) =>
    request.put<CcfCurveVO>(`/v1/parameters/ccf/curves/${id}`, data),
  deleteCurve: (id: string) =>
    request.delete(`/v1/parameters/ccf/curves/${id}`),
  batchImport: (schemeId: string, curves: Omit<CcfCurveVO, 'curveId' | 'schemeId' | 'createdAt' | 'updatedAt'>[]) =>
    request.post('/v1/parameters/ccf/curves/batch', { schemeId, curves }),
};
