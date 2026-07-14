import request from './request';

export interface SchemeVO {
  schemeId: string;
  schemeCode: string;
  schemeName: string;
  schemeVersion: string;
  status: string;
  statusDisplay: string;
  effectiveDate?: string;
  effectiveAt?: string;
  expiredAt?: string;
  discountRate: number;
  defaultCcf: number;
  defaultLgd: number;
  lgdFloor: number;
  createdBy: string;
  createdAt: string;
  updatedBy?: string;
  updatedAt?: string;
  description?: string;
  editable: boolean;
}

export interface SchemeDiffVO {
  module: string;
  versionFrom: string;
  versionTo: string;
  changedItems: number;
  same: boolean;
}

export const schemeApi = {
  list: (status?: string) => request.get<SchemeVO[]>('/v1/schemes', { params: { status } }),
  getEffective: () => request.get<SchemeVO>('/v1/schemes/effective'),
  getById: (id: string) => request.get<SchemeVO>(`/v1/schemes/${id}`),
  create: (data: { schemeName: string; description?: string }) =>
    request.post<SchemeVO>('/v1/schemes', data),
  copy: (description: string) =>
    request.post<SchemeVO>('/v1/schemes/copy', null, { params: { description } }),
  copyFrom: (id: string, description?: string) =>
    request.post<SchemeVO>(`/v1/schemes/${id}/copy`, null, { params: { description } }),
  update: (id: string, data: { schemeName: string; description?: string }) =>
    request.put<SchemeVO>(`/v1/schemes/${id}`, data),
  publish: (id: string, immediate: boolean, effectiveDate?: string) =>
    request.put<SchemeVO>(`/v1/schemes/${id}/publish`, { immediate, effectiveDate }),
  compare: (schemeId1: string, schemeId2: string) =>
    request.get<SchemeDiffVO[]>('/v1/schemes/compare', { params: { schemeId1, schemeId2 } }),
  delete: (id: string) => request.delete(`/v1/schemes/${id}`),
  updateDefaultParams: (id: string, data: { discountRate: number; defaultCcf: number; defaultLgd: number; lgdFloor: number }) =>
    request.put<SchemeVO>(`/v1/schemes/${id}/default-params`, data),
};
