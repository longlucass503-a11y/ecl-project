import request from './request';

export interface LgdVO {
  lgdId: string;
  schemeId: string;
  riskGroupId: string;
  stageId: string;
  lgdValue: number;
  createdAt: string;
  updatedAt?: string;
}

export interface LgdMatrixVO {
  riskGroups: string[];
  stages: string[];
  matrix: Record<string, Record<string, number>>;
}

/** LGD 基准曲线 */
export interface LgdCurveVO {
  curveId?: string;
  schemeId: string;
  groupId?: string;
  collateralType: string;
  productType: string;
  lgdBaseValue: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 押品折扣率 */
export interface LgdCollateralDiscountVO {
  discountId?: string;
  schemeId: string;
  collateralType: string;
  discountRate: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 押品折旧率 */
export interface LgdDepreciationVO {
  depreciationId?: string;
  schemeId: string;
  groupId?: string;
  collateralType: string;
  yearOffset: number;
  depreciationRate: number;
  createdAt?: string;
  updatedAt?: string;
}

export const lgdApi = {
  list: (schemeId: string) => request.get<LgdVO[]>('/v1/parameters/lgd', { params: { schemeId } }),
  getMatrix: (schemeId: string) => request.get<LgdMatrixVO>('/v1/parameters/lgd/matrix', { params: { schemeId } }),
  saveMatrix: (schemeId: string, matrix: LgdMatrixVO) =>
    request.put('/v1/parameters/lgd/matrix', { schemeId, ...matrix }),
  update: (id: string, data: Partial<LgdVO>) => request.put<LgdVO>(`/v1/parameters/lgd/${id}`, data),

  // ─── 基准曲线 CRUD ───
  listCurves: (schemeId: string, groupId?: string) =>
    request.get<LgdCurveVO[]>('/v1/parameters/lgd/curves', { params: { schemeId, groupId } }),
  createCurve: (data: Omit<LgdCurveVO, 'curveId' | 'createdAt' | 'updatedAt'>) =>
    request.post<LgdCurveVO>('/v1/parameters/lgd/curves', data),
  updateCurve: (id: string, data: Partial<LgdCurveVO>) =>
    request.put<LgdCurveVO>(`/v1/parameters/lgd/curves/${id}`, data),
  deleteCurve: (id: string) =>
    request.delete(`/v1/parameters/lgd/curves/${id}`),
  batchSaveCurves: (schemeId: string, curves: Omit<LgdCurveVO, 'schemeId' | 'createdAt' | 'updatedAt'>[]) =>
    request.put('/v1/parameters/lgd/curves/batch', { schemeId, curves }),

  // ─── 押品折扣率 CRUD ───
  listDiscounts: (schemeId: string) =>
    request.get<LgdCollateralDiscountVO[]>('/v1/parameters/lgd/collateral-discounts', { params: { schemeId } }),
  createDiscount: (data: Omit<LgdCollateralDiscountVO, 'discountId' | 'createdAt' | 'updatedAt'>) =>
    request.post<LgdCollateralDiscountVO>('/v1/parameters/lgd/collateral-discounts', data),
  updateDiscount: (id: string, data: Partial<LgdCollateralDiscountVO>) =>
    request.put<LgdCollateralDiscountVO>(`/v1/parameters/lgd/collateral-discounts/${id}`, data),
  deleteDiscount: (id: string) =>
    request.delete(`/v1/parameters/lgd/collateral-discounts/${id}`),
  batchSaveDiscounts: (schemeId: string, discounts: Omit<LgdCollateralDiscountVO, 'schemeId' | 'createdAt' | 'updatedAt'>[]) =>
    request.put('/v1/parameters/lgd/collateral-discounts/batch', { schemeId, discounts }),

  // ─── 押品折旧率 CRUD ───
  listDepreciations: (schemeId: string, collateralType: string, groupId?: string) =>
    request.get<LgdDepreciationVO[]>('/v1/parameters/lgd/depreciations', {
      params: { schemeId, collateralType, groupId },
    }),
  createDepreciation: (data: Omit<LgdDepreciationVO, 'depreciationId' | 'createdAt' | 'updatedAt'>) =>
    request.post<LgdDepreciationVO>('/v1/parameters/lgd/depreciations', data),
  updateDepreciation: (id: string, data: Partial<LgdDepreciationVO>) =>
    request.put<LgdDepreciationVO>(`/v1/parameters/lgd/depreciations/${id}`, data),
  deleteDepreciation: (id: string) =>
    request.delete(`/v1/parameters/lgd/depreciations/${id}`),
  batchSaveDepreciations: (schemeId: string, collateralType: string, depreciations: Omit<LgdDepreciationVO, 'schemeId' | 'collateralType' | 'createdAt' | 'updatedAt'>[]) =>
    request.put('/v1/parameters/lgd/depreciations/batch', { schemeId, collateralType, depreciations }),
};
