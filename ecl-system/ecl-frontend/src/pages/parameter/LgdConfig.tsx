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
import { PageHeader, Panel, GroupSelector } from '../../components';

/* ===================================================================
   Tab1: 基准曲线
   =================================================================== */
const BenchmarkCurveTab: React.FC<{
  selectedSchemeId: string;
  selectedGroupId: string;
}> = ({ selectedSchemeId, selectedGroupId }) => {
  const [curves, setCurves] = useState<LgdCurveVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<LgdCurveVO | null>(null);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    if (!selectedSchemeId) return;
    setLoading(true);
    try {
      const res = await lgdApi.listCurves(selectedSchemeId, selectedGroupId || undefined);
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
    if (editing) {
      await lgdApi.updateCurve(editing.curveId!, { ...values, schemeId: selectedSchemeId });
      message.success('更新成功');
    } else {
      await lgdApi.createCurve({ ...values, schemeId: selectedSchemeId, groupId: selectedGroupId || undefined });
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
        await lgdApi.deleteCurve(id);
        message.success('已删除');
        load();
      },
    });
  };

  const handleBatchSave = async () => {
    try {
      const payload = curves.map((c) => ({
        curveId: c.curveId,
        groupId: c.groupId,
        collateralType: c.collateralType,
        productType: c.productType,
        lgdBaseValue: c.lgdBaseValue,
      }));
      await lgdApi.batchSaveCurves(selectedSchemeId, payload);
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

  return (
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
            <th>产品类型</th>
            <th>LGD 基准值</th>
            <th style={{ width: 120 }}>操作</th>
          </tr>
        </thead>
        <tbody>
          {curves.map((c) => (
            <tr key={c.curveId}>
              <td>{c.collateralType}</td>
              <td>{c.productType}</td>
              <td>{(c.lgdBaseValue * 100).toFixed(4)}%</td>
              <td>
                <Space>
                  <Button type="link" size="small" icon={<EditOutlined />}
                    onClick={() => { setEditing(c); form.setFieldsValue(c); setModalOpen(true); }} />
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

      <Modal
        title={editing ? '编辑基准曲线' : '新增基准曲线'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="collateralType" label="担保类型" rules={[{ required: true, message: '请输入担保类型' }]}>
            <Input placeholder="如：MORTGAGE, PLEDGE" />
          </Form.Item>
          <Form.Item name="productType" label="产品类型" rules={[{ required: true, message: '请输入产品类型' }]}>
            <Input placeholder="如：LOAN, BOND" />
          </Form.Item>
          <Form.Item name="lgdBaseValue" label="LGD 基准值" rules={[
            { required: true, message: '请输入 LGD 基准值' },
            { type: 'number', min: 0, max: 1, message: '范围 0 ~ 1' },
          ]}>
            <InputNumber min={0} max={1} step={0.0001} style={{ width: '100%' }} placeholder="0 ~ 1 之间的小数" />
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
    // 校验 discountRate 0~1
    if (values.discountRate < 0 || values.discountRate > 1) {
      message.error('折扣率范围必须在 0 ~ 1 之间');
      return;
    }
    if (editing) {
      await lgdApi.updateDiscount(editing.discountId!, { ...values, schemeId: selectedSchemeId });
      message.success('更新成功');
    } else {
      await lgdApi.createDiscount({ ...values, schemeId: selectedSchemeId });
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
      onOk: async () => { await lgdApi.deleteDiscount(id); message.success('已删除'); load(); },
    });
  };

  const handleBatchSave = async () => {
    try {
      const payload = list.map((d) => ({ discountId: d.discountId, collateralType: d.collateralType, discountRate: d.discountRate }));
      await lgdApi.batchSaveDiscounts(selectedSchemeId, payload);
      message.success('批量保存成功');
      load();
    } catch { message.error('批量保存失败'); }
  };

  const columns = [
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
          <Button type="link" icon={<EditOutlined />} onClick={() => { setEditing(record); form.setFieldsValue(record); setModalOpen(true); }} />
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
            <th>押品类型</th>
            <th>折扣率</th>
            <th style={{ width: 120 }}>操作</th>
          </tr>
        </thead>
        <tbody>
          {list.map((d) => (
            <tr key={d.discountId}>
              <td>{d.collateralType}</td>
              <td>{(d.discountRate * 100).toFixed(2)}%</td>
              <td>
                <Space>
                  <Button type="link" size="small" icon={<EditOutlined />}
                    onClick={() => { setEditing(d); form.setFieldsValue(d); setModalOpen(true); }} />
                  <Button type="link" size="small" danger icon={<DeleteOutlined />}
                    onClick={() => handleDelete(d.discountId!)} />
                </Space>
              </td>
            </tr>
          ))}
          {list.length === 0 && (
            <tr><td colSpan={3}><div className="ecl-empty-row">暂无数据</div></td></tr>
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
          <Form.Item name="collateralType" label="押品类型" rules={[{ required: true, message: '请输入押品类型' }]}>
            <Input placeholder="如：REAL_ESTATE, EQUIPMENT" />
          </Form.Item>
          <Form.Item
            name="discountRate"
            label="折扣率"
            rules={[
              { required: true, message: '请输入折扣率' },
              { type: 'number', min: 0, max: 1, message: '折扣率范围 0 ~ 1' },
            ]}
          >
            <InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} placeholder="0 ~ 1 之间的小数" />
          </Form.Item>
          <Typography.Text type="secondary">校验规则：折扣率 0 ~ 1</Typography.Text>
        </Form>
      </Modal>
    </>
  );
};

/* ===================================================================
   Tab3: 押品折旧率（按 schemeId + collateralType 分组）
   =================================================================== */
const DepreciationTab: React.FC<{
  selectedSchemeId: string;
  selectedGroupId: string;
}> = ({ selectedSchemeId, selectedGroupId }) => {
  const [collateralTypes, setCollateralTypes] = useState<string[]>([]);
  const [selectedType, setSelectedType] = useState<string | null>(null);
  const [list, setList] = useState<LgdDepreciationVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<LgdDepreciationVO | null>(null);
  const [form] = Form.useForm();

  // 加载已存在的押品类型列表（从折扣率 API 获取可选项，或从折旧 API 获取已有类型）
  const loadTypes = useCallback(async () => {
    if (!selectedSchemeId) return;
    try {
      // 先尝试从折旧 API 获取已有的押品类型列表
      const res = await lgdApi.listDiscounts(selectedSchemeId);
      const discounts: LgdCollateralDiscountVO[] = (res.data as any)?.data || res.data || [];
      const types = [...new Set(discounts.map((d) => d.collateralType))];
      setCollateralTypes(types);
    } catch {
      // ignore
    }
  }, [selectedSchemeId]);

  useEffect(() => { loadTypes(); }, [loadTypes]);

  // 加载折旧率数据
  const loadData = useCallback(async () => {
    if (!selectedSchemeId || !selectedType) {
      setList([]);
      return;
    }
    setLoading(true);
    try {
      const res = await lgdApi.listDepreciations(selectedSchemeId, selectedType, selectedGroupId || undefined);
      setList((res.data as any)?.data || res.data || []);
    } finally {
      setLoading(false);
    }
  }, [selectedSchemeId, selectedType, selectedGroupId]);

  useEffect(() => { loadData(); }, [loadData]);

  const handleSave = async () => {
    const values = await form.validateFields();
    // 校验 depreciationRate 0~1
    if (values.depreciationRate < 0 || values.depreciationRate > 1) {
      message.error('折旧率范围必须在 0 ~ 1 之间');
      return;
    }
    if (editing) {
      await lgdApi.updateDepreciation(editing.depreciationId!, { ...values, schemeId: selectedSchemeId });
      message.success('更新成功');
    } else {
      await lgdApi.createDepreciation({
        ...values,
        schemeId: selectedSchemeId,
        collateralType: selectedType!,
        groupId: selectedGroupId || undefined,
      });
      message.success('创建成功');
    }
    setModalOpen(false);
    setEditing(null);
    form.resetFields();
    loadData();
  };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这条折旧率吗？',
      onOk: async () => { await lgdApi.deleteDepreciation(id); message.success('已删除'); loadData(); },
    });
  };

  const handleBatchSave = async () => {
    if (!selectedType) return;
    try {
      const payload = list.map((d) => ({
        depreciationId: d.depreciationId,
        yearOffset: d.yearOffset,
        depreciationRate: d.depreciationRate,
      }));
      await lgdApi.batchSaveDepreciations(selectedSchemeId, selectedType, payload);
      message.success('批量保存成功');
      loadData();
    } catch { message.error('批量保存失败'); }
  };

  const columns = [
    {
      title: '年份',
      dataIndex: 'yearOffset',
      key: 'yearOffset',
      width: 200,
      render: (v: number) => (v != null ? `第 ${v} 年` : '-'),
    },
    {
      title: '折旧率',
      dataIndex: 'depreciationRate',
      key: 'depreciationRate',
      width: 200,
      render: (v: number) => (v != null ? (v * 100).toFixed(2) + '%' : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: any, record: LgdDepreciationVO) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => { setEditing(record); form.setFieldsValue(record); setModalOpen(true); }} />
          <Button type="link" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.depreciationId!)} />
        </Space>
      ),
    },
  ];

  if (!selectedType) {
    return (
      <div style={{ padding: 16 }}>
        <Typography.Text strong style={{ marginRight: 12 }}>选择押品类型：</Typography.Text>
        <Select
          style={{ width: 300 }}
          placeholder="请选择押品类型"
          onChange={setSelectedType}
          options={collateralTypes.map((t) => ({ label: t, value: t }))}
        />
        {collateralTypes.length === 0 && (
          <div style={{ marginTop: 16 }}><Empty description="暂无押品类型数据，请先在「押品折扣率」Tab 中添加押品类型" /></div>
        )}
      </div>
    );
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12, gap: 12 }}>
        <Typography.Text strong>押品类型：</Typography.Text>
        <Select
          style={{ width: 300 }}
          value={selectedType}
          onChange={(v) => { setSelectedType(v); setEditing(null); form.resetFields(); }}
          options={collateralTypes.map((t) => ({ label: t, value: t }))}
        />
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8, gap: 8 }}>
        <Button icon={<PlusOutlined />} type="primary" onClick={() => { setEditing(null); form.resetFields(); setModalOpen(true); }}>新增</Button>
        <Button onClick={handleBatchSave}>批量保存</Button>
      </div>
      <table className="ecl-table">
        <thead>
          <tr>
            <th>年份</th>
            <th>折旧率</th>
            <th style={{ width: 120 }}>操作</th>
          </tr>
        </thead>
        <tbody>
          {list.map((d) => (
            <tr key={d.depreciationId}>
              <td>{d.yearOffset != null ? `第 ${d.yearOffset} 年` : '-'}</td>
              <td>{(d.depreciationRate * 100).toFixed(2)}%</td>
              <td>
                <Space>
                  <Button type="link" size="small" icon={<EditOutlined />}
                    onClick={() => { setEditing(d); form.setFieldsValue(d); setModalOpen(true); }} />
                  <Button type="link" size="small" danger icon={<DeleteOutlined />}
                    onClick={() => handleDelete(d.depreciationId!)} />
                </Space>
              </td>
            </tr>
          ))}
          {list.length === 0 && (
            <tr><td colSpan={3}><div className="ecl-empty-row">暂无数据</div></td></tr>
          )}
        </tbody>
      </table>

      <Modal
        title={editing ? '编辑押品折旧率' : '新增押品折旧率'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="yearOffset"
            label="年份偏移"
            rules={[
              { required: true, message: '请输入年份偏移' },
              { type: 'number', min: 0, message: '年份偏移不能小于 0' },
            ]}
          >
            <InputNumber min={0} style={{ width: '100%' }} placeholder="如：0（当年）" />
          </Form.Item>
          <Form.Item
            name="depreciationRate"
            label="折旧率"
            rules={[
              { required: true, message: '请输入折旧率' },
              { type: 'number', min: 0, max: 1, message: '折旧率范围 0 ~ 1' },
            ]}
          >
            <InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} placeholder="0 ~ 1 之间的小数" />
          </Form.Item>
          <Typography.Text type="secondary">校验规则：折旧率 0 ~ 1</Typography.Text>
        </Form>
      </Modal>
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

  // ─── 分组 ───
  const [groups, setGroups] = useState<RiskGroupVO[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<string>('');

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
      setSelectedGroupId('');
      return;
    }
    riskGroupApi.listByScheme(selectedSchemeId).then((res) => {
      setGroups((res.data as any)?.data || res.data || []);
    });
    setSelectedGroupId('');
  }, [selectedSchemeId]);

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
              onChange={(v) => { setSelectedSchemeId(v); setSelectedGroupId(''); }}
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
      children: <BenchmarkCurveTab selectedSchemeId={selectedSchemeId} selectedGroupId={selectedGroupId} />,
    },
    {
      key: 'discount',
      label: '押品折扣率',
      children: <CollateralDiscountTab selectedSchemeId={selectedSchemeId} />,
    },
    {
      key: 'depreciation',
      label: '押品折旧率',
      children: <DepreciationTab selectedSchemeId={selectedSchemeId} selectedGroupId={selectedGroupId} />,
    },
  ];
  const groupSelectorItems = groups.map((g) => ({
    groupId: g.groupId,
    groupName: g.groupName,
    groupCode: g.groupCode,
  }));

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
              onChange={(v) => { setSelectedSchemeId(v); setSelectedGroupId(''); }}
              options={schemes.map((s) => ({ label: `${s.schemeName}(${s.schemeCode})`, value: s.schemeId }))}
            />
          </Space>
        }
      />

      {groups.length > 0 && (
        <GroupSelector
          groups={groupSelectorItems}
          selectedId={selectedGroupId || undefined}
          onChange={setSelectedGroupId}
        />
      )}

      <Panel>
        <Tabs items={tabItems} />
      </Panel>
    </div>
  );
};

export default LgdConfig;
