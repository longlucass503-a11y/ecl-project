import React, { useEffect, useMemo, useState } from 'react';
import { Button, Drawer, Empty, Space, Table, Tag, message } from 'antd';
import { ReloadOutlined, FileTextOutlined } from '@ant-design/icons';
import { PageHeader, Panel } from '../../components';
import { jobsApi, type EclJobVO } from '../../api/jobs';
import './JobsMonitor.css';

const statusColor: Record<string, string> = {
  SUCCESS: 'green',
  PROCESSING: 'blue',
  FAILED: 'red',
};

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
    } catch (err) {
      console.error(err);
      message.error('任务列表加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadJobs();
  }, []);

  const stats = useMemo(() => {
    const total = jobs.length;
    const success = jobs.filter((j) => j.status === 'SUCCESS').length;
    const failed = jobs.filter((j) => j.status === 'FAILED').length;
    const trial = jobs.filter((j) => j.trialMode).length;
    return { total, success, failed, trial };
  }, [jobs]);

  const openDetail = async (jobId: string) => {
    try {
      const res = await jobsApi.getById(jobId);
      setDetail((res.data as any)?.data || res.data);
      setDetailOpen(true);
    } catch (err) {
      console.error(err);
      message.error('任务详情加载失败');
    }
  };

  const columns = [
    { title: '任务 ID', dataIndex: 'jobId', key: 'jobId', render: (v: string) => <span className="ecl-mono">{v}</span> },
    { title: '方案', dataIndex: 'schemeId', key: 'schemeId', render: (v: string) => <span className="ecl-mono">{v}</span> },
    { title: '计量日', dataIndex: 'calcDate', key: 'calcDate', width: 120 },
    {
      title: '模式', dataIndex: 'trialMode', key: 'trialMode', width: 90,
      render: (v: boolean) => <Tag color={v ? 'purple' : 'default'}>{v ? '试算' : '正式'}</Tag>,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 110,
      render: (v: string) => <Tag color={statusColor[v] || 'default'}>{v}</Tag>,
    },
    { title: '总笔数', dataIndex: 'totalAssets', key: 'totalAssets', width: 100 },
    { title: '成功', dataIndex: 'successCount', key: 'successCount', width: 100 },
    { title: '异常', dataIndex: 'exceptionCount', key: 'exceptionCount', width: 100 },
    { title: '耗时', dataIndex: 'durationMs', key: 'durationMs', width: 100, render: (v: number) => `${v || 0} ms` },
    {
      title: '操作', key: 'action', width: 120,
      render: (_: unknown, record: EclJobVO) => (
        <Button type="link" icon={<FileTextOutlined />} onClick={() => openDetail(record.jobId)}>
          查看明细
        </Button>
      ),
    },
  ];

  return (
    <div className="ecl-page">
      <PageHeader
        title="跑批监控"
        subtitle="查看 ECL 计算任务状态、耗时、异常与逐笔明细"
        extra={<Button icon={<ReloadOutlined />} onClick={loadJobs} loading={loading}>刷新</Button>}
      />

      <div className="jobs-summary">
        <SummaryItem label="任务总数" value={stats.total} />
        <SummaryItem label="成功任务" value={stats.success} />
        <SummaryItem label="失败任务" value={stats.failed} />
        <SummaryItem label="试算任务" value={stats.trial} />
      </div>

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
        title={detail ? `任务详情 ${detail.jobId}` : '任务详情'}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={960}
      >
        {detail && <JobDetail job={detail} />}
      </Drawer>
    </div>
  );
};

const SummaryItem: React.FC<{ label: string; value: number }> = ({ label, value }) => (
  <div className="jobs-summary-item">
    <div className="jobs-summary-label">{label}</div>
    <div className="jobs-summary-value">{value}</div>
  </div>
);

const JobDetail: React.FC<{ job: EclJobVO }> = ({ job }) => (
  <div>
    <Panel title="任务概览">
      <div className="job-kv-grid">
        <Kv label="状态" value={job.status} />
        <Kv label="计量日" value={job.calcDate} />
        <Kv label="总耗时" value={`${job.durationMs || 0} ms`} />
        <Kv label="总笔数" value={job.totalAssets || 0} />
        <Kv label="成功" value={job.successCount || 0} />
        <Kv label="异常" value={job.exceptionCount || 0} />
      </div>
    </Panel>

    <div className="job-detail-grid">
      <Panel title="各步骤耗时">
        {job.steps?.map((step) => (
          <div className="job-step-row" key={step.name}>
            <span>{step.name}</span>
            <span className="job-step-bar"><span style={{ width: `${step.percent}%` }} /></span>
            <span>{step.durationMs} ms</span>
          </div>
        ))}
      </Panel>

      <Panel title="任务日志">
        <div className="job-log-list">
          {job.logs?.map((log, index) => (
            <div className="job-log-line" key={`${log.time}-${index}`}>
              <span>{log.time}</span>
              <span className="job-log-level">{log.level}</span>
              <span>{log.message}</span>
            </div>
          ))}
        </div>
      </Panel>
    </div>

    <Panel title="计算明细">
      <table className="ecl-table">
        <thead>
          <tr>
            <th>借据 ID</th>
            <th>分组</th>
            <th>阶段</th>
            <th>EAD</th>
            <th>LGD</th>
            <th>ECL 加权</th>
            <th>ECL 最终</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          {job.details?.map((d) => (
            <tr key={d.detailId}>
              <td><span className="ecl-mono">{d.assetId}</span></td>
              <td>{d.groupId || '-'}</td>
              <td>{d.stageResult || '-'}</td>
              <td>{d.eadTotal ?? '-'}</td>
              <td>{d.lgdValue ?? '-'}</td>
              <td>{d.eclWeighted ?? '-'}</td>
              <td>{d.eclFinal ?? '-'}</td>
              <td>{d.calcStatus || '-'}</td>
            </tr>
          ))}
          {(!job.details || job.details.length === 0) && (
            <tr><td colSpan={8}><div className="ecl-empty-row">暂无明细</div></td></tr>
          )}
        </tbody>
      </table>
    </Panel>
  </div>
);

const Kv: React.FC<{ label: string; value: React.ReactNode }> = ({ label, value }) => (
  <div className="job-kv">
    <div className="job-kv-label">{label}</div>
    <div className="job-kv-value">{value}</div>
  </div>
);

export default JobsMonitor;
