import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Modal, Form, Input, Select, message } from 'antd';
import { PlusOutlined, CopyOutlined, SendOutlined, DeleteOutlined, ExperimentOutlined, EyeOutlined } from '@ant-design/icons';
import { useNavigate, useOutletContext } from 'react-router-dom';
import { schemeApi, type SchemeVO } from '../../api/scheme';
import { PageHeader, Panel, StatusTag } from '../../components';

const statusOptions = [
  { label: '全部状态', value: undefined },
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已生效', value: 'EFFECTIVE' },
  { label: '已失效', value: 'EXPIRED' },
];

const SchemeList: React.FC = () => {
  const [data, setData] = useState<SchemeVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [copyModalOpen, setCopyModalOpen] = useState(false);
  const [publishModalOpen, setPublishModalOpen] = useState(false);
  const [selectedScheme, setSelectedScheme] = useState<SchemeVO | null>(null);
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const { setSchemeContext } = useOutletContext<{
    setSchemeContext: (ctx: { schemeId: string; schemeCode: string; schemeName: string; status: string }) => void;
  }>();

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await schemeApi.list(statusFilter);
      setData((res.data as any)?.data || res.data || []);
    } catch (err) {
      console.error(err);
      message.error('加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, [statusFilter]);

  const handleCreate = async () => {
    const values = await form.validateFields();
    await schemeApi.create(values);
    message.success('方案创建成功');
    setCreateModalOpen(false);
    form.resetFields();
    fetchData();
  };

  const handleCopy = async () => {
    const values = await form.validateFields();
    await schemeApi.copy(values.description || '基于已有方案复制');
    message.success('方案复制成功');
    setCopyModalOpen(false);
    form.resetFields();
    fetchData();
  };

  const handlePublish = async () => {
    if (!selectedScheme) return;
    const values = await form.validateFields();
    await schemeApi.publish(selectedScheme.schemeId, values.immediate, values.effectiveDate);
    message.success('方案发布成功');
    setPublishModalOpen(false);
    setSelectedScheme(null);
    form.resetFields();
    fetchData();
  };

  const handleDelete = async (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个 DRAFT 方案吗？删除后不可恢复。',
      okButtonProps: { danger: true },
      okText: '确认删除',
      cancelText: '取消',
      onOk: async () => {
        await schemeApi.delete(id);
        message.success('已删除');
        fetchData();
      },
    });
  };

  const handleView = (record: SchemeVO) => {
    setSchemeContext({
      schemeId: record.schemeId,
      schemeCode: record.schemeCode,
      schemeName: record.schemeName,
      status: record.status,
    });
    navigate(`/schemes/${record.schemeId}`);
  };

  const columns = [
    {
      title: '方案编码',
      dataIndex: 'schemeCode',
      key: 'schemeCode',
      width: 140,
      render: (v: string) => <span className="mono-cell">{v}</span>,
    },
    { title: '方案名称', dataIndex: 'schemeName', key: 'schemeName' },
    { title: '版本', dataIndex: 'schemeVersion', key: 'schemeVersion', width: 80 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (status: string, record: SchemeVO) => (
        <StatusTag status={status as any}>{record.statusDisplay}</StatusTag>
      ),
    },
    { title: '生效日期', dataIndex: 'effectiveDate', key: 'effectiveDate', width: 120 },
    { title: '创建人', dataIndex: 'createdBy', key: 'createdBy', width: 100 },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
    {
      title: '操作', key: 'action', width: 240,
      render: (_: any, record: SchemeVO) => (
        <Space size={0}>
          <Button type="link" icon={<EyeOutlined />} onClick={() => handleView(record)}>查看</Button>
          {record.status === 'DRAFT' && (
            <>
              <Button type="link" icon={<SendOutlined />}
                onClick={() => { setSelectedScheme(record); setPublishModalOpen(true); }}>发布</Button>
              <Button type="link" danger icon={<DeleteOutlined />}
                onClick={() => handleDelete(record.schemeId)}>删除</Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className="ecl-page">
      <PageHeader
        title="ECL 方案管理"
        subtitle="管理 ECL 减值方案的生命周期：创建、复制、发布、失效"
        extra={
          <Space>
            <Button icon={<CopyOutlined />} onClick={() => { form.resetFields(); setCopyModalOpen(true); }}>
              从生效方案复制
            </Button>
            <Button icon={<ExperimentOutlined />} onClick={() => navigate('/schemes/compare')}>
              方案对比
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
              新建方案
            </Button>
          </Space>
        }
      />

      <Panel
        title="方案列表"
        extra={
          <Select
            style={{ width: 140 }}
            placeholder="状态筛选"
            allowClear
            value={statusFilter}
            onChange={setStatusFilter}
            options={statusOptions}
            size="small"
          />
        }
      >
        <Table
          columns={columns}
          dataSource={data}
          rowKey="schemeId"
          loading={loading}
          size="middle"
          pagination={{ showSizeChanger: true, showTotal: (total) => `共 ${total} 个方案` }}
        />
      </Panel>

      {/* 创建方案弹窗 */}
      <Modal title="新建方案" open={createModalOpen}
        onOk={handleCreate} onCancel={() => { setCreateModalOpen(false); form.resetFields(); }}
        okText="创建" cancelText="取消">
        <Form form={form} layout="vertical">
          <Form.Item name="schemeName" label="方案名称" rules={[{ required: true, message: '请输入方案名称' }]}>
            <Input placeholder="如：2026年Q3方案" />
          </Form.Item>
          <Form.Item name="description" label="变更说明">
            <Input.TextArea rows={3} placeholder="描述本次方案变更的内容" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 复制方案弹窗 */}
      <Modal title="从生效方案复制" open={copyModalOpen}
        onOk={handleCopy} onCancel={() => { setCopyModalOpen(false); form.resetFields(); }}
        okText="复制" cancelText="取消">
        <p style={{ marginBottom: 16, color: 'var(--color-text-secondary)', fontSize: 13 }}>
          将基于当前生效方案复制一份全新的 DRAFT 方案。
        </p>
        <Form form={form} layout="vertical">
          <Form.Item name="description" label="变更说明" rules={[{ required: true, message: '请输入变更说明' }]}>
            <Input.TextArea rows={3} placeholder="如：基于 Q2 方案修改 PD 参数" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 发布方案弹窗 */}
      <Modal title="发布方案" open={publishModalOpen}
        onOk={handlePublish} onCancel={() => { setPublishModalOpen(false); form.resetFields(); }}
        okText="发布" cancelText="取消">
        <p style={{ marginBottom: 16, color: 'var(--color-text-secondary)', fontSize: 13 }}>
          方案：{selectedScheme?.schemeName}（{selectedScheme?.schemeCode}）
        </p>
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

      <style>{`
        .mono-cell {
          font-family: "SF Mono", "JetBrains Mono", "Fira Code", monospace;
          font-size: 12px;
          letter-spacing: 0.02em;
          font-weight: 500;
          color: var(--color-primary-dark);
        }
      `}</style>
    </div>
  );
};

export default SchemeList;
