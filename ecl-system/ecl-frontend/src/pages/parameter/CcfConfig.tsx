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
  Upload,
  Typography,
  Empty,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import { useSearchParams, useOutletContext } from 'react-router-dom';
import { schemeApi, type SchemeVO } from '../../api/scheme';
import { ccfApi, type CcfCurveVO } from '../../api/ccf';
import { dictApi, type DictEntryVO } from '../../api/dict';
import { PageHeader, Panel } from '../../components';

const CcfConfig: React.FC = () => {
  const [searchParams] = useSearchParams();
  const schemeIdFromUrl = searchParams.get('schemeId') || '';
  const { schemeContext } = useOutletContext<{ schemeContext?: { schemeId: string } }>();
  const effectiveSchemeId = schemeIdFromUrl || schemeContext?.schemeId || '';

  // ─── 方案 ───
  const [schemes, setSchemes] = useState<SchemeVO[]>([]);
  const [selectedSchemeId, setSelectedSchemeId] = useState<string>(effectiveSchemeId);

  // ─── CCF 曲线 ───
  const [curves, setCurves] = useState<CcfCurveVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingCurve, setEditingCurve] = useState<CcfCurveVO | null>(null);
  const [form] = Form.useForm();
  const [dictProductType, setDictProductType] = useState<DictEntryVO[]>([]);
  const [dictCommitmentType, setDictCommitmentType] = useState<DictEntryVO[]>([]);
  const [dictCollateral, setDictCollateral] = useState<DictEntryVO[]>([]);

  // 加载字典选项
  useEffect(() => {
    if (effectiveSchemeId) {
      Promise.all([
        dictApi.getEffectiveEntries(effectiveSchemeId, 'PRODUCT_TYPE'),
        dictApi.getEffectiveEntries(effectiveSchemeId, 'COMMITMENT_TYPE'),
      ]).then(([prodRes, cmtRes]) => {
        setDictProductType((prodRes.data as any)?.data || prodRes.data || []);
        setDictCommitmentType((cmtRes.data as any)?.data || cmtRes.data || []);
      }).catch(console.error);
    }
  }, [effectiveSchemeId]);

  // 加载方案列表
  useEffect(() => {
    schemeApi.list().then((res) => {
      setSchemes((res.data as any)?.data || res.data || []);
    });
  }, []);

  // 加载曲线
  const loadCurves = useCallback(async () => {
    if (!selectedSchemeId) {
      setCurves([]);
      return;
    }
    setLoading(true);
    try {
      const res = await ccfApi.listCurves(selectedSchemeId);
      setCurves((res.data as any)?.data || res.data || []);
    } finally {
      setLoading(false);
    }
  }, [selectedSchemeId]);

  useEffect(() => {
    loadCurves();
  }, [loadCurves]);

  // ─── 新建/编辑 ───
  const handleSave = async () => {
    const values = await form.validateFields();

    // 校验 daysMin < daysMax
    if (values.commitmentDaysMin >= values.commitmentDaysMax) {
      message.error('期限下限必须小于期限上限');
      return;
    }

    if (editingCurve) {
      await ccfApi.updateCurve(editingCurve.curveId!, {
        ...values,
        schemeId: selectedSchemeId,
      });
      message.success('CCF 曲线更新成功');
    } else {
      await ccfApi.createCurve({
        ...values,
        schemeId: selectedSchemeId,
      });
      message.success('CCF 曲线创建成功');
    }
    setModalOpen(false);
    setEditingCurve(null);
    form.resetFields();
    loadCurves();
  };

  const handleDelete = (curveId: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这条 CCF 曲线吗？',
      onOk: async () => {
        await ccfApi.deleteCurve(curveId);
        message.success('已删除');
        loadCurves();
      },
    });
  };

  // ─── 批量导入（JSON 文件） ───
  const handleBatchImport = async (file: File) => {
    const text = await file.text();
    let data: any[];
    try {
      data = JSON.parse(text);
    } catch {
      message.error('文件格式错误，请上传有效的 JSON 文件');
      return false;
    }

    if (!Array.isArray(data) || data.length === 0) {
      message.error('JSON 应为非空数组');
      return false;
    }

    try {
      await ccfApi.batchImport(selectedSchemeId, data);
      message.success(`批量导入成功，共 ${data.length} 条`);
      loadCurves();
    } catch {
      message.error('批量导入失败');
    }
    return false; // 阻止 Upload 的默认行为
  };

  // ─── 列定义 ───
  const columns = [
    { title: '产品类型', dataIndex: 'productType', key: 'productType', width: 140 },
    { title: '承诺类型', dataIndex: 'commitmentType', key: 'commitmentType', width: 140 },
    {
      title: '期限下限(天)',
      dataIndex: 'commitmentDaysMin',
      key: 'commitmentDaysMin',
      width: 130,
      render: (v: number) => (v != null ? v : '-'),
    },
    {
      title: '期限上限(天)',
      dataIndex: 'commitmentDaysMax',
      key: 'commitmentDaysMax',
      width: 130,
      render: (v: number) => (v != null ? v : '-'),
    },
    {
      title: 'CCF 值',
      dataIndex: 'ccfValue',
      key: 'ccfValue',
      width: 130,
      render: (v: number) => (v != null ? (v * 100).toFixed(2) + '%' : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: any, record: CcfCurveVO) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingCurve(record);
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

  // ─── 渲染 ───
  if (!selectedSchemeId) {
    return (
      <div className="ecl-page">
        <PageHeader
          title="CCF 参数配置"
          subtitle="管理信用转换系数曲线，按产品类型和承诺期限定义 CCF 值"
        />
        <Panel>
          <Empty description="请先选择一个 ECL 方案">
            <Select
              style={{ width: 300, marginTop: 16 }}
              placeholder="请选择 ECL 方案"
              value={selectedSchemeId || undefined}
              onChange={(v) => {
                setSelectedSchemeId(v);
                setEditingCurve(null);
                form.resetFields();
              }}
              options={schemes.map((s) => ({
                label: `${s.schemeName}(${s.schemeCode})`,
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
        title="CCF 参数配置"
        subtitle="管理信用转换系数曲线，按产品类型和承诺期限定义 CCF 值"
        extra={
          <Space>
            <Select
              style={{ width: 300 }}
              placeholder="请选择 ECL 方案"
              value={selectedSchemeId || undefined}
              onChange={(v) => {
                setSelectedSchemeId(v);
                setEditingCurve(null);
                form.resetFields();
              }}
              options={schemes.map((s) => ({
                label: `${s.schemeName}(${s.schemeCode})`,
                value: s.schemeId,
              }))}
            />
            <Upload
              accept=".json"
              showUploadList={false}
              beforeUpload={handleBatchImport}
            >
              <Button icon={<UploadOutlined />}>批量导入</Button>
            </Upload>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditingCurve(null);
                form.resetFields();
                setModalOpen(true);
              }}
            >
              新增曲线
            </Button>
          </Space>
        }
      />

      <Panel>
        <table className="ecl-table">
          <thead>
            <tr>
              <th>产品类型</th>
              <th>承诺类型</th>
              <th>期限下限(天)</th>
              <th>期限上限(天)</th>
              <th>CCF 值</th>
              <th style={{ width: 140 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {curves.map((c) => (
              <tr key={c.curveId}>
                <td>{c.productType}</td>
                <td>{c.commitmentType}</td>
                <td>{c.commitmentDaysMin != null ? c.commitmentDaysMin : '-'}</td>
                <td>{c.commitmentDaysMax != null ? c.commitmentDaysMax : '-'}</td>
                <td>{(c.ccfValue * 100).toFixed(2)}%</td>
                <td>
                  <Space>
                    <Button type="link" size="small" icon={<EditOutlined />}
                      onClick={() => { setEditingCurve(c); form.setFieldsValue(c); setModalOpen(true); }} />
                    <Button type="link" size="small" danger icon={<DeleteOutlined />}
                      onClick={() => handleDelete(c.curveId!)} />
                  </Space>
                </td>
              </tr>
            ))}
            {curves.length === 0 && (
              <tr><td colSpan={6}><div className="ecl-empty-row">暂无数据</div></td></tr>
            )}
          </tbody>
        </table>
      </Panel>

      {/* ─── 新建/编辑弹窗 ─── */}
      <Modal
        title={editingCurve ? '编辑 CCF 曲线' : '新增 CCF 曲线'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="productType"
            label="产品类型"
            rules={[{ required: false }]}
          >
            <Select
              allowClear
              placeholder="请选择产品类型（为空=全集）"
              options={[
                { label: '不限（全集）', value: '' },
                ...dictProductType.map((d) => ({
                  label: `${d.entryName}(${d.entryCode})`,
                  value: d.entryCode || '',
                })),
              ]}
            />
          </Form.Item>
          <Form.Item
            name="commitmentType"
            label="承诺类型"
            rules={[{ required: false }]}
          >
            <Select
              allowClear
              placeholder="请选择承诺类型（为空=全集）"
              options={[
                { label: '不限（全集）', value: '' },
                ...dictCommitmentType.map((d) => ({
                  label: `${d.entryName}(${d.entryCode})`,
                  value: d.entryCode || '',
                })),
              ]}
            />
          </Form.Item>
          <Form.Item
            name="commitmentDaysMin"
            label="期限下限(天)"
            rules={[
              { required: true, message: '请输入期限下限' },
              { type: 'number', min: 0, message: '期限下限不能小于 0' },
            ]}
          >
            <InputNumber min={0} style={{ width: '100%' }} placeholder="如：0" />
          </Form.Item>
          <Form.Item
            name="commitmentDaysMax"
            label="期限上限(天)"
            rules={[
              { required: true, message: '请输入期限上限' },
              { type: 'number', min: 1, message: '期限上限至少为 1' },
            ]}
          >
            <InputNumber min={1} style={{ width: '100%' }} placeholder="如：365" />
          </Form.Item>
          <Form.Item
            name="ccfValue"
            label="CCF 值"
            rules={[
              { required: true, message: '请输入 CCF 值' },
              {
                type: 'number',
                min: 0,
                max: 1,
                message: 'CCF 值范围 0 ~ 1',
              },
            ]}
          >
            <InputNumber
              min={0}
              max={1}
              step={0.01}
              style={{ width: '100%' }}
              placeholder="0 ~ 1 之间的小数"
            />
          </Form.Item>
          <Typography.Text type="secondary">
            校验规则：期限下限 &lt; 期限上限，CCF 值 0 ~ 1
          </Typography.Text>
        </Form>
      </Modal>
    </div>
  );
};

export default CcfConfig;
