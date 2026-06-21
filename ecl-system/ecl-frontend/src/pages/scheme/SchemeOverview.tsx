import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useOutletContext } from 'react-router-dom';
import { Button, Space, Modal, Form, Input, Select, message, Spin, Typography } from 'antd';
import {
  AppstoreOutlined,
  BarChartOutlined,
  BranchesOutlined,
  DeleteOutlined,
  EditOutlined,
  LineChartOutlined,
  PercentageOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { schemeApi, type SchemeVO } from '../../api/scheme';
import { Panel, StatusTag } from '../../components';

const moduleCards = [
  {
    key: 'risk-groups', title: '风险分组', desc: '定义资产风险分组及多维匹配规则',
    icon: <AppstoreOutlined />, iconBg: '#dbeafe', iconColor: '#2563eb',
  },
  {
    key: 'stage', title: '阶段划分', desc: '配置三阶段判定规则与阈值',
    icon: <BranchesOutlined />, iconBg: '#d1fae5', iconColor: '#059669',
  },
  {
    key: 'pd', title: 'PD 参数', desc: '管理违约概率曲线及情景权重',
    icon: <LineChartOutlined />, iconBg: '#fef3c7', iconColor: '#d97706',
  },
  {
    key: 'lgd', title: 'LGD 参数', desc: '配置违约损失率及押品折扣/折旧',
    icon: <BarChartOutlined />, iconBg: '#ede9fe', iconColor: '#7c3aed',
  },
  {
    key: 'ccf', title: 'CCF 参数', desc: '管理信用转换系数曲线',
    icon: <PercentageOutlined />, iconBg: '#fce7f3', iconColor: '#db2777',
  },
  {
    key: 'overlay', title: '管理层叠加', desc: '配置管理层判断的调整规则',
    icon: <SafetyCertificateOutlined />, iconBg: '#f3f4f6', iconColor: '#6b7280',
  },
];

const SchemeOverview: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { setSchemeContext } = useOutletContext<any>();
  const [scheme, setScheme] = useState<SchemeVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [publishModalOpen, setPublishModalOpen] = useState(false);
  const [form] = Form.useForm();

  const fetchScheme = async () => {
    if (!id) return;
    setLoading(true);
    try {
      const res = await schemeApi.getById(id);
      const s = (res.data as any)?.data || res.data || null;
      setScheme(s);
      if (s) {
        setSchemeContext({
          schemeId: s.schemeId,
          schemeCode: s.schemeCode,
          schemeName: s.schemeName,
          status: s.status,
        });
      }
    } catch (err) {
      console.error(err);
      message.error('加载方案失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchScheme(); }, [id]);

  const handleEdit = async () => {
    if (!scheme) return;
    const values = await form.validateFields();
    await schemeApi.update(scheme.schemeId, {
      schemeName: values.schemeName,
      description: values.description,
    });
    message.success('方案信息已更新');
    setEditModalOpen(false);
    form.resetFields();
    fetchScheme();
  };

  const handlePublish = async () => {
    if (!scheme) return;
    const values = await form.validateFields();
    await schemeApi.publish(scheme.schemeId, values.immediate, values.effectiveDate);
    message.success('方案发布成功');
    setPublishModalOpen(false);
    form.resetFields();
    fetchScheme();
  };

  const handleDelete = async () => {
    if (!scheme) return;
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个 DRAFT 方案吗？',
      okButtonProps: { danger: true },
      onOk: async () => {
        await schemeApi.delete(scheme.schemeId);
        message.success('已删除');
        navigate('/schemes');
      },
    });
  };

  if (loading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />;
  if (!scheme) return <Panel><Typography.Text type="secondary">方案不存在或已删除</Typography.Text></Panel>;

  return (
    <div className="ecl-page">
      {/* Breadcrumb */}
      <div className="ecl-breadcrumb">
        <button type="button" onClick={() => navigate('/schemes')}>
          方案管理
        </button>
        <span className="ecl-breadcrumb-sep">/</span>
        <span>{scheme.schemeCode} · {scheme.schemeName}</span>
      </div>

      {/* Info Panel */}
      <Panel>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <h1 style={{ fontSize: 20, fontWeight: 600, margin: 0 }}>{scheme.schemeName}</h1>
              <StatusTag status={scheme.status as any}>{scheme.statusDisplay}</StatusTag>
            </div>
            <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginTop: 4 }}>
              方案编码：{scheme.schemeCode} · 版本 {scheme.schemeVersion}
            </div>
          </div>
          <Space>
            {scheme.status === 'DRAFT' && (
              <>
                <Button icon={<EditOutlined />}
                  onClick={() => { form.setFieldsValue(scheme); setEditModalOpen(true); }}>编辑</Button>
                <Button type="primary" icon={<SendOutlined />}
                  onClick={() => setPublishModalOpen(true)}>发布</Button>
                <Button danger icon={<DeleteOutlined />}
                  onClick={handleDelete}>删除</Button>
              </>
            )}
            <Button onClick={() => {
              Modal.confirm({
                title: '基于本方案复制',
                content: '将基于当前方案创建一份全新的 DRAFT 方案。',
                onOk: async () => {
                  await schemeApi.copy('基于 ' + scheme.schemeCode + ' 复制');
                  message.success('复制成功');
                  navigate('/schemes');
                },
              });
            }}>基于本方案复制</Button>
          </Space>
        </div>

        <div className="ecl-info-grid">
          <div>
            <div className="ecl-info-label">生效日期</div>
            <div className="ecl-info-value">{scheme.effectiveDate || '-'}</div>
          </div>
          <div>
            <div className="ecl-info-label">创建人</div>
            <div className="ecl-info-value">{scheme.createdBy}</div>
          </div>
          <div>
            <div className="ecl-info-label">创建时间</div>
            <div className="ecl-info-value">{scheme.createdAt}</div>
          </div>
          <div>
            <div className="ecl-info-label">变更说明</div>
            <div className="ecl-info-value">{scheme.description || '-'}</div>
          </div>
        </div>
      </Panel>

      {/* Scheme-level Parameters */}
      <Panel title="方案级缺省参数" extra={<span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>点击可编辑</span>}>
        <div className="ecl-param-grid">
          <div className="ecl-param-item" onClick={() => message.info('编辑折扣率功能')}>
            <div className="ecl-info-label">折扣率 (discount_rate)</div>
            <div className="ecl-info-value" style={{ color: 'var(--color-primary)' }}>{scheme.discountRate}%</div>
          </div>
          <div className="ecl-param-item" onClick={() => message.info('编辑默认CCF功能')}>
            <div className="ecl-info-label">默认 CCF (default_ccf)</div>
            <div className="ecl-info-value" style={{ color: 'var(--color-primary)' }}>{scheme.defaultCcf}</div>
          </div>
          <div className="ecl-param-item" onClick={() => message.info('编辑默认LGD功能')}>
            <div className="ecl-info-label">默认 LGD (default_lgd)</div>
            <div className="ecl-info-value" style={{ color: 'var(--color-primary)' }}>{scheme.defaultLgd}</div>
          </div>
        </div>
      </Panel>

      {/* Module Cards Grid */}
      <Panel title="参数模块" extra={<span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>点击进入详细配置</span>}>
        <div className="module-grid">
          {moduleCards.map((mod) => (
            <div
              key={mod.key}
              className="module-card"
              onClick={() => navigate(`/parameters/${mod.key}?schemeId=${scheme.schemeId}`)}
            >
              <div className="module-card-icon" style={{ background: mod.iconBg, color: mod.iconColor }}>{mod.icon}</div>
              <div>
                <div className="module-card-title">{mod.title}</div>
                <div className="module-card-desc">{mod.desc}</div>
              </div>
              <div className="module-card-status">已配置</div>
            </div>
          ))}
        </div>
      </Panel>

      {/* Modals same as before — edit / publish */}
      <Modal title="编辑方案" open={editModalOpen}
        onOk={handleEdit} onCancel={() => { setEditModalOpen(false); form.resetFields(); }}
        okText="保存" cancelText="取消">
        <Form form={form} layout="vertical">
          <Form.Item name="schemeName" label="方案名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="变更说明">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="发布方案" open={publishModalOpen}
        onOk={handlePublish} onCancel={() => { setPublishModalOpen(false); form.resetFields(); }}
        okText="发布" cancelText="取消">
        <p>方案：{scheme.schemeName}（{scheme.schemeCode}）</p>
        <Form form={form} layout="vertical" initialValues={{ immediate: true }}>
          <Form.Item name="immediate" label="生效方式">
            <Select options={[
              { label: '立即生效', value: true },
              { label: '计划生效', value: false },
            ]} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.immediate !== cur.immediate}>
            {({ getFieldValue }) =>
              getFieldValue('immediate') === false ? (
                <Form.Item name="effectiveDate" label="计划生效日期" rules={[{ required: true }]}>
                  <Input type="date" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>

    </div>
  );
};

export default SchemeOverview;
