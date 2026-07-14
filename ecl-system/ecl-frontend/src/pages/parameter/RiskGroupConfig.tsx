import React, { useState, useEffect, useCallback } from 'react';
import { Button, Space, Modal, Form, Input, InputNumber, message, Typography, Empty, Select } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useSearchParams, useOutletContext } from 'react-router-dom';
import { schemeApi, type SchemeVO } from '../../api/scheme';
import { riskGroupApi, type RiskGroupVO, type RiskGroupDetailVO } from '../../api/riskGroup';
import { dictApi, type DictEntryVO } from '../../api/dict';
import { PageHeader, Panel } from '../../components';

const RiskGroupConfig: React.FC = () => {
  const [searchParams] = useSearchParams();
  const schemeIdFromUrl = searchParams.get('schemeId') || '';
  const { schemeContext } = useOutletContext<{ schemeContext?: { schemeId: string } }>();
  const effectiveSchemeId = schemeIdFromUrl || schemeContext?.schemeId || '';

  const [schemes, setSchemes] = useState<SchemeVO[]>([]);
  const [selectedSchemeId, setSelectedSchemeId] = useState<string>(effectiveSchemeId);
  const [groups, setGroups] = useState<RiskGroupVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedGroupId, setSelectedGroupId] = useState<string | null>(null);
  const [details, setDetails] = useState<RiskGroupDetailVO[]>([]);
  const [groupModalOpen, setGroupModalOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<RiskGroupVO | null>(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [editingDetail, setEditingDetail] = useState<RiskGroupDetailVO | null>(null);
  const [groupForm] = Form.useForm();
  const [detailForm] = Form.useForm();
  const [dictSegment, setDictSegment] = useState<DictEntryVO[]>([]);
  const [dictProductType, setDictProductType] = useState<DictEntryVO[]>([]);
  const [dictIndustry, setDictIndustry] = useState<DictEntryVO[]>([]);
  const [dictCollateral, setDictCollateral] = useState<DictEntryVO[]>([]);

  useEffect(() => {
    schemeApi.list().then((res) =>
      setSchemes((res.data as any)?.data || res.data || []));
  }, []);

  const loadDictOptions = useCallback(async (schemeId: string) => {
    try {
      const [segRes, prodRes, indRes, colRes] = await Promise.all([
        dictApi.getEffectiveEntries(schemeId, 'CUSTOMER_TYPE'),
        dictApi.getEffectiveEntries(schemeId, 'PRODUCT_TYPE'),
        dictApi.getEffectiveEntries(schemeId, 'INDUSTRY'),
        dictApi.getEffectiveEntries(schemeId, 'COLLATERAL_TYPE'),
      ]);
      setDictSegment((segRes.data as any)?.data || segRes.data || []);
      setDictProductType((prodRes.data as any)?.data || prodRes.data || []);
      setDictIndustry((indRes.data as any)?.data || indRes.data || []);
      setDictCollateral((colRes.data as any)?.data || colRes.data || []);
    } catch (err) {
      console.error('加载字典选项失败', err);
    }
  }, []);

  const loadGroups = useCallback(async () => {
    if (!selectedSchemeId) { setGroups([]); return; }
    setLoading(true);
    try {
      const res = await riskGroupApi.listByScheme(selectedSchemeId);
      const list = (res.data as any)?.data || res.data || [];
      setGroups(list);
    } finally { setLoading(false); }
  }, [selectedSchemeId]);

  useEffect(() => { loadGroups(); }, [loadGroups]);

  useEffect(() => {
    if (selectedSchemeId) {
      loadDictOptions(selectedSchemeId);
    }
  }, [selectedSchemeId]);

  useEffect(() => {
    if (groups.length > 0 && !selectedGroupId) {
      setSelectedGroupId(groups[0].groupId);
    }
  }, [groups]);

  useEffect(() => {
    if (selectedGroupId) {
      const group = groups.find((g) => g.groupId === selectedGroupId);
      setDetails(group?.details || []);
    }
  }, [selectedGroupId, groups]);

  const handleSaveGroup = async () => {
    try {
      const values = await groupForm.validateFields();
      if (editingGroup) {
        await riskGroupApi.update(editingGroup.groupId, { ...values, schemeId: selectedSchemeId });
        message.success('分组更新成功');
      } else {
        const { groupCode: _, ...createValues } = values;
        await riskGroupApi.create({ ...createValues, schemeId: selectedSchemeId });
        message.success('分组创建成功');
      }
      setGroupModalOpen(false);
      setEditingGroup(null);
      groupForm.resetFields();
      loadGroups();
    } catch (err: any) {
      if (err?.errorFields) {
        // form validation error, antd will show field errors
        return;
      }
      message.error(err?.message || '操作失败');
    }
  };

  const handleDeleteGroup = (groupId: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '删除后该分组下的所有规则也将被删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await riskGroupApi.delete(selectedSchemeId, groupId);
        message.success('已删除');
        if (selectedGroupId === groupId) setSelectedGroupId(null);
        loadGroups();
      },
    });
  };

  const handleSaveDetail = async () => {
    const values = await detailForm.validateFields();
    if (!selectedGroupId) return;
    if (!values.segment && !values.productType &&
        !values.industryCode && !values.collateralType) {
      message.error('至少填写一个匹配维度');
      return;
    }
    let updated: RiskGroupDetailVO[];
    if (editingDetail) {
      updated = details.map((d) => d.detailId === editingDetail.detailId ? { ...d, ...values } : d);
    } else {
      updated = [...details, { ...values, detailId: `tmp_${Date.now()}` }];
    }
    await riskGroupApi.updateDetails(selectedSchemeId, selectedGroupId, updated);
    message.success('规则保存成功');
    setDetailModalOpen(false);
    setEditingDetail(null);
    detailForm.resetFields();
    const res = await riskGroupApi.listByScheme(selectedSchemeId);
    const list = (res.data as any)?.data || res.data || [];
    setGroups(list);
  };

  const handleDeleteDetail = async (record: RiskGroupDetailVO) => {
    if (!selectedGroupId) return;
    const newDetails = details.filter((d) => d.detailId !== record.detailId);
    await riskGroupApi.updateDetails(selectedSchemeId, selectedGroupId, newDetails);
    message.success('已删除');
    setDetails(newDetails);
    const res = await riskGroupApi.listByScheme(selectedSchemeId);
    setGroups((res.data as any)?.data || res.data || []);
  };

  const selectedGroup = groups.find((g) => g.groupId === selectedGroupId);

  if (!effectiveSchemeId && !selectedSchemeId) {
    return (
      <div className="ecl-page">
        <PageHeader title="风险分组配置" subtitle="管理风险分组主数据及匹配规则" />
        <Panel>
          <Empty description="请先选择 ECL 方案">
            <Select
              style={{ width: 300, marginTop: 16 }}
              placeholder="选择 ECL 方案"
              value={selectedSchemeId || undefined}
              onChange={setSelectedSchemeId}
              options={schemes.map((s) => ({
                label: `${s.schemeName} (${s.schemeCode})`,
                value: s.schemeId,
              }))}
            />
          </Empty>
        </Panel>
      </div>
    );
  }

  return (
    <div className="ecl-page">
      <PageHeader
        title="风险分组配置"
        subtitle="管理风险分组主数据及匹配规则，系统根据业务维度自动将借据归类到对应分组"
        extra={
          selectedSchemeId ? (
            <Button type="primary" icon={<PlusOutlined />}
              onClick={() => { setEditingGroup(null); groupForm.resetFields(); setGroupModalOpen(true); }}>
              新增分组
            </Button>
          ) : null
        }
      />

      {!selectedSchemeId ? (
        <Panel>
          <Empty description="请先选择一个 ECL 方案" />
        </Panel>
      ) : (
        <Panel noPadding>
          <div className="split-layout ecl-split-layout">
            {/* Left: group list */}
            <div className="split-sidebar ecl-split-sidebar">
              <div className="split-sidebar-header">
                <span>风险分组</span>
                <span style={{ fontWeight: 400, color: 'var(--color-text-muted)' }}>{groups.length} 个</span>
              </div>
              <div>
                {groups.map((group) => (
                  <div
                    key={group.groupId}
                    className={`group-item ${selectedGroupId === group.groupId ? 'active' : ''}`}
                    onClick={() => setSelectedGroupId(group.groupId)}
                  >
                    <div style={{ flex: 1 }}>
                      <div className="gi-name">{group.groupName}</div>
                      <div className="gi-code">{group.groupCode} · 排序 {group.sortOrder}</div>
                    </div>
                    <span className="gi-count">{group.details?.length || 0} 条规则</span>
                    <span className="gi-delete" onClick={(e) => { e.stopPropagation(); handleDeleteGroup(group.groupId); }}>✕</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Right: rules table */}
            <div className="split-main ecl-split-main">
              <div className="split-main-header ecl-split-header">
                <div>
                  <span className="split-main-title ecl-split-title">{selectedGroup?.groupName || '选择分组'}</span>
                  <span style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginLeft: 8 }}>匹配规则</span>
                </div>
                <Space>
                  <Button size="small" icon={<EditOutlined />}
                    onClick={() => { if (selectedGroup) { setEditingGroup(selectedGroup); groupForm.setFieldsValue(selectedGroup); setGroupModalOpen(true); } }}
                    disabled={!selectedGroupId}>编辑分组</Button>
                  <Button type="primary" size="small" icon={<PlusOutlined />}
                    onClick={() => { setEditingDetail(null); detailForm.resetFields(); setDetailModalOpen(true); }}
                    disabled={!selectedGroupId}>新增规则</Button>
                </Space>
              </div>

              <table className="ecl-table">
                <thead>
                  <tr>
                    <th style={{ width: 70 }}>优先级</th>
                    <th>segment</th>
                    <th>产品类型</th>
                    <th style={{ width: 80 }}>行业</th>
                    <th>担保类型</th>
                    <th style={{ width: 80 }}>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {details.map((d) => (
                    <tr key={d.detailId}>
                      <td>{d.priority}</td>
                      <td>{d.segment || <span className="wildcard">*</span>}</td>
                      <td>{d.productType || <span className="wildcard">*</span>}</td>
                      <td>{d.industryCode || <span className="wildcard">*</span>}</td>
                      <td>{d.collateralType || <span className="wildcard">*</span>}</td>
                      <td>
                        <Space size={0}>
                          <Button type="link" size="small" icon={<EditOutlined />}
                            onClick={() => { setEditingDetail(d); detailForm.setFieldsValue(d); setDetailModalOpen(true); }} />
                          <Button type="link" size="small" danger icon={<DeleteOutlined />}
                            onClick={() => handleDeleteDetail(d)} />
                        </Space>
                      </td>
                    </tr>
                  ))}
                  {details.length === 0 && (
                    <tr><td colSpan={6}><div className="ecl-empty-row">暂无匹配规则，请点击「新增规则」</div></td></tr>
                  )}
                </tbody>
              </table>
              <div className="info-note ecl-info-note">空值字段 = 通配（不限制该维度）· 4 个维度全空禁止保存 · 引擎按优先级逐条匹配</div>
            </div>
          </div>
        </Panel>
      )}

      {/* Group Modal */}
      <Modal title={editingGroup ? '编辑分组' : '新增分组'} open={groupModalOpen}
        onOk={handleSaveGroup} onCancel={() => { setGroupModalOpen(false); groupForm.resetFields(); }}
        okText="保存" cancelText="取消">
        <Form form={groupForm} layout="vertical">
          {editingGroup ? (
            <Form.Item name="groupCode" label="分组编码">
              <Input disabled />
            </Form.Item>
          ) : (
            <Form.Item name="groupCode" hidden>
              <Input />
            </Form.Item>
          )}
          <Form.Item name="groupName" label="分组名称" rules={[{ required: true }]}>
            <Input placeholder="如：政府贷款" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序" initialValue={0}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Detail Modal */}
      <Modal title={editingDetail ? '编辑规则' : '新增规则'} open={detailModalOpen}
        onOk={handleSaveDetail} onCancel={() => { setDetailModalOpen(false); detailForm.resetFields(); }}
        okText="保存" cancelText="取消">
        <Form form={detailForm} layout="vertical">
          <Form.Item name="priority" label="优先级" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 12px' }}>
            <Form.Item name="segment" label="segment">
              <Select placeholder="不限制（通配）" allowClear showSearch
                options={dictSegment.map(e => ({ label: `${e.entryName} (${e.entryCode})`, value: e.entryCode }))} />
            </Form.Item>
            <Form.Item name="productType" label="产品类型">
              <Select placeholder="不限制（通配）" allowClear showSearch
                options={dictProductType.map(e => ({ label: `${e.entryName} (${e.entryCode})`, value: e.entryCode }))} />
            </Form.Item>
            <Form.Item name="industryCode" label="行业代码">
              <Select placeholder="不限制（通配）" allowClear showSearch
                options={dictIndustry.map(e => ({ label: `${e.entryName} (${e.entryCode})`, value: e.entryCode }))} />
            </Form.Item>
            <Form.Item name="collateralType" label="担保类型">
              <Select placeholder="不限制（通配）" allowClear showSearch
                options={dictCollateral.map(e => ({ label: `${e.entryName} (${e.entryCode})`, value: e.entryCode }))} />
            </Form.Item>
          </div>
          <Typography.Text type="secondary">至少填写一个匹配维度</Typography.Text>
        </Form>
      </Modal>

      <style>{`
        .split-layout {
          display: grid;
          grid-template-columns: 300px 1fr;
          min-height: 420px;
        }
        .split-sidebar {
          border-right: 1px solid var(--color-border);
          background: #fafbfc;
        }
        .split-sidebar-header {
          padding: 12px 16px;
          border-bottom: 1px solid var(--color-border);
          font-size: 12px;
          font-weight: 600;
          color: var(--color-text-secondary);
          display: flex;
          justify-content: space-between;
        }
        .split-main {
          overflow-y: auto;
        }
        .split-main-header {
          padding: 14px 20px;
          border-bottom: 1px solid var(--color-border);
          display: flex;
          justify-content: space-between;
          align-items: center;
        }
        .split-main-title {
          font-size: 15px;
          font-weight: 600;
        }
        .group-item {
          padding: 12px 16px;
          cursor: pointer;
          border-bottom: 1px solid var(--color-border-light);
          display: flex;
          align-items: center;
          gap: 10px;
          transition: all 0.1s;
        }
        .group-item:hover { background: var(--color-primary-light); }
        .group-item.active {
          background: var(--color-primary-light);
          border-left: 3px solid var(--color-primary);
        }
        .gi-name { font-size: 13px; font-weight: 500; }
        .gi-code { font-size: 11px; color: var(--color-text-muted); }
        .gi-count { font-size: 11px; color: var(--color-text-secondary); margin-left: auto; }
        .gi-delete {
          font-size: 10px;
          color: var(--color-text-muted);
          cursor: pointer;
          padding: 2px 4px;
          border-radius: 4px;
          opacity: 0;
          transition: opacity 0.12s;
          background: none;
          border: none;
        }
        .group-item:hover .gi-delete { opacity: 1; }
        .gi-delete:hover { color: #dc2626; background: #fee2e2; }
        .wildcard { color: var(--color-text-muted); font-size: 12px; font-style: italic; }
        .info-note {
          font-size: 12px;
          color: var(--color-text-muted);
          padding: 8px 12px;
          border-top: 1px solid var(--color-border);
          background: var(--color-bg-alt);
        }
        .ecl-table { width: 100%; border-collapse: collapse; }
        .ecl-table th {
          text-align: left;
          font-size: 11px;
          font-weight: 700;
          color: var(--color-text-secondary);
          padding: 12px 16px;
          background: #f1f5f9;
          border-bottom: 2px solid #dde1e7;
          white-space: nowrap;
          text-transform: uppercase;
          letter-spacing: 0.06em;
        }
        .ecl-table td {
          padding: 13px 16px;
          border-bottom: 1px solid #f0f1f3;
          font-size: 13px;
          color: var(--color-text);
          background: #fff;
          transition: background var(--transition-fast);
        }
        .ecl-table tr:nth-child(even) td { background: #fafbfc; }
        .ecl-table tr:hover td { background: #eff3ff !important; }
        .ecl-table tr:last-child td { border-bottom: none; }
        @media (max-width: 800px) {
          .split-layout { grid-template-columns: 1fr; }
        }
      `}</style>
    </div>
  );
};

export default RiskGroupConfig;
