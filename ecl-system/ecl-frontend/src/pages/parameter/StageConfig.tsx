import React, { useState, useEffect, useCallback } from 'react';
import { Select, Button, Space, Modal, Form, Input, InputNumber, message } from 'antd';
import { CopyOutlined, PlusOutlined } from '@ant-design/icons';
import { useSearchParams, useOutletContext } from 'react-router-dom';
import { schemeApi, type SchemeVO } from '../../api/scheme';
import { riskGroupApi, type RiskGroupVO } from '../../api/riskGroup';
import {
  stageApi,
  type StageRuleVO,
  type RatingDowngradeRuleVO,
} from '../../api/stage';
import { PageHeader, Panel, GroupSelector } from '../../components';

/* ═══════════════════════════════════════════════════
   Condition types & helpers
   ═══════════════════════════════════════════════════ */

interface ConditionItem {
  type: string;
  operator: string;
  value?: string | number;
  values?: string[];
}

interface ConditionJSON {
  logic: 'OR' | 'AND';
  conditions: ConditionItem[];
}

const CONDITION_TYPE_OPTIONS = [
  '逾期天数',
  '五级分类',
  'CRR 评级下降',
  '违约标识',
  '还款状态',
  '逾期状态',
  '舆情事件',
];

/** Parse jsonCondition string → ConditionJSON, or return empty default */
function parseConditions(jsonStr?: string): ConditionItem[] {
  if (!jsonStr) return [];
  try {
    const parsed: ConditionJSON = JSON.parse(jsonStr);
    return parsed.conditions || [];
  } catch {
    return [];
  }
}

/** Serialize conditions → JSON string */
function serializeConditions(conditions: ConditionItem[], logic: 'OR' | 'AND'): string {
  return JSON.stringify({ logic, conditions });
}

/** Human-readable label for a single condition */
function conditionLabel(c: ConditionItem): string {
  switch (c.type) {
    case '逾期天数': {
      const op = c.operator === 'lte' ? '≤' : '≥';
      return `逾期 ${op} ${c.value} 天`;
    }
    case '五级分类': {
      const op = c.operator === 'not_in' ? '不属于' : '属于';
      return `五级分类 ${op} {${(c.values || []).join(', ')}}`;
    }
    case '违约标识':
      return `违约标识 = ${c.value}`;
    case 'CRR 评级下降':
      return `CRR 下降 ≥ ${c.value} 级`;
    case '还款状态':
      return `还款状态 = ${c.value}`;
    case '逾期状态':
      return `逾期状态 = ${c.value}`;
    case '舆情事件':
      return `舆情事件: ${c.value || c.operator}`;
    default:
      return `${c.type} ${c.operator} ${c.value || (c.values || []).join(',')}`;
  }
}

/** Get stage color tokens */
function stageColor(stage: string): { bg: string; border: string; text: string } {
  switch (stage) {
    case 'Stage 3':
    case 'STAGE_3':
      return { bg: '#dbeafe', border: '#93c5fd', text: '#1e40af' };
    case 'Stage 2':
    case 'STAGE_2':
      return { bg: '#fef3c7', border: '#fcd34d', text: '#92400e' };
    default:
      return { bg: '#f3f4f6', border: '#e5e7eb', text: '#6b7280' };
  }
}

/** Check if a forward rule is the default Stage 1 fallback */
function isDefaultRule(rule: StageRuleVO): boolean {
  return rule.ruleType === 'FORWARD' && rule.targetStage === 'STAGE_1' && rule.priority === 99;
}

/* ═══════════════════════════════════════════════════
   Component
   ═══════════════════════════════════════════════════ */

const StageConfig: React.FC = () => {
  const [searchParams] = useSearchParams();
  const schemeIdFromUrl = searchParams.get('schemeId') || '';
  const { schemeContext } = useOutletContext<{ schemeContext?: { schemeId: string } }>();
  const effectiveSchemeId = schemeIdFromUrl || schemeContext?.schemeId || '';

  // ─── Scheme & Group ───
  const [schemes, setSchemes] = useState<SchemeVO[]>([]);
  const [selectedSchemeId, setSelectedSchemeId] = useState<string>(effectiveSchemeId);
  const [groups, setGroups] = useState<RiskGroupVO[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<string>('');

  // ─── Tab ───
  const [activeTab, setActiveTab] = useState<'forward' | 'rollback' | 'crr'>('forward');

  // ─── Rules ───
  const [stageRules, setStageRules] = useState<StageRuleVO[]>([]);
  const [ratingRules, setRatingRules] = useState<RatingDowngradeRuleVO[]>([]);
  const [loading, setLoading] = useState(false);

  // ─── Rule modal (step 1 — basic info) ───
  const [ruleModalOpen, setRuleModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<StageRuleVO | null>(null);
  const [ruleForm] = Form.useForm();

  // ─── Condition editor modal (step 2) ───
  const [editorModalOpen, setEditorModalOpen] = useState(false);
  const [editorRuleId, setEditorRuleId] = useState<string | null>(null);
  const [editorTabType, setEditorTabType] = useState<'forward' | 'rollback'>('forward');
  const [editorConditions, setEditorConditions] = useState<ConditionItem[]>([]);

  // ─── Copy rules modal ───
  const [copyModalOpen, setCopyModalOpen] = useState(false);
  const [copySourceSchemeId, setCopySourceSchemeId] = useState<string>('');
  const [copySourceGroupId, setCopySourceGroupId] = useState<string>('');

  // ─── CRR batch ───
  const [crrBatchVal, setCrrBatchVal] = useState<number>(3);

  // ─── Load schemes ───
  useEffect(() => {
    schemeApi.list().then((res) => {
      setSchemes((res.data as any)?.data || res.data || []);
    });
  }, []);

  // ─── Load groups when scheme changes ───
  useEffect(() => {
    if (!selectedSchemeId) {
      setGroups([]);
      setSelectedGroupId('');
      setStageRules([]);
      setRatingRules([]);
      return;
    }
    riskGroupApi.listByScheme(selectedSchemeId).then((res) => {
      setGroups((res.data as any)?.data || res.data || []);
    });
    setSelectedGroupId('');
    setStageRules([]);
    setRatingRules([]);
  }, [selectedSchemeId]);

  // ─── Load rules when group changes ───
  const loadRules = useCallback(async () => {
    if (!selectedSchemeId || !selectedGroupId) {
      setStageRules([]);
      setRatingRules([]);
      return;
    }
    setLoading(true);
    try {
      const [stageRes, ratingRes] = await Promise.all([
        stageApi.getRulesByGroup(selectedSchemeId, selectedGroupId),
        stageApi.getRatingRulesByGroup(selectedSchemeId, selectedGroupId),
      ]);
      setStageRules((stageRes.data as any)?.data || stageRes.data || []);
      setRatingRules((ratingRes.data as any)?.data || ratingRes.data || []);
    } finally {
      setLoading(false);
    }
  }, [selectedSchemeId, selectedGroupId]);

  useEffect(() => { loadRules(); }, [loadRules]);

  const forwardRules = stageRules
    .filter((r) => r.ruleType === 'FORWARD')
    .sort((a, b) => a.priority - b.priority);
  const rollbackRules = stageRules
    .filter((r) => r.ruleType === 'ROLLBACK')
    .sort((a, b) => a.priority - b.priority);

  // ─── Rule CRUD ───
  const openRuleModal = (rule?: StageRuleVO) => {
    if (rule) {
      setEditingRule(rule);
      ruleForm.setFieldsValue({
        ruleType: rule.ruleType,
        priority: rule.priority,
        sourceStage: rule.sourceStage,
        targetStage: rule.targetStage,
        observationDays: rule.observationDays,
      });
    } else {
      setEditingRule(null);
      ruleForm.resetFields();
      ruleForm.setFieldsValue({
        ruleType: activeTab === 'crr' ? 'FORWARD' : activeTab.toUpperCase(),
        priority: 1,
        sourceStage: undefined,
        targetStage: 'STAGE_2',
        observationDays: undefined,
      });
    }
    setRuleModalOpen(true);
  };

  const handleSaveRule = async () => {
    const values = await ruleForm.validateFields();
    const payload = {
      schemeId: selectedSchemeId,
      groupId: selectedGroupId,
      ruleType: values.ruleType,
      priority: values.priority,
      sourceStage: values.sourceStage || '',
      targetStage: values.targetStage,
      observationDays: values.observationDays,
      jsonCondition: '',
    };

    if (editingRule) {
      // Keep existing jsonCondition unless user re-edits conditions
      payload.jsonCondition = editingRule.jsonCondition || '';
      await stageApi.updateRule(editingRule.ruleId!, {
        ...editingRule,
        conditions: undefined,
        ...payload,
      });
      message.success('规则已更新');
      setRuleModalOpen(false);
      setEditingRule(null);
      loadRules();
    } else {
      // Create rule, then open condition editor
      const res = await stageApi.createRule(payload);
      message.success('规则已创建，请编辑触发条件');
      setRuleModalOpen(false);
      const created = (res.data as any)?.data || res.data;
      const ruleId = created?.ruleId;
      if (ruleId) {
        await loadRules();
        // Open condition editor for the new rule
        setEditorRuleId(ruleId);
        setEditorTabType(values.ruleType === 'FORWARD' ? 'forward' : 'rollback');
        setEditorConditions([]);
        setEditorModalOpen(true);
      }
    }
  };

  const handleDeleteRule = (rule: StageRuleVO) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这条规则吗？',
      onOk: async () => {
        await stageApi.deleteRule(rule.ruleId!);
        message.success('已删除');
        loadRules();
      },
    });
  };

  // ─── Condition editor ───
  const openEditor = (rule: StageRuleVO) => {
    const tabType = rule.ruleType === 'FORWARD' ? 'forward' : 'rollback';
    setEditorRuleId(rule.ruleId!);
    setEditorTabType(tabType);
    const parsed = parseConditions(rule.jsonCondition);
    setEditorConditions(parsed.length > 0 ? parsed : [{ type: '逾期天数', operator: 'gte', value: '' }]);
    setEditorModalOpen(true);
  };

  const saveEditorConditions = async () => {
    if (!editorRuleId) return;
    const logic = editorTabType === 'forward' ? 'OR' : 'AND';
    const jsonStr = serializeConditions(editorConditions, logic);

    // Find the rule and update it
    const rule = stageRules.find((r) => r.ruleId === editorRuleId);
    if (!rule) return;

    await stageApi.updateRule(editorRuleId, {
      ...rule,
      conditions: undefined,
      jsonCondition: jsonStr,
    });
    message.success('条件已保存');
    setEditorModalOpen(false);
    loadRules();
  };

  // ─── CRR rating rules ───
  const openRatingModal = (rule?: RatingDowngradeRuleVO) => {
    if (rule) {
      Modal.info({
        title: '编辑评级阈值',
        content: (
          <div style={{ marginTop: 16 }}>
            <InputNumber
              defaultValue={rule.downgradeThreshold}
              min={0}
              placeholder="下降级数"
              onChange={(v) => {
                if (v != null) {
                  stageApi.updateRatingRule(rule.ruleId!, {
                    ...rule,
                    downgradeThreshold: v,
                  }).then(() => {
                    message.success('已更新');
                    loadRules();
                  });
                }
              }}
            />
            <span style={{ marginLeft: 8, color: 'var(--color-text-secondary)', fontSize: 13 }}>级</span>
          </div>
        ),
      });
    } else {
      // Add new rating rule — simple prompt
      let rating = '';
      let threshold = 3;
      Modal.confirm({
        title: '新增评级下降规则',
        content: (
          <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Input placeholder="评级代码（如 CRR 1）" onChange={(e) => (rating = e.target.value)} />
            <InputNumber min={0} defaultValue={3} placeholder="下降阈值（级数）" onChange={(v) => (threshold = v || 3)} />
          </div>
        ),
        onOk: async () => {
          if (!rating) { message.warning('请输入评级代码'); return; }
          await stageApi.createRatingRule({
            schemeId: selectedSchemeId,
            groupId: selectedGroupId,
            currentRating: rating,
            downgradeThreshold: threshold,
          });
          message.success('已添加');
          loadRules();
        },
      });
    }
  };

  const handleDeleteRatingRule = (rule: RatingDowngradeRuleVO) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这条评级下降规则吗？',
      onOk: async () => {
        await stageApi.deleteRatingRule(rule.ruleId!);
        message.success('已删除');
        loadRules();
      },
    });
  };

  const batchSetCrr = async () => {
    if (!crrBatchVal || crrBatchVal <= 0) return;
    // Update all existing rating rules to the batch value
    await Promise.all(
      ratingRules.map((r) =>
        stageApi.updateRatingRule(r.ruleId!, { ...r, downgradeThreshold: crrBatchVal })
      )
    );
    message.success(`所有评级阈值已设为 ≥ ${crrBatchVal} 级`);
    loadRules();
  };

  // ─── Copy rules ───
  const [copySourceGroups, setCopySourceGroups] = useState<RiskGroupVO[]>([]);
  const handleOpenCopy = () => {
    setCopySourceSchemeId('');
    setCopySourceGroupId('');
    setCopySourceGroups([]);
    setCopyModalOpen(true);
  };

  const handleCopySourceSchemeChange = async (schemeId: string) => {
    setCopySourceSchemeId(schemeId);
    setCopySourceGroupId('');
    if (schemeId) {
      const res = await riskGroupApi.listByScheme(schemeId);
      setCopySourceGroups((res.data as any)?.data || res.data || []);
    } else {
      setCopySourceGroups([]);
    }
  };

  const handleCopyRules = async () => {
    if (!copySourceSchemeId || !copySourceGroupId) {
      message.warning('请选择源方案和源风险分组');
      return;
    }
    try {
      const res = await stageApi.getBySchemeAndGroup(copySourceSchemeId, copySourceGroupId);
      const data = (res.data as any)?.data || res.data;
      const srcStageRules: StageRuleVO[] = data?.stageRules || [];
      const srcRatingRules: RatingDowngradeRuleVO[] = data?.ratingRules || [];

      // Copy stage rules to current group
      await Promise.all(
        srcStageRules.map((r) =>
          stageApi.createRule({
            schemeId: selectedSchemeId,
            groupId: selectedGroupId,
            ruleType: r.ruleType,
            sourceStage: r.sourceStage,
            targetStage: r.targetStage,
            priority: r.priority,
            observationDays: r.observationDays,
            jsonCondition: r.jsonCondition,
          })
        )
      );

      // Copy rating rules
      await Promise.all(
        srcRatingRules.map((r) =>
          stageApi.createRatingRule({
            schemeId: selectedSchemeId,
            groupId: selectedGroupId,
            currentRating: r.currentRating,
            downgradeThreshold: r.downgradeThreshold,
          })
        )
      );

      message.success(`已从源分组复制 ${srcStageRules.length} 条阶段规则和 ${srcRatingRules.length} 条评级规则`);
      setCopyModalOpen(false);
      loadRules();
    } catch {
      message.error('复制失败');
    }
  };

  // ─── Render helpers ───
  const groupItems = groups.map((g) => ({
    groupId: g.groupId,
    groupName: g.groupName,
    groupCode: g.groupCode,
  }));

  // ═══════════════════════════════════════════
  // Empty states
  // ═══════════════════════════════════════════

  if (!selectedSchemeId) {
    return (
      <div className="ecl-page">
        <PageHeader title="阶段划分配置" subtitle="按风险分组配置阶段判定规则和 CRR 评级下降阈值" />
        <Panel>
          <div style={{ textAlign: 'center', padding: 40, color: 'var(--color-text-muted)', fontSize: 13 }}>
            请先选择一个 ECL 方案
            <div style={{ marginTop: 16 }}>
              <Select
                style={{ width: 300 }}
                placeholder="请选择 ECL 方案"
                value={undefined}
                onChange={setSelectedSchemeId}
                options={schemes.map((s) => ({
                  label: `${s.schemeName} (${s.schemeCode})`,
                  value: s.schemeId,
                }))}
              />
            </div>
          </div>
        </Panel>
      </div>
    );
  }

  // ═══════════════════════════════════════════
  // Main render
  // ═══════════════════════════════════════════
  return (
    <>
      <style>{`
        /* ─── Tab bar ─── */
        .stage-tabs {
          display: flex;
          gap: 0;
          border-bottom: 1px solid var(--color-border);
          padding: 0 20px;
          background: var(--color-surface);
        }
        .stage-tab-item {
          padding: 8px 20px;
          font-size: 13px;
          font-weight: 500;
          color: var(--color-text-secondary);
          cursor: pointer;
          border-bottom: 2px solid transparent;
          margin-bottom: -1px;
          transition: all 0.12s;
          background: none;
          border-top: none;
          border-left: none;
          border-right: none;
          font-family: inherit;
        }
        .stage-tab-item:hover {
          color: var(--color-primary-dark);
          background: var(--color-primary-light);
        }
        .stage-tab-item.active {
          color: var(--color-primary);
          border-bottom-color: var(--color-primary);
          font-weight: 600;
        }

        /* ─── Table ─── */
        .ecl-table {
          width: 100%;
          border-collapse: collapse;
        }
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
        .ecl-table tr:nth-child(even) td {
          background: #fafbfc;
        }
        .ecl-table tr:hover td {
          background: #eff3ff !important;
        }
        .ecl-table tr:last-child td {
          border-bottom: none;
        }

        /* ─── Condition chips ─── */
        .cond-chip {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 2px 10px;
          background: #f3f4f6;
          border-radius: 4px;
          font-size: 12px;
          border: 1px solid #e5e7eb;
        }
        .cond-or {
          font-size: 10px;
          color: var(--color-text-muted);
          padding: 0 4px;
          text-transform: uppercase;
          font-weight: 500;
        }
        .cond-and {
          font-size: 10px;
          color: var(--color-text-muted);
          padding: 0 4px;
          font-weight: 500;
        }

        /* ─── Stage badge ─── */
        .stage-badge {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 2px 10px;
          border-radius: 4px;
          font-size: 12px;
          font-weight: 500;
          border: 1px solid;
        }

        /* ─── Notes & footer ─── */
        .info-note {
          font-size: 12px;
          color: var(--color-text-muted);
          padding: 8px 12px;
          border-top: 1px solid var(--color-border);
          background: var(--color-bg-alt);
        }
        .crr-batch {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 10px 16px;
          background: var(--color-bg-alt);
          border-radius: var(--radius-md);
          border: 1px dashed var(--color-border);
          margin: 12px 16px 16px;
          font-size: 13px;
        }
        .crr-batch input {
          width: 60px;
          border: 1px solid var(--color-border);
          border-radius: var(--radius-sm);
          padding: 4px 8px;
          font-size: 13px;
          text-align: center;
        }

        /* ─── Buttons ─── */
        .btn-text {
          background: none;
          border: none;
          padding: 3px 8px;
          border-radius: var(--radius-sm);
          font-size: 12px;
          font-weight: 500;
          color: var(--color-text-secondary);
          cursor: pointer;
          transition: all 0.12s;
          font-family: inherit;
        }
        .btn-text:hover {
          color: var(--color-primary);
          background: var(--color-primary-light);
        }
        .btn-text.danger:hover {
          color: var(--color-danger);
          background: #fef2f2;
        }
        .btn-ghost {
          background: none;
          border: none;
          padding: 5px 12px;
          border-radius: var(--radius-sm);
          font-size: 13px;
          color: var(--color-text-secondary);
          cursor: pointer;
          transition: all 0.12s;
          font-family: inherit;
        }
        .btn-ghost:hover {
          background: var(--color-bg-alt);
          color: var(--color-text);
        }
        .btn-primary {
          background: var(--color-primary);
          color: #fff;
          border: none;
          padding: 7px 18px;
          border-radius: var(--radius-sm);
          font-size: 13px;
          font-weight: 500;
          cursor: pointer;
          transition: all var(--transition-fast);
          display: inline-flex;
          align-items: center;
          gap: 6px;
          font-family: inherit;
        }
        .btn-primary:hover {
          background: var(--color-primary-dark);
          box-shadow: 0 2px 8px rgba(37, 99, 235, 0.25);
        }

        /* ─── Modal ─── */
        .modal-overlay-custom {
          display: none;
          position: fixed;
          inset: 0;
          background: rgba(0, 0, 0, 0.4);
          backdrop-filter: blur(4px);
          z-index: 1000;
          align-items: center;
          justify-content: center;
        }
        .modal-overlay-custom.show {
          display: flex;
        }
        .modal-box-custom {
          background: var(--color-surface);
          border-radius: var(--radius-lg);
          box-shadow: var(--shadow-lg);
          overflow: hidden;
          max-width: 90vw;
        }
        .modal-head-custom {
          padding: 16px 20px;
          border-bottom: 1px solid var(--color-border);
          display: flex;
          justify-content: space-between;
          align-items: center;
          background: var(--color-bg-alt);
        }
        .modal-head-custom h3 {
          font-size: 15px;
          font-weight: 600;
          color: var(--color-text);
        }
        .modal-close-custom {
          background: none;
          border: none;
          font-size: 18px;
          color: var(--color-text-muted);
          cursor: pointer;
          padding: 4px;
        }
        .modal-close-custom:hover { color: var(--color-text); }
        .modal-body-custom {
          padding: 20px;
          max-height: 70vh;
          overflow-y: auto;
        }
        .modal-foot-custom {
          padding: 12px 20px;
          border-top: 1px solid var(--color-border);
          display: flex;
          justify-content: flex-end;
          gap: 8px;
          background: var(--color-bg-alt);
        }

        /* ─── Condition editor ─── */
        .editor-split {
          display: grid;
          grid-template-columns: 1fr 280px;
          gap: 16px;
        }
        .cond-editor {
          border: 1px solid var(--color-border);
          border-radius: var(--radius-lg);
          overflow: hidden;
        }
        .ce-header {
          padding: 10px 14px;
          background: var(--color-bg-alt);
          border-bottom: 1px solid var(--color-border);
          display: flex;
          justify-content: space-between;
          align-items: center;
        }
        .ce-header span {
          font-size: 12px;
          font-weight: 600;
          color: var(--color-text-secondary);
        }
        .ce-body {
          padding: 14px;
        }
        .cond-block {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 6px 0;
          flex-wrap: wrap;
        }
        .cond-block select, .cond-block input {
          border: 1px solid var(--color-border);
          border-radius: var(--radius-sm);
          padding: 5px 8px;
          font-size: 12px;
          background: #fff;
        }
        .cond-remove {
          color: var(--color-text-muted);
          cursor: pointer;
          font-size: 14px;
          padding: 2px 4px;
          background: none;
          border: none;
          font-family: inherit;
        }
        .cond-remove:hover { color: #dc2626; }
        .or-divider {
          text-align: center;
          padding: 2px 0;
        }
        .or-divider span {
          font-size: 10px;
          color: var(--color-text-muted);
          font-weight: 600;
        }
        .form-group {
          display: flex;
          flex-direction: column;
          gap: 4px;
          margin-bottom: 12px;
        }
        .form-group label {
          font-size: 12px;
          color: var(--color-text-secondary);
          font-weight: 500;
        }
        .form-group select, .form-group input {
          border: 1px solid var(--color-border);
          border-radius: var(--radius-sm);
          padding: 6px 10px;
          font-size: 13px;
        }
        .form-row {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 12px;
        }

        /* Empty state */
        .empty-state {
          padding: 40px;
          text-align: center;
          color: var(--color-text-muted);
          font-size: 13px;
        }

        /* Toast */
        .toast-custom {
          position: fixed;
          bottom: 24px;
          right: 24px;
          background: var(--color-text);
          color: #f1f5f9;
          padding: 10px 18px;
          border-radius: var(--radius-md);
          font-size: 13px;
          z-index: 9999;
          opacity: 0;
          transform: translateY(10px);
          transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
          pointer-events: none;
        }
        .toast-custom.show {
          opacity: 1;
          transform: translateY(0);
        }
      `}</style>

      <div className="ecl-page">
        {/* ─── Page header ─── */}
        <PageHeader
          title="阶段划分配置"
          subtitle="按风险分组配置阶段判定规则和 CRR 评级下降阈值"
          extra={
            <Space>
              <Select
                style={{ width: 260 }}
                placeholder="请选择 ECL 方案"
                value={selectedSchemeId || undefined}
                onChange={setSelectedSchemeId}
                options={schemes.map((s) => ({
                  label: `${s.schemeName} (${s.schemeCode})`,
                  value: s.schemeId,
                }))}
              />
              <Button icon={<CopyOutlined />} onClick={handleOpenCopy}>复制规则</Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => openRuleModal()}>新增规则</Button>
            </Space>
          }
        />

        {/* ─── Group selector ─── */}
        {groups.length > 0 && (
          <GroupSelector
            groups={groupItems}
            selectedId={selectedGroupId || undefined}
            onChange={setSelectedGroupId}
          />
        )}

        {!selectedGroupId ? (
          <Panel>
            {groups.length === 0 ? (
              <div className="ecl-empty-row">当前方案暂无风险分组，请先在风险分组页面创建分组</div>
            ) : (
              <div className="ecl-empty-row">请选择一个风险分组以查看规则</div>
            )}
          </Panel>
        ) : (
          <Panel noPadding>
            {/* ─── Tabs ─── */}
            <div className="stage-tabs">
              <button
                className={`stage-tab-item ${activeTab === 'forward' ? 'active' : ''}`}
                onClick={() => setActiveTab('forward')}
              >
                向前判定 (FORWARD)
              </button>
              <button
                className={`stage-tab-item ${activeTab === 'rollback' ? 'active' : ''}`}
                onClick={() => setActiveTab('rollback')}
              >
                回跳校验 (ROLLBACK)
              </button>
              <button
                className={`stage-tab-item ${activeTab === 'crr' ? 'active' : ''}`}
                onClick={() => setActiveTab('crr')}
              >
                CRR 评级阈值
              </button>
            </div>

            {/* ─── FORWARD tab ─── */}
            {activeTab === 'forward' && (
              <div style={{ padding: 0 }}>
                <table className="ecl-table">
                  <thead>
                    <tr>
                      <th style={{ width: 70 }}>优先级</th>
                      <th style={{ width: 120 }}>目标阶段</th>
                      <th>触发条件</th>
                      <th style={{ width: 100 }}>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {forwardRules.length === 0 && (
                      <tr>
                        <td colSpan={4}><div className="ecl-empty-row">暂无向前判定规则</div></td>
                      </tr>
                    )}
                    {forwardRules.map((r) => {
                      const conditions = parseConditions(r.jsonCondition);
                      const defaultRule = isDefaultRule(r);
                      const sc = stageColor(r.targetStage);
                      return (
                        <tr key={r.ruleId}>
                          <td><strong>{r.priority}</strong></td>
                          <td>
                            <span
                              className="stage-badge"
                              style={{
                                background: sc.bg,
                                borderColor: sc.border,
                                color: sc.text,
                              }}
                            >
                              {r.targetStage?.replace('STAGE_', 'Stage ')}
                            </span>
                          </td>
                          <td>
                            {defaultRule ? (
                              <span style={{ color: 'var(--color-text-muted)', fontSize: 12 }}>
                                兜底 — 未命中以上规则的借据
                              </span>
                            ) : (
                              <>
                                {conditions.map((c, i) => (
                                  <span key={i}>
                                    <span className="cond-chip">{conditionLabel(c)}</span>
                                    {i < conditions.length - 1 && (
                                      <span className="cond-or">或</span>
                                    )}
                                  </span>
                                ))}
                                {conditions.length === 0 && (
                                  <span style={{ color: 'var(--color-text-muted)', fontSize: 12 }}>
                                    无条件（始终匹配）
                                  </span>
                                )}
                              </>
                            )}
                          </td>
                          <td>
                            {defaultRule ? (
                              <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>只读</span>
                            ) : (
                              <>
                                <button className="btn-text" onClick={() => openEditor(r)}>编辑</button>
                                <button className="btn-text danger" onClick={() => handleDeleteRule(r)}>删除</button>
                              </>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                <div className="info-note ecl-info-note">引擎按优先级逐条匹配，Stage 1 为兜底规则（只读）</div>
              </div>
            )}

            {/* ─── ROLLBACK tab ─── */}
            {activeTab === 'rollback' && (
              <div style={{ padding: 0 }}>
                <table className="ecl-table">
                  <thead>
                    <tr>
                      <th style={{ width: 70 }}>优先级</th>
                      <th style={{ width: 150 }}>回跳路径</th>
                      <th style={{ width: 100 }}>观察期(天)</th>
                      <th>条件</th>
                      <th style={{ width: 100 }}>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rollbackRules.length === 0 && (
                      <tr>
                        <td colSpan={5}><div className="ecl-empty-row">暂无回跳规则</div></td>
                      </tr>
                    )}
                    {rollbackRules.map((r) => {
                      const conditions = parseConditions(r.jsonCondition);
                      return (
                        <tr key={r.ruleId}>
                          <td><strong>{r.priority}</strong></td>
                          <td>
                            <span style={{ fontSize: 13, fontWeight: 500 }}>
                              {r.sourceStage?.replace('STAGE_', 'Stage ')} → {r.targetStage?.replace('STAGE_', 'Stage ')}
                            </span>
                          </td>
                          <td>{r.observationDays || '-'}</td>
                          <td>
                            {conditions.map((c, i) => (
                              <span key={i}>
                                <span className="cond-chip">{conditionLabel(c)}</span>
                                {i < conditions.length - 1 && (
                                  <span className="cond-and">且</span>
                                )}
                              </span>
                            ))}
                            {conditions.length === 0 && (
                              <span style={{ color: 'var(--color-text-muted)', fontSize: 12 }}>—</span>
                            )}
                          </td>
                          <td>
                            <button className="btn-text" onClick={() => openEditor(r)}>编辑</button>
                            <button className="btn-text danger" onClick={() => handleDeleteRule(r)}>删除</button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {/* ─── CRR tab ─── */}
            {activeTab === 'crr' && (
              <div style={{ padding: 0 }}>
                <table className="ecl-table">
                  <thead>
                    <tr>
                      <th style={{ width: 180 }}>评级代码</th>
                      <th style={{ width: 160 }}>下降阈值</th>
                      <th style={{ width: 100 }}>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {ratingRules.length === 0 && (
                      <tr>
                        <td colSpan={3}><div className="ecl-empty-row">暂无评级下降规则</div></td>
                      </tr>
                    )}
                    {ratingRules.map((r) => (
                      <tr key={r.ruleId}>
                        <td style={{ fontWeight: 500 }}>{r.currentRating}</td>
                        <td>
                          <span style={{ fontWeight: 600, color: 'var(--color-primary)' }}>
                            ≥ {r.downgradeThreshold} 级
                          </span>
                        </td>
                        <td>
                          <button className="btn-text" onClick={() => openRatingModal(r)}>编辑</button>
                          <button className="btn-text danger" onClick={() => handleDeleteRatingRule(r)}>删除</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div className="crr-batch">
                  <span>批量设置：所有评级下降 ≥</span>
                  <input
                    type="number"
                    value={crrBatchVal}
                    onChange={(e) => setCrrBatchVal(parseInt(e.target.value) || 0)}
                    min={1}
                  />
                  <span>级</span>
                  <button className="btn-text" onClick={batchSetCrr}>应用</button>
                  <span style={{ flex: 1 }} />
                  <button className="btn-text" onClick={() => openRatingModal()}>＋ 新增评级</button>
                </div>
              </div>
            )}
          </Panel>
        )}
      </div>

      {/* ═══════════════════════════════════════════
         Rule Modal (Step 1 — basic info)
         ═══════════════════════════════════════════ */}
      <Modal
        title={editingRule ? '编辑规则' : '新增规则'}
        open={ruleModalOpen}
        onOk={handleSaveRule}
        onCancel={() => {
          setRuleModalOpen(false);
          setEditingRule(null);
          ruleForm.resetFields();
        }}
        okText={editingRule ? '保存' : '保存并配置条件'}
      >
        <Form form={ruleForm} layout="vertical">
          <div className="form-row">
            <Form.Item name="ruleType" label="规则类型" rules={[{ required: true }]}>
              <Select
                options={[
                  { label: 'FORWARD — 向前判定', value: 'FORWARD' },
                  { label: 'ROLLBACK — 回滚判定', value: 'ROLLBACK' },
                ]}
              />
            </Form.Item>
            <Form.Item name="priority" label="优先级（数字越小越高）" rules={[{ required: true }]}>
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <div className="form-row">
            <Form.Item name="sourceStage" label="来源阶段">
              <Select
                allowClear
                placeholder="（不适用）"
                options={[
                  { label: 'Stage 1', value: 'STAGE_1' },
                  { label: 'Stage 2', value: 'STAGE_2' },
                  { label: 'Stage 3', value: 'STAGE_3' },
                ]}
              />
            </Form.Item>
            <Form.Item name="targetStage" label="目标阶段" rules={[{ required: true }]}>
              <Select
                options={[
                  { label: 'Stage 2', value: 'STAGE_2' },
                  { label: 'Stage 3', value: 'STAGE_3' },
                  { label: 'Stage 1', value: 'STAGE_1' },
                ]}
              />
            </Form.Item>
          </div>
          <Form.Item name="observationDays" label="观察期（天，ROLLBACK 时填写）">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
        {!editingRule && (
          <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: -8 }}>
            保存后将在弹出的条件编辑器中配置详细触发条件
          </div>
        )}
      </Modal>

      {/* ═══════════════════════════════════════════
         Condition Editor Modal (Step 2)
         ═══════════════════════════════════════════ */}
      <Modal
        title={
          <div>
            <span>{editorTabType === 'forward' ? '编辑条件 — 向前判定' : '编辑条件 — 回跳校验'}</span>
            <span style={{ fontSize: 12, color: 'var(--color-text-muted)', marginLeft: 12, fontWeight: 400 }}>
              {groups.find((g) => g.groupId === selectedGroupId)?.groupName || ''}
              {' · '}
              {editorTabType === 'forward' ? '任一满足 (OR)' : '全部满足 (AND)'}
            </span>
          </div>
        }
        open={editorModalOpen}
        onOk={saveEditorConditions}
        onCancel={() => setEditorModalOpen(false)}
        okText="保存条件"
        width={780}
      >
        <div className="editor-split">
          <div>
            <div className="cond-editor">
              <div className="ce-header">
                <span>条件列表 · 逻辑：{editorTabType === 'forward' ? '任一满足 (OR)' : '全部满足 (AND)'}</span>
                <button className="btn-text" onClick={() => setEditorConditions([...editorConditions, { type: '逾期天数', operator: 'gte', value: '' }])}>
                  ＋ 添加条件
                </button>
              </div>
              <div className="ce-body">
                {editorConditions.map((c, i) => (
                  <React.Fragment key={i}>
                    <div className="cond-block">
                      <select
                        style={{ width: 120 }}
                        value={c.type}
                        onChange={(e) => {
                          const updated = [...editorConditions];
                          updated[i] = { type: e.target.value, operator: 'eq', value: '' };
                          setEditorConditions(updated);
                        }}
                      >
                        {CONDITION_TYPE_OPTIONS.map((t) => (
                          <option key={t} value={t}>{t}</option>
                        ))}
                      </select>

                      {/* Type-specific inputs */}
                      {c.type === '逾期天数' && (
                        <>
                          <select
                            style={{ width: 60 }}
                            value={c.operator}
                            onChange={(e) => {
                              const updated = [...editorConditions];
                              updated[i] = { ...updated[i], operator: e.target.value };
                              setEditorConditions(updated);
                            }}
                          >
                            <option value="gte">≥</option>
                            <option value="lte">≤</option>
                          </select>
                          <input
                            type="number"
                            value={c.value || ''}
                            style={{ width: 70 }}
                            onChange={(e) => {
                              const updated = [...editorConditions];
                              updated[i] = { ...updated[i], value: parseInt(e.target.value) || 0 };
                              setEditorConditions(updated);
                            }}
                          />
                          <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>天</span>
                        </>
                      )}

                      {c.type === '五级分类' && (
                        <>
                          <select
                            style={{ width: 80 }}
                            value={c.operator}
                            onChange={(e) => {
                              const updated = [...editorConditions];
                              updated[i] = { ...updated[i], operator: e.target.value };
                              setEditorConditions(updated);
                            }}
                          >
                            <option value="in">属于</option>
                            <option value="not_in">不属于</option>
                          </select>
                          {['正常', '关注', '次级', '可疑', '损失'].map((v) => (
                            <label key={v} style={{ fontSize: 12, marginRight: 6, whiteSpace: 'nowrap' }}>
                              <input
                                type="checkbox"
                                checked={(c.values || []).includes(v)}
                                onChange={() => {
                                  const updated = [...editorConditions];
                                  const vals = [...(updated[i].values || [])];
                                  const idx = vals.indexOf(v);
                                  if (idx > -1) vals.splice(idx, 1);
                                  else vals.push(v);
                                  updated[i] = { ...updated[i], values: vals };
                                  setEditorConditions(updated);
                                }}
                              />{' '}{v}
                            </label>
                          ))}
                        </>
                      )}

                      {c.type === '违约标识' && (
                        <select
                          style={{ width: 60 }}
                          value={c.value || ''}
                          onChange={(e) => {
                            const updated = [...editorConditions];
                            updated[i] = { ...updated[i], value: e.target.value };
                            setEditorConditions(updated);
                          }}
                        >
                          <option value="是">是</option>
                          <option value="否">否</option>
                        </select>
                      )}

                      {c.type === 'CRR 评级下降' && (
                        <>
                          <input
                            value={c.value || ''}
                            placeholder="级数"
                            style={{ width: 60 }}
                            onChange={(e) => {
                              const updated = [...editorConditions];
                              updated[i] = { ...updated[i], value: e.target.value };
                              setEditorConditions(updated);
                            }}
                          />
                          <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>级</span>
                        </>
                      )}

                      {c.type === '还款状态' && (
                        <select
                          style={{ width: 120 }}
                          value={c.value || ''}
                          onChange={(e) => {
                            const updated = [...editorConditions];
                            updated[i] = { ...updated[i], value: e.target.value };
                            setEditorConditions(updated);
                          }}
                        >
                          <option value="正常">正常还本付息</option>
                          <option value="逾期">逾期</option>
                        </select>
                      )}

                      {c.type === '逾期状态' && (
                        <select
                          style={{ width: 80 }}
                          value={c.value || ''}
                          onChange={(e) => {
                            const updated = [...editorConditions];
                            updated[i] = { ...updated[i], value: e.target.value };
                            setEditorConditions(updated);
                          }}
                        >
                          <option value="已消除">已消除</option>
                          <option value="未消除">未消除</option>
                        </select>
                      )}

                      {c.type === '舆情事件' && (
                        <input
                          value={c.value || ''}
                          placeholder="如：中度"
                          style={{ width: 120 }}
                          onChange={(e) => {
                            const updated = [...editorConditions];
                            updated[i] = { ...updated[i], value: e.target.value };
                            setEditorConditions(updated);
                          }}
                        />
                      )}

                      <button
                        className="cond-remove"
                        onClick={() => {
                          if (editorConditions.length <= 1) {
                            message.warning('至少保留一个条件');
                            return;
                          }
                          const updated = [...editorConditions];
                          updated.splice(i, 1);
                          setEditorConditions(updated);
                        }}
                      >
                        ✕
                      </button>
                    </div>
                    {i < editorConditions.length - 1 && (
                      <div className="or-divider">
                        <span>{editorTabType === 'forward' ? '或' : '且'}</span>
                      </div>
                    )}
                  </React.Fragment>
                ))}
              </div>
            </div>
          </div>
          <div>
            <div style={{
              background: 'var(--color-bg-alt)',
              borderRadius: 'var(--radius-lg)',
              padding: 14,
              fontSize: 12,
            }}>
              <div style={{ fontWeight: 600, marginBottom: 8, color: 'var(--color-text-secondary)' }}>JSON 预览</div>
              <pre style={{
                background: 'var(--color-text)',
                color: '#f1f5f9',
                padding: 10,
                borderRadius: 'var(--radius-sm)',
                fontSize: 11,
                lineHeight: 1.5,
                overflowX: 'auto',
                whiteSpace: 'pre-wrap',
                minHeight: 140,
              }}>
                {JSON.stringify(
                  {
                    logic: editorTabType === 'forward' ? 'OR' : 'AND',
                    conditions: editorConditions.map((c) => {
                      const obj: any = { type: c.type };
                      if (c.type === '逾期天数') { obj.operator = c.operator; obj.value = parseInt(String(c.value)) || 0; }
                      else if (c.type === '五级分类') { obj.operator = c.operator; obj.values = c.values || []; }
                      else if (c.type === '违约标识') { obj.operator = 'eq'; obj.value = c.value === '是'; }
                      else { obj.operator = c.operator || 'eq'; obj.value = c.value; }
                      return obj;
                    }),
                  },
                  null,
                  2
                )}
              </pre>
            </div>
          </div>
        </div>
      </Modal>

      {/* ═══════════════════════════════════════════
         Copy Rules Modal
         ═══════════════════════════════════════════ */}
      <Modal
        title="复制规则"
        open={copyModalOpen}
        onOk={handleCopyRules}
        onCancel={() => setCopyModalOpen(false)}
        okText="复制"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div>
            <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginBottom: 4, fontWeight: 500 }}>
              源方案
            </div>
            <Select
              style={{ width: '100%' }}
              placeholder="选择源方案"
              value={copySourceSchemeId || undefined}
              onChange={handleCopySourceSchemeChange}
              options={schemes
                .filter((s) => s.schemeId !== selectedSchemeId)
                .map((s) => ({ label: `${s.schemeName} (${s.schemeCode})`, value: s.schemeId }))}
            />
          </div>
          {copySourceGroups.length > 0 && (
            <div>
              <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginBottom: 4, fontWeight: 500 }}>
                源风险分组
              </div>
              <Select
                style={{ width: '100%' }}
                placeholder="选择源风险分组"
                value={copySourceGroupId || undefined}
                onChange={setCopySourceGroupId}
                options={copySourceGroups.map((g) => ({ label: g.groupName, value: g.groupId }))}
              />
            </div>
          )}
          <div style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>
            复制目标：{groups.find((g) => g.groupId === selectedGroupId)?.groupName || selectedGroupId}
          </div>
        </div>
      </Modal>
    </>
  );
};

export default StageConfig;
