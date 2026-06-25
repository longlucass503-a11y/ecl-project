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
  Row,
  Col,
  Progress,
  Typography,
  Empty,
  Tooltip,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import { useSearchParams, useOutletContext } from 'react-router-dom';
import ReactEChartsCore from 'echarts-for-react';
import { schemeApi, type SchemeVO } from '../../api/scheme';
import { pdApi, type ScenarioVO, type PdCurveVO } from '../../api/pd';
import { GroupSelector, PageHeader, Panel } from '../../components';
import { riskGroupApi, type RiskGroupVO } from '../../api/riskGroup';

const RATING_AGENCY_OPTIONS = [
  { label: '内部评级 (INTERNAL_CRR)', value: 'INTERNAL_CRR' },
  { label: '穆迪 (MOODY)', value: 'MOODY' },
  { label: '标普 (S&P)', value: 'S&P' },
  { label: '惠誉 (FITCH)', value: 'FITCH' },
];

const RATING_SCALES: Record<string, string[]> = {
  INTERNAL_CRR: [
    'CRR1', 'CRR2', 'CRR3', 'CRR4', 'CRR5',
    'CRR6', 'CRR7', 'CRR8', 'CRR9', 'CRR10',
    'CRR11', 'CRR12', 'CRR13', 'CRR14',
  ],
  MOODY: [
    'Aaa', 'Aa1', 'Aa2', 'Aa3',
    'A1', 'A2', 'A3',
    'Baa1', 'Baa2', 'Baa3',
    'Ba1', 'Ba2', 'Ba3',
    'B1', 'B2', 'B3',
    'Caa1', 'Caa2', 'Caa3',
    'Ca', 'C',
  ],
  'S&P': [
    'AAA', 'AA+', 'AA', 'AA-',
    'A+', 'A', 'A-',
    'BBB+', 'BBB', 'BBB-',
    'BB+', 'BB', 'BB-',
    'B+', 'B', 'B-',
    'CCC+', 'CCC', 'CCC-',
    'CC', 'C', 'D',
  ],
  FITCH: [
    'AAA', 'AA+', 'AA', 'AA-',
    'A+', 'A', 'A-',
    'BBB+', 'BBB', 'BBB-',
    'BB+', 'BB', 'BB-',
    'B+', 'B', 'B-',
    'CCC', 'CC', 'C',
    'RD', 'D',
  ],
};

function getRatingOptions(agency: string | undefined): { label: string; value: string }[] {
  const list = RATING_SCALES[agency || 'INTERNAL_CRR'] || RATING_SCALES.INTERNAL_CRR;
  return list.map((r) => ({ label: r, value: r }));
}

const PdConfig: React.FC = () => {
  const [searchParams] = useSearchParams();
  const schemeIdFromUrl = searchParams.get('schemeId') || '';
  const { schemeContext } = useOutletContext<{ schemeContext?: { schemeId: string } }>();
  const effectiveSchemeId = schemeIdFromUrl || schemeContext?.schemeId || '';

  // ─── 方案 ───
  const [schemes, setSchemes] = useState<SchemeVO[]>([]);
  const [selectedSchemeId, setSelectedSchemeId] = useState<string>(effectiveSchemeId);

  // ─── 风险分组 ───
  const [groups, setGroups] = useState<RiskGroupVO[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<string>('');

  // ─── 情景 ───
  const [scenarios, setScenarios] = useState<ScenarioVO[]>([]);
  const [scenarioLoading, setScenarioLoading] = useState(false);
  const [scenarioModalOpen, setScenarioModalOpen] = useState(false);
  const [editingScenario, setEditingScenario] = useState<ScenarioVO | null>(null);
  const [scenarioForm] = Form.useForm();

  // ─── 选中情景 → 曲线 ───
  const [selectedScenarioId, setSelectedScenarioId] = useState<string | null>(null);
  const [curves, setCurves] = useState<PdCurveVO[]>([]);
  const [curvesLoading, setCurvesLoading] = useState(false);
  const [curveModalOpen, setCurveModalOpen] = useState(false);
  const [editingCurve, setEditingCurve] = useState<PdCurveVO | null>(null);
  const [curveForm] = Form.useForm();
  const [curveFormAgency, setCurveFormAgency] = useState('INTERNAL_CRR');

  // ─── 矩阵视图 ───
  const [matrixModalOpen, setMatrixModalOpen] = useState(false);
  const [matrixData, setMatrixData] = useState<{
    ratingCodes: string[];
    scenarioNames: string[];
    values: number[][];
  }>({ ratingCodes: [], scenarioNames: [], values: [] });
  const [matrixLoading, setMatrixLoading] = useState(false);

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

  // 加载情景列表
  const loadScenarios = useCallback(async () => {
    if (!selectedSchemeId) {
      setScenarios([]);
      return;
    }
    setScenarioLoading(true);
    try {
      const res = await pdApi.listScenarios(selectedSchemeId);
      setScenarios((res.data as any)?.data || res.data || []);
    } finally {
      setScenarioLoading(false);
    }
  }, [selectedSchemeId]);

  useEffect(() => {
    loadScenarios();
  }, [loadScenarios]);

  // 选中情景 → 加载曲线
  const loadCurves = useCallback(async (scenarioId: string) => {
    if (!selectedSchemeId || !selectedGroupId) {
      setCurves([]);
      return;
    }
    setCurvesLoading(true);
    try {
      const res = await pdApi.listCurves(selectedSchemeId, selectedGroupId, scenarioId);
      setCurves((res.data as any)?.data || res.data || []);
    } finally {
      setCurvesLoading(false);
    }
  }, [selectedSchemeId, selectedGroupId]);

  useEffect(() => {
    if (selectedScenarioId) {
      loadCurves(selectedScenarioId);
    } else {
      setCurves([]);
    }
  }, [selectedScenarioId, loadCurves]);

  // 方案切换
  const handleSchemeChange = (v: string) => {
    setSelectedSchemeId(v);
    setSelectedScenarioId(null);
    setCurves([]);
  };

  // ─── 情景 CRUD ───
  const handleSaveScenario = async () => {
    const values = await scenarioForm.validateFields();

    // 校验权重总和 ≤ 1.0
    const otherWeight = scenarios
      .filter((s) => s.scenarioId !== editingScenario?.scenarioId)
      .reduce((sum, s) => sum + s.weight, 0);
    if (otherWeight + values.weight > 1.0) {
      message.error('同一方案下所有情景权重总和不超过 1.0');
      return;
    }

    if (editingScenario) {
      await pdApi.updateScenario(editingScenario.scenarioId, { ...values, schemeId: selectedSchemeId });
      message.success('情景更新成功');
    } else {
      await pdApi.createScenario({ ...values, schemeId: selectedSchemeId });
      message.success('情景创建成功');
    }
    setScenarioModalOpen(false);
    setEditingScenario(null);
    scenarioForm.resetFields();
    loadScenarios();
  };

  const handleDeleteScenario = (scenarioId: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '删除该情景后，其下的所有 PD 曲线也将被删除',
      onOk: async () => {
        await pdApi.deleteScenario(scenarioId);
        message.success('已删除');
        if (selectedScenarioId === scenarioId) {
          setSelectedScenarioId(null);
          setCurves([]);
        }
        loadScenarios();
      },
    });
  };

  // ─── 曲线 CRUD ───
  const handleSaveCurve = async () => {
    const values = await curveForm.validateFields();
    if (!selectedScenarioId || !selectedGroupId) {
      message.warning('请先选择风险分组');
      return;
    }

    if (editingCurve) {
      // 内联更新：构造新列表
      const updated = curves.map((c) =>
        c.curveId === editingCurve.curveId ? { ...c, ...values } : c,
      );
      await pdApi.batchUpdateCurves(
        selectedSchemeId,
        selectedGroupId,
        selectedScenarioId,
        updated.map((c) => ({ ratingCode: c.ratingCode, pdValue: c.pdValue, ratingAgency: c.ratingAgency })),
      );
      message.success('曲线更新成功');
    } else {
      const newCurves = [...curves, { ...values, scenarioId: selectedScenarioId, curveId: `tmp_${Date.now()}` }];
      await pdApi.batchUpdateCurves(
        selectedSchemeId,
        selectedGroupId,
        selectedScenarioId,
        newCurves.map((c) => ({ ratingCode: c.ratingCode, pdValue: c.pdValue, ratingAgency: c.ratingAgency })),
      );
      message.success('曲线新增成功');
    }
    setCurveModalOpen(false);
    setEditingCurve(null);
    setCurveFormAgency('INTERNAL_CRR');
    curveForm.resetFields();
    if (selectedScenarioId) loadCurves(selectedScenarioId);
  };

  const handleDeleteCurve = (record: PdCurveVO) => {
    const newCurves = curves.filter((c) => c.curveId !== record.curveId);
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这条 PD 曲线数据吗？',
      onOk: async () => {
        if (!selectedScenarioId || !selectedGroupId) {
          message.warning('请先选择风险分组');
          return;
        }
        await pdApi.batchUpdateCurves(
          selectedSchemeId,
          selectedGroupId,
          selectedScenarioId,
          newCurves.map((c) => ({ ratingCode: c.ratingCode, pdValue: c.pdValue, ratingAgency: c.ratingAgency })),
        );
        message.success('已删除');
        loadCurves(selectedScenarioId);
      },
    });
  };

  // ─── 批量保存曲线 ───
  const handleBatchSaveCurves = async () => {
    if (!selectedScenarioId || !selectedGroupId) {
      message.warning('请先选择风险分组');
      return;
    }
    try {
      await pdApi.batchUpdateCurves(
        selectedSchemeId,
        selectedGroupId,
        selectedScenarioId,
        curves.map((c) => ({ ratingCode: c.ratingCode, pdValue: c.pdValue, ratingAgency: c.ratingAgency })),
      );
      message.success('批量保存成功');
    } catch {
      message.error('批量保存失败');
    }
  };

  // ─── 矩阵视图 ───
  const handleOpenMatrix = async () => {
    if (!selectedSchemeId) return;
    if (!selectedGroupId) {
      message.warning('请先选择风险分组');
      return;
    }
    setMatrixModalOpen(true);
    setMatrixLoading(true);
    try {
      const res = await pdApi.getMatrix(selectedSchemeId, selectedGroupId);
      const m = (res.data as any)?.data || res.data;
      const ratingCodes = m.ratingCodes || [];
      const scenarioNames = (m.scenarios || []).map((scenario: ScenarioVO) => scenario.scenarioName || scenario.scenarioType);
      const values = m.matrix || [];
      setMatrixData({ ratingCodes, scenarioNames, values });
    } catch {
      message.error('加载矩阵数据失败');
    } finally {
      setMatrixLoading(false);
    }
  };

  // ─── 列定义（曲线表格） ───
  const curveColumns = [
    {
      title: '评级代码',
      dataIndex: 'ratingCode',
      key: 'ratingCode',
      width: 200,
    },
    {
      title: 'PD 值',
      dataIndex: 'pdValue',
      key: 'pdValue',
      width: 200,
      render: (v: number) => (v != null ? (v * 100).toFixed(4) + '%' : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_: any, record: PdCurveVO) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingCurve(record);
              curveForm.setFieldsValue(record);
              setCurveFormAgency(record.ratingAgency || 'INTERNAL_CRR');
              setCurveModalOpen(true);
            }}
          />
          <Button
            type="link"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDeleteCurve(record)}
          />
        </Space>
      ),
    },
  ];

  // ─── ECharts 热力图 option ───
  const heatmapOption = {
    tooltip: {
      position: 'top',
      formatter: (params: any) => {
        const { value } = params;
        return `评级: ${matrixData.ratingCodes[value[1]]}<br/>情景: ${matrixData.scenarioNames[value[0]]}<br/>PD: ${(value[2] * 100).toFixed(4)}%`;
      },
    },
    grid: { left: 120, right: 40, top: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      data: matrixData.scenarioNames,
      axisLabel: { rotate: 15 },
    },
    yAxis: {
      type: 'category',
      data: matrixData.ratingCodes,
    },
    visualMap: {
      min: 0,
      max: Math.max(0.01, ...matrixData.values.flat()),
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: {
        color: ['#f7fbff', '#c6dbef', '#6baed6', '#2171b5', '#08306b'],
      },
    },
    series: [
      {
        type: 'heatmap',
        data: matrixData.values.flatMap((row, i) =>
          row.map((v, j) => [j, i, v]),
        ),
        label: {
          show: true,
          formatter: (params: any) => (params.value[2] * 100).toFixed(2) + '%',
          fontSize: 11,
        },
        emphasis: {
          itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' },
        },
      },
    ],
  };

  // ─── 渲染 ───
  if (!selectedSchemeId) {
    return (
      <div className="ecl-page">
        <PageHeader
          title="PD 参数配置"
          subtitle="按风险分组管理 PD 曲线，每个分组可独立配置情景"
        />
        <Panel>
          <Empty description="请先选择一个 ECL 方案">
            <Select
              style={{ width: 300, marginTop: 16 }}
              placeholder="请选择 ECL 方案"
              value={selectedSchemeId || undefined}
              onChange={handleSchemeChange}
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

  // 计算剩余权重
  const totalWeight = scenarios.reduce((s, c) => s + c.weight, 0);
  const remainingWeight = Math.max(0, +(1 - totalWeight).toFixed(4));
  const groupSelectorItems = groups.map((g) => ({
    groupId: g.groupId,
    groupName: g.groupName,
    groupCode: g.groupCode,
  }));

  return (
    <div className="ecl-page">
      <PageHeader
        title="PD 参数配置"
        subtitle="按风险分组管理 PD 曲线，每个分组可独立配置情景"
        extra={
          <Space>
            <Select
              style={{ width: 300 }}
              placeholder="请选择 ECL 方案"
              value={selectedSchemeId || undefined}
              onChange={handleSchemeChange}
              options={schemes.map((s) => ({
                label: `${s.schemeName}(${s.schemeCode})`,
                value: s.schemeId,
              }))}
            />
            <Button icon={<BarChartOutlined />} onClick={handleOpenMatrix}>
              矩阵视图
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditingScenario(null);
                scenarioForm.resetFields();
                setScenarioModalOpen(true);
              }}
            >
              新增情景
            </Button>
          </Space>
        }
      />

      {groups.length > 0 && (
        <GroupSelector
          groups={groupSelectorItems}
          selectedId={selectedGroupId}
          onChange={setSelectedGroupId}
        />
      )}

      {/* 情景列表 */}
      <Panel title="情景管理">
        <div style={{ marginBottom: 12 }}>
          <Typography.Text type="secondary">
            权重合计：{(totalWeight * 100).toFixed(2)}% &nbsp;
            {remainingWeight > 0 && (
              <span style={{ color: '#52c41a' }}>
                （剩余 {((1 - totalWeight) * 100).toFixed(2)}% 可用）
              </span>
            )}
            {totalWeight > 1 && (
              <span style={{ color: '#ff4d4f' }}>（已超过 100%，请调整）</span>
            )}
          </Typography.Text>
        </div>
        {scenarios.length === 0 ? (
          <Empty description="暂无情景，请点击「新增情景」创建" />
        ) : (
          <Row gutter={[16, 16]}>
            {scenarios.map((scenario) => (
              <Col key={scenario.scenarioId} xs={24} sm={12} md={8} lg={6}>
                <div
                  className="scenario-card"
                  style={{
                    border: `1px solid ${selectedScenarioId === scenario.scenarioId ? 'var(--color-primary)' : 'var(--color-border)'}`,
                    borderRadius: 8,
                    padding: 16,
                    cursor: 'pointer',
                    background: selectedScenarioId === scenario.scenarioId ? 'var(--color-primary-light)' : '#fff',
                    transition: 'all 0.15s',
                  }}
                  onClick={() => setSelectedScenarioId(scenario.scenarioId)}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <Space>
                      <Typography.Text strong>{scenario.scenarioName}</Typography.Text>
                      <Tag>{scenario.scenarioType}</Tag>
                    </Space>
                    <Space size={0}>
                      <Tooltip title="编辑权重">
                        <Button
                          type="text"
                          size="small"
                          icon={<EditOutlined />}
                          onClick={(e) => {
                            e.stopPropagation();
                            setEditingScenario(scenario);
                            scenarioForm.setFieldsValue(scenario);
                            setScenarioModalOpen(true);
                          }}
                        />
                      </Tooltip>
                      <Tooltip title="删除情景">
                        <Button
                          type="text"
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDeleteScenario(scenario.scenarioId);
                          }}
                        />
                      </Tooltip>
                    </Space>
                  </div>
                  <div style={{ marginTop: 12 }}>
                    <div style={{ marginBottom: 4, fontSize: 13, color: 'var(--color-text-secondary)' }}>
                      权重：{(scenario.weight * 100).toFixed(2)}%
                    </div>
                    <Progress
                      percent={+(scenario.weight * 100).toFixed(2)}
                      size="small"
                      strokeColor={
                        scenario.weight > 0.5
                          ? '#ff4d4f'
                          : scenario.weight > 0.3
                            ? '#faad14'
                            : '#52c41a'
                      }
                    />
                  </div>
                </div>
              </Col>
            ))}
          </Row>
        )}
      </Panel>

      {/* 曲线编辑 */}
      {selectedScenarioId && !selectedGroupId && (
        <Panel>
          <div className="ecl-empty-row">请选择一个风险分组以维护该情景下的 PD 曲线</div>
        </Panel>
      )}

      {selectedScenarioId && selectedGroupId && (
        <Panel
          title={
            <span>
              曲线编辑 —{' '}
              {scenarios.find((s) => s.scenarioId === selectedScenarioId)?.scenarioName ||
                selectedScenarioId}
            </span>
          }
          extra={
            <Space>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setEditingCurve(null);
                  curveForm.resetFields();
                  setCurveFormAgency('INTERNAL_CRR');
                  setCurveModalOpen(true);
                }}
              >
                新增行
              </Button>
              <Button onClick={handleBatchSaveCurves}>批量保存</Button>
            </Space>
          }
        >
          <table className="ecl-table">
            <thead>
              <tr>
                <th>评级机构/来源</th>
                <th>评级代码</th>
                <th>PD 值</th>
                <th style={{ width: 160 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {curves.map((c) => (
                <tr key={c.curveId}>
                  <td>{c.ratingAgency || <span className="wildcard">*</span>}</td>
                  <td>{c.ratingCode}</td>
                  <td>{(c.pdValue * 100).toFixed(4)}%</td>
                  <td>
                    <Space>
                      <Button type="link" size="small" icon={<EditOutlined />}
                        onClick={() => { setEditingCurve(c); curveForm.setFieldsValue(c); setCurveFormAgency(c.ratingAgency || 'INTERNAL_CRR'); setCurveModalOpen(true); }} />
                      <Button type="link" size="small" danger icon={<DeleteOutlined />}
                        onClick={() => handleDeleteCurve(c)} />
                    </Space>
                  </td>
                </tr>
              ))}
              {curves.length === 0 && (
                <tr><td colSpan={5}><div className="ecl-empty-row">暂无曲线数据</div></td></tr>
              )}
            </tbody>
          </table>
        </Panel>
      )}

      {/* ─── 情景弹窗 ─── */}
      <Modal
        title={editingScenario ? '编辑情景' : '新增情景'}
        open={scenarioModalOpen}
        onOk={handleSaveScenario}
        onCancel={() => {
          setScenarioModalOpen(false);
          scenarioForm.resetFields();
        }}
      >
        <Form form={scenarioForm} layout="vertical">
          <Form.Item
            name="scenarioType"
            label="情景类型"
            rules={[{ required: true, message: '请输入情景类型' }]}
          >
            <Input placeholder="如：BASE, UP, DOWN" />
          </Form.Item>
          <Form.Item
            name="scenarioName"
            label="情景名称"
            rules={[{ required: true, message: '请输入情景名称' }]}
          >
            <Input placeholder="如：基准情景" />
          </Form.Item>
          <Form.Item
            name="weight"
            label="权重"
            rules={[
              { required: true, message: '请输入权重' },
              {
                type: 'number',
                min: 0,
                max: 1,
                message: '权重范围 0 ~ 1',
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
            当前已分配权重：{(totalWeight * 100).toFixed(2)}% ，剩余可用：
            {(remainingWeight * 100).toFixed(2)}%
          </Typography.Text>
        </Form>
      </Modal>

      {/* ─── 曲线弹窗 ─── */}
      <Modal
        title={editingCurve ? '编辑 PD 曲线' : '新增 PD 曲线'}
        open={curveModalOpen}
        onOk={handleSaveCurve}
        onCancel={() => {
          setCurveModalOpen(false);
          setCurveFormAgency('INTERNAL_CRR');
          curveForm.resetFields();
        }}
      >
        <Form form={curveForm} layout="vertical">
          <Form.Item
            name="ratingAgency"
            label="评级机构/来源"
          >
            <Select
              placeholder="选择评级机构"
              options={RATING_AGENCY_OPTIONS}
              onChange={(val) => {
                setCurveFormAgency(val);
                curveForm.setFieldValue('ratingCode', undefined);
              }}
            />
          </Form.Item>
          <Form.Item
            name="ratingCode"
            label="评级代码"
            rules={[{ required: true, message: '请选择评级代码' }]}
          >
            <Select
              placeholder="选择评级"
              options={getRatingOptions(curveFormAgency)}
              showSearch
            />
          </Form.Item>
          <Form.Item
            name="pdValue"
            label="PD 值"
            rules={[
              { required: true, message: '请输入 PD 值' },
              {
                type: 'number',
                min: 0,
                max: 1,
                message: 'PD 值范围 0 ~ 1',
              },
            ]}
          >
            <InputNumber
              min={0}
              max={1}
              step={0.0001}
              style={{ width: '100%' }}
              placeholder="0 ~ 1 之间的小数，如 0.0012"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* ─── 矩阵视图 Modal ─── */}
      <Modal
        title="PD 矩阵视图"
        open={matrixModalOpen}
        onCancel={() => setMatrixModalOpen(false)}
        footer={null}
        width={900}
      >
        {matrixLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Typography.Text>加载中...</Typography.Text>
          </div>
        ) : matrixData.ratingCodes.length === 0 ? (
          <Empty description="暂无矩阵数据" />
        ) : (
          <ReactEChartsCore option={heatmapOption} style={{ height: 500 }} />
        )}
      </Modal>
    </div>
  );
};

export default PdConfig;
