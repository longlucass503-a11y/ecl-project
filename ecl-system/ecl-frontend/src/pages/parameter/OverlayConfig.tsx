import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Select,
  Button,
  Space,
  Modal,
  Form,
  Input,
  InputNumber,
  message,
  Typography,
  Empty,
  Tag,
  Divider,
  Row,
  Col,
  Upload,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  MinusCircleOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import * as XLSX from 'xlsx';
import { useSearchParams, useOutletContext } from 'react-router-dom';
import { schemeApi, type SchemeVO } from '../../api/scheme';
import { riskGroupApi, type RiskGroupVO } from '../../api/riskGroup';
import {
  overlayApi,
  type OverlayRuleVO,
  type OverlayMatchTestResp,
} from '../../api/overlay';
import { dictApi, type DictEntryVO } from '../../api/dict';
import { PageHeader, Panel, GroupSelector } from '../../components';

/* ===================================================================
   条件编辑器（单元格可编辑）
   =================================================================== */

interface ConditionRow {
  key: string; // 唯一标识
  type: string;
  operator: string;
  value: string | number | string[];
}

const CONDITION_TYPES = [
  { label: '逾期天数', value: '逾期天数' },
  { label: '五级分类', value: '五级分类' },
  { label: 'CRR 评级下降', value: 'CRR 评级下降' },
  { label: '违约标识', value: '违约标识' },
  { label: '逾期天数范围', value: '逾期天数范围' },
  { label: '舆情事件', value: '舆情事件' },
  { label: '行业代码', value: '行业代码' },
  { label: '产品类型', value: '产品类型' },
  { label: '客户名称', value: '客户名称' },
  { label: '客户名称列表', value: '客户名称列表' },
  { label: 'EAD均值比', value: 'EAD均值比' },
];

const OPERATORS_BY_TYPE: Record<string, { label: string; value: string }[]> = {
  '逾期天数': [
    { label: '>', value: 'gt' },
    { label: '>=', value: 'gte' },
    { label: '<', value: 'lt' },
    { label: '<=', value: 'lte' },
    { label: '=', value: 'eq' },
  ],
  '五级分类': [
    { label: '属于', value: 'in' },
    { label: '不属于', value: 'not_in' },
  ],
  'CRR 评级下降': [
    { label: '是', value: '是' },
    { label: '否', value: '否' },
  ],
  '违约标识': [
    { label: '是', value: '是' },
    { label: '否', value: '否' },
  ],
  '逾期天数范围': [
    { label: '在范围内', value: 'range' },
  ],
  '舆情事件': [
    { label: '包含关键词', value: 'contains' },
  ],
  '行业代码': [
    { label: '=', value: 'eq' },
    { label: '!=', value: 'ne' },
    { label: '属于', value: 'in' },
    { label: '不属于', value: 'not_in' },
  ],
  '产品类型': [
    { label: '=', value: 'eq' },
    { label: '!=', value: 'ne' },
    { label: '属于', value: 'in' },
    { label: '不属于', value: 'not_in' },
  ],
  '客户名称': [
    { label: '包含关键词', value: 'contains' },
  ],
  '客户名称列表': [
    { label: '属于', value: 'in' },
    { label: '不属于', value: 'not_in' },
  ],
  'EAD均值比': [
    { label: '>', value: 'gt' },
    { label: '>=', value: 'gte' },
    { label: '<', value: 'lt' },
    { label: '<=', value: 'lte' },
    { label: '=', value: 'eq' },
  ],
};

const FIVE_CATEGORY_OPTIONS = [
  '正常', '关注', '次级', '可疑', '损失',
];

// 命中测试可选字段
const TEST_FIELD_OPTIONS = [
  { label: '行业代码 (industryCode)', value: 'industryCode' },
  { label: '逾期天数 (overdueDays)', value: 'overdueDays' },
  { label: '五级分类 (fiveCategory)', value: 'fiveCategory' },
  { label: '违约标识 (defaultFlag)', value: 'defaultFlag' },
  { label: 'CRR评级 (crrRating)', value: 'crrRating' },
  { label: '客户类型 (customerType)', value: 'customerType' },
  { label: '担保方式 (guaranteeType)', value: 'guaranteeType' },
  { label: '行业分类 (industry)', value: 'industry' },
  { label: '资产状态 (assetStatus)', value: 'assetStatus' },
  { label: '是否不良 (isNpl)', value: 'isNpl' },
  { label: '客户名称 (customerName)', value: 'customerName' },
  { label: 'EAD均值 (eadAvg)', value: 'eadAvg' },
];

let _rowKeySeed = 0;
const genKey = () => `row_${++_rowKeySeed}_${Date.now()}`;

// 将 JSON 字符串解析为 ConditionRow[]
function parseConditionsJson(jsonStr: string): ConditionRow[] {
  if (!jsonStr || jsonStr === '{}' || jsonStr.trim() === '') return [];
  try {
    const obj = JSON.parse(jsonStr);
    // 新编辑器格式
    if (obj.conditions && Array.isArray(obj.conditions)) {
      return obj.conditions.map((cond: any, idx: number) => ({
        key: genKey(),
        type: cond.type || '逾期天数',
        operator: cond.operator || 'eq',
        value: cond.values ?? (cond.value ?? ''),
      }));
    }
    // 兼容旧格式: {"overdue_days": {"min": 30}}
    // 只处理简单等值格式
    return [];
  } catch {
    return [];
  }
}

// 将 ConditionRow[] 序列化为编辑器 JSON
function serializeConditions(rows: ConditionRow[]): string {
  const conditions = rows.map((row) => {
    if (row.type === '五级分类') {
      const vals = Array.isArray(row.value) ? row.value : (typeof row.value === 'string' ? row.value.split(',').filter(Boolean) : []);
      return { type: row.type, operator: row.operator, values: vals };
    }
    if (row.type === '逾期天数范围') {
      const v = row.value;
      if (typeof v === 'string') {
        const parts = v.split(',');
        return { type: row.type, operator: row.operator, min: Number(parts[0] ?? 0), max: Number(parts[1] ?? 0) };
      }
      return { type: row.type, operator: row.operator, min: 0, max: Number(row.value ?? 0) };
    }
    if (row.type === '舆情事件') {
      return { type: row.type, operator: 'contains', value: String(row.value ?? '') };
    }
    if (row.type === '行业代码') {
      const v = String(row.value ?? '').trim();
      if (row.operator === 'in' || row.operator === 'not_in') {
        const parts = v.split(',').map((s) => s.trim()).filter(Boolean);
        return { type: row.type, operator: row.operator, values: parts };
      }
      return { type: row.type, operator: row.operator, value: v };
    }
    if (row.type === '违约标识') {
      return { type: row.type, operator: row.operator, value: row.operator === '是' };
    }
    if (row.type === '客户名称') {
      return { type: row.type, operator: 'contains', value: String(row.value ?? '') };
    }
    if (row.type === '客户名称列表') {
      const vals = Array.isArray(row.value) ? row.value : (typeof row.value === 'string' ? row.value.split(',').map((s) => s.trim()).filter(Boolean) : []);
      return { type: row.type, operator: row.operator, values: vals };
    }
    if (row.type === 'EAD均值比') {
      const numVal = Number(row.value);
      return { type: row.type, operator: row.operator, value: (!isNaN(numVal) && numVal !== 0) ? numVal : 1.0 };
    }
    if (row.type === '产品类型') {
      const v = String(row.value ?? '').trim();
      if (row.operator === 'in' || row.operator === 'not_in') {
        const parts = v.split(',').map((s) => s.trim()).filter(Boolean);
        return { type: row.type, operator: row.operator, values: parts };
      }
      return { type: row.type, operator: row.operator, value: v };
    }
    return { type: row.type, operator: row.operator, value: row.value };
  });
  return JSON.stringify({ logic: 'AND', conditions });
}

/* 条件行编辑器 */
const ConditionBuilder: React.FC<{
  value?: ConditionRow[];
  onChange: (rows: ConditionRow[]) => void;
  error?: boolean;
  industryOptions?: DictEntryVO[];
  productTypeOptions?: DictEntryVO[];
}> = ({ value: rows = [], onChange, error, industryOptions = [], productTypeOptions = [] }) => {
  const addRow = () => {
    onChange([...rows, { key: genKey(), type: '逾期天数', operator: 'gte', value: '' }]);
  };

  const removeRow = (key: string) => {
    onChange(rows.filter((r) => r.key !== key));
  };

  const updateRow = (key: string, field: keyof ConditionRow, rawVal: any) => {
    const updated = rows.map((r) => {
      if (r.key !== key) return r;
      const next = { ...r, [field]: rawVal };
      // 切换类型时重置 operator
      if (field === 'type') {
        const ops = OPERATORS_BY_TYPE[rawVal] || [];
        next.operator = ops[0]?.value || 'eq';
        next.value = rawVal === 'EAD均值比' ? '1.0' : '';
      }
      return next;
    });
    onChange(updated);
  };

  const getOperators = (type: string) => OPERATORS_BY_TYPE[type] || [];

  return (
    <div>
      <table style={{ width: '100%', borderCollapse: 'collapse', border: error ? '1px solid #ff4d4f' : '1px solid #d9d9d9', borderRadius: 6 }}>
        <thead>
          <tr style={{ background: '#fafafa' }}>
            <th style={{ padding: '8px 12px', textAlign: 'left', width: '30%', borderBottom: '1px solid #d9d9d9', fontWeight: 500 }}>字段</th>
            <th style={{ padding: '8px 12px', textAlign: 'left', width: '20%', borderBottom: '1px solid #d9d9d9', fontWeight: 500 }}>操作符</th>
            <th style={{ padding: '8px 12px', textAlign: 'left', borderBottom: '1px solid #d9d9d9', fontWeight: 500 }}>值</th>
            <th style={{ padding: '8px 12px', width: 48, borderBottom: '1px solid #d9d9d9' }}></th>
          </tr>
        </thead>
        <tbody>
          {(rows == null || rows.length === 0) && (
            <tr>
              <td colSpan={4} style={{ padding: '16px 12px', textAlign: 'center', color: '#999' }}>
                暂无条件（点击下方按钮添加）
              </td>
            </tr>
          )}
          {rows.map((row) => {
            const ops = getOperators(row.type);
            const isFiveCategory = row.type === '五级分类';
            const isIndustryCode = row.type === '行业代码';
            const isProductType = row.type === '产品类型';
            const isDefaultFlag = row.type === '违约标识';
            return (
              <tr key={row.key}>
                {/* 字段选择 */}
                <td style={{ padding: '4px 8px' }}>
                  <Select
                    value={row.type}
                    onChange={(v) => updateRow(row.key, 'type', v)}
                    options={CONDITION_TYPES}
                    style={{ width: '100%' }}
                    size="small"
                  />
                </td>
                {/* 操作符选择 */}
                <td style={{ padding: '4px 8px' }}>
                  <Select
                    value={row.operator}
                    onChange={(v) => updateRow(row.key, 'operator', v)}
                    options={ops}
                    style={{ width: '100%' }}
                    size="small"
                  />
                </td>
                {/* 值输入 */}
                <td style={{ padding: '4px 8px' }}>
                  {isDefaultFlag ? (
                    <span style={{ color: '#999', fontSize: 12 }}>
                      {row.operator === '是' ? '违约' : '未违约'}
                    </span>
                  ) : isFiveCategory ? (
                    <Select
                      mode="multiple"
                      value={Array.isArray(row.value) ? row.value : []}
                      onChange={(v) => updateRow(row.key, 'value', v)}
                      options={FIVE_CATEGORY_OPTIONS.map((o) => ({ label: o, value: o }))}
                      style={{ width: '100%' }}
                      size="small"
                      placeholder="选择分类"
                    />
                  ) : isIndustryCode ? (
                    <Select
                      mode="multiple"
                      value={(() => {
                        const v = row.value;
                        if (Array.isArray(v)) return v;
                        if (typeof v === 'string' && v.trim()) {
                          if (row.operator === 'in' || row.operator === 'not_in') {
                            return v.split(',').map(s => s.trim()).filter(Boolean);
                          }
                          return [v.trim()];
                        }
                        return [];
                      })()}
                      onChange={(v) => updateRow(row.key, 'value', v.join(','))}
                      options={[
                        ...industryOptions.map((d) => ({
                          label: `${d.entryName}(${d.entryCode})`,
                          value: d.entryCode || '',
                        })),
                      ]}
                      style={{ width: '100%' }}
                      size="small"
                      placeholder="选择行业代码（多选）"
                    />
                  ) : row.type === '逾期天数范围' ? (
                    <Space size={4}>
                      <InputNumber
                        value={typeof row.value === 'string' ? Number(row.value.split(',')[0]) : Number(row.value)}
                        onChange={(v) => {
                          const max = typeof row.value === 'string' ? Number(row.value.split(',')[1]) : 0;
                          updateRow(row.key, 'value', `${v ?? 0},${max}`);
                        }}
                        placeholder="最小值"
                        style={{ width: 80 }}
                        size="small"
                      />
                      <span>~</span>
                      <InputNumber
                        value={typeof row.value === 'string' ? Number(row.value.split(',')[1]) : 0}
                        onChange={(v) => {
                          const min = typeof row.value === 'string' ? Number(row.value.split(',')[0]) : 0;
                          updateRow(row.key, 'value', `${min},${v ?? 0}`);
                        }}
                        placeholder="最大值"
                        style={{ width: 80 }}
                        size="small"
                      />
                    </Space>
                  ) : isProductType ? (
                    <Select
                      mode="multiple"
                      value={(() => {
                        const v = row.value;
                        if (Array.isArray(v)) return v;
                        if (typeof v === 'string' && v.trim()) {
                          if (row.operator === 'in' || row.operator === 'not_in') {
                            return v.split(',').map(s => s.trim()).filter(Boolean);
                          }
                          return [v.trim()];
                        }
                        return [];
                      })()}
                      onChange={(v) => updateRow(row.key, 'value', v.join(','))}
                      options={[
                        ...productTypeOptions.map((d) => ({
                          label: `${d.entryName}(${d.entryCode})`,
                          value: d.entryCode || '',
                        })),
                      ]}
                      style={{ width: '100%' }}
                      size="small"
                      placeholder="选择产品类型（多选）"
                    />
                  ) : row.type === '客户名称列表' ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <Upload
                        accept=".xlsx,.xls"
                        showUploadList={false}
                        beforeUpload={(file) => {
                          const reader = new FileReader();
                          reader.onload = (e) => {
                            try {
                              const data = new Uint8Array(e.target?.result as ArrayBuffer);
                              const workbook = XLSX.read(data, { type: 'array' });
                              const firstSheet = workbook.Sheets[workbook.SheetNames[0]];
                              const json = XLSX.utils.sheet_to_json(firstSheet, { header: 1 }) as any[][];
                              const names: string[] = [];
                              for (const row of json) {
                                if (row && row.length > 0 && row[0] != null) {
                                  const name = String(row[0]).trim();
                                  if (name) names.push(name);
                                }
                              }
                              const existing = (() => {
                                const v = row.value;
                                if (Array.isArray(v)) return v;
                                if (typeof v === 'string' && v.trim()) return v.split(',').map(s => s.trim()).filter(Boolean);
                                return [];
                              })();
                              const merged = [...new Set([...existing, ...names])];
                              updateRow(row.key, 'value', merged.join(','));
                              message.success(`成功导入 ${names.length} 个客户名称`);
                            } catch (err) {
                              message.error('Excel 解析失败: ' + (err as Error).message);
                            }
                          };
                          reader.readAsArrayBuffer(file);
                          return false;
                        }}
                      >
                        <Button size="small" icon={<UploadOutlined />}>上传Excel</Button>
                      </Upload>
                      {(() => {
                        const v = row.value;
                        const names: string[] = [];
                        if (Array.isArray(v)) names.push(...v);
                        else if (typeof v === 'string' && v.trim()) {
                          names.push(...v.split(',').map(s => s.trim()).filter(Boolean));
                        }
                        if (names.length === 0) return (
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>尚未上传客户名单</Typography.Text>
                        );
                        return (
                          <div style={{ marginTop: 2 }}>
                            <Typography.Text type="secondary" style={{ fontSize: 11 }}>共 {names.length} 个客户：</Typography.Text>
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 2, marginTop: 2, maxHeight: 90, overflowY: 'auto' }}>
                              {names.map((name, idx) => (
                                <Tag
                                  key={idx}
                                  closable
                                  onClose={() => {
                                    const remaining = names.filter((_, i) => i !== idx);
                                    updateRow(row.key, 'value', remaining.join(','));
                                  }}
                                  style={{ fontSize: 11, margin: 1 }}
                                >{name}</Tag>
                              ))}
                            </div>
                          </div>
                        );
                      })()}
                    </div>
                  ) : row.type === '客户名称' ? (
                    <Input
                      value={String(row.value ?? '')}
                      onChange={(e) => updateRow(row.key, 'value', e.target.value)}
                      placeholder="输入客户名称关键词"
                      size="small"
                    />
                  ) : (
                    <Input
                      value={String(row.value ?? '')}
                      onChange={(e) => updateRow(row.key, 'value', e.target.value)}
                      placeholder="输入值"
                      size="small"
                    />
                  )}
                </td>
                {/* 删除按钮 */}
                <td style={{ padding: '4px 4px', textAlign: 'center' }}>
                  <Button
                    type="text"
                    danger
                    size="small"
                    icon={<MinusCircleOutlined />}
                    onClick={() => removeRow(row.key)}
                  />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <Button
        type="dashed"
        icon={<PlusOutlined />}
        onClick={addRow}
        size="small"
        style={{ marginTop: 8, width: '100%' }}
      >
        添加条件
      </Button>
      {(rows != null && rows.length > 1) && (
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 6, fontSize: 12 }}>
          多个条件之间为 AND 关系（同时满足）
        </Typography.Text>
      )}
    </div>
  );
};

/* ===================================================================
   OverlayConfig 主页面
   =================================================================== */
const OverlayConfig: React.FC = () => {
  const [searchParams] = useSearchParams();
  const schemeIdFromUrl = searchParams.get('schemeId') || '';
  const { schemeContext } = useOutletContext<{ schemeContext?: { schemeId: string } }>();
  const effectiveSchemeId = schemeIdFromUrl || schemeContext?.schemeId || '';

  // ─── 方案 & 分组 ───
  const [schemes, setSchemes] = useState<SchemeVO[]>([]);
  const [selectedSchemeId, setSelectedSchemeId] = useState<string>(effectiveSchemeId);
  const [groups, setGroups] = useState<RiskGroupVO[]>([]);


  // ─── 规则列表 ───
  const [rules, setRules] = useState<OverlayRuleVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<OverlayRuleVO | null>(null);
  const [form] = Form.useForm();

  // ─── 命中测试 ───
  const [testModalOpen, setTestModalOpen] = useState(false);
  const [testFields, setTestFields] = useState<{ key: string; value: string }[]>([
    { key: 'industryCode', value: '' },
    { key: 'overdueDays', value: '' },
  ]);
  const [testResult, setTestResult] = useState<OverlayMatchTestResp | null>(null);
  const [dictIndustry, setDictIndustry] = useState<DictEntryVO[]>([]);
  const [dictProductType, setDictProductType] = useState<DictEntryVO[]>([]);
  const [testLoading, setTestLoading] = useState(false);
  const [selectedAdjustmentType, setSelectedAdjustmentType] = useState<string>('ADDBP');

  // 加载字典选项（按 selectedSchemeId 加载）
  useEffect(() => {
    if (selectedSchemeId) {
      dictApi.getEffectiveEntries(selectedSchemeId, 'INDUSTRY').then((res) => {
        setDictIndustry((res.data as any)?.data || res.data || []);
      }).catch(console.error);
      dictApi.getEffectiveEntries(selectedSchemeId, 'PRODUCT_TYPE').then((res) => {
        setDictProductType((res.data as any)?.data || res.data || []);
      }).catch(console.error);
    } else {
      setDictIndustry([]);
      setDictProductType([]);
    }
  }, [selectedSchemeId]);

  // 加载方案列表
  useEffect(() => {
    schemeApi.list().then((res) => {
      setSchemes((res.data as any)?.data || res.data || []);
    });
  }, []);

  // 方案变化 -> 加载分组
  useEffect(() => {
    if (!selectedSchemeId) {
      setGroups([]);
      
      return;
    }
    riskGroupApi.listByScheme(selectedSchemeId).then((res) => {
      setGroups((res.data as any)?.data || res.data || []);
    });
    
    setRules([]);
  }, [selectedSchemeId]);

  // 加载规则列表
  const loadRules = useCallback(async () => {
    if (!selectedSchemeId) {
      setRules([]);
      return;
    }
    setLoading(true);
    try {
      const res = await overlayApi.list(selectedSchemeId);
      setRules((res.data as any)?.data || res.data || []);
    } finally {
      setLoading(false);
    }
  }, [selectedSchemeId]);

  useEffect(() => {
    loadRules();
  }, [loadRules]);

  // ─── 规则 CRUD ───
  const handleSave = async () => {
    const values = await form.validateFields();
    if (editingRule) {
      await overlayApi.update(editingRule.overlayId!, {
        ...values,
        schemeId: selectedSchemeId,
        groupId: undefined,
      });
      message.success('规则更新成功');
    } else {
      await overlayApi.create({
        ...values,
        schemeId: selectedSchemeId,
        groupId: undefined,
      });
      message.success('规则创建成功');
    }
    setModalOpen(false);
    setEditingRule(null);
    form.resetFields();
    loadRules();
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这条叠加规则吗？',
      onOk: async () => {
        await overlayApi.delete(id);
        message.success('已删除');
        loadRules();
      },
    });
  };

  // ─── 命中测试 ───
  const handleTestMatch = async () => {
    if (!selectedSchemeId) { message.warning('请先选择方案'); return; }

    const fieldMap: Record<string, any> = {};
    testFields.forEach((f) => {
      if (f.key && f.value) {
        const num = Number(f.value);
        fieldMap[f.key] = isNaN(num) ? f.value : num;
      }
    });
    if (Object.keys(fieldMap).length === 0) {
      message.warning('请至少填写一组键值对');
      return;
    }
    setTestLoading(true);
    setTestResult(null);
    try {
      const res = await overlayApi.testMatch({
        schemeId: selectedSchemeId,
        groupId: undefined,
        fieldValues: fieldMap,
      });
      setTestResult((res.data as any)?.data || res.data);
    } catch (err: any) {
      console.error('命中测试请求失败:', err);
      const errMsg = err?.response?.data?.message || err?.message || '未知错误';
      message.error('命中测试请求失败: ' + errMsg);
    } finally {
      setTestLoading(false);
    }
  };

  const handleOpenTest = () => {
    setTestResult(null);
    setTestFields([{ key: '', value: '' }]);
    setTestModalOpen(true);
  };

  // 打开编辑弹窗时，解析 conditions 并初始化 ConditionBuilder
  const handleOpenModal = (rule?: OverlayRuleVO) => {
    if (rule) {
      setEditingRule(rule);
      setSelectedAdjustmentType(rule.adjustmentType || 'ADDBP');
      const parsed = parseConditionsJson(rule.conditions || '{}');
      form.setFieldsValue({
        ...rule,
        _conditions: parsed,
      });
    } else {
      setEditingRule(null);
      setSelectedAdjustmentType('ADDBP');
      form.setFieldsValue({ _conditions: [] });
    }
    setModalOpen(true);
  };

  // 保存时从 ConditionBuilder 取值生成 JSON
  const handleFormValuesChange = (changedValues: any, allValues: any) => {
    if (changedValues.adjustmentType) {
      setSelectedAdjustmentType(changedValues.adjustmentType);
    }
    if ('_conditions' in changedValues) {
      // ConditionBuilder 的值已在上层处理，这里不需要额外操作
    }
  };

  const adjustmentTypeColor: Record<string, string> = {
    ADDBP: 'blue',
    PERCENTAGE: 'green',
    FIXED: 'orange',
  };

  // ─── 渲染 ───
  if (!selectedSchemeId) {
    return (
      <div className="ecl-page">
        <PageHeader
          title="管理层叠加配置"
          subtitle="定义管理层叠加规则，按风险分组对 PD/LGD/CCF/EAD 进行调整"
        />
        <Panel>
          <Empty description="请先选择一个 ECL 方案">
            <Select
              style={{ width: 300, marginTop: 16 }}
              placeholder="请选择 ECL 方案"
              value={selectedSchemeId || undefined}
              onChange={(v) => { setSelectedSchemeId(v);  }}
              options={schemes.map((s) => ({ label: `${s.schemeName}(${s.schemeCode})`, value: s.schemeId }))}
            />
          </Empty>
        </Panel>
      </div>
    );
  }

  return (
    <div className="ecl-page">
      <PageHeader
        title="管理层叠加配置"
        subtitle="定义管理层叠加规则，按风险分组对 PD/LGD/CCF/EAD 进行调整"
        extra={
          <Space>
            <Select
              style={{ width: 280 }}
              placeholder="请选择 ECL 方案"
              value={selectedSchemeId || undefined}
              onChange={(v) => { setSelectedSchemeId(v);  }}
              options={schemes.map((s) => ({ label: `${s.schemeName}(${s.schemeCode})`, value: s.schemeId }))}
            />
          </Space>
        }
      />

      <Panel
        extra={
          <Space>
            <Button icon={<ExperimentOutlined />} onClick={handleOpenTest}>命中测试</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => handleOpenModal()}>新增规则</Button>
          </Space>
        }
      >
        <table className="ecl-table">
          <thead>
            <tr>
              <th>规则 ID</th>
              <th>调整类型</th>
              <th>调整方式</th>
              <th>调整值</th>
              <th>优先级</th>
              <th>生效日期</th>
              <th>失效日期</th>
              <th>条件</th>
              <th style={{ width: 120 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {rules.map((r) => (
              <tr key={r.overlayId}>
                <td>{r.overlayId}</td>
                <td><Tag color="purple">{r.overlayType}</Tag></td>
                <td><Tag color={adjustmentTypeColor[r.adjustmentType] || 'default'}>{r.adjustmentType}</Tag></td>
                <td>
                  {r.adjustmentValue != null ? (
                    <span>
                      {(() => {
                        if (r.adjustmentType === 'PERCENTAGE') {
                          const pct = (r.adjustmentValue - 1) * 100;
                          return pct >= 0 ? (
                            <span style={{ color: '#cf1322' }}>up {pct.toFixed(0)}%</span>
                          ) : (
                            <span style={{ color: '#389e0d' }}>down {Math.abs(pct).toFixed(0)}%</span>
                          );
                        }
                        return r.adjustmentValue;
                      })()}
                      <span style={{ fontSize: 11, color: '#999', marginLeft: 4 }}>
                        {r.adjustmentType === 'ADDBP' ? 'BP' : r.adjustmentType === 'FIXED' ? '元' : ''}
                      </span>
                    </span>
                  ) : '-'}
                </td>
                <td>{r.priority}</td>
                <td>{r.effectiveDate ? r.effectiveDate.slice(0, 10) : '-'}</td>
                <td>{r.expiryDate ? r.expiryDate.slice(0, 10) : '-'}</td>
                <td>
                  {r.conditions && r.conditions !== '{}' ? (
                    <Tag color="cyan">有条件</Tag>
                  ) : (
                    <Tag>无条件</Tag>
                  )}
                </td>
                <td>
                  <Space>
                    <Button type="link" size="small" icon={<EditOutlined />}
                      onClick={() => handleOpenModal(r)} />
                    <Button type="link" size="small" danger icon={<DeleteOutlined />}
                      onClick={() => handleDelete(r.overlayId!)} />
                  </Space>
                </td>
              </tr>
            ))}
            {rules.length === 0 && (
              <tr><td colSpan={9}><div className="ecl-empty-row">暂无数据</div></td></tr>
            )}
          </tbody>
        </table>
      </Panel>

      {/* ─── 新增/编辑规则弹窗 ─── */}
      <Modal
        title={editingRule ? '编辑叠加规则' : '新增叠加规则'}
        open={modalOpen}
        onOk={async () => {
          try {
            const values = await form.validateFields();
            const condRows: ConditionRow[] = values._conditions || [];
            const finalValues = {
              ...values,
              conditions: serializeConditions(condRows),
            };
            delete finalValues._conditions;
            if (editingRule) {
              await overlayApi.update(editingRule.overlayId!, { ...finalValues, schemeId: selectedSchemeId, groupId: undefined });
            } else {
              await overlayApi.create({ ...finalValues, schemeId: selectedSchemeId, groupId: undefined });
            }
            message.success(editingRule ? '规则更新成功' : '规则创建成功');
            setModalOpen(false);
            setEditingRule(null);
            form.resetFields();
            loadRules();
          } catch (err: any) {
            console.error('保存失败:', err);
            message.error('保存失败：' + (err?.errorFields?.map((f: any) => f.errors[0]).join('；') || err?.message || '未知错误'));
          }
        }}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        width={720}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onValuesChange={handleFormValuesChange}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="overlayType" label="调整类型" rules={[{ required: true, message: '请选择调整类型' }]}>
                <Select options={[
                  { label: 'PD — 违约概率', value: 'PD' },
                  { label: 'LGD — 违约损失率', value: 'LGD' },
                  { label: 'CCF — 信用转换系数', value: 'CCF' },
                  { label: 'EAD — 违约风险暴露', value: 'EAD' },
                  { label: 'RISK_RATE — 风险率', value: 'RISK_RATE' },
                ]} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="adjustmentType" label="调整方式" rules={[{ required: true, message: '请选择调整方式' }]}>
                <Select options={[
                  { label: 'ADDBP — 加点(基点)', value: 'ADDBP' },
                  { label: 'PERCENTAGE — 百分比', value: 'PERCENTAGE' },
                  { label: 'FIXED — 固定值', value: 'FIXED' },
                ]} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="adjustmentValue" label={'调整值' + (selectedAdjustmentType === 'ADDBP' ? ' (BP)' : selectedAdjustmentType === 'PERCENTAGE' ? ' (乘数)' : selectedAdjustmentType === 'FIXED' ? ' (元)' : '')} rules={[{ required: true, message: '请输入调整值' }]}>
                <InputNumber style={{ width: '100%' }} placeholder={selectedAdjustmentType === 'ADDBP' ? '如：100' : selectedAdjustmentType === 'PERCENTAGE' ? '如：0.8 (下调) / 1.2 (上调)' : '如：500000'} addonAfter={selectedAdjustmentType === 'ADDBP' ? 'BP' : selectedAdjustmentType === 'PERCENTAGE' ? '×' : selectedAdjustmentType === 'FIXED' ? '元' : ''} />
              </Form.Item>
              <div style={{ fontSize: 12, color: '#888', marginTop: -4, marginBottom: 12, lineHeight: 1.5 }}>
                {selectedAdjustmentType === 'ADDBP' && '计入等效比例 = 值 ÷ 10000，如 100BP = 1 个百分点'}
                {selectedAdjustmentType === 'PERCENTAGE' && '乘数格式：小于1 下调，大于1 上调。如 0.8 = down 20%，1.2 = up 20%'}
                {selectedAdjustmentType === 'FIXED' && '固定金额（元），等效比例 = 值 ÷ EAD'}
              </div>
            </Col>
            <Col span={12}>
              <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请输入优先级' }]}>
                <InputNumber min={1} style={{ width: '100%' }} placeholder="数值越小优先级越高" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="effectiveDate" label="生效日期">
                <Input placeholder="YYYY-MM-DD" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="expiryDate" label="失效日期">
                <Input placeholder="YYYY-MM-DD" />
              </Form.Item>
            </Col>
          </Row>

          {/* ─── 条件配置（单元格可编辑） ─── */}
          <Form.Item
            name="_conditions"
            label="匹配条件"
            extra="点击下方表格直接填写，生成 JSON 条件。多个条件为 AND 关系。"
            style={{ marginBottom: 8 }}
          >
            <ConditionBuilder
              value={form.getFieldValue('_conditions') || []}
              onChange={(rows) => form.setFieldValue('_conditions', rows)}
              productTypeOptions={dictProductType}
              industryOptions={dictIndustry}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* ─── 命中测试弹窗 ─── */}
      <Modal
        title="命中测试"
        open={testModalOpen}
        onCancel={() => { setTestModalOpen(false); setTestResult(null); }}
        footer={null}
        width={640}
      >
        <div style={{ marginBottom: 16 }}>
          <Typography.Text strong>输入测试数据：</Typography.Text>
          <Typography.Text type="secondary" style={{ marginLeft: 8 }}>字段名 = 值，可动态添加多行</Typography.Text>
        </div>
        {testFields.map((field, index) => (
          <Space key={index} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
            <Select
              style={{ width: 220 }}
              placeholder="选择字段"
              value={field.key || undefined}
              onChange={(v) => { const nf = [...testFields]; nf[index].key = v; setTestFields(nf); }}
              options={TEST_FIELD_OPTIONS}
              allowClear
              showSearch
            />
            <span>=</span>
            <Input style={{ width: 180 }} placeholder="值" value={field.value}
              onChange={(e) => { const nf = [...testFields]; nf[index].value = e.target.value; setTestFields(nf); }} />
            <Button type="text" danger icon={<MinusCircleOutlined />}
              onClick={() => setTestFields(testFields.filter((_, i) => i !== index))} />
          </Space>
        ))}
        <div style={{ marginBottom: 16 }}>
          <Button onClick={() => setTestFields([...testFields, { key: 'industryCode', value: '' }])}>+ 添加字段</Button>
        </div>
        <Button type="primary" loading={testLoading} onClick={handleTestMatch} icon={<ExperimentOutlined />}>测试匹配</Button>

        <Divider />

        {testResult !== null && (
          <>
            {testResult.hasMatch ? (
              <div className="test-result-panel">
                <Typography.Text strong>选中规则：</Typography.Text>
                <Typography.Text>
                  {testResult.selectedRule?.overlayType} - 调整值: {testResult.selectedRule?.adjustmentValue}
                  {testResult.selectedRule?.adjustmentType && ` (${testResult.selectedRule.adjustmentType})`}
                </Typography.Text>
                <br />
                <Typography.Text strong>等效比例：</Typography.Text>
                <Typography.Text style={{ color: '#1890ff', fontSize: 16, fontWeight: 'bold' }}>
                  {(testResult.effectiveRatio * 100).toFixed(2)}%
                </Typography.Text>
                <Divider />
                <Typography.Text strong>全部命中规则：</Typography.Text>
                <div style={{ marginTop: 8 }}>
                  {testResult.matchedRules?.map((rule, idx) => (
                    <Tag key={rule.overlayId || idx}
                      color={rule.overlayId === testResult.selectedRule?.overlayId ? 'blue' : 'default'}
                      style={{ marginBottom: 4 }}>
                      {rule.overlayType} | 调整值={rule.adjustmentValue} | 优先级={rule.priority}
                    </Tag>
                  ))}
                </div>
              </div>
            ) : (
              <Typography.Text type="secondary">无匹配规则</Typography.Text>
            )}
          </>
        )}
      </Modal>
    </div>
  );
};

export default OverlayConfig;
