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
  const [selected, setSelected] = useState<IntentDto | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);

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

  // Parent options: everything except the selected node and its descendants.
  const parentOptions = useMemo(() => {
    if (!selected) return allIntents;
    const banned = new Set<string>();
    const start = tree.find((n) => n.node.id === selected.id);
    if (start) collectDescendantIds(start, banned);
    return allIntents.filter((i) => !banned.has(i.id));
  }, [allIntents, selected, tree]);

  const selectNode = (dto: IntentDto) => {
    setSelected(dto);
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

  const startNew = (parentId: string | null) => {
    setSelected(null);
    setIsNew(true);
    setForm({ ...EMPTY, parentId });
    setNotice(null);
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
        await createIntent({
          intentKey: form.intentKey.trim(),
          name: form.name.trim(),
          parentId: form.parentId,
          description: form.description,
          examples,
          sortOrder: form.sortOrder,
        });
      } else if (selected) {
        await updateIntent(selected.id, {
          name: form.name.trim(),
          parentId: form.parentId,
          description: form.description,
          examples,
          sortOrder: form.sortOrder,
        });
      }
      await load();
      setNotice({ ok: true, text: "已保存" });
      setIsNew(false);
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
      setSelected(null);
      setForm(EMPTY);
      await load();
      setNotice({ ok: true, text: "已删除" });
    } catch {
      setNotice({ ok: false, text: "删除失败（可能仍存在子意图）" });
    }
  };

  const renderTree = (nodes: IntentNode[], depth: number) =>
    nodes.map((n) => (
      <div key={n.node.id}>
        <button
          className={selected?.id === n.node.id ? "intent-tree-node active" : "intent-tree-node"}
          style={{ paddingLeft: 8 + depth * 14 }}
          onClick={() => selectNode(n.node)}
        >
          <span className="intent-key">{n.node.intentKey}</span>
          <span className="intent-name">{n.node.name}</span>
        </button>
        {n.children.length > 0 && renderTree(n.children, depth + 1)}
      </div>
    ));

  return (
    <>
      <PageHeader
        kicker="BUSINESS / INTENT TREE"
        title="意图管理"
        description="管理客服意图树与每个意图的业务语义（名称、描述、示例 query）。意图与子 Agent 的路由绑定在「Agent 配置 → SubAgent 配置」里维护。"
        action={<Button onClick={() => startNew(null)}>＋ 新建根意图</Button>}
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
          ) : tree.length === 0 ? (
            <div className="empty-state">暂无意图，点击右上角新建根意图</div>
          ) : (
            renderTree(tree, 0)
          )}
        </aside>
        <section className="intent-editor">
          {!selected && !isNew ? (
            <div className="empty-state">从左侧选择意图，或新建一个意图</div>
          ) : (
            <>
              <div className="intent-editor-head">
                <h2>{isNew ? "新建意图" : "编辑意图"}</h2>
                {!isNew && (
                  <button className="link-button danger" onClick={remove}>
                    删除
                  </button>
                )}
              </div>
              <div className="intent-form">
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
                        {i.name}（{i.intentKey}）
                      </option>
                    ))}
                  </select>
                </label>
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
                  ＋ 新建子意图
                </button>
              </div>
            </>
          )}
        </section>
      </div>
    </>
  );
}
