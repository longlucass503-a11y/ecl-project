import React, { useState, useEffect, useCallback } from 'react';
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
  Alert,
  Row,
  Col,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import { useSearchParams, useOutletContext } from 'react-router-dom';
import { schemeApi, type SchemeVO } from '../../api/scheme';
import { riskGroupApi, type RiskGroupVO } from '../../api/riskGroup';
import {
  overlayApi,
  type OverlayRuleVO,
  type OverlayMatchTestResp,
} from '../../api/overlay';
import { PageHeader, Panel, GroupSelector } from '../../components';

const { TextArea } = Input;

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
  const [selectedGroupId, setSelectedGroupId] = useState<string>('');

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
  const [testLoading, setTestLoading] = useState(false);

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
      setRules([]);
      return;
    }
    riskGroupApi.listByScheme(selectedSchemeId).then((res) => {
      setGroups((res.data as any)?.data || res.data || []);
    });
    setSelectedGroupId('');
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
      const res = await overlayApi.list(selectedSchemeId, selectedGroupId || undefined);
      setRules((res.data as any)?.data || res.data || []);
    } finally {
      setLoading(false);
    }
  }, [selectedSchemeId, selectedGroupId]);

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
        groupId: selectedGroupId || undefined,
      });
      message.success('规则更新成功');
    } else {
      await overlayApi.create({
        ...values,
        schemeId: selectedSchemeId,
        groupId: selectedGroupId || undefined,
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
    if (!selectedSchemeId) {
      message.warning('请先选择方案');
      return;
    }
    const fieldMap: Record<string, any> = {};
    testFields.forEach((f) => {
      if (f.key && f.value) {
        // 尝试数值转换
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
        groupId: selectedGroupId || undefined,
        fieldValues: fieldMap,
      });
      setTestResult((res.data as any)?.data || res.data);
    } catch {
      message.error('命中测试请求失败');
    } finally {
      setTestLoading(false);
    }
  };

  const handleOpenTest = () => {
    setTestResult(null);
    setTestFields([
      { key: 'industryCode', value: '' },
      { key: 'overdueDays', value: '' },
    ]);
    setTestModalOpen(true);
  };

  // 调整方式 Tag 颜色
  const adjustmentTypeColor: Record<string, string> = {
    ADDBP: 'blue',
    PERCENTAGE: 'green',
    FIXED: 'orange',
  };
  const groupSelectorItems = groups.map((g) => ({
    groupId: g.groupId,
    groupName: g.groupName,
    groupCode: g.groupCode,
  }));

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
              onChange={(v) => { setSelectedSchemeId(v); setSelectedGroupId(''); }}
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

      <Panel
        extra={
          <Space>
            <Button
              icon={<ExperimentOutlined />}
              onClick={handleOpenTest}
            >
              命中测试
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditingRule(null);
                form.resetFields();
                setModalOpen(true);
              }}
            >
              新增规则
            </Button>
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
                <td>{r.adjustmentValue != null ? r.adjustmentValue : '-'}</td>
                <td>{r.priority}</td>
                <td>{r.effectiveDate ? r.effectiveDate.slice(0, 10) : '-'}</td>
                <td>{r.expiryDate ? r.expiryDate.slice(0, 10) : '-'}</td>
                <td>{r.conditions ? <code style={{ fontSize: 12 }}>{r.conditions}</code> : '-'}</td>
                <td>
                  <Space>
                    <Button type="link" size="small" icon={<EditOutlined />}
                      onClick={() => { setEditingRule(r); form.setFieldsValue(r); setModalOpen(true); }} />
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
        onOk={handleSave}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="overlayType"
                label="调整类型"
                rules={[{ required: true, message: '请选择调整类型' }]}
              >
                <Select
                  options={[
                    { label: 'PD — 违约概率', value: 'PD' },
                    { label: 'LGD — 违约损失率', value: 'LGD' },
                    { label: 'CCF — 信用转换系数', value: 'CCF' },
                    { label: 'EAD — 违约风险暴露', value: 'EAD' },
                    { label: 'RISK_RATE — 风险率', value: 'RISK_RATE' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="adjustmentType"
                label="调整方式"
                rules={[{ required: true, message: '请选择调整方式' }]}
              >
                <Select
                  options={[
                    { label: 'ADDBP — 加点(基点)', value: 'ADDBP' },
                    { label: 'PERCENTAGE — 百分比', value: 'PERCENTAGE' },
                    { label: 'FIXED — 固定值', value: 'FIXED' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="adjustmentValue"
                label="调整值"
                rules={[{ required: true, message: '请输入调整值' }]}
              >
                <InputNumber style={{ width: '100%' }} placeholder="如：100" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="priority"
                label="优先级"
                rules={[{ required: true, message: '请输入优先级' }]}
              >
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
          <Form.Item
            name="conditions"
            label="JSON 条件"
            extra={'输入提示：{"industry_codes":["J","K"],"overdue_days_ge":90}'}
          >
            <TextArea rows={4} placeholder='{"industry_codes":["J","K"],"overdue_days_ge":90}' />
          </Form.Item>
        </Form>
      </Modal>

      {/* ─── 命中测试弹窗 ─── */}
      <Modal
        title="命中测试"
        open={testModalOpen}
        onCancel={() => {
          setTestModalOpen(false);
          setTestResult(null);
        }}
        footer={null}
        width={640}
      >
        <div style={{ marginBottom: 16 }}>
          <Typography.Text strong>输入测试数据：</Typography.Text>
          <Typography.Text type="secondary" style={{ marginLeft: 8 }}>
            字段名 = 值，可动态添加多行
          </Typography.Text>
        </div>

        {testFields.map((field, index) => (
          <Space key={index} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
            <Input
              style={{ width: 180 }}
              placeholder="字段名"
              value={field.key}
              onChange={(e) => {
                const newFields = [...testFields];
                newFields[index] = { ...newFields[index], key: e.target.value };
                setTestFields(newFields);
              }}
            />
            <span>=</span>
            <Input
              style={{ width: 180 }}
              placeholder="值"
              value={field.value}
              onChange={(e) => {
                const newFields = [...testFields];
                newFields[index] = { ...newFields[index], value: e.target.value };
                setTestFields(newFields);
              }}
            />
            <Button
              type="text"
              danger
              icon={<MinusCircleOutlined />}
              onClick={() => {
                setTestFields(testFields.filter((_, i) => i !== index));
              }}
            />
          </Space>
        ))}

        <div style={{ marginBottom: 16 }}>
          <Button
            onClick={() => setTestFields([...testFields, { key: '', value: '' }])}
          >
            + 添加字段
          </Button>
        </div>

        <Button
          type="primary"
          loading={testLoading}
          onClick={handleTestMatch}
          icon={<ExperimentOutlined />}
        >
          测试匹配
        </Button>

        <Divider />

        {/* 匹配结果展示 */}
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
                    <Tag
                      key={rule.overlayId || idx}
                      color={
                        rule.overlayId === testResult.selectedRule?.overlayId ? 'blue' : 'default'
                      }
                      style={{ marginBottom: 4 }}
                    >
                      {rule.overlayType} | 调整值={rule.adjustmentValue} | 优先级={rule.priority}
                    </Tag>
                  ))}
                </div>
              </div>
            ) : (
              <Alert type="info" message="无匹配规则" showIcon />
            )}
          </>
        )}
      </Modal>
    </div>
  );
};

export default OverlayConfig;
