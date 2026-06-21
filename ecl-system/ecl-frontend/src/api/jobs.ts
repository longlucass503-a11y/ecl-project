import request from './request';

export interface EclJobStepVO {
  name: string;
  durationMs: number;
  percent: number;
}

export interface EclJobLogVO {
  time: string;
  level: string;
  message: string;
}

export interface EclJobDetailVO {
  detailId: number;
  assetId: string;
  schemeId: string;
  calcDate: string;
  groupId?: string;
  stageResult?: string;
  eadTotal?: number;
  lgdValue?: number;
  eclWeighted?: number;
  eclOverlayTotal?: number;
  eclFinal?: number;
  calcStatus?: string;
  errorSummary?: string;
}

export interface EclJobVO {
  jobId: string;
  schemeId: string;
  calcDate: string;
  trialMode: boolean;
  status: string;
  totalAssets?: number;
  successCount?: number;
  exceptionCount?: number;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
  errorSummary?: string;
  steps?: EclJobStepVO[];
  logs?: EclJobLogVO[];
  details?: EclJobDetailVO[];
}

export const jobsApi = {
  list: () => request.get<EclJobVO[]>('/v1/ecl/jobs'),
  getById: (jobId: string) => request.get<EclJobVO>(`/v1/ecl/jobs/${jobId}`),
};
