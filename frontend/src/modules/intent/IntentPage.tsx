import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation();
  const [tree, setTree] = useState<IntentNode[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTree(await loadIntentTree());
    } catch {
      setNotice({ ok: false, text: t("intents.loadFailed") });
    } finally {
      setLoading(false);
    }
  }, [t]);

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
      setNotice({ ok: false, text: t("intents.keyRequired") });
      return;
    }
    if (!form.name.trim()) {
      setNotice({ ok: false, text: t("intents.nameRequired") });
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
      setNotice({ ok: true, text: t("intents.saved") });
    } catch (e) {
      setNotice({ ok: false, text: e instanceof Error ? e.message : t("intents.saveFailed") });
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!selected) return;
    if (!window.confirm(t("intents.deleteConfirm", { name: selected.name }))) return;
    try {
      await deleteIntent(selected.id);
      setSelectedId(null);
      setForm(EMPTY);
      await load();
      setNotice({ ok: true, text: t("intents.deleted") });
    } catch {
      setNotice({ ok: false, text: t("intents.deleteFailed") });
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
                aria-label={isCollapsed ? t("intents.expand") : t("intents.collapse")}
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
      ? t("intents.root")
      : allIntents.find((i) => i.id === form.parentId)?.name ?? t("intents.root");

  return (
    <>
      <PageHeader
        kicker={t("intents.kicker")}
        title={t("intents.title")}
        description={t("intents.description")}
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
            <div className="empty-state">{t("common.loading")}</div>
          ) : (
            <>
              <div className="intent-tree-row">
                <button
                  className="intent-chevron"
                  onClick={() => toggleCollapsed(ROOT_ID)}
                  aria-label={collapsed.has(ROOT_ID) ? t("intents.expand") : t("intents.collapse")}
                >
                  {collapsed.has(ROOT_ID) ? "▸" : "▾"}
                </button>
                <button
                  className={isRootSelected ? "intent-tree-node root active" : "intent-tree-node root"}
                  onClick={selectRoot}
                >
                  <span className="intent-name">{t("intents.all")}</span>
                </button>
              </div>
              {!collapsed.has(ROOT_ID) &&
                (tree.length === 0 ? (
                  <div className="intent-empty-hint">{t("intents.treeEmpty")}</div>
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
                ? t("intents.rootHint")
                : t("intents.selectHint")}
              {isRootSelected && (
                <div className="intent-empty-action">
                  <Button onClick={() => startNew(null)}>＋ {t("intents.createRoot")}</Button>
                </div>
              )}
            </div>
          ) : (
            <>
              <div className="intent-editor-head">
                <h2>
                  {isNew
                    ? t("intents.createTitle", {
                        type: form.parentId == null ? t("intents.root") : t("intents.child"),
                        parent: newParentName,
                      })
                    : t("intents.editTitle")}
                </h2>
                {!isNew && (
                  <button className="link-button danger" onClick={remove}>
                    {t("common.delete")}
                  </button>
                )}
              </div>
              <div className="intent-form">
                <div className="intent-form-row">
                  <label>
                    <span>{t("intents.key")}</span>
                    <input
                      value={form.intentKey}
                      disabled={!isNew}
                      onChange={(e) => setForm({ ...form, intentKey: e.target.value })}
                    />
                  </label>
                  <label>
                    <span>{t("intents.name")}</span>
                    <input
                      value={form.name}
                      onChange={(e) => setForm({ ...form, name: e.target.value })}
                    />
                  </label>
                  <label>
                    <span>{t("intents.parent")}</span>
                    <select
                      value={form.parentId ?? ""}
                      onChange={(e) => setForm({ ...form, parentId: e.target.value || null })}
                    >
                      <option value="">{t("intents.rootOption")}</option>
                      {parentOptions.map((i) => (
                        <option key={i.id} value={i.id}>
                          {i.name}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                <label className="span-2">
                  <span>{t("intents.descriptionLabel")}</span>
                  <textarea
                    rows={2}
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                  />
                </label>
                <label className="span-2">
                  <span>{t("intents.examples")}</span>
                  <textarea
                    rows={4}
                    value={form.examplesText}
                    placeholder={t("intents.examplesPlaceholder")}
                    onChange={(e) => setForm({ ...form, examplesText: e.target.value })}
                  />
                </label>
                <label>
                  <span>{t("intents.sortOrder")}</span>
                  <input
                    type="number"
                    value={form.sortOrder}
                    onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })}
                  />
                </label>
              </div>
              <div className="config-save-bar">
                <Button onClick={save} disabled={saving}>
                  {saving ? t("common.saving") : t("common.save")}
                </Button>
                <button className="ui-button quiet" onClick={() => startNew(form.parentId)}>
                  ＋ {form.parentId == null ? t("intents.createRoot") : t("intents.createChild")}
                </button>
              </div>
            </>
          )}
        </section>
      </div>
    </>
  );
}
