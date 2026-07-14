import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Select, Button, Table, Tag, Space, Spin, Typography, message } from 'antd';
import { ArrowLeftOutlined, SwapOutlined } from '@ant-design/icons';
import { schemeApi, type SchemeVO, type SchemeDiffVO } from '../../api/scheme';
import { PageHeader, Panel } from '../../components';

const SchemeCompare: React.FC = () => {
  const navigate = useNavigate();
  const [schemes, setSchemes] = useState<SchemeVO[]>([]);
  const [schemeId1, setSchemeId1] = useState<string | undefined>();
  const [schemeId2, setSchemeId2] = useState<string | undefined>();
  const [diffData, setDiffData] = useState<SchemeDiffVO[]>([]);
  const [comparing, setComparing] = useState(false);
  const [loadingSchemes, setLoadingSchemes] = useState(false);

  const unwrapList = <T,>(payload: T[] | { data?: T[] } | undefined): T[] => {
    if (Array.isArray(payload)) return payload;
    return payload?.data || [];
  };

  // 加载所有方案
  const fetchSchemes = async () => {
    setLoadingSchemes(true);
    try {
      const res = await schemeApi.list();
      setSchemes(unwrapList<SchemeVO>(res.data));
    } finally {
      setLoadingSchemes(false);
    }
  };

  useEffect(() => { fetchSchemes(); }, []);

  // 开始对比
  const handleCompare = async () => {
    if (!schemeId1 || !schemeId2) {
      message.warning('请选择两个要对比的方案');
      return;
    }
    if (schemeId1 === schemeId2) {
      message.warning('请选择两个不同的方案');
      return;
    }
    setComparing(true);
    try {
      const res = await schemeApi.compare(schemeId1, schemeId2);
      setDiffData(unwrapList<SchemeDiffVO>(res.data));
    } finally {
      setComparing(false);
    }
  };

  const schemeOptions = schemes.map((s) => ({
    label: `${s.schemeName} (${s.schemeCode} v${s.schemeVersion})`,
    value: s.schemeId,
  }));

  const diffColumns = [
    { title: '模块名称', dataIndex: 'module', key: 'module', width: 200 },
    { title: '方案1 版本', dataIndex: 'versionFrom', key: 'versionFrom' },
    { title: '方案2 版本', dataIndex: 'versionTo', key: 'versionTo' },
    { title: '差异项数', dataIndex: 'changedItems', key: 'changedItems', width: 100 },
    {
      title: '是否一致', key: 'match', width: 100,
      render: (_: unknown, record: SchemeDiffVO) =>
        record.same
          ? <Tag color="success">✔ 一致</Tag>
          : <Tag color="error">✖ 不一致</Tag>,
    },
  ];

  return (
    <div className="ecl-page">
      <div className="ecl-breadcrumb">
        <button type="button" onClick={() => navigate('/schemes')}>
          <ArrowLeftOutlined /> 返回方案列表
        </button>
      </div>

      <PageHeader
        title="方案对比"
        subtitle="选择两个 ECL 减值方案，对比参数差异和方案级配置变化"
      />

      <Panel title="对比条件">
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          {/* 方案选择 */}
          <Space>
            <Select
              style={{ width: 360 }}
              placeholder="选择方案1"
              showSearch
              value={schemeId1}
              onChange={setSchemeId1}
              options={schemeOptions}
              filterOption={(input, option) =>
                (option?.label as string ?? '').toLowerCase().includes(input.toLowerCase())
              }
              notFoundContent={loadingSchemes ? <Spin size="small" /> : '暂无方案'}
              allowClear
            />
            <SwapOutlined style={{ fontSize: 18, color: '#999' }} />
            <Select
              style={{ width: 360 }}
              placeholder="选择方案2"
              showSearch
              value={schemeId2}
              onChange={setSchemeId2}
              options={schemeOptions}
              filterOption={(input, option) =>
                (option?.label as string ?? '').toLowerCase().includes(input.toLowerCase())
              }
              notFoundContent={loadingSchemes ? <Spin size="small" /> : '暂无方案'}
              allowClear
            />
            <Button type="primary" icon={<SwapOutlined />} onClick={handleCompare} loading={comparing}>
              开始对比
            </Button>
          </Space>

          {/* 对比结果 */}
          {diffData.length > 0 && (
            <>
              <Typography.Text strong>
                共发现 {diffData.length} 项差异
              </Typography.Text>
              <Table
                columns={diffColumns}
                dataSource={diffData}
                rowKey="module"
                pagination={false}
                bordered
                size="small"
              />
            </>
          )}

          {comparing && <Spin tip="对比中..." />}

          {!comparing && diffData.length === 0 && schemeId1 && schemeId2 && schemeId1 !== schemeId2 && (
            <Typography.Text type="secondary">
              点击「开始对比」查看差异结果
            </Typography.Text>
          )}
        </Space>
      </Panel>
    </div>
  );
};
export default SchemeCompare;
