import React, { useEffect, useMemo, useState } from 'react';
import { Button, Collapse, DatePicker, Input, InputNumber, Radio, Select, Space, Table, Tag, message } from 'antd';
import { DownloadOutlined, ExperimentOutlined, PlusOutlined, DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { PageHeader, Panel } from '../../components';
import { schemeApi, type SchemeVO } from '../../api/scheme';
import {
  trialApi, type TrialCalculationResp, type TrialStepVO, type AssetResult,
  type TrialLoanRowReq, type TrialFacilityRowReq, type TrialRepaymentRowReq,
  type TrialCollateralRowReq, type TrialRatingRowReq, type TrialHistoricalStageRowReq,
} from '../../api/trial';
import { downloadExcelTemplate } from '../../utils/excelTemplate';
import { parseTrialExcel } from '../../utils/excelParser';
import './TrialCenter.css';

const today = dayjs();
const PRODUCT_TYPES = ['公司贷款', '银团贷款', '汽车贷款', '个经营贷', '个消费贷'];
const COLLATERAL_TYPES = ['房产', '土地', '存单', '保证金', '信用', '保证'];
const RATING_CODES = ['CRR1', 'CRR2', 'CRR3', 'CRR4', 'CRR5', 'CRR6', 'CRR7', 'CRR8', 'CRR9', 'CRR10', 'Aaa', 'Aa1', 'A1', 'A2', 'A3', 'Baa3'];
const GUARANTEE_TYPES = ['信用', '保证', '抵押', '质押', '保证金', '存单'];
const STAGE_OPTIONS = ['STAGE_1', 'STAGE_2', 'STAGE_3'];
const SEGMENT_OPTIONS = ['大型', '中型', '小型', '微型'];
const INDUSTRY_OPTIONS = ['制造业', '金融业', '批发零售', '房地产', '建筑', '交通运输', '信息技术', '农林牧渔', '其他'];
const BUSINESS_TYPE_OPTIONS = ['ON_BS', 'OFF_BS'];
const REVOLVING_OPTIONS = ['Y', 'N'];

// ---------------------------------------------------------------------------
// Helpers: default row factories (called during render / preset)
// ---------------------------------------------------------------------------
const makeDefaultLoan = (): TrialLoanRowReq => ({
  reportDt: today.format('YYYY-MM-DD'),
  id: `LN_${String(Date.now()).slice(-6)}`,
  facilityCd: `FAC_${String(Date.now()).slice(-6)}`,
  customerNo: 'CUST_001',
  customerName: '试算企业',
  industryCn: '制造业',
  segment: '大型',
  productType: '公司贷款',
  currencyCd: 'CNY',
  amtFinancedCny: 5000000,
  loanBalCny: 5000000,
  intAccruedCny: 10000,
  interestRate: 4.5,
  loanStartDt: today.format('YYYY-MM-DD'),
  loanMaturityDt: today.add(2, 'year').format('YYYY-MM-DD'),
  overdueDays: 0,
  loanClassifCd: '正常',
  isNpl: 'N',
  guaranteeType: '信用',
  normalConsecutiveDays: 180,
  otherRiskInfo: '',
  businessType: 'ON_BS',
  overduePrincipal: 0,
  overdueInterest: 0,
});

const makeDefaultFacility = (): TrialFacilityRowReq => ({
  facilityCd: `FAC_${String(Date.now()).slice(-6)}`,
  cifNo: 'CUST_001',
  customerName: '试算企业',
  limitCurrencyCd: 'CNY',
  limitAmtCny: 8000000,
  usedLimit: 5000000,
  limitAvailAmtCny: 3000000,
  undrawnAmtCny: 3000000,
  isRevolving: 'Y',
  facilityStartDate: today.format('YYYY-MM-DD'),
  facilityMaturityDate: today.add(2, 'year').format('YYYY-MM-DD'),
  collateralPoolId: 'POOL_001',
});

const makeDefaultCollateral = (): TrialCollateralRowReq => ({
  cifNo: 'CUST_001',
  customerName: '试算企业',
  facilityUniqueCode: 'FAC_001',
  collateralCode: `COL_${String(Date.now()).slice(-6)}`,
  collateralPoolCode: 'POOL_001',
  collateralCategory: '房产',
  collateralType: '房产',
  collateralStatus: '有效',
  collateralCurrency: 'CNY',
  collateralValue: 10000000,
  reportCurrency: 'CNY',
  appraisalEffectiveDate: today.format('YYYY-MM-DD'),
  appraisalValue: 10000000,
  guaranteeMethod: '抵押',
});

const makeDefaultRating = (): TrialRatingRowReq => ({
  cifNo: 'CUST_001',
  customerName: '试算企业',
  crrIntLastYear: 'CRR4',
  crrIntThisYear: 'CRR5',
  crrFinal: 'CRR5',
  extRatingCoLastYear: 'MOODY',
  extRatingLastYear: 'A1',
  extRatingCoThisYear: 'MOODY',
  extRatingThisYear: 'Baa3',
});

const makeDefaultRepayment = (loanId: string): TrialRepaymentRowReq => ({
  loanReceiptNo: loanId,
  totalPeriods: 1,
  periodNo: 1,
  dueDate: today.add(1, 'year').format('YYYY-MM-DD'),
  duePrincipal: 1000000,
  dueInterest: 50000,
});

const makeDefaultHistoricalStage = (loanId: string): TrialHistoricalStageRowReq => ({
  assetId: loanId,
  calcDate: today.format('YYYY-MM-DD'),
  stageResult: 'STAGE_1',
});

// ---------------------------------------------------------------------------
// Form builder helpers
// ---------------------------------------------------------------------------
const field = (label: string, child: React.ReactNode) => (
  <div className="trial-field"><label>{label}</label>{child}</div>
);

const textField = (value: string | undefined, onChange: (v: string) => void, placeholder?: string) => (
  <Input style={{ width: 140 }} value={value || ''} onChange={(e) => onChange(e.target.value)}
    placeholder={placeholder || '不限'} allowClear />
);

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------
const TrialCenter: React.FC = () => {
  const [schemes, setSchemes] = useState<SchemeVO[]>([]);
  const [selectedSchemeId, setSelectedSchemeId] = useState<string>('');
  const [scope, setScope] = useState<'SINGLE' | 'BATCH'>('SINGLE');
  const [calcDate, setCalcDate] = useState(today);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<TrialCalculationResp | null>(null);

  // ---- Source table state ------------------------------------------------
  const [loans, setLoans] = useState<TrialLoanRowReq[]>([makeDefaultLoan()]);
  const [facilities, setFacilities] = useState<TrialFacilityRowReq[]>([makeDefaultFacility()]);
  const [repaymentSchedules, setRepaymentSchedules] = useState<TrialRepaymentRowReq[]>([]);
  const [collaterals, setCollaterals] = useState<TrialCollateralRowReq[]>([makeDefaultCollateral()]);
  const [ratings, setRatings] = useState<TrialRatingRowReq[]>([makeDefaultRating()]);
  const [historicalStages, setHistoricalStages] = useState<TrialHistoricalStageRowReq[]>([]);

  // ---- Excel import ------------------------------------------------------
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  const handleImportExcel = async (file: File) => {
    try {
      const result = await parseTrialExcel(file);

      setLoans(result.loans.length > 0 ? result.loans as TrialLoanRowReq[] : [makeDefaultLoan()]);
      setFacilities(result.facilities as TrialFacilityRowReq[]);
      setRepaymentSchedules(result.repaymentSchedules as TrialRepaymentRowReq[]);
      setCollaterals(result.collaterals as TrialCollateralRowReq[]);
      setRatings(result.ratings as TrialRatingRowReq[]);
      setHistoricalStages(result.historicalStages as TrialHistoricalStageRowReq[]);

      const totalRows = result.loans.length + result.facilities.length
        + result.repaymentSchedules.length + result.collaterals.length
        + result.ratings.length + result.historicalStages.length;

      // Build status message with error details
      const parts: string[] = [];
      if (totalRows > 0) parts.push(`${totalRows} 行数据已填充`);

      if (result.errors.length > 0) {
        const detailStrs = result.errors.slice(0, 5).map(
          (e) => `[${e.sheet} 第${e.row}行] ${e.field}：${e.message}`,
        );
        let errorDetail = detailStrs.join('；');
        if (result.errors.length > 5) {
          errorDetail += ` …等共${result.errors.length}条`;
        }
        parts.push(`错误：${errorDetail}`);
      }

      if (result.skippedSheets.length > 0) {
        const skipDetail = result.skippedSheets
          .map((s) => `${s.sheet}：${s.reason}`)
          .join('；');
        parts.push(`${result.skippedSheets.length} 张表被跳过（${skipDetail}）`);
      }

      if (result.errors.length > 0 || result.skippedSheets.length > 0) {
        message.warning(`导入完成 — ${parts.join('；')}`, 8);
      } else {
        message.success(`导入完成 — ${parts.join('；')}`);
      }
    } catch (err) {
      console.error(err);
      message.error('Excel 解析失败，请确认文件格式正确');
    }
  };

  // ---- Load schemes ------------------------------------------------------
  useEffect(() => {
    schemeApi.list().then((res) => {
      const list = (res.data as any)?.data || res.data || [];
      setSchemes(list);
      const preferred = list.find((s: SchemeVO) => s.status === 'DRAFT') || list[0];
      if (preferred) setSelectedSchemeId(preferred.schemeId);
    }).catch(() => message.error('方案列表加载失败'));
  }, []);

  const schemeOptions = useMemo(() => schemes.map((s) => ({
    label: `${s.schemeCode} · ${s.schemeName} (${s.status})`,
    value: s.schemeId,
  })), [schemes]);

  // ---- Update helpers per table -------------------------------------------
  const updateLoan = (idx: number, patch: Partial<TrialLoanRowReq>) =>
    setLoans((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));

  const updateFacility = (idx: number, patch: Partial<TrialFacilityRowReq>) =>
    setFacilities((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));

  const updateRepayment = (idx: number, patch: Partial<TrialRepaymentRowReq>) =>
    setRepaymentSchedules((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));

  const updateCollateral = (idx: number, patch: Partial<TrialCollateralRowReq>) =>
    setCollaterals((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));

  const updateRating = (idx: number, patch: Partial<TrialRatingRowReq>) =>
    setRatings((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));

  const updateHistoricalStage = (idx: number, patch: Partial<TrialHistoricalStageRowReq>) =>
    setHistoricalStages((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));

  // ---- Add / Remove helpers -----------------------------------------------
  const addLoan = () => setLoans((prev) => [...prev, makeDefaultLoan()]);
  const removeLoan = (idx: number) => setLoans((prev) => prev.length > 1 ? prev.filter((_, i) => i !== idx) : prev);

  const addFacility = () => setFacilities((prev) => [...prev, makeDefaultFacility()]);
  const removeFacility = (idx: number) => setFacilities((prev) => prev.length > 1 ? prev.filter((_, i) => i !== idx) : prev);

  const addRepayment = () => {
    const loanId = loans[0]?.id || '';
    setRepaymentSchedules((prev) => [...prev, makeDefaultRepayment(loanId)]);
  };
  const removeRepayment = (idx: number) => setRepaymentSchedules((prev) => prev.filter((_, i) => i !== idx));

  const addCollateral = () => setCollaterals((prev) => [...prev, makeDefaultCollateral()]);
  const removeCollateral = (idx: number) => setCollaterals((prev) => prev.length > 1 ? prev.filter((_, i) => i !== idx) : prev);

  const addRating = () => setRatings((prev) => [...prev, makeDefaultRating()]);
  const removeRating = (idx: number) => setRatings((prev) => prev.length > 1 ? prev.filter((_, i) => i !== idx) : prev);

  const addHistoricalStage = () => {
    const loanId = loans[0]?.id || '';
    setHistoricalStages((prev) => [...prev, makeDefaultHistoricalStage(loanId)]);
  };
  const removeHistoricalStage = (idx: number) => setHistoricalStages((prev) => prev.filter((_, i) => i !== idx));

  // ---- Presets -----------------------------------------------------------
  const applyPreset = (preset: string) => {
    const d = today;
    switch (preset) {
      case 'corp_dual': {
        setLoans([
          { ...makeDefaultLoan(), id: 'LN_001', facilityCd: 'FAC_001', customerNo: 'CUST_001', customerName: '测试企业A', industryCn: '制造业', segment: '大型', productType: '公司贷款', amtFinancedCny: 5000000, loanBalCny: 5000000, intAccruedCny: 10000, interestRate: 4.5, loanStartDt: d.subtract(1, 'year').format('YYYY-MM-DD'), loanMaturityDt: d.add(1, 'year').format('YYYY-MM-DD'), overdueDays: 10, guaranteeType: '抵押', businessType: 'ON_BS' },
          { ...makeDefaultLoan(), id: 'LN_002', facilityCd: 'FAC_002', customerNo: 'CUST_002', customerName: '测试企业B', industryCn: '制造业', segment: '中型', productType: '公司贷款', amtFinancedCny: 3000000, loanBalCny: 3000000, intAccruedCny: 15000, interestRate: 5.0, loanStartDt: d.subtract(6, 'month').format('YYYY-MM-DD'), loanMaturityDt: d.add(2, 'year').format('YYYY-MM-DD'), overdueDays: 45, guaranteeType: '信用', businessType: 'ON_BS' },
        ]);
        setFacilities([
          { ...makeDefaultFacility(), facilityCd: 'FAC_001', cifNo: 'CUST_001', customerName: '测试企业A', limitAmtCny: 8000000, usedLimit: 5000000, limitAvailAmtCny: 3000000, undrawnAmtCny: 3000000, collateralPoolId: 'POOL_001' },
          { ...makeDefaultFacility(), facilityCd: 'FAC_002', cifNo: 'CUST_002', customerName: '测试企业B', limitAmtCny: 5000000, usedLimit: 3000000, limitAvailAmtCny: 2000000, undrawnAmtCny: 2000000, collateralPoolId: 'POOL_002' },
        ]);
        setCollaterals([
          { ...makeDefaultCollateral(), collateralCode: 'COL_001', cifNo: 'CUST_001', customerName: '测试企业A', facilityUniqueCode: 'FAC_001', collateralPoolCode: 'POOL_001', collateralCategory: '房产', collateralType: '房产', collateralValue: 10000000, appraisalValue: 10000000 },
          { ...makeDefaultCollateral(), collateralCode: 'COL_002', cifNo: 'CUST_002', customerName: '测试企业B', facilityUniqueCode: 'FAC_002', collateralPoolCode: 'POOL_002', collateralCategory: '信用', collateralType: '信用', collateralValue: 0, appraisalValue: 0 },
        ]);
        setRatings([
          { ...makeDefaultRating(), cifNo: 'CUST_001', customerName: '测试企业A', crrFinal: 'CRR4', crrIntThisYear: 'CRR4' },
          { ...makeDefaultRating(), cifNo: 'CUST_002', customerName: '测试企业B', crrFinal: 'CRR6', crrIntThisYear: 'CRR6' },
        ]);
        setRepaymentSchedules([
          { ...makeDefaultRepayment('LN_001'), loanReceiptNo: 'LN_001', totalPeriods: 2, periodNo: 1, dueDate: d.add(6, 'month').format('YYYY-MM-DD'), duePrincipal: 2500000, dueInterest: 50000 },
          { ...makeDefaultRepayment('LN_001'), loanReceiptNo: 'LN_001', totalPeriods: 2, periodNo: 2, dueDate: d.add(12, 'month').format('YYYY-MM-DD'), duePrincipal: 2500000, dueInterest: 50000 },
        ]);
        setHistoricalStages([]);
        break;
      }
      case 'retail_multi': {
        setLoans([
          { ...makeDefaultLoan(), id: 'LN_003', facilityCd: 'FAC_003', customerNo: 'CUST_003', customerName: '测试个人A', industryCn: '其他', segment: '个人', productType: '个消费贷', amtFinancedCny: 150000, loanBalCny: 150000, intAccruedCny: 0, interestRate: 6.0, loanStartDt: d.subtract(1, 'year').format('YYYY-MM-DD'), loanMaturityDt: d.add(1, 'year').format('YYYY-MM-DD'), overdueDays: 0, guaranteeType: '信用', businessType: 'ON_BS' },
          { ...makeDefaultLoan(), id: 'LN_004', facilityCd: 'FAC_004', customerNo: 'CUST_004', customerName: '测试个人B', industryCn: '其他', segment: '个人', productType: '个消费贷', amtFinancedCny: 80000, loanBalCny: 80000, intAccruedCny: 0, interestRate: 6.5, loanStartDt: d.subtract(6, 'month').format('YYYY-MM-DD'), loanMaturityDt: d.add(1, 'year').format('YYYY-MM-DD'), overdueDays: 5, guaranteeType: '信用', businessType: 'ON_BS' },
        ]);
        setFacilities([
          { ...makeDefaultFacility(), facilityCd: 'FAC_003', cifNo: 'CUST_003', customerName: '测试个人A', limitAmtCny: 150000, usedLimit: 150000, limitAvailAmtCny: 0, undrawnAmtCny: 0, collateralPoolId: 'POOL_003' },
          { ...makeDefaultFacility(), facilityCd: 'FAC_004', cifNo: 'CUST_004', customerName: '测试个人B', limitAmtCny: 80000, usedLimit: 80000, limitAvailAmtCny: 0, undrawnAmtCny: 0, collateralPoolId: 'POOL_004' },
        ]);
        setCollaterals([
          { ...makeDefaultCollateral(), collateralCode: 'COL_003', cifNo: 'CUST_003', customerName: '测试个人A', facilityUniqueCode: 'FAC_003', collateralPoolCode: 'POOL_003', collateralCategory: '信用', collateralType: '信用', collateralValue: 0, appraisalValue: 0 },
        ]);
        setRatings([
          { ...makeDefaultRating(), cifNo: 'CUST_003', customerName: '测试个人A', crrFinal: 'CRR4', crrIntThisYear: 'CRR4' },
          { ...makeDefaultRating(), cifNo: 'CUST_004', customerName: '测试个人B', crrFinal: 'CRR6', crrIntThisYear: 'CRR6' },
        ]);
        setRepaymentSchedules([]);
        setHistoricalStages([]);
        break;
      }
    }
    message.success('预设数据已加载');
  };

  // ---- Submit ------------------------------------------------------------
  const handleRunTrial = async () => {
    if (!selectedSchemeId) { message.warning('请选择方案'); return; }
    if (!calcDate) { message.warning('请选择计算日期'); return; }
    setLoading(true);
    try {
      const res = await trialApi.runTrial({
        schemeId: selectedSchemeId,
        assetId: loans[0]?.id || 'TRIAL_AST',
        calcDate: calcDate.format('YYYY-MM-DD'),
        scope,
        // Source tables
        loans,
        facilities,
        repaymentSchedules,
        collaterals,
        ratings,
        historicalStages,
      });
      setResult((res.data as any)?.data || res.data);
      message.success(`试算完成 · ${loans.length} 笔借据`);
    } catch (err: any) {
      console.error('试算失败:', err);
      const errMsg = err?.response?.data?.message || err?.message || '未知错误';
      message.error('试算失败: ' + errMsg);
    }
    finally { setLoading(false); }
  };

  // =====================================================================
  // Section renderers (each returns the children for a Collapse panel)
  // =====================================================================

  const renderLoanRows = () => {
    const rows = loans;
    return (
      <div>
        <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addLoan} style={{ marginBottom: 8 }}>
          添加借据
        </Button>
        {rows.length === 0 && <span style={{ color: '#999', fontSize: 12 }}>暂无数据，请添加借据</span>}
        {rows.map((r, idx) => (
          <div key={idx} className="trial-source-row">
            <div className="trial-field"><label>report_dt</label>
              <DatePicker size="small" style={{ width: 120, fontSize: 11 }} value={r.reportDt ? dayjs(r.reportDt) : undefined} onChange={(v) => updateLoan(idx, { reportDt: v ? v.format('YYYY-MM-DD') : undefined })} /></div>
            <div className="trial-field"><label>id</label>
              <Input style={{ width: 110, fontSize: 11 }} size="small" value={r.id} onChange={(e) => updateLoan(idx, { id: e.target.value })} /></div>
            <div className="trial-field"><label>customer_no</label>
              <Input style={{ width: 100, fontSize: 11 }} size="small" value={r.customerNo} onChange={(e) => updateLoan(idx, { customerNo: e.target.value })} /></div>
            <div className="trial-field"><label>customer_name</label>
              <Input style={{ width: 120, fontSize: 11 }} size="small" value={r.customerName} onChange={(e) => updateLoan(idx, { customerName: e.target.value })} /></div>
            <div className="trial-field"><label>industry_cn</label>
              {textField(r.industryCn, (v) => updateLoan(idx, { industryCn: v }))}</div>
            <div className="trial-field"><label>segment</label>
              {textField(r.segment, (v) => updateLoan(idx, { segment: v }))}</div>
            <div className="trial-field"><label>product_type</label>
              {textField(r.productType, (v) => updateLoan(idx, { productType: v }))}</div>
            <div className="trial-field"><label>currency_cd</label>
              <Input style={{ width: 80, fontSize: 11 }} size="small" value={r.currencyCd} onChange={(e) => updateLoan(idx, { currencyCd: e.target.value })} /></div>
            <div className="trial-field"><label>amt_financed_cny</label>
              <InputNumber style={{ width: 120, fontSize: 11 }} size="small" value={r.amtFinancedCny} onChange={(v) => updateLoan(idx, { amtFinancedCny: v ?? undefined })} min={0} step={10000} /></div>
            <div className="trial-field"><label>loan_bal_cny</label>
              <InputNumber style={{ width: 120, fontSize: 11 }} size="small" value={r.loanBalCny} onChange={(v) => updateLoan(idx, { loanBalCny: v ?? undefined })} min={0} step={10000} /></div>
            <div className="trial-field"><label>int_accrued_cny</label>
              <InputNumber style={{ width: 110, fontSize: 11 }} size="small" value={r.intAccruedCny} onChange={(v) => updateLoan(idx, { intAccruedCny: v ?? undefined })} min={0} /></div>
            <div className="trial-field"><label>interest_rate</label>
              <InputNumber style={{ width: 80, fontSize: 11 }} size="small" value={r.interestRate} onChange={(v) => updateLoan(idx, { interestRate: v ?? undefined })} min={0} step={0.1} precision={2} /></div>
            <div className="trial-field"><label>loan_start_dt</label>
              <DatePicker size="small" style={{ width: 120, fontSize: 11 }} value={r.loanStartDt ? dayjs(r.loanStartDt) : undefined} onChange={(v) => updateLoan(idx, { loanStartDt: v ? v.format('YYYY-MM-DD') : undefined })} /></div>
            <div className="trial-field"><label>loan_maturity_dt</label>
              <DatePicker size="small" style={{ width: 120, fontSize: 11 }} value={r.loanMaturityDt ? dayjs(r.loanMaturityDt) : undefined} onChange={(v) => updateLoan(idx, { loanMaturityDt: v ? v.format('YYYY-MM-DD') : undefined })} /></div>
            <div className="trial-field"><label>overdue_days</label>
              <InputNumber style={{ width: 80, fontSize: 11 }} size="small" value={r.overdueDays} onChange={(v) => updateLoan(idx, { overdueDays: v ?? undefined })} min={0} /></div>
            <div className="trial-field"><label>loan_classif_cd</label>
              {textField(r.loanClassifCd, (v) => updateLoan(idx, { loanClassifCd: v }))}</div>
            <div className="trial-field"><label>is_npl</label>
              <Input style={{ width: 70, fontSize: 11 }} size="small" value={r.isNpl} onChange={(e) => updateLoan(idx, { isNpl: e.target.value })} /></div>
            <div className="trial-field"><label>guarantee_type</label>
              {textField(r.guaranteeType, (v) => updateLoan(idx, { guaranteeType: v }))}</div>
            <div className="trial-field"><label>normal_consecutive_days</label>
              <InputNumber style={{ width: 90, fontSize: 11 }} size="small" value={r.normalConsecutiveDays} onChange={(v) => updateLoan(idx, { normalConsecutiveDays: v ?? undefined })} min={0} /></div>
            <div className="trial-field"><label>other_risk_info</label>
              <Input style={{ width: 120, fontSize: 11 }} size="small" value={r.otherRiskInfo} onChange={(e) => updateLoan(idx, { otherRiskInfo: e.target.value })} /></div>
            <div className="trial-field"><label>business_type</label>
              {textField(r.businessType, (v) => updateLoan(idx, { businessType: v }))}</div>
            <div className="trial-field"><label>overdue_principal</label>
              <InputNumber style={{ width: 110, fontSize: 11 }} size="small" value={r.overduePrincipal} onChange={(v) => updateLoan(idx, { overduePrincipal: v ?? undefined })} min={0} /></div>
            <div className="trial-field"><label>overdue_interest</label>
              <InputNumber style={{ width: 110, fontSize: 11 }} size="small" value={r.overdueInterest} onChange={(v) => updateLoan(idx, { overdueInterest: v ?? undefined })} min={0} /></div>
            <div className="trial-field"><label>facility_cd</label>
              <Input style={{ width: 100, fontSize: 11 }} size="small" value={r.facilityCd} onChange={(e) => updateLoan(idx, { facilityCd: e.target.value })} /></div>
            {rows.length > 1 && (
              <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeLoan(idx)} style={{ alignSelf: 'center' }} />
            )}
          </div>
        ))}
      </div>
    );
  };

  const renderFacilityRows = () => {
    const rows = facilities;
    return (
      <div>
        <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addFacility} style={{ marginBottom: 8 }}>
          添加授信额度
        </Button>
        {rows.length === 0 && <span style={{ color: '#999', fontSize: 12 }}>暂无数据，请添加授信额度</span>}
        {rows.map((r, idx) => (
          <div key={idx} className="trial-source-row">
            <div className="trial-field"><label>facility_cd</label>
              <Input style={{ width: 110, fontSize: 11 }} size="small" value={r.facilityCd} onChange={(e) => updateFacility(idx, { facilityCd: e.target.value })} /></div>
            <div className="trial-field"><label>cif_no</label>
              <Input style={{ width: 100, fontSize: 11 }} size="small" value={r.cifNo} onChange={(e) => updateFacility(idx, { cifNo: e.target.value })} /></div>
            <div className="trial-field"><label>limit_currency_cd</label>
              <Input style={{ width: 80, fontSize: 11 }} size="small" value={r.limitCurrencyCd} onChange={(e) => updateFacility(idx, { limitCurrencyCd: e.target.value })} /></div>
            <div className="trial-field"><label>limit_amt_cny</label>
              <InputNumber style={{ width: 120, fontSize: 11 }} size="small" value={r.limitAmtCny} onChange={(v) => updateFacility(idx, { limitAmtCny: v ?? undefined })} min={0} step={10000} /></div>
            <div className="trial-field"><label>used_limit</label>
              <InputNumber style={{ width: 110, fontSize: 11 }} size="small" value={r.usedLimit} onChange={(v) => updateFacility(idx, { usedLimit: v ?? undefined })} min={0} step={10000} /></div>
            <div className="trial-field"><label>undrawn_amt_cny</label>
              <InputNumber style={{ width: 110, fontSize: 11 }} size="small" value={r.undrawnAmtCny} onChange={(v) => updateFacility(idx, { undrawnAmtCny: v ?? undefined })} min={0} step={10000} /></div>
            <div className="trial-field"><label>is_revolving</label>
              {textField(r.isRevolving, (v) => updateFacility(idx, { isRevolving: v }))}</div>
            <div className="trial-field"><label>facility_start_date</label>
              <DatePicker size="small" style={{ width: 120, fontSize: 11 }} value={r.facilityStartDate ? dayjs(r.facilityStartDate) : undefined} onChange={(v) => updateFacility(idx, { facilityStartDate: v ? v.format('YYYY-MM-DD') : undefined })} /></div>
            <div className="trial-field"><label>facility_maturity_date</label>
              <DatePicker size="small" style={{ width: 120, fontSize: 11 }} value={r.facilityMaturityDate ? dayjs(r.facilityMaturityDate) : undefined} onChange={(v) => updateFacility(idx, { facilityMaturityDate: v ? v.format('YYYY-MM-DD') : undefined })} /></div>
            <div className="trial-field"><label>collateral_pool_id</label>
              <Input style={{ width: 100, fontSize: 11 }} size="small" value={r.collateralPoolId} onChange={(e) => updateFacility(idx, { collateralPoolId: e.target.value })} /></div>
            {rows.length > 1 && (
              <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeFacility(idx)} style={{ alignSelf: 'center' }} />
            )}
          </div>
        ))}
      </div>
    );
  };

  const renderRepaymentRows = () => {
    const rows = repaymentSchedules;
    return (
      <div>
        <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addRepayment} style={{ marginBottom: 8 }}>
          添加还款计划
        </Button>
        {rows.length === 0 && <span style={{ color: '#999', fontSize: 12 }}>暂无还款计划数据</span>}
        {rows.map((r, idx) => (
          <div key={idx} className="trial-source-row">
            <div className="trial-field"><label>loan_receipt_no</label>
              <Input style={{ width: 110, fontSize: 11 }} size="small" value={r.loanReceiptNo} onChange={(e) => updateRepayment(idx, { loanReceiptNo: e.target.value })} /></div>
            <div className="trial-field"><label>total_periods</label>
              <InputNumber style={{ width: 80, fontSize: 11 }} size="small" value={r.totalPeriods} onChange={(v) => updateRepayment(idx, { totalPeriods: v ?? undefined })} min={1} /></div>
            <div className="trial-field"><label>period_no</label>
              <InputNumber style={{ width: 80, fontSize: 11 }} size="small" value={r.periodNo} onChange={(v) => updateRepayment(idx, { periodNo: v ?? undefined })} min={1} /></div>
            <div className="trial-field"><label>due_date</label>
              <DatePicker size="small" style={{ width: 120, fontSize: 11 }} value={r.dueDate ? dayjs(r.dueDate) : undefined} onChange={(v) => updateRepayment(idx, { dueDate: v ? v.format('YYYY-MM-DD') : undefined })} /></div>
            <div className="trial-field"><label>due_principal</label>
              <InputNumber style={{ width: 110, fontSize: 11 }} size="small" value={r.duePrincipal} onChange={(v) => updateRepayment(idx, { duePrincipal: v ?? undefined })} min={0} /></div>
            <div className="trial-field"><label>due_interest</label>
              <InputNumber style={{ width: 110, fontSize: 11 }} size="small" value={r.dueInterest} onChange={(v) => updateRepayment(idx, { dueInterest: v ?? undefined })} min={0} /></div>
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeRepayment(idx)} style={{ alignSelf: 'center' }} />
          </div>
        ))}
      </div>
    );
  };

  const renderCollateralRows = () => {
    const rows = collaterals;
    return (
      <div>
        <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addCollateral} style={{ marginBottom: 8 }}>
          添加抵质押品
        </Button>
        {rows.length === 0 && <span style={{ color: '#999', fontSize: 12 }}>暂无抵质押品数据</span>}
        {rows.map((r, idx) => (
          <div key={idx} className="trial-source-row">
            <div className="trial-field"><label>collateral_code</label>
              <Input style={{ width: 110, fontSize: 11 }} size="small" value={r.collateralCode} onChange={(e) => updateCollateral(idx, { collateralCode: e.target.value })} /></div>
            <div className="trial-field"><label>collateral_pool_code</label>
              <Input style={{ width: 110, fontSize: 11 }} size="small" value={r.collateralPoolCode} onChange={(e) => updateCollateral(idx, { collateralPoolCode: e.target.value })} /></div>
            <div className="trial-field"><label>cif_no</label>
              <Input style={{ width: 100, fontSize: 11 }} size="small" value={r.cifNo} onChange={(e) => updateCollateral(idx, { cifNo: e.target.value })} /></div>
            <div className="trial-field"><label>facility_unique_code</label>
              <Input style={{ width: 110, fontSize: 11 }} size="small" value={r.facilityUniqueCode} onChange={(e) => updateCollateral(idx, { facilityUniqueCode: e.target.value })} /></div>
            <div className="trial-field"><label>collateral_category</label>
              {textField(r.collateralCategory, (v) => updateCollateral(idx, { collateralCategory: v }))}</div>
            <div className="trial-field"><label>collateral_type</label>
              {textField(r.collateralType, (v) => updateCollateral(idx, { collateralType: v }))}</div>
            <div className="trial-field"><label>collateral_value</label>
              <InputNumber style={{ width: 120, fontSize: 11 }} size="small" value={r.collateralValue} onChange={(v) => updateCollateral(idx, { collateralValue: v ?? undefined })} min={0} step={10000} /></div>
            <div className="trial-field"><label>appraisal_value</label>
              <InputNumber style={{ width: 120, fontSize: 11 }} size="small" value={r.appraisalValue} onChange={(v) => updateCollateral(idx, { appraisalValue: v ?? undefined })} min={0} step={10000} /></div>
            <div className="trial-field"><label>appraisal_effective_date</label>
              <DatePicker size="small" style={{ width: 120, fontSize: 11 }} value={r.appraisalEffectiveDate ? dayjs(r.appraisalEffectiveDate) : undefined} onChange={(v) => updateCollateral(idx, { appraisalEffectiveDate: v ? v.format('YYYY-MM-DD') : undefined })} /></div>
            <div className="trial-field"><label>guarantee_method</label>
              <Input style={{ width: 90, fontSize: 11 }} size="small" value={r.guaranteeMethod} onChange={(e) => updateCollateral(idx, { guaranteeMethod: e.target.value })} /></div>
            {rows.length > 1 && (
              <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeCollateral(idx)} style={{ alignSelf: 'center' }} />
            )}
          </div>
        ))}
      </div>
    );
  };

  const renderRatingRows = () => {
    const rows = ratings;
    return (
      <div>
        <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addRating} style={{ marginBottom: 8 }}>
          添加评级信息
        </Button>
        {rows.length === 0 && <span style={{ color: '#999', fontSize: 12 }}>暂无评级数据</span>}
        {rows.map((r, idx) => (
          <div key={idx} className="trial-source-row">
            <div className="trial-field"><label>cif_no</label>
              <Input style={{ width: 100, fontSize: 11 }} size="small" value={r.cifNo} onChange={(e) => updateRating(idx, { cifNo: e.target.value })} /></div>
            <div className="trial-field"><label>customer_name</label>
              <Input style={{ width: 110, fontSize: 11 }} size="small" value={r.customerName} onChange={(e) => updateRating(idx, { customerName: e.target.value })} /></div>
            <div className="trial-field"><label>crr_int_last_year</label>
              {textField(r.crrIntLastYear, (v) => updateRating(idx, { crrIntLastYear: v }))}</div>
            <div className="trial-field"><label>crr_int_this_year</label>
              {textField(r.crrIntThisYear, (v) => updateRating(idx, { crrIntThisYear: v }))}</div>
            <div className="trial-field"><label>crr_final</label>
              {textField(r.crrFinal, (v) => updateRating(idx, { crrFinal: v }))}</div>
            <div className="trial-field"><label>ext_rating_co_last_year</label>
              <Input style={{ width: 110, fontSize: 11 }} size="small" value={r.extRatingCoLastYear} onChange={(e) => updateRating(idx, { extRatingCoLastYear: e.target.value })} /></div>
            <div className="trial-field"><label>ext_rating_last_year</label>
              {textField(r.extRatingLastYear, (v) => updateRating(idx, { extRatingLastYear: v }))}</div>
            <div className="trial-field"><label>ext_rating_co_this_year</label>
              <Input style={{ width: 110, fontSize: 11 }} size="small" value={r.extRatingCoThisYear} onChange={(e) => updateRating(idx, { extRatingCoThisYear: e.target.value })} /></div>
            <div className="trial-field"><label>ext_rating_this_year</label>
              {textField(r.extRatingThisYear, (v) => updateRating(idx, { extRatingThisYear: v }))}</div>
            {rows.length > 1 && (
              <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeRating(idx)} style={{ alignSelf: 'center' }} />
            )}
          </div>
        ))}
      </div>
    );
  };

  const renderHistoricalStageRows = () => {
    const rows = historicalStages;
    return (
      <div>
        <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addHistoricalStage} style={{ marginBottom: 8 }}>
          添加历史阶段
        </Button>
        {rows.length === 0 && <span style={{ color: '#999', fontSize: 12 }}>暂无历史阶段数据</span>}
        {rows.map((r, idx) => (
          <div key={idx} className="trial-source-row">
            <div className="trial-field"><label>asset_id</label>
              <Input style={{ width: 110, fontSize: 11 }} size="small" value={r.assetId} onChange={(e) => updateHistoricalStage(idx, { assetId: e.target.value })} /></div>
            <div className="trial-field"><label>stage_result</label>
              {textField(r.stageResult, (v) => updateHistoricalStage(idx, { stageResult: v }))}</div>
            <div className="trial-field"><label>calc_date</label>
              <DatePicker size="small" style={{ width: 120, fontSize: 11 }} value={r.calcDate ? dayjs(r.calcDate) : undefined} onChange={(v) => updateHistoricalStage(idx, { calcDate: v ? v.format('YYYY-MM-DD') : undefined })} /></div>
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeHistoricalStage(idx)} style={{ alignSelf: 'center' }} />
          </div>
        ))}
      </div>
    );
  };

  // ---- Result table columns -----------------------------------------------
  const resultColumns = [
    { title: '借据', dataIndex: 'assetId', key: 'assetId', width: 180 },
    { title: '分组', dataIndex: 'groupLabel', key: 'groupLabel' },
    { title: '产品', dataIndex: 'productType', key: 'productType', width: 100 },
    { title: '阶段', dataIndex: 'stage', key: 'stage', width: 80 },
    { title: 'PD(存续期)', dataIndex: 'pdLifetime', key: 'pdLifetime', width: 110 },
    { title: 'EAD', dataIndex: 'ead', key: 'ead', width: 130 },
    { title: 'LGD', dataIndex: 'lgd', key: 'lgd', width: 100 },
    { title: 'ECL 最终', dataIndex: 'eclFinal', key: 'eclFinal', width: 140,
      render: (v: string) => <strong style={{ color: '#f5222d' }}>{v}</strong> },
    { title: '异常', dataIndex: 'exceptionSummary', key: 'exceptionSummary',
      render: (v: string) => v ? <Tag color="red" style={{ fontSize: 10 }}>{v}</Tag> : null },
  ];

  // =====================================================================
  // Render
  // =====================================================================
  return (
    <div className="ecl-page">
      <PageHeader title="试算中心"
        subtitle="手工构造借据参数，支持多笔借据按客户维度跑批。试算数据与正式跑批完全隔离。" />

      <Panel title="快速预设">
        <Space wrap>
          <Button size="small" onClick={() => applyPreset('corp_dual')}>对公 · 双借据</Button>
          <Button size="small" onClick={() => applyPreset('retail_multi')}>零售 · 双借据</Button>
        </Space>
      </Panel>

      <Panel title="试算条件" extra={<Tag color="blue">试算数据 · 不写入正式跑批</Tag>}>
        <div style={{ marginBottom: 16, display: 'flex', gap: 8 }}>
          <Button size="small" icon={<DownloadOutlined />} onClick={downloadExcelTemplate}>
            下载模板
          </Button>
          <Button size="small" icon={<UploadOutlined />}
            onClick={() => fileInputRef.current?.click()}>
            导入 Excel
          </Button>
          <input ref={fileInputRef} type="file" accept=".xlsx,.xls"
            style={{ display: 'none' }}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) { handleImportExcel(file); e.target.value = ''; }
            }} />
        </div>

        <div className="trial-form-row">
          <div className="trial-field">
            <label>scheme_id</label>
            <Select style={{ width: 280 }} placeholder="请选择" value={selectedSchemeId || undefined}
              onChange={setSelectedSchemeId} options={schemeOptions} />
          </div>
          <div className="trial-field">
            <label>calc_date</label>
            <DatePicker value={calcDate} onChange={(v) => v && setCalcDate(v)} />
          </div>
          <div className="trial-field">
            <label>scope</label>
            <Radio.Group value={scope} onChange={(e) => setScope(e.target.value)}>
              <Radio value="SINGLE">单笔/多笔</Radio>
              <Radio value="BATCH" disabled>批量</Radio>
            </Radio.Group>
          </div>
        </div>

        <div style={{ marginTop: 12 }}>
          <Collapse size="small" defaultActiveKey={['loans']}
            items={[
              {
                key: 'loans',
                label: `借据信息表（${loans.length} 行）`,
                children: renderLoanRows(),
              },
              {
                key: 'facilities',
                label: `授信额度表（${facilities.length} 行）`,
                children: renderFacilityRows(),
              },
              {
                key: 'repayments',
                label: `还款计划表（${repaymentSchedules.length} 行）`,
                children: renderRepaymentRows(),
              },
              {
                key: 'collaterals',
                label: `抵质押品表（${collaterals.length} 行）`,
                children: renderCollateralRows(),
              },
              {
                key: 'ratings',
                label: `评级信息表（${ratings.length} 行）`,
                children: renderRatingRows(),
              },
              {
                key: 'stages',
                label: `历史阶段表（${historicalStages.length} 行）`,
                children: renderHistoricalStageRows(),
              },
            ]}
          />
        </div>

        <div style={{ marginTop: 16 }}>
          <Button type="primary" size="large" icon={<ExperimentOutlined />}
            loading={loading} onClick={handleRunTrial}>开始试算</Button>
        </div>
      </Panel>

      {result && (
        <Panel title="试算结果" extra={
          <Space>
            <span className="ecl-mono">{result.jobId}</span>
            <Tag color="green">耗时 {result.durationMs}ms</Tag>
          </Space>
        }>
          {result.assetResults && result.assetResults.length > 1 ? (
            <Table rowKey="assetId" columns={resultColumns} dataSource={result.assetResults}
              size="small" pagination={false}
              expandable={{
                expandedRowRender: (row: AssetResult) => (
                  <div style={{ padding: 8 }}>
                    {row.steps.map((step) => <TrialStep key={step.key} step={step} />)}
                  </div>
                ),
              }}
            />
          ) : (
            <>
              <div className="trial-final-bar">
                <div><div className="trial-final-label">阶段</div><div className="trial-final-value">{result.stage}</div></div>
                <div><div className="trial-final-label">PD (12M)</div><div className="trial-final-value">{result.pd12m}</div></div>
                <div><div className="trial-final-label">PD (存续期)</div><div className="trial-final-value">{result.pdLifetime}</div></div>
                <div><div className="trial-final-label">EAD</div><div className="trial-final-value">{result.ead}</div></div>
                <div><div className="trial-final-label">LGD</div><div className="trial-final-value">{result.lgd}</div></div>
                <div><div className="trial-final-label">ECL 加权</div><div className="trial-final-value">{result.eclValue}</div></div>
                <div><div className="trial-final-label">叠加</div><div className="trial-final-value">{result.overlayAmount}</div></div>
                <div><div className="trial-final-label">ECL 最终</div><div className="trial-final-value emphasis">{result.eclFinal}</div></div>
              </div>
              {result.steps.map((step) => <TrialStep key={step.key} step={step} />)}
            </>
          )}
        </Panel>
      )}
    </div>
  );
};

const TrialStep: React.FC<{ step: TrialStepVO }> = ({ step }) => (
  <div className="trial-step">
    <div className="trial-step-header">
      <span className="trial-step-icon">{step.note ? '!' : '✓'}</span>
      <span className="trial-step-title">{step.title}</span>
      <span className="trial-step-summary">{step.summary}</span>
    </div>
    <div className="trial-step-body">
      {step.note && <div className="trial-note">{step.note}</div>}
      {step.scenarioRows && step.scenarioRows.length > 0 ? (
        <table className="trial-scenario-table">
          <thead><tr><th>情景</th><th>权重</th><th>PD</th><th>加权 PD</th></tr></thead>
          <tbody>
            {step.scenarioRows.map((row) => (
              <tr key={row.scenario} className={row.highlight ? 'highlight' : undefined}>
                <td>{row.scenario}</td><td>{row.weight}</td><td>{row.pd}</td><td>{row.weightedPd}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="trial-metrics">
          {step.metrics?.map((metric) => (
            <div className="trial-metric" key={metric.label}>
              <div className="trial-metric-label">{metric.label}</div>
              <div className="trial-metric-value">{metric.value}</div>
              {metric.note && <div className="trial-metric-note">{metric.note}</div>}
            </div>
          ))}
        </div>
      )}
    </div>
  </div>
);

export default TrialCenter;
