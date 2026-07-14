import request from './request';

export interface DictCategoryVO {
  categoryId: string;
  categoryCode: string;
  categoryName: string;
  description?: string;
  isSystem?: boolean;
  sortOrder?: number;
  entries?: DictEntryVO[];
}

export interface DictEntryVO {
  entryId?: string;
  categoryId?: string;
  entryCode?: string;
  entryName?: string;
  sortOrder?: number;
  isActive?: boolean;
}

export interface SchemeDictVO {
  id?: string;
  schemeId: string;
  categoryId: string;
  categoryCode: string;
  categoryName: string;
  overrideType: string; // INHERIT / CUSTOM
  entryIds?: string[];
  effectiveEntries: DictEntryVO[];
}

export interface SchemeDictSaveReq {
  overrideType: string;
  entryIds?: string[];
}

export const dictApi = {
  // 全局字典分类
  listCategories: () => request.get<DictCategoryVO[]>('/v1/dict/categories'),
  getCategory: (id: string) => request.get<DictCategoryVO>(`/v1/dict/categories/${id}`),

  // 字典条目 CRUD
  createEntry: (data: DictEntryVO) => request.post<DictEntryVO>('/v1/dict/entries', data),
  updateEntry: (id: string, data: DictEntryVO) => request.put<DictEntryVO>(`/v1/dict/entries/${id}`, data),
  deleteEntry: (id: string) => request.delete(`/v1/dict/entries/${id}`),

  // 方案基础信息
  listSchemeDicts: (schemeId: string) => request.get<SchemeDictVO[]>(`/v1/dict/scheme/${schemeId}`),
  getSchemeDict: (schemeId: string, categoryId: string) =>
    request.get<SchemeDictVO>(`/v1/dict/scheme/${schemeId}/category/${categoryId}`),
  saveSchemeDict: (schemeId: string, categoryId: string, data: SchemeDictSaveReq) =>
    request.put<SchemeDictVO>(`/v1/dict/scheme/${schemeId}/category/${categoryId}`, data),

  // 查询方案指定分类的生效条目（供下拉组件使用）
  getEffectiveEntries: (schemeId: string, categoryCode: string) =>
    request.get<DictEntryVO[]>(`/v1/dict/scheme/${schemeId}/effective`, { params: { categoryCode } }),
};
