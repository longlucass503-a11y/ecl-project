import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Button, Space, Modal, Form, Input, Select, message, Spin, Typography, Empty, Tag, Popconfirm } from 'antd';
import { ArrowLeftOutlined, SettingOutlined, PlusOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { dictApi, type SchemeDictVO, type DictCategoryVO, type DictEntryVO } from '../../api/dict';
import { Panel } from '../../components';

const SchemeDictConfig: React.FC = () => {
  const [searchParams] = useSearchParams();
  const schemeId = searchParams.get('schemeId') || '';
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [schemeDicts, setSchemeDicts] = useState<SchemeDictVO[]>([]);
  const [globalCategories, setGlobalCategories] = useState<DictCategoryVO[]>([]);

  // 方案级配置弹窗（选择条目）
  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [editingDict, setEditingDict] = useState<SchemeDictVO | null>(null);
  const [selectedEntryIds, setSelectedEntryIds] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  // 条目 CRUD 弹窗
  const [entryModalOpen, setEntryModalOpen] = useState(false);
  const [editingEntry, setEditingEntry] = useState<DictEntryVO | null>(null);
  const [entryForm] = Form.useForm();
  const [entrySaving, setEntrySaving] = useState(false);
  const [currentCategoryId, setCurrentCategoryId] = useState<string>('');

  const loadData = async () => {
    if (!schemeId) return;
    setLoading(true);
    try {
      const [dictRes, catRes] = await Promise.all([
        dictApi.listSchemeDicts(schemeId),
        dictApi.listCategories(),
      ]);
      setSchemeDicts((dictRes.data as any)?.data || dictRes.data || []);
      setGlobalCategories((catRes.data as any)?.data || catRes.data || []);
    } catch (err) {
      console.error(err);
      message.error('加载基础信息失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, [schemeId]);

  // ========= 方案级配置 =========
  const handleConfig = (sd: SchemeDictVO) => {
    setEditingDict(sd);
    if (sd.overrideType === 'CUSTOM' && sd.entryIds) {
      setSelectedEntryIds(sd.entryIds);
    } else {
      setSelectedEntryIds(sd.effectiveEntries.map(e => e.entryId!).filter(Boolean));
    }
    setConfigModalOpen(true);
  };

  const handleSaveConfig = async () => {
    if (!editingDict || !schemeId) return;
    setSaving(true);
    try {
      const globalCat = globalCategories.find(c => c.categoryId === editingDict.categoryId);
      const allActiveIds = (globalCat?.entries || [])
        .filter(e => e.isActive !== false)
        .map(e => e.entryId!)
        .filter(Boolean);

      const isInherit = selectedEntryIds.length === allActiveIds.length &&
        selectedEntryIds.every(id => allActiveIds.includes(id));

      const req = isInherit
        ? { overrideType: 'INHERIT' as const }
        : { overrideType: 'CUSTOM' as const, entryIds: selectedEntryIds };

      await dictApi.saveSchemeDict(schemeId, editingDict.categoryId, req);
      message.success('保存成功');
      setConfigModalOpen(false);
      loadData();
    } catch (err: any) {
      message.error(err?.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  // ========= 条目 CRUD =========
  const openCreateEntry = (categoryId: string) => {
    setCurrentCategoryId(categoryId);
    setEditingEntry(null);
    entryForm.resetFields();
    setEntryModalOpen(true);
  };

  const openEditEntry = (entry: DictEntryVO) => {
    setCurrentCategoryId(entry.categoryId!);
    setEditingEntry(entry);
    entryForm.setFieldsValue(entry);
    setEntryModalOpen(true);
  };

  const handleSaveEntry = async () => {
    const values = await entryForm.validateFields();
    setEntrySaving(true);
    try {
      if (editingEntry) {
        await dictApi.updateEntry(editingEntry.entryId!, {
          ...values,
          categoryId: currentCategoryId,
        });
        message.success('条目已更新');
      } else {
        await dictApi.createEntry({
          ...values,
          categoryId: currentCategoryId,
        });
        message.success('条目已创建');
      }
      setEntryModalOpen(false);
      setEditingEntry(null);
      entryForm.resetFields();
      loadData(); // 刷新方案字典（条目列表会变）
    } catch (err: any) {
      message.error(err?.response?.data?.message || err?.message || '操作失败');
    } finally {
      setEntrySaving(false);
    }
  };

  const handleDeleteEntry = async (entry: DictEntryVO) => {
    try {
      await dictApi.deleteEntry(entry.entryId!);
      message.success('条目已删除');
      loadData();
    } catch (err: any) {
      message.error(err?.message || '删除失败');
    }
  };

  // 获取某个分类的全局条目列表
  const getEntriesForCategory = (categoryId: string): DictEntryVO[] => {
    return globalCategories.find(c => c.categoryId === categoryId)?.entries || [];
  };

  if (loading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />;

  return (
    <div className="ecl-page">
      <div style={{ marginBottom: 16 }}>
        <Button type="link" icon={<ArrowLeftOutlined />} onClick={() => navigate(`/schemes/${schemeId}`)}>
          返回方案详情
        </Button>
      </div>

      <Panel title="基础信息配置" subtitle="管理方案所使用的字典枚举值，可继承全局字典或自定义">
        {schemeDicts.length === 0 ? (
          <Empty description="暂无基础信息配置" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {schemeDicts.map(sd => {
              const allEntries = getEntriesForCategory(sd.categoryId);
              return (
                <div key={sd.categoryId} className="ecl-card" style={{ padding: '16px 20px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 4 }}>
                        {sd.categoryName}
                        <Tag style={{ marginLeft: 8, fontSize: 11 }}
                          color={sd.overrideType === 'CUSTOM' ? 'orange' : 'blue'}>
                          {sd.overrideType === 'CUSTOM' ? '自定义' : '继承全局'}
                        </Tag>
                        <span style={{ marginLeft: 8, fontSize: 12, color: 'var(--color-text-muted)' }}>
                          {sd.effectiveEntries.length} / {allEntries.length} 项
                        </span>
                      </div>

                      {/* 全局条目表格 */}
                      <table style={{ width: '100%', marginTop: 8, fontSize: 13, borderCollapse: 'collapse' }}>
                        <thead>
                          <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
                            <th style={{ textAlign: 'left', padding: '4px 8px', width: 40 }}>#</th>
                            <th style={{ textAlign: 'left', padding: '4px 8px', width: 100 }}>编码</th>
                            <th style={{ textAlign: 'left', padding: '4px 8px' }}>名称</th>
                            <th style={{ textAlign: 'left', padding: '4px 8px', width: 60 }}>状态</th>
                            <th style={{ textAlign: 'right', padding: '4px 8px', width: 100 }}>操作</th>
                          </tr>
                        </thead>
                        <tbody>
                          {allEntries.map((entry, idx) => {
                            const active = entry.isActive !== false;
                            const selected = selectedEntryIds.includes(entry.entryId!);
                            return (
                              <tr key={entry.entryId}
                                style={{
                                  borderBottom: '1px solid var(--color-border-secondary)',
                                  opacity: active ? 1 : 0.4,
                                }}>
                                <td style={{ padding: '4px 8px' }}>{idx + 1}</td>
                                <td style={{ padding: '4px 8px', fontFamily: 'monospace' }}>{entry.entryCode}</td>
                                <td style={{ padding: '4px 8px' }}>{entry.entryName}</td>
                                <td style={{ padding: '4px 8px' }}>
                                  {active
                                    ? <span style={{ color: '#52c41a' }}>启用</span>
                                    : <span style={{ color: '#999' }}>停用</span>}
                                </td>
                                <td style={{ padding: '4px 8px', textAlign: 'right' }}>
                                  <Space size={0}>
                                    <Button type="link" size="small" icon={<EditOutlined />}
                                      onClick={() => openEditEntry(entry)} />
                                    <Popconfirm title="确认删除此条目？"
                                      onConfirm={() => handleDeleteEntry(entry)}
                                      okText="确认" cancelText="取消">
                                      <Button type="link" size="small" danger icon={<DeleteOutlined />} />
                                    </Popconfirm>
                                  </Space>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>

                      <div style={{ marginTop: 8, display: 'flex', gap: 8 }}>
                        <Button size="small" icon={<PlusOutlined />}
                          onClick={() => openCreateEntry(sd.categoryId)}>
                          新增条目
                        </Button>
                        <Button size="small" icon={<SettingOutlined />}
                          onClick={() => handleConfig(sd)}>
                          选择启用条目
                        </Button>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Panel>

      {/* 方案级配置弹窗（选择启用哪些条目） */}
      <Modal title={editingDict ? `选择 ${editingDict.categoryName} 条目` : ''}
        open={configModalOpen} onOk={handleSaveConfig} onCancel={() => setConfigModalOpen(false)}
        okText="保存" cancelText="取消" confirmLoading={saving} width={480}>
        {editingDict && (
          <div>
            <p style={{ marginBottom: 12, color: 'var(--color-text-secondary)', fontSize: 13 }}>
              勾选该方案需要使用的枚举值。全部勾选 = 继承全局字典。
            </p>
            <div style={{
              maxHeight: 400, overflowY: 'auto',
              border: '1px solid var(--color-border)', borderRadius: 6, padding: '8px 12px'
            }}>
              {(globalCategories.find(c => c.categoryId === editingDict.categoryId)?.entries || [])
                .filter(e => e.isActive !== false)
                .map(entry => (
                  <div key={entry.entryId}
                    style={{
                      display: 'flex', alignItems: 'center', gap: 8,
                      padding: '8px 4px', cursor: 'pointer',
                      borderBottom: '1px solid var(--color-border-secondary)',
                    }}
                    onClick={() => {
                      setSelectedEntryIds(prev =>
                        prev.includes(entry.entryId!)
                          ? prev.filter(id => id !== entry.entryId)
                          : [...prev, entry.entryId!]
                      );
                    }}>
                    <input type="checkbox" checked={selectedEntryIds.includes(entry.entryId!)}
                      onChange={() => {}} style={{ cursor: 'pointer' }} />
                    <span style={{ fontSize: 13, fontWeight: 500, minWidth: 80 }}>{entry.entryCode}</span>
                    <span style={{ fontSize: 13 }}>{entry.entryName}</span>
                  </div>
                ))}
            </div>
          </div>
        )}
      </Modal>

      {/* 条目新增/编辑弹窗 */}
      <Modal title={editingEntry ? '编辑条目' : '新增条目'}
        open={entryModalOpen} onOk={handleSaveEntry} onCancel={() => { setEntryModalOpen(false); entryForm.resetFields(); }}
        okText="保存" cancelText="取消" confirmLoading={entrySaving} width={420}>
        <Form form={entryForm} layout="vertical">
          <Form.Item name="entryCode" label="编码" rules={[{ required: true, message: '请输入编码' }]}>
            <Input placeholder="如：MORTGAGE" disabled={!!editingEntry} />
          </Form.Item>
          <Form.Item name="entryName" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：抵押" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序" initialValue={0}>
            <Input type="number" min={0} />
          </Form.Item>
          <Form.Item name="isActive" label="状态" initialValue={true}>
            <Select options={[
              { label: '启用', value: true },
              { label: '停用', value: false },
            ]} />
          </Form.Item>
        </Form>
      </Modal>

      <style>{`
        .ecl-card {
          background: #fff;
          border: 1px solid var(--color-border);
          border-radius: 8px;
          transition: box-shadow 0.2s;
        }
        .ecl-card:hover {
          box-shadow: 0 2px 8px rgba(0,0,0,0.06);
        }
      `}</style>
    </div>
  );
};

export default SchemeDictConfig;
