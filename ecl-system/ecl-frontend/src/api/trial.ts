import request from './request';

export interface TrialMetricVO {
  label: string;
  value: string;
  note?: string;
}

export interface TrialScenarioRowVO {
  scenario: string;
  weight: string;
  pd: string;
  weightedPd: string;
  highlight?: boolean;
}

// 6.1a 借据信息表行
export interface TrialLoanRowReq {
  id: string;
  facilityCd?: string;
  customerNo?: string;
  customerName?: string;
  industryCn?: string;
  segment?: string;
  productType?: string;
  currencyCd?: string;
  amtFinancedCny?: number;
  loanBalCny?: number;
  intAccruedCny?: number;
  interestRate?: number;
  loanStartDt?: string;
  loanMaturityDt?: string;
  overdueDays?: number;
  isNpl?: string;
  guaranteeType?: string;
  normalConsecutiveDays?: number;
  otherRiskInfo?: string;
  businessType?: string;
  overduePrincipal?: number;
  overdueInterest?: number;
}

// 授信额度表行
export interface TrialFacilityRowReq {
  facilityCd: string;
  limitCurrencyCd?: string;
  fxRateLimitToCny?: number;
  limitAmtFcy?: number;
  limitAmtCny?: number;
  limitAvailAmtFcy?: number;
  limitAvailAmtCny?: number;
  undrawnAmtCny?: number;
  commitWithdrawFlg?: string;
  isRevolving?: string;
  calcTypeCd?: string;
  facilityStartDate?: string;
  facilityMaturityDate?: string;
  usedLimit?: number;
  collateralPoolId?: string;
  cifNo?: string;
  customerName?: string;
}

// 还款计划表行
export interface TrialRepaymentRowReq {
  loanReceiptNo?: string;
  totalPeriods?: number;
  periodNo?: number;
  dueDate?: string;
  duePrincipal?: number;
  dueInterest?: number;
}

// 抵质押品表行
export interface TrialCollateralRowReq {
  branchCode?: string;
  cifNo?: string;
  customerName?: string;
  facilityUniqueCode?: string;
  facilityNumber?: string;
  guaranteeContractNo?: string;
  collateralType?: string;
  collateralCategory?: string;
  categoryDesc?: string;
  collateralCode?: string;
  collateralPoolCode?: string;
  collateralStatus?: string;
  collateralDesc?: string;
  collateralStartDate?: string;
  collateralEndDate?: string;
  collateralCurrency?: string;
  collateralValue?: number;
  reportCurrency?: string;
  collateralValueFcy?: number;
  appraisalCompany?: string;
  appraisalEffectiveDate?: string;
  appraisalExpiryDate?: string;
  appraisalValue?: number;
  guaranteeMethod?: string;
}

// 评级信息表行
export interface TrialRatingRowReq {
  cifNo?: string;
  customerName?: string;
  extRatingCoLastYear?: string;
  extRatingLastYear?: string;
  crrIntLastYear?: string;
  extRatingCoThisYear?: string;
  extRatingThisYear?: string;
  crrIntThisYear?: string;
  crrFinal?: string;
}

// 历史阶段表行
export interface TrialHistoricalStageRowReq {
  assetId?: string;
  calcDate?: string;
  stageResult?: string;
}

export interface AssetInputReq {
  assetId: string;
  businessLine?: string;
  customerType?: string;
  productType?: string;
  industryCode?: string;
  regionCode?: string;
  collateralType?: string;
  lastStage?: string;
  overdueDays?: number;
  crrRating?: string;
  fiveCategory?: string;
  defaultFlag?: boolean;
  mediaSentiment?: string;
  ratingDropLevels?: number;
  ratingCode?: string;
  maturityDate?: string;
  outstandingBalance?: number;
  accruedInterest?: number;
  totalLimit?: number;
  commitmentType?: string;
  commitmentDays?: number;
}

export interface AssetResult {
  assetId: string;
  groupId?: string;
  groupLabel: string;
  productType: string;
  ratingCode: string;
  stage: string;
  ead: string;
  lgd: string;
  pd12m: string;
  pdLifetime: string;
  eclValue: string;
  overlayAmount: string;
  eclFinal: string;
  exceptionSummary?: string;
  steps: TrialStepVO[];
}

export interface TrialStepVO {
  key: string;
  title: string;
  summary: string;
  note?: string;
  metrics?: TrialMetricVO[];
  scenarioRows?: TrialScenarioRowVO[];
}

export interface TrialCalculationResp {
  jobId: string;
  status: string;
  durationMs: number;
  assetId: string;
  groupId?: string;
  groupLabel: string;
  productType: string;
  ratingCode: string;
  stage: string;
  ead: string;
  lgd: string;
  pd12m: string;
  pdLifetime: string;
  eclValue: string;
  overlayAmount: string;
  eclFinal: string;
  exceptionSummary?: string;
  steps: TrialStepVO[];

  /** 多借据结果 */
  assetResults?: AssetResult[];
}

export interface TrialCalculationReq {
  schemeId: string;
  assetId: string;
  calcDate?: string;
  scope: 'SINGLE' | 'BATCH';

  // 6.1 风险分组入参
  businessLine?: string;
  customerType?: string;
  productType?: string;
  industryCode?: string;
  regionCode?: string;
  collateralType?: string;

  // 6.2 阶段划分入参
  lastStage?: string;
  overdueDays?: number;
  crrRating?: string;
  fiveCategory?: string;
  defaultFlag?: boolean;
  mediaSentiment?: string;
  ratingDropLevels?: number;

  // 6.3 PD 入参
  ratingCode?: string;
  maturityDate?: string;

  // 6.4 EAD 入参
  outstandingBalance?: number;
  accruedInterest?: number;
  totalLimit?: number;
  commitmentType?: string;
  commitmentDays?: number;

  /** 多借据模式 */
  assets?: AssetInputReq[];

  // 源表数据 (Task 13)
  loans?: TrialLoanRowReq[];
  facilities?: TrialFacilityRowReq[];
  repaymentSchedules?: TrialRepaymentRowReq[];
  collaterals?: TrialCollateralRowReq[];
  ratings?: TrialRatingRowReq[];
  historicalStages?: TrialHistoricalStageRowReq[];
}

export const trialApi = {
  runTrial: (data: TrialCalculationReq) =>
    request.post<TrialCalculationResp>('/v1/ecl/calculate/trial', data),
};
