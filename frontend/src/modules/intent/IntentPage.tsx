import { useCallback, useEffect, useMemo, useState } from "react";
import { PageHeader, Button } from "../shared";
import {
  createIntent,
  deleteIntent,
  flatten,
  loadIntentTree,
  updateIntent,
} from "./api";
import type { IntentDto, IntentNode } from "./types";
import "./intent.css";

type FormState = {
  intentKey: string;
  name: string;
  parentId: string | null;
  description: string;
  examplesText: string;
  sortOrder: number;
};

const ROOT_ID = "__root__";

const EMPTY: FormState = {
  intentKey: "",
  name: "",
  parentId: null,
  description: "",
  examplesText: "",
  sortOrder: 0,
};

function collectDescendantIds(node: IntentNode, acc: Set<string>) {
  acc.add(node.node.id);
  node.children.forEach((c) => collectDescendantIds(c, acc));
}

export function IntentPage() {
  const [tree, setTree] = useState<IntentNode[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set([ROOT_ID]));

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTree(await loadIntentTree());
    } catch {
      setNotice({ ok: false, text: "加载意图树失败" });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const allIntents = useMemo(() => flatten(tree), [tree]);

  const selected = useMemo<IntentDto | null>(() => {
    if (!selectedId || selectedId === ROOT_ID) return null;
    return allIntents.find((i) => i.id === selectedId) ?? null;
  }, [selectedId, allIntents]);

  // Parent options: everything except the edited node and its descendants.
  const parentOptions = useMemo(() => {
    if (!isNew && !selected) return allIntents;
    const selfId = isNew ? null : selected!.id;
    const banned = new Set<string>();
    if (selfId) {
      const start = tree.find((n) => n.node.id === selfId);
      if (start) collectDescendantIds(start, banned);
    }
    return allIntents.filter((i) => !banned.has(i.id));
  }, [allIntents, selected, isNew, tree]);

  const selectNode = (dto: IntentDto) => {
    setSelectedId(dto.id);
    setIsNew(false);
    setForm({
      intentKey: dto.intentKey,
      name: dto.name,
      parentId: dto.parentId,
      description: dto.description,
      examplesText: (dto.examples ?? []).join("\n"),
      sortOrder: dto.sortOrder,
    });
    setNotice(null);
  };

  const selectRoot = () => {
    setSelectedId(ROOT_ID);
    setIsNew(false);
    setForm(EMPTY);
    setNotice(null);
  };

  const startNew = (parentId: string | null) => {
    setSelectedId(null);
    setIsNew(true);
    setForm({ ...EMPTY, parentId });
    setNotice(null);
  };

  const toggleCollapsed = (id: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const save = async () => {
    if (!form.intentKey.trim() && isNew) {
      setNotice({ ok: false, text: "意图 Key 不能为空" });
      return;
    }
    if (!form.name.trim()) {
      setNotice({ ok: false, text: "意图名称不能为空" });
      return;
    }
    setSaving(true);
    setNotice(null);
    const examples = form.examplesText
      .split("\n")
      .map((x) => x.trim())
      .filter(Boolean);
    try {
      if (isNew) {
        const created = await createIntent({
          intentKey: form.intentKey.trim(),
          name: form.name.trim(),
          parentId: form.parentId,
          description: form.description,
          examples,
          sortOrder: form.sortOrder,
        });
        await load();
        setSelectedId(created.id);
        setIsNew(false);
      } else if (selected) {
        await updateIntent(selected.id, {
          name: form.name.trim(),
          parentId: form.parentId,
          description: form.description,
          examples,
          sortOrder: form.sortOrder,
        });
        await load();
      }
      setNotice({ ok: true, text: "已保存" });
    } catch (e) {
      setNotice({ ok: false, text: e instanceof Error ? e.message : "保存失败" });
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!selected) return;
    if (!window.confirm(`确认删除意图「${selected.name}」？若存在子意图将被拒绝。`)) return;
    try {
      await deleteIntent(selected.id);
      setSelectedId(null);
      setForm(EMPTY);
      await load();
      setNotice({ ok: true, text: "已删除" });
    } catch {
      setNotice({ ok: false, text: "删除失败（可能仍存在子意图）" });
    }
  };

  const renderTree = (nodes: IntentNode[], depth: number) =>
    nodes.map((n) => {
      const hasChildren = n.children.length > 0;
      const isCollapsed = collapsed.has(n.node.id);
      return (
        <div key={n.node.id}>
          <div className="intent-tree-row">
            {hasChildren ? (
              <button
                className="intent-chevron"
                style={{ marginLeft: depth * 14 }}
                onClick={() => toggleCollapsed(n.node.id)}
                aria-label={isCollapsed ? "展开" : "折叠"}
              >
                {isCollapsed ? "▸" : "▾"}
              </button>
            ) : (
              <span className="intent-chevron placeholder" style={{ marginLeft: depth * 14 }}>
                ·
              </span>
            )}
            <button
              className={
                selectedId === n.node.id ? "intent-tree-node active" : "intent-tree-node"
              }
              onClick={() => selectNode(n.node)}
            >
              <span className="intent-name">{n.node.name}</span>
            </button>
          </div>
          {hasChildren && !isCollapsed && renderTree(n.children, depth + 1)}
        </div>
      );
    });

  const isRootSelected = selectedId === ROOT_ID;
  const showEditor = isNew || selected;
  const newParentName =
    form.parentId == null
      ? "根意图"
      : allIntents.find((i) => i.id === form.parentId)?.name ?? "根意图";

  return (
    <>
      <PageHeader
        kicker="BUSINESS / INTENT TREE"
        title="意图管理"
        description="管理客服意图树与每个意图的业务语义（名称、描述、示例 query）。意图与子 Agent 的路由绑定在「Agent 配置 → SubAgent 配置」里维护。"
      />
      {notice && (
        <div className={notice.ok ? "connection-result connection-result--success" : "skill-error"}>
          {notice.ok ? "✓ " : "× "}
          {notice.text}
        </div>
      )}
      <div className="intent-layout">
        <aside className="intent-tree">
          {loading ? (
            <div className="empty-state">加载中…</div>
          ) : (
            <>
              <div className="intent-tree-row">
                <button
                  className="intent-chevron"
                  onClick={() => toggleCollapsed(ROOT_ID)}
                  aria-label={collapsed.has(ROOT_ID) ? "展开" : "折叠"}
                >
                  {collapsed.has(ROOT_ID) ? "▸" : "▾"}
                </button>
                <button
                  className={isRootSelected ? "intent-tree-node root active" : "intent-tree-node root"}
                  onClick={selectRoot}
                >
                  <span className="intent-name">全部意图</span>
                </button>
              </div>
              {!collapsed.has(ROOT_ID) &&
                (tree.length === 0 ? (
                  <div className="intent-empty-hint">暂无意图，选中「全部意图」后在右侧新建</div>
                ) : (
                  renderTree(tree, 1)
                ))}
            </>
          )}
        </aside>
        <section className="intent-editor">
          {!showEditor ? (
            <div className="empty-state">
              {isRootSelected
                ? "选中「全部意图」，点击下方「新建根意图」开始维护意图树。"
                : "从左侧选择意图查看详情，或在某个节点上新建子意图。"}
              {isRootSelected && (
                <div className="intent-empty-action">
                  <Button onClick={() => startNew(null)}>＋ 新建根意图</Button>
                </div>
              )}
            </div>
          ) : (
            <>
              <div className="intent-editor-head">
                <h2>
                  {isNew
                    ? `新建${form.parentId == null ? "根意图" : "子意图"}（上级：${newParentName}）`
                    : "编辑意图"}
                </h2>
                {!isNew && (
                  <button className="link-button danger" onClick={remove}>
                    删除
                  </button>
                )}
              </div>
              <div className="intent-form">
                <div className="intent-form-row">
                  <label>
                    <span>意图 Key（唯一）</span>
                    <input
                      value={form.intentKey}
                      disabled={!isNew}
                      onChange={(e) => setForm({ ...form, intentKey: e.target.value })}
                    />
                  </label>
                  <label>
                    <span>名称</span>
                    <input
                      value={form.name}
                      onChange={(e) => setForm({ ...form, name: e.target.value })}
                    />
                  </label>
                  <label>
                    <span>父意图</span>
                    <select
                      value={form.parentId ?? ""}
                      onChange={(e) => setForm({ ...form, parentId: e.target.value || null })}
                    >
                      <option value="">（根意图）</option>
                      {parentOptions.map((i) => (
                        <option key={i.id} value={i.id}>
                          {i.name}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                <label className="span-2">
                  <span>描述</span>
                  <textarea
                    rows={2}
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                  />
                </label>
                <label className="span-2">
                  <span>示例 query（每行一条）</span>
                  <textarea
                    rows={4}
                    value={form.examplesText}
                    placeholder={"我想贷款\n怎么申请额度\n还款方式有哪些"}
                    onChange={(e) => setForm({ ...form, examplesText: e.target.value })}
                  />
                </label>
                <label>
                  <span>排序</span>
                  <input
                    type="number"
                    value={form.sortOrder}
                    onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })}
                  />
                </label>
              </div>
              <div className="config-save-bar">
                <Button onClick={save} disabled={saving}>
                  {saving ? "保存中…" : "保存"}
                </Button>
                <button className="ui-button quiet" onClick={() => startNew(form.parentId)}>
                  ＋ {form.parentId == null ? "新建根意图" : "新建子意图"}
                </button>
              </div>
            </>
          )}
        </section>
      </div>
    </>
  );
}
