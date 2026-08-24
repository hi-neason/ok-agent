import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { loadAgents, type AgentOption } from "../../api";
import { loadIntentTree, flatten } from "../../../intent/api";
import type { IntentDto, IntentNode } from "../../../intent/types";
import type { AgentForm, AgentSubagentConfig } from "../../types";

export function AgentSubAgentTab({
  form,
  setField,
  currentAgentId,
}: {
  form: AgentForm;
  setField: (key: "subagents", value: AgentSubagentConfig[]) => void;
  currentAgentId: string;
}) {
  const { t } = useTranslation();
  const subagents = form.subagents ?? [];
  const [agents, setAgents] = useState<AgentOption[]>([]);
  const [intentTree, setIntentTree] = useState<IntentNode[]>([]);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  const toggleCollapsed = (key: string) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  useEffect(() => {
    let alive = true;
    loadAgents().then((list) => {
      if (alive) setAgents(list);
    });
    loadIntentTree()
      .then((tree) => {
        if (alive) setIntentTree(tree);
      })
      .catch(() => {
        /* best effort */
      });
    return () => {
      alive = false;
    };
  }, []);

  const allIntents = useMemo(() => flatten(intentTree), [intentTree]);

  const update = (next: AgentSubagentConfig[]) => setField("subagents", next);

  const addOne = () =>
    update([...subagents, { agentId: null, intentKeys: [] }]);

  const removeAt = (idx: number) => update(subagents.filter((_, i) => i !== idx));

  const patch = (idx: number, p: Partial<AgentSubagentConfig>) =>
    update(subagents.map((s, i) => (i === idx ? { ...s, ...p } : s)));

  function isClaimedElsewhere(idx: number, intentKey: string) {
    return subagents.some((s, i) => i !== idx && s.intentKeys.includes(intentKey));
  }
  const descendants = useMemo(() => {
    const map = new Map<string, string[]>();
    const collectKeys = (node: IntentNode): string[] => {
      const out = [node.node.intentKey];
      for (const c of node.children) out.push(...collectKeys(c));
      return out;
    };
    const walk = (nodes: IntentNode[]) => {
      for (const n of nodes) {
        map.set(n.node.intentKey, collectKeys(n));
        walk(n.children);
      }
    };
    walk(intentTree);
    return map;
  }, [intentTree]);

  const toggleIntent = (idx: number, intentKey: string) => {
    const s = subagents[idx];
    const current = new Set(s.intentKeys);
    const family = descendants.get(intentKey) ?? [intentKey];
    const allOn = family.every((k) => current.has(k));
    if (allOn) {
      family.forEach((k) => current.delete(k));
    } else {
      family.forEach((k) => {
        // can't add an intent claimed by another subagent
        if (!isClaimedElsewhere(idx, k)) current.add(k);
      });
    }
    patch(idx, { intentKeys: [...current] });
  };

  // agents already referenced (can't pick twice) — plus exclude self
  const referencedAgentIds = useMemo(
    () => new Set(subagents.map((s) => s.agentId).filter(Boolean) as string[]),
    [subagents],
  );

  const agentName = (id: string | null) =>
    id ? agents.find((a) => a.id === id)?.name ?? t("agents.subagents.deleted") : "";

  const renderIntentTree = (
    nodes: IntentNode[],
    selected: Set<string>,
    idx: number,
    depth = 0,
  ): ReactNode => {
    return nodes.map((n) => {
      const hasChildren = n.children.length > 0;
      const isCollapsed = collapsed.has(n.node.intentKey);
      const family = descendants.get(n.node.intentKey) ?? [n.node.intentKey];
      const checkedCount = family.filter((k) => selected.has(k)).length;
      const allChecked = checkedCount === family.length;
      const someChecked = checkedCount > 0 && !allChecked;
      const claimedByOther =
        !selected.has(n.node.intentKey) && isClaimedElsewhere(idx, n.node.intentKey);
      const claimer =
        claimedByOther && n.node.intentKey
          ? subagents.find(
              (s, i) => i !== idx && s.intentKeys.includes(n.node.intentKey),
            )
          : null;
      const claimerName = claimer ? agentName(claimer.agentId) : "";
      const nodeClasses = [
        "intent-tree-node",
        "intent-check-node",
        hasChildren ? "is-parent" : "",
        claimedByOther ? "is-disabled" : "",
        allChecked ? "active" : "",
      ]
        .filter(Boolean)
        .join(" ");
      return (
        <div key={n.node.id} className="intent-tree-branch">
          <div className="intent-tree-row">
            {hasChildren ? (
              <button
                type="button"
                className="intent-chevron"
                style={{ marginLeft: depth * 14 }}
                onClick={() => toggleCollapsed(n.node.intentKey)}
                aria-label={t(isCollapsed ? "common.expand" : "common.collapse")}
              >
                {isCollapsed ? "▸" : "▾"}
              </button>
            ) : (
              <span
                className="intent-chevron placeholder"
                style={{ marginLeft: depth * 14 }}
              >
                ·
              </span>
            )}
            <label className={nodeClasses}>
              <input
                type="checkbox"
                checked={allChecked}
                ref={(el) => {
                  if (el) el.indeterminate = someChecked;
                }}
                disabled={claimedByOther}
                onChange={() => toggleIntent(idx, n.node.intentKey)}
              />
              <span className="intent-name">{n.node.name}</span>
              {claimedByOther && claimerName && (
                <em className="intent-claimed-by">{t("agents.subagents.claimed", { name: claimerName })}</em>
              )}
            </label>
          </div>
          {hasChildren && !isCollapsed && (
            <div className="intent-tree-children">
              {renderIntentTree(n.children, selected, idx, depth + 1)}
            </div>
          )}
        </div>
      );
    });
  };

  return (
    <div className="agent-form-grid">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <p className="form-title" style={{ margin: 0 }}>
          {t("agents.tab.subagents")}
        </p>
        <button className="ui-button quiet" onClick={addOne}>
          ＋ {t("agents.subagents.add")}
        </button>
      </div>
      <p className="form-hint">
        {t("agents.subagents.hint")}
      </p>

      {subagents.length === 0 && (
        <div className="empty-state">
          {t("agents.subagents.empty")}
        </div>
      )}

      {subagents.map((s, idx) => {
        const selected = new Set(s.intentKeys);
        const agent = s.agentId ? agents.find((a) => a.id === s.agentId) : null;
        // options: exclude self + already-referenced (except current)
        const options = agents.filter(
          (a) =>
            a.id !== currentAgentId &&
            (!referencedAgentIds.has(a.id) || a.id === s.agentId),
        );
        return (
          <div className="subagent-card" key={idx}>
            <div className="subagent-card-head">
              <strong>{agent ? agent.name : t("agents.subagents.fallbackName", { index: idx + 1 })}</strong>
              <button className="link-button danger" onClick={() => removeAt(idx)}>
                {t("common.remove")}
              </button>
            </div>
            <div className="subagent-grid">
              <label className="span-2">
                <span>{t("agents.subagents.reference")}</span>
                <select
                  value={s.agentId ?? ""}
                  onChange={(e) =>
                    patch(idx, { agentId: e.target.value || null })
                  }
                >
                  <option value="">{t("agents.subagents.select")}</option>
                  {options.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}（{a.agentKey}）
                    </option>
                  ))}
                </select>
              </label>

              {agent && (
                <div className="span-2 subagent-meta">
                  <div>
                    <span className="meta-label">Key</span>
                    <code>{agent.agentKey}</code>
                  </div>
                  {agent.description && (
                    <div>
                      <span className="meta-label">{t("agents.subagents.description")}</span>
                      <span>{agent.description}</span>
                    </div>
                  )}
                  <div className="meta-hint">
                    {t("agents.subagents.inherited")}
                  </div>
                </div>
              )}

              <div className="span-2 subagent-intents">
                <details>
                  <summary>
                    {t("agents.subagents.intents")}
                    <span className="subagent-intent-count">{selected.size}</span>
                  </summary>
                  {allIntents.length === 0 ? (
                    <div className="form-hint" style={{ margin: "8px 0 0" }}>
                      {t("agents.subagents.noIntents")}
                    </div>
                  ) : (
                    <div className="intent-picker">
                      <div className="intent-picker-bar">
                        <button
                          type="button"
                          className="link-button"
                          onClick={() => {
                            const next = new Set(s.intentKeys);
                            allIntents.forEach((it) => {
                              if (!isClaimedElsewhere(idx, it.intentKey)) {
                                next.add(it.intentKey);
                              }
                            });
                            patch(idx, { intentKeys: [...next] });
                          }}
                        >
                          {t("agents.subagents.selectAvailable")}
                        </button>
                        <button
                          type="button"
                          className="link-button"
                          onClick={() => patch(idx, { intentKeys: [] })}
                        >
                          {t("agents.subagents.clear")}
                        </button>
                      </div>
                      <div className="intent-tree intent-check-tree">
                        {renderIntentTree(intentTree, selected, idx)}
                      </div>
                    </div>
                  )}
                </details>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
