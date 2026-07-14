import React, { useState, useEffect, useCallback } from 'react';
import {
  Select,
  Button,
  Table,
  Space,
  Modal,
  Form,
  Input,
  InputNumber,
  message,
  Tabs,
  Typography,
  Empty,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useSearchParams, useOutletContext } from 'react-router-dom';
import { schemeApi, type SchemeVO } from '../../api/scheme';
import { riskGroupApi, type RiskGroupVO } from '../../api/riskGroup';
import { lgdApi, type LgdCurveVO, type LgdCollateralDiscountVO, type LgdDepreciationVO } from '../../api/lgd';
import { dictApi, type DictEntryVO } from '../../api/dict';
import { PageHeader, Panel, GroupSelector } from '../../components';

/* ===================================================================
   Tab1: 基准曲线
   =================================================================== */
const BenchmarkCurveTab: React.FC<{
  selectedSchemeId: string;
}> = ({ selectedSchemeId }) => {
  const [groups, setGroups] = useState<RiskGroupVO[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<string>('');
  const [curves, setCurves] = useState<LgdCurveVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<LgdCurveVO | null>(null);
  const [form] = Form.useForm();
  const [dictCollateral, setDictCollateral] = useState<DictEntryVO[]>([]);
  const [dictProductType, setDictProductType] = useState<DictEntryVO[]>([]);
  const [dictCollateralCategory, setDictCollateralCategory] = useState<DictEntryVO[]>([]);

  useEffect(() => {
    if (!selectedSchemeId) {
      setGroups([]);
      setSelectedGroupId('');
      return;
    }
    riskGroupApi.listByScheme(selectedSchemeId).then((res) => {
      setGroups((res.data as any)?.data || res.data || []);
    });
    loadDictOptions(selectedSchemeId);
    setSelectedGroupId('');
  }, [selectedSchemeId]);

  const loadDictOptions = useCallback(async (schemeId: string) => {
    try {
      const [colRes, prodRes, catRes] = await Promise.all([
        dictApi.getEffectiveEntries(schemeId, 'COLLATERAL_TYPE'),
        dictApi.getEffectiveEntries(schemeId, 'PRODUCT_TYPE'),
        dictApi.getEffectiveEntries(schemeId, 'COLLATERAL_CATEGORY'),
      ]);
      setDictCollateral((colRes.data as any)?.data || colRes.data || []);
      setDictProductType((prodRes.data as any)?.data || prodRes.data || []);
      setDictCollateralCategory((catRes.data as any)?.data || catRes.data || []);
    } catch (err) {
      console.error('加载字典选项失败', err);
    }
  }, []);

  // 加载字典
  useEffect(() => {
    if (selectedSchemeId) {
      loadDictOptions(selectedSchemeId);
    } else {
      setDictCollateral([]);
      setDictProductType([]);
      setDictCollateralCategory([]);
    }
  }, [selectedSchemeId, loadDictOptions]);

  const load = useCallback(async () => {
    if (!selectedSchemeId || !selectedGroupId) {
      setCurves([]);
      return;
    }
    setLoading(true);
    try {
      const res = await lgdApi.listCurves(selectedSchemeId, selectedGroupId);
      setCurves((res.data as any)?.data || res.data || []);
    } finally {
      setLoading(false);
    }
  }, [selectedSchemeId, selectedGroupId]);

  useEffect(() => {
    load();
  }, [load]);

  const handleSave = async () => {
    const values = await form.validateFields();
    if (!selectedGroupId) {
      message.warning('请先选择风险分组');
      return;
    }
    const nextCurves = editing
      ? curves.map((curve) =>
          curve.curveId === editing.curveId
            ? { collateralType: values.collateralType, productType: values.productType || '', lgdBaseValue: +(values.lgdBaseValue / 100).toFixed(6) }
            : { collateralType: curve.collateralType, productType: curve.productType, lgdBaseValue: curve.lgdBaseValue }
        )
      : [...curves, { ...values, lgdBaseValue: +(values.lgdBaseValue / 100).toFixed(6), schemeId: selectedSchemeId, groupId: selectedGroupId, curveId: `tmp_${Date.now()}` }];
    await lgdApi.batchSaveCurves(
      selectedSchemeId,
      selectedGroupId,
      nextCurves.map((curve) => ({
        collateralType: curve.collateralType,
        productType: curve.productType,
        lgdBaseValue: curve.lgdBaseValue,
      })),
    );
    if (editing) {
      message.success('更新成功');
    } else {
      message.success('创建成功');
    }
    setModalOpen(false);
    setEditing(null);
    form.resetFields();
    load();
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这条基准曲线数据吗？',
      onOk: async () => {
        if (!selectedGroupId) {
          message.warning('请先选择风险分组');
          return;
        }
        const nextCurves = curves.filter((curve) => curve.curveId !== id);
        await lgdApi.batchSaveCurves(
          selectedSchemeId,
          selectedGroupId,
          nextCurves.map((curve) => ({
            collateralType: curve.collateralType,
            productType: curve.productType,
            lgdBaseValue: curve.lgdBaseValue,
          })),
        );
        message.success('已删除');
        load();
      },
    });
  };

  const handleBatchSave = async () => {
    if (!selectedGroupId) {
      message.warning('请先选择风险分组');
      return;
    }
    try {
      const payload = curves.map((c) => ({
        collateralType: c.collateralType,
        productType: c.productType,
        lgdBaseValue: c.lgdBaseValue,
      }));
      await lgdApi.batchSaveCurves(selectedSchemeId, selectedGroupId, payload);
      message.success('批量保存成功');
      load();
    } catch {
      message.error('批量保存失败');
    }
  };

  const columns = [
    { title: '担保类型', dataIndex: 'collateralType', key: 'collateralType', width: 160 },
    { title: '产品类型', dataIndex: 'productType', key: 'productType', width: 160 },
    {
      title: 'LGD 基准值',
      dataIndex: 'lgdBaseValue',
      key: 'lgdBaseValue',
      width: 160,
      render: (v: number) => (v != null ? (v * 100).toFixed(4) + '%' : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: any, record: LgdCurveVO) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => {
              setEditing(record);
              form.setFieldsValue(record);
              setModalOpen(true);
            }}
          />
          <Button
            type="link"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.curveId!)}
          />
        </Space>
      ),
    },
  ];
  const groupSelectorItems = groups.map((g) => ({
    groupId: g.groupId,
    groupName: g.groupName,
    groupCode: g.groupCode,
  }));

  return (
    <>
      {groups.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <GroupSelector
            groups={groupSelectorItems}
            selectedId={selectedGroupId || undefined}
            onChange={setSelectedGroupId}
          />
        </div>
      )}
      {!selectedGroupId ? (
        <div className="ecl-empty-row">请选择一个风险分组以维护 LGD 基准曲线</div>
      ) : (
        <>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8, gap: 8 }}>
        <Button icon={<PlusOutlined />} type="primary" onClick={() => { setEditing(null); form.resetFields(); setModalOpen(true); }}>
          新增
        </Button>
        <Button onClick={handleBatchSave}>批量保存</Button>
      </div>
      <table className="ecl-table">
        <thead>
          <tr>
            <th>担保类型</th>
            <th>产品类型<br/><span style={{fontSize:10,fontWeight:400,color:'#999'}}>空=全集</span></th>
            <th>LGD 基准值</th>
            <th style={{ width: 120 }}>操作</th>
          </tr>
        </thead>
        <tbody>
          {curves.map((c) => (
            <tr key={c.curveId}>
              <td>{c.collateralType}</td>
              <td>{c.productType || <span style={{ color: 'var(--color-text-muted)' }}>全集 *</span>}</td>
              <td>{(c.lgdBaseValue * 100).toFixed(4)}%</td>
              <td>
                <Space>
                  <Button type="link" size="small" icon={<EditOutlined />}
                    onClick={() => { setEditing(c); form.setFieldsValue({ ...c, lgdBaseValue: c.lgdBaseValue != null ? +(c.lgdBaseValue * 100).toFixed(4) : 0 }); setModalOpen(true); }} />
                  <Button type="link" size="small" danger icon={<DeleteOutlined />}
                    onClick={() => handleDelete(c.curveId!)} />
                </Space>
              </td>
            </tr>
          ))}
          {curves.length === 0 && (
            <tr><td colSpan={4}><div className="ecl-empty-row">暂无数据</div></td></tr>
          )}
        </tbody>
      </table>
        </>
      )}

      <Modal
        title={editing ? '编辑基准曲线' : '新增基准曲线'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="collateralType" label="担保类型" rules={[{ required: true, message: '请选择担保类型' }]}>
            <Select placeholder="请选择担保类型" allowClear showSearch
              options={dictCollateral.map(e => ({ label: `${e.entryName} (${e.entryCode})`, value: e.entryCode }))} />
          </Form.Item>
          <Form.Item name="productType" label="产品类型">
            <Select placeholder="为空=全集（不限制）" allowClear showSearch
              options={dictProductType.map(e => ({ label: `${e.entryName} (${e.entryCode})`, value: e.entryCode }))} />
          </Form.Item>
          <Form.Item name="lgdBaseValue" label="LGD 基准值 (%)" rules={[
            { required: true, message: '请输入 LGD 基准值' },
            { type: 'number', min: 0, max: 100, message: '范围 0 ~ 100' },
          ]}>
            <InputNumber min={0} max={100} step={0.01} style={{ width: '100%' }} placeholder="如：45" addonAfter="%" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

/* ===================================================================
   Tab2: 押品折扣率（不依赖分组，按方案隔离）
   =================================================================== */
const CollateralDiscountTab: React.FC<{
  selectedSchemeId: string;
}> = ({ selectedSchemeId }) => {
  const [list, setList] = useState<LgdCollateralDiscountVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<LgdCollateralDiscountVO | null>(null);
  const [form] = Form.useForm();
  const [dictCollateral, setDictCollateral] = useState<DictEntryVO[]>([]);
  const [dictProductType, setDictProductType] = useState<DictEntryVO[]>([]);
  const [dictCollateralCategory, setDictCollateralCategory] = useState<DictEntryVO[]>([]);

  const loadDictOptions = useCallback(async (schemeId: string) => {
    try {
      const [colRes, prodRes, catRes] = await Promise.all([
        dictApi.getEffectiveEntries(schemeId, 'COLLATERAL_TYPE'),
        dictApi.getEffectiveEntries(schemeId, 'PRODUCT_TYPE'),
        dictApi.getEffectiveEntries(schemeId, 'COLLATERAL_CATEGORY'),
      ]);
      setDictCollateral((colRes.data as any)?.data || colRes.data || []);
      setDictProductType((prodRes.data as any)?.data || prodRes.data || []);
      setDictCollateralCategory((catRes.data as any)?.data || catRes.data || []);
    } catch (err) {
      console.error('加载字典选项失败', err);
    }
  }, []);

  // 加载字典
  useEffect(() => {
    if (selectedSchemeId) {
      loadDictOptions(selectedSchemeId);
    } else {
      setDictCollateral([]);
      setDictProductType([]);
      setDictCollateralCategory([]);
    }
  }, [selectedSchemeId, loadDictOptions]);

  const load = useCallback(async () => {
    if (!selectedSchemeId) return;
    setLoading(true);
    try {
      const res = await lgdApi.listDiscounts(selectedSchemeId);
      setList((res.data as any)?.data || res.data || []);
    } finally {
      setLoading(false);
    }
  }, [selectedSchemeId]);

  useEffect(() => { load(); }, [load]);

  const handleSave = async () => {
    const values = await form.validateFields();

    const nextList = editing
      ? list.map((item) =>
          item.discountId === editing.discountId
            ? { collateralCategory: values.collateralCategory, collateralType: values.collateralType, discountRate: +(values.discountRate / 100).toFixed(4) }
            : { collateralCategory: item.collateralCategory, collateralType: item.collateralType, discountRate: item.discountRate }
        )
      : [...list, { collateralCategory: values.collateralCategory, collateralType: values.collateralType, discountRate: +(values.discountRate / 100).toFixed(4), schemeId: selectedSchemeId, discountId: `tmp_${Date.now()}` }];
    await lgdApi.batchSaveDiscounts(
      selectedSchemeId,
      nextList.map((item) => ({
        collateralCategory: item.collateralCategory,
        collateralType: item.collateralType,
        discountRate: item.discountRate,
      })),
    );
    if (editing) {
      message.success('更新成功');
    } else {
      message.success('创建成功');
    }
    setModalOpen(false);
    setEditing(null);
    form.resetFields();
    load();
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这条押品折扣率吗？',
      onOk: async () => {
        const nextList = list.filter((item) => item.discountId !== id);
        await lgdApi.batchSaveDiscounts(
          selectedSchemeId,
          nextList.map((item) => ({
            collateralCategory: item.collateralCategory,
            collateralType: item.collateralType,
            discountRate: item.discountRate,
          })),
        );
        message.success('已删除');
        load();
      },
    });
  };

  const handleBatchSave = async () => {
    try {
      const payload = list.map((d) => ({
        collateralCategory: d.collateralCategory,
        collateralType: d.collateralType,
        discountRate: d.discountRate,
      }));
      await lgdApi.batchSaveDiscounts(selectedSchemeId, payload);
      message.success('批量保存成功');
      load();
    } catch { message.error('批量保存失败'); }
  };

  const columns = [
    { title: '押品大类', dataIndex: 'collateralCategory', key: 'collateralCategory', width: 200 },
    { title: '押品类型', dataIndex: 'collateralType', key: 'collateralType', width: 200 },
    {
      title: '折扣率',
      dataIndex: 'discountRate',
      key: 'discountRate',
      width: 200,
      render: (v: number) => (v != null ? (v * 100).toFixed(2) + '%' : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: any, record: LgdCollateralDiscountVO) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => { setEditing(record); form.setFieldsValue({ ...record, lgdBaseValue: record.lgdBaseValue != null ? +(record.lgdBaseValue * 100).toFixed(4) : 0 }); setModalOpen(true); }} />
          <Button type="link" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.discountId!)} />
        </Space>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8, gap: 8 }}>
        <Button icon={<PlusOutlined />} type="primary" onClick={() => { setEditing(null); form.resetFields(); setModalOpen(true); }}>新增</Button>
        <Button onClick={handleBatchSave}>批量保存</Button>
      </div>
      <table className="ecl-table">
        <thead>
          <tr>
            <th>押品大类</th>
            <th>押品类型</th>
            <th>折扣率</th>
            <th style={{ width: 120 }}>操作</th>
          </tr>
        </thead>
        <tbody>
          {list.map((d) => (
            <tr key={d.discountId}>
              <td>{d.collateralCategory}</td>
              <td>{d.collateralType}</td>
              <td>{(d.discountRate * 100).toFixed(2)}%</td>
              <td>
                <Space>
                  <Button type="link" size="small" icon={<EditOutlined />}
                    onClick={() => { setEditing(d); form.setFieldsValue({ ...d, discountRate: d.discountRate != null ? +(d.discountRate * 100).toFixed(2) : 0 }); setModalOpen(true); }} />
                  <Button type="link" size="small" danger icon={<DeleteOutlined />}
                    onClick={() => handleDelete(d.discountId!)} />
                </Space>
              </td>
            </tr>
          ))}
          {list.length === 0 && (
            <tr><td colSpan={4}><div className="ecl-empty-row">暂无数据</div></td></tr>
          )}
        </tbody>
      </table>

      <Modal
        title={editing ? '编辑押品折扣率' : '新增押品折扣率'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="collateralCategory" label="押品大类" rules={[{ required: true, message: '请选择押品大类' }]}>
            <Select placeholder="请选择押品大类" allowClear showSearch
              options={dictCollateralCategory.map(e => ({ label: `${e.entryName} (${e.entryCode})`, value: e.entryCode }))} />
          </Form.Item>
          <Form.Item name="collateralType" label="押品类型" rules={[{ required: true, message: '请选择押品类型' }]}>
            <Select placeholder="请选择押品类型" allowClear showSearch
              options={dictCollateral.map(e => ({ label: `${e.entryName} (${e.entryCode})`, value: e.entryCode }))} />
          </Form.Item>
          <Form.Item
            name="discountRate"
            label="折扣率 (%)"
            rules={[
              { required: true, message: '请输入折扣率' },
              { type: 'number', min: 0, max: 100, message: '折扣率范围 0 ~ 100' },
            ]}
          >
            <InputNumber min={0} max={100} step={0.1} style={{ width: '100%' }} placeholder="如：70" addonAfter="%" />
          </Form.Item>
          <Typography.Text type="secondary">校验规则：折扣率 0% ~ 100%</Typography.Text>
        </Form>
      </Modal>
    </>
  );
};

/* ===================================================================
   Tab3: 押品折旧率（按 schemeId + collateralType 分组）
   =================================================================== */
const DEFAULT_DEPRECIATION_YEARS = [0, 1, 2, 3, 4, 5, 6, 7, 8];

type DepreciationMatrixRow = {
  rowId: string;
  collateralType: string;
  originalCollateralType?: string;
  rates: Record<number, number>;
};

const createEmptyRates = (years: number[]) =>
  years.reduce<Record<number, number>>((acc, year) => {
    acc[year] = 0;
    return acc;
  }, {});

const DepreciationTab: React.FC<{
  selectedSchemeId: string;
}> = ({ selectedSchemeId }) => {
  const [years, setYears] = useState<number[]>(DEFAULT_DEPRECIATION_YEARS);
  const [rows, setRows] = useState<DepreciationMatrixRow[]>([]);
  const [deletedTypes, setDeletedTypes] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  const loadData = useCallback(async () => {
    if (!selectedSchemeId) {
      setRows([]);
      setDeletedTypes([]);
      setYears(DEFAULT_DEPRECIATION_YEARS);
      return;
    }
    setLoading(true);
    try {
      const res = await lgdApi.listDepreciations(selectedSchemeId);
      const data: LgdDepreciationVO[] = (res.data as any)?.data || res.data || [];
      const rowMap = new Map<string, DepreciationMatrixRow>();
      const persistedYears = [...new Set(data.map((item) => item.yearOffset))]
        .filter((year) => Number.isInteger(year) && year >= 0)
        .sort((a, b) => a - b);
      const nextYears = persistedYears.length > 0 ? persistedYears : DEFAULT_DEPRECIATION_YEARS;
      data.forEach((item) => {
        if (!item.collateralType) return;
        if (!rowMap.has(item.collateralType)) {
          rowMap.set(item.collateralType, {
            rowId: item.collateralType,
            collateralType: item.collateralType,
            originalCollateralType: item.collateralType,
            rates: createEmptyRates(nextYears),
          });
        }
        const row = rowMap.get(item.collateralType)!;
        if (nextYears.includes(item.yearOffset)) {
          row.rates[item.yearOffset] = Number(item.depreciationRate ?? 0);
        }
      });
      setYears(nextYears);
      setRows(Array.from(rowMap.values()));
      setDeletedTypes([]);
    } finally {
      setLoading(false);
    }
  }, [selectedSchemeId]);

  useEffect(() => { loadData(); }, [loadData]);

  const handleAddRow = () => {
    setRows((prev) => [
      ...prev,
      {
        rowId: `tmp_${Date.now()}`,
        collateralType: '',
        rates: createEmptyRates(years),
      },
    ]);
  };

  const handleAddYear = () => {
    const nextYear = (years.length > 0 ? Math.max(...years) : -1) + 1;
    setYears((prev) => [...prev, nextYear]);
    setRows((prev) =>
      prev.map((row) => ({
        ...row,
        rates: { ...row.rates, [nextYear]: 0 },
      })),
    );
  };

  const handleRemoveYear = () => {
    if (years.length <= 1) {
      message.warning('至少保留一个年份');
      return;
    }
    const removedYear = years[years.length - 1];
    setYears((prev) => prev.slice(0, -1));
    setRows((prev) =>
      prev.map((row) => {
        const { [removedYear]: _removed, ...nextRates } = row.rates;
        return { ...row, rates: nextRates };
      }),
    );
  };

  const handleYearChange = (index: number, value: number | null) => {
    if (value == null) return;
    const nextYear = Number(value);
    if (!Number.isInteger(nextYear) || nextYear < 0) {
      message.error('年份必须是非负整数');
      return;
    }
    if (years.some((year, yearIndex) => yearIndex !== index && year === nextYear)) {
      message.error('年份不能重复');
      return;
    }
    const previousYear = years[index];
    if (previousYear === nextYear) return;
    setYears((prev) => prev.map((year, yearIndex) => (yearIndex === index ? nextYear : year)));
    setRows((prev) =>
      prev.map((row) => {
        const previousRate = row.rates[previousYear] ?? 0;
        const { [previousYear]: _removed, ...remainingRates } = row.rates;
        return {
          ...row,
          rates: {
            ...remainingRates,
            [nextYear]: previousRate,
          },
        };
      }),
    );
  };

  const handleDeleteRow = (row: DepreciationMatrixRow) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该押品类型的折旧率配置吗？',
      onOk: () => {
        const persistedType = row.originalCollateralType || row.collateralType.trim();
        if (persistedType) {
          setDeletedTypes((prev) => (prev.includes(persistedType) ? prev : [...prev, persistedType]));
        }
        setRows((prev) => prev.filter((item) => item.rowId !== row.rowId));
      },
    });
  };

  const handleCollateralTypeChange = (rowId: string, value: string) => {
    setRows((prev) =>
      prev.map((row) => {
        if (row.rowId !== rowId) return row;
        const originalType = row.originalCollateralType?.trim();
        const nextType = value.trim();
        if (originalType && originalType !== nextType) {
          setDeletedTypes((deleted) => (deleted.includes(originalType) ? deleted : [...deleted, originalType]));
        }
        return { ...row, collateralType: value };
      }),
    );
  };

  const handleRateChange = (rowId: string, year: number, percent: number | null) => {
    const nextPercent = Number(percent ?? 0);
    setRows((prev) =>
      prev.map((row) =>
        row.rowId === rowId
          ? { ...row, rates: { ...row.rates, [year]: nextPercent / 100 } }
          : row,
      ),
    );
  };

  const validateRows = () => {
    const types = rows.map((row) => row.collateralType.trim());
    if (types.some((type) => !type)) {
      message.error('押品类型不能为空');
      return null;
    }
    if (new Set(types).size !== types.length) {
      message.error('押品类型不能重复');
      return null;
    }
    if (new Set(years).size !== years.length) {
      message.error('年份不能重复');
      return null;
    }
    for (const row of rows) {
      for (const year of years) {
        const rate = row.rates[year] ?? 0;
        if (rate < -1 || rate > 1) {
          message.error('折旧率范围必须在 -100% ~ 100% 之间');
          return null;
        }
      }
    }
    return types;
  };

  const handleBatchSave = async () => {
    const validTypes = validateRows();
    if (!validTypes) return;
    setLoading(true);
    try {
      const currentTypes = new Set(validTypes);
      for (const row of rows) {
        await lgdApi.batchSaveDepreciations(
          selectedSchemeId,
          row.collateralType.trim(),
          years.map((year) => ({
            yearOffset: year,
            depreciationRate: row.rates[year] ?? 0,
          })),
        );
      }
      for (const type of deletedTypes.filter((item) => !currentTypes.has(item))) {
        await lgdApi.batchSaveDepreciations(selectedSchemeId, type, []);
      }
      message.success('批量保存成功');
      loadData();
    } catch {
      message.error('批量保存失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8, gap: 8 }}>
        <Button onClick={handleAddYear}>新增年份</Button>
        <Button onClick={handleRemoveYear} disabled={years.length <= 1}>删除末年</Button>
        <Button icon={<PlusOutlined />} type="primary" onClick={handleAddRow}>新增押品类型</Button>
        <Button loading={loading} onClick={handleBatchSave}>批量保存</Button>
      </div>
      {rows.length === 0 ? (
        <div style={{ marginTop: 16 }}>
          <Empty description="暂无押品折旧率数据" />
        </div>
      ) : (
        <table className="ecl-table">
          <thead>
            <tr>
              <th style={{ minWidth: 180 }}>押品类型</th>
              {years.map((year, index) => (
                <th key={`${index}-${year}`} style={{ width: 100 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <InputNumber
                      controls={false}
                      precision={0}
                      min={0}
                      size="small"
                      value={year}
                      onChange={(value) => handleYearChange(index, value == null ? null : Number(value))}
                      style={{ width: 54 }}
                    />
                    <span>年</span>
                    <span style={{ color: '#9ca3af' }}>%</span>
                  </div>
                </th>
              ))}
              <th style={{ width: 80 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.rowId}>
                <td>
                  <Input
                    value={row.collateralType}
                    placeholder="请输入押品类型"
                    onChange={(event) => handleCollateralTypeChange(row.rowId, event.target.value)}
                  />
                </td>
                {years.map((year) => (
                  <td key={year}>
                    <InputNumber
                      controls={false}
                      precision={2}
                      min={-100}
                      max={100}
                      size="small"
                      value={Number(((row.rates[year] ?? 0) * 100).toFixed(2))}
                      onChange={(value) => handleRateChange(row.rowId, year, Number(value ?? 0))}
                      style={{ width: 72 }}
                    />
                  </td>
                ))}
                <td>
                  <Button
                    type="link"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => handleDeleteRow(row)}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
};

/* ===================================================================
   LgdConfig 主页面
   =================================================================== */
const LgdConfig: React.FC = () => {
  const [searchParams] = useSearchParams();
  const schemeIdFromUrl = searchParams.get('schemeId') || '';
  const { schemeContext } = useOutletContext<{ schemeContext?: { schemeId: string } }>();
  const effectiveSchemeId = schemeIdFromUrl || schemeContext?.schemeId || '';

  // ─── 方案 ───
  const [schemes, setSchemes] = useState<SchemeVO[]>([]);
  const [selectedSchemeId, setSelectedSchemeId] = useState<string>(effectiveSchemeId);

  // 加载方案列表
  useEffect(() => {
    schemeApi.list().then((res) => {
      setSchemes((res.data as any)?.data || res.data || []);
    });
  }, []);

  if (!selectedSchemeId) {
    return (
      <div className="ecl-page">
        <PageHeader
          title="LGD 参数配置"
          subtitle="管理违约损失率基准曲线、押品折扣率和折旧率"
        />
        <Panel>
          <Empty description="请先选择一个 ECL 方案">
            <Select
              style={{ width: 300, marginTop: 16 }}
              placeholder="请选择 ECL 方案"
              value={selectedSchemeId || undefined}
              onChange={setSelectedSchemeId}
              options={schemes.map((s) => ({ label: `${s.schemeName}(${s.schemeCode})`, value: s.schemeId }))}
            />
          </Empty>
        </Panel>
      </div>
    );
  }

  const tabItems = [
    {
      key: 'benchmark',
      label: '基准曲线',
      children: <BenchmarkCurveTab selectedSchemeId={selectedSchemeId} />,
    },
    {
      key: 'discount',
      label: '押品折扣率',
      children: <CollateralDiscountTab selectedSchemeId={selectedSchemeId} />,
    },
    {
      key: 'depreciation',
      label: '押品折旧率',
      children: <DepreciationTab selectedSchemeId={selectedSchemeId} />,
    },
  ];
  return (
    <div className="ecl-page">
      <PageHeader
        title="LGD 参数配置"
        subtitle="管理违约损失率基准曲线、押品折扣率和折旧率"
        extra={
          <Space>
            <Select
              style={{ width: 280 }}
              placeholder="请选择 ECL 方案"
              value={selectedSchemeId || undefined}
              onChange={setSelectedSchemeId}
              options={schemes.map((s) => ({ label: `${s.schemeName}(${s.schemeCode})`, value: s.schemeId }))}
            />
          </Space>
        }
      />

      <Panel>
        <Tabs items={tabItems} />
      </Panel>
    </div>
  );
};

export default LgdConfig;
