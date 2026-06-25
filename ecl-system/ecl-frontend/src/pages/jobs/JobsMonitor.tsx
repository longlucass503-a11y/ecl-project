import React, { useEffect, useState } from 'react';
import { Button, Drawer, Empty, Table, Tag, message, Collapse } from 'antd';
import { ReloadOutlined, FileTextOutlined } from '@ant-design/icons';
import { PageHeader, Panel } from '../../components';
import { jobsApi, type EclJobVO, type EclJobDetailVO } from '../../api/jobs';
import './JobsMonitor.css';

const statusColor: Record<string, string> = {
  SUCCESS: 'green',
  PROCESSING: 'blue',
  FAILED: 'red',
};

/** Parse request payload JSON into typed source tables */
function parseRequestPayload(json?: string): Record<string, unknown> | null {
  if (!json) return null;
  try { return JSON.parse(json); } catch { return null; }
}

/** Render a key-value table from a flat object */
function SourceTable({ data, title }: { data: unknown[] | undefined; title: string }) {
  if (!data || data.length === 0) return null;
  const rows = data as Record<string, unknown>[];
  const keys = Object.keys(rows[0] || {}).filter((k) => !k.startsWith('_'));
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8, color: 'var(--color-text)' }}>{title}（{rows.length} 行）</div>
      <div style={{ overflowX: 'auto' }}>
        <table className="ecl-table" style={{ fontSize: 11 }}>
          <thead>
            <tr>{keys.map((k) => <th key={k}>{k}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
              <tr key={i}>{keys.map((k) => <td key={k}>{String(row[k] ?? '-')}</td>)}</tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** Parse error JSON string into a readable object */
function parseError(json?: string): Record<string, string> | null {
  if (!json || json === '{}') return null;
  try { return JSON.parse(json); } catch { return null; }
}

const JobsMonitor: React.FC = () => {
  const [jobs, setJobs] = useState<EclJobVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<EclJobVO | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const loadJobs = async () => {
    setLoading(true);
    try {
      const res = await jobsApi.list();
      setJobs((res.data as any)?.data || res.data || []);
    } catch {
      message.error('任务列表加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadJobs(); }, []);

  const openDetail = async (jobId: string) => {
    try {
      const res = await jobsApi.getById(jobId);
      setDetail((res.data as any)?.data || res.data);
      setDetailOpen(true);
    } catch {
      message.error('任务详情加载失败');
    }
  };

  const columns = [
    { title: '任务 ID', dataIndex: 'jobId', key: 'jobId', width: 220, render: (v: string) => <span className="ecl-mono">{v.slice(0, 12)}…</span> },
    { title: '方案', dataIndex: 'schemeId', key: 'schemeId', width: 200, render: (v: string) => <span className="ecl-mono">{v}</span> },
    { title: '计量日', dataIndex: 'calcDate', key: 'calcDate', width: 120 },
    {
      title: '模式', dataIndex: 'trialMode', key: 'trialMode', width: 80,
      render: (v: boolean) => <Tag color={v ? 'purple' : 'default'}>{v ? '试算' : '正式'}</Tag>,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (v: string) => <Tag color={statusColor[v] || 'default'}>{v}</Tag>,
    },
    {
      title: '操作', key: 'action', width: 120,
      render: (_: unknown, record: EclJobVO) => (
        <Button type="link" icon={<FileTextOutlined />} onClick={() => openDetail(record.jobId)}>明细</Button>
      ),
    },
  ];

  return (
    <div className="ecl-page">
      <PageHeader
        title="跑批监控"
        subtitle="查看 ECL 试算任务的输入数据、计算明细与输出结果"
        extra={<Button icon={<ReloadOutlined />} onClick={loadJobs} loading={loading}>刷新</Button>}
      />

      <Panel title="任务列表">
        <Table
          columns={columns}
          dataSource={jobs}
          rowKey="jobId"
          loading={loading}
          locale={{ emptyText: <Empty description="暂无计算任务" /> }}
          pagination={{ pageSize: 8, showTotal: (total) => `共 ${total} 个任务` }}
        />
      </Panel>

      <Drawer
        title={detail ? `任务详情 · ${detail.jobId}` : '任务详情'}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={1100}
      >
        {detail && <JobDetailView job={detail} />}
      </Drawer>
    </div>
  );
};

/** ── Helpers ── */
function safeJson<T = unknown>(json?: string | null): T | null {
  if (!json || json === '{}') return null;
  try { return JSON.parse(json); } catch { return null; }
}

/** Show a small metric label:value pair */
const Metric: React.FC<{ label: string; value: React.ReactNode; ok?: boolean }> = ({ label, value, ok }) => (
  <div style={{ fontSize: 12, padding: '2px 0' }}>
    <span style={{ color: 'var(--color-text-secondary)' }}>{label}：</span>
    <span style={{ color: ok === false ? 'var(--color-error)' : ok === true ? 'var(--color-success)' : 'var(--color-text)', fontWeight: 500 }}>{value ?? '-'}</span>
  </div>
);

/** Render JSON array of scenario results as mini table */
function ScenarioTable({ json, columns }: { json?: string | null; columns: string[] }) {
  const rows = safeJson<Record<string, unknown>[]>(json);
  if (!rows || rows.length === 0) return <span style={{ color: 'var(--color-text-muted)', fontSize: 11 }}>—</span>;
  return (
    <table style={{ fontSize: 11, borderCollapse: 'collapse', width: '100%', marginTop: 4 }}>
      <thead><tr>{columns.map((c) => <th key={c} style={{ borderBottom: '1px solid var(--color-border)', padding: '2px 6px', textAlign: 'left' }}>{c}</th>)}</tr></thead>
      <tbody>
        {rows.map((row, i) => (
          <tr key={i}>{columns.map((c) => <td key={c} style={{ padding: '2px 6px' }}>{String(row[c] ?? '-')}</td>)}</tr>
        ))}
      </tbody>
    </table>
  );
}

/** ── Job Detail View ── */
const JobDetailView: React.FC<{ job: EclJobVO }> = ({ job }) => {
  const payload = parseRequestPayload(job.requestPayload);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* ── 1. Input ── */}
      <Panel title="📥 输入数据">
        {payload ? (
          <Collapse
            size="small"
            items={[
              payload.loans && { key: 'loans', label: `借据信息表（${(payload.loans as unknown[]).length} 行）`, children: <SourceTable data={payload.loans as unknown[]} title="" /> },
              payload.facilities && { key: 'facilities', label: `授信额度表（${(payload.facilities as unknown[]).length} 行）`, children: <SourceTable data={payload.facilities as unknown[]} title="" /> },
              payload.repaymentSchedules && { key: 'repayments', label: `还款计划表（${(payload.repaymentSchedules as unknown[]).length} 行）`, children: <SourceTable data={payload.repaymentSchedules as unknown[]} title="" /> },
              payload.collaterals && { key: 'collaterals', label: `抵质押品表（${(payload.collaterals as unknown[]).length} 行）`, children: <SourceTable data={payload.collaterals as unknown[]} title="" /> },
              payload.ratings && { key: 'ratings', label: `评级信息表（${(payload.ratings as unknown[]).length} 行）`, children: <SourceTable data={payload.ratings as unknown[]} title="" /> },
              payload.historicalStages && { key: 'stages', label: `历史阶段表（${(payload.historicalStages as unknown[]).length} 行）`, children: <SourceTable data={payload.historicalStages as unknown[]} title="" /> },
            ].filter((x): x is NonNullable<typeof x> => !!x).map((item, i) => ({ ...item, key: String(i) }))}
          />
        ) : (
          <Empty description="无输入数据记录" />
        )}
      </Panel>

      {/* ── 2. Engine Details — per asset ── */}
      <Panel title="📊 引擎计算明细">
        {job.details && job.details.length > 0 ? (
          <Collapse
            size="small"
            items={job.details.map((d, idx) => ({
              key: String(idx),
              label: (
                <span>
                  <span className="ecl-mono" style={{ fontWeight: 600 }}>{d.assetId}</span>
                  <Tag color={d.calcStatus === 'SUCCESS' ? 'green' : 'orange'} style={{ marginLeft: 8 }}>{d.calcStatus}</Tag>
                  <span style={{ marginLeft: 8, fontSize: 12, color: 'var(--color-text-secondary)' }}>ECL 最终: {d.eclFinal != null ? d.eclFinal.toFixed(2) : '-'}</span>
                </span>
              ),
              children: (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px 24px' }}>

                  {/* ── 6.1 风险分组 ── */}
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4, color: 'var(--color-primary)' }}>① 风险分组</div>
                    <Metric label="匹配分组" value={d.groupId || 'GRP_DEFAULT'} ok={!d.groupException} />
                    {d.groupException && <Metric label="异常" value={d.groupException} ok={false} />}
                  </div>

                  {/* ── 6.2 阶段划分 ── */}
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4, color: 'var(--color-primary)' }}>② 阶段判定</div>
                    <Metric label="判定阶段" value={d.stageResult} ok={!d.stageException} />
                    <Metric label="触发规则" value={d.triggerType || '—'} />
                    {d.stageException && <Metric label="异常" value={d.stageException} ok={false} />}
                  </div>

                  {/* ── 6.3 PD ── */}
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4, color: 'var(--color-primary)' }}>③ PD 取值</div>
                    {d.pdException && <Metric label="异常" value={d.pdException} ok={false} />}
                    <ScenarioTable json={d.pdDetails} columns={['scenarioType', 'scenarioName', 'weight', 'pdValue']} />
                  </div>

                  {/* ── 6.4 EAD ── */}
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4, color: 'var(--color-primary)' }}>④ EAD 计算</div>
                    <Metric label="EAD 总额" value={d.eadTotal != null ? d.eadTotal.toFixed(2) : '-'} />
                    {d.eadBreakdown && <Metric label="明细" value={<pre style={{ margin: 0, fontSize: 11, whiteSpace: 'pre-wrap' }}>{safeJson(d.eadBreakdown) ? JSON.stringify(safeJson(d.eadBreakdown), null, 1) : d.eadBreakdown}</pre>} />}
                    {d.eadException && <Metric label="异常" value={d.eadException} ok={false} />}
                  </div>

                  {/* ── 6.5 LGD ── */}
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4, color: 'var(--color-primary)' }}>⑤ LGD 计算</div>
                    <Metric label="LGD" value={d.lgdValue != null ? (d.lgdValue * 100).toFixed(2) + '%' : '-'} />
                    {d.lgdDetails && <Metric label="明细" value={<pre style={{ margin: 0, fontSize: 11, whiteSpace: 'pre-wrap' }}>{safeJson(d.lgdDetails) ? JSON.stringify(safeJson(d.lgdDetails), null, 1) : d.lgdDetails}</pre>} />}
                    {d.lgdException && <Metric label="异常" value={d.lgdException} ok={false} />}
                  </div>

                  {/* ── 6.6~6.7 ECL + Overlay ── */}
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4, color: 'var(--color-primary)' }}>⑥⑦ ECL · Overlay</div>
                    <Metric label="ECL 加权" value={d.eclWeighted != null ? d.eclWeighted.toFixed(2) : '-'} />
                    <Metric label="叠加调整" value={d.eclOverlayTotal != null ? d.eclOverlayTotal.toFixed(2) : '-'} />
                    <Metric label="ECL 最终" value={<strong>{d.eclFinal != null ? d.eclFinal.toFixed(2) : '-'}</strong>} />
                    {d.selectedOverlayId && <Metric label="命中叠加规则" value={d.selectedOverlayId} />}
                    {d.eclDetails && <ScenarioTable json={d.eclDetails} columns={['scenarioType', 'weight', 'scenarioEcl', 'weightedEcl']} />}
                  </div>

                  {/* ── Errors ── */}
                  {d.errorSummary && d.errorSummary !== '{}' && (
                    <div style={{ gridColumn: '1 / -1' }}>
                      <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4, color: 'var(--color-error)' }}>⚠ 异常摘要</div>
                      <pre style={{ fontSize: 11, whiteSpace: 'pre-wrap', margin: 0, color: 'var(--color-error)' }}>
                        {JSON.stringify(JSON.parse(d.errorSummary), null, 1)}
                      </pre>
                    </div>
                  )}
                </div>
              ),
            }))}
          />
        ) : (
          <Empty description="暂无计算明细" />
        )}
      </Panel>
    </div>
  );
};

export default JobsMonitor;
