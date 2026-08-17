import { useEffect, useMemo, useState } from "react";
import { Button } from "../../../shared";
import {
  listAgentBindings,
  listCatalog,
  listSources,
  replaceAgentBindings,
} from "../../../workflow/api";
import type {
  AgentWorkflowBinding,
  AgentWorkflowBindingDraft,
  WorkflowCatalogItem,
  WorkflowSource,
} from "../../../workflow/types";

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

type LocalBinding = {
  catalogItemId: string;
  descriptionOverride: string;
  parameterDefaults: string;
};

export function AgentWorkflowTab({ agentId }: { agentId: string }) {
  const [sources, setSources] = useState<WorkflowSource[]>([]);
  const [catalog, setCatalog] = useState<WorkflowCatalogItem[]>([]);
  const [bindings, setBindings] = useState<Record<string, LocalBinding>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      setLoading(true);
      try {
        const [srcs, current] = await Promise.all([
          listSources(),
          listAgentBindings(agentId),
        ]);
        if (cancelled) return;
        setSources(srcs);
        const catalogLists = await Promise.all(
          srcs.filter((s) => s.enabled).map((s) => listCatalog(s.id).catch(() => [] as WorkflowCatalogItem[])),
        );
        if (cancelled) return;
        const all = catalogLists.flat();
        setCatalog(all);
        setBindings(bindingsToMap(current));
      } catch (e) {
        if (!cancelled) setNotice({ ok: false, text: msg(e) });
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [agentId]);

  const boundCount = useMemo(
    () => Object.values(bindings).filter((b) => b.catalogItemId).length,
    [bindings],
  );

  const toggle = (item: WorkflowCatalogItem) => {
    setBindings((prev) => {
      const next = { ...prev };
      if (next[item.id]) {
        delete next[item.id];
      } else {
        next[item.id] = {
          catalogItemId: item.id,
          descriptionOverride: "",
          parameterDefaults: "",
        };
      }
      return next;
    });
    setDirty(true);
  };

  const updateBinding = (itemId: string, patch: Partial<LocalBinding>) => {
    setBindings((prev) => ({
      ...prev,
      [itemId]: { ...prev[itemId], ...patch },
    }));
    setDirty(true);
  };

  const save = async () => {
    setSaving(true);
    setNotice(null);
    try {
      // Validate parameter defaults are valid JSON if provided.
      for (const b of Object.values(bindings)) {
        if (b.parameterDefaults.trim()) {
          JSON.parse(b.parameterDefaults);
        }
      }
      const payload: AgentWorkflowBindingDraft[] = Object.values(bindings).map((b) => ({
        catalogItemId: b.catalogItemId,
        descriptionOverride: b.descriptionOverride.trim(),
        parameterDefaults: b.parameterDefaults.trim(),
      }));
      const saved = await replaceAgentBindings(agentId, payload);
      setBindings(bindingsToMap(saved));
      setDirty(false);
      setNotice({ ok: true, text: "工作流绑定已保存" });
    } catch (e) {
      setNotice({
        ok: false,
        text: e instanceof SyntaxError ? `参数默认值不是合法 JSON：${e.message}` : msg(e),
      });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="config-section">加载中…</div>;

  const activeCatalog = catalog.filter((c) => c.active);

  return (
    <div className="config-section">
      <div className="section-head">
        <b>外部工作流</b>
        <small>
          勾选后，Agent 运行时将获得 list_workflows / describe_workflow /
          start_workflow 三个工具，模型可自主发现并调用。描述与入参 schema
          由「工作流 - 集成」目录统一维护，此处仅做绑定与可选微调。
        </small>
      </div>

      {notice && (
        <div className={`mcp-notice ${notice.ok ? "success" : "error"}`}>
          <b>
            {notice.ok ? "✓" : "×"} {notice.text}
          </b>
        </div>
      )}

      {activeCatalog.length === 0 ? (
        <small style={{ padding: 8 }}>
          暂无可用工作流。请先到「工作流 - 集成」添加源并同步（且源需启用）。
        </small>
      ) : (
        <div className="binding-list">
          {activeCatalog.map((item) => {
            const bound = !!bindings[item.id];
            const source = sources.find((s) => s.id === item.sourceId);
            return (
              <div
                key={item.id}
                className={`binding-item binding-card ${bound ? "selected" : ""}`}
              >
                <input
                  type="checkbox"
                  checked={bound}
                  onChange={() => toggle(item)}
                />
                <span className="meta">
                  <b>{item.name}</b>
                  <small>
                    {source?.name ?? item.sourceName} · {item.remoteWorkflowId}
                  </small>
                </span>
                {bound && (
                  <div className="wf-binding-detail">
                    <p className="wf-binding-desc">
                      {item.description ||
                        item.remoteDescription ||
                        "（目录未填写描述）"}
                    </p>
                    <button
                      type="button"
                      className="link-button"
                      onClick={() =>
                        setExpanded(expanded === item.id ? null : item.id)
                      }
                    >
                      {expanded === item.id ? "收起微调" : "高级：描述覆盖 / 参数默认值"}
                    </button>
                    {expanded === item.id && (
                      <div className="wf-binding-overrides">
                        <label>
                          <span>Agent 专属描述（可选，留空用目录描述）</span>
                          <textarea
                            rows={2}
                            value={bindings[item.id]?.descriptionOverride ?? ""}
                            onChange={(e) =>
                              updateBinding(item.id, {
                                descriptionOverride: e.target.value,
                              })
                            }
                            placeholder="覆盖该 Agent 看到的工作流描述"
                          />
                        </label>
                        <label>
                          <span>参数默认值 JSON（可选）</span>
                          <textarea
                            rows={3}
                            spellCheck={false}
                            value={bindings[item.id]?.parameterDefaults ?? ""}
                            onChange={(e) =>
                              updateBinding(item.id, {
                                parameterDefaults: e.target.value,
                              })
                            }
                            placeholder='{"city": "重庆"}'
                          />
                        </label>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      <div className="config-save-bar">
        <Button onClick={() => void save()} disabled={saving || !dirty}>
          {saving ? "保存中…" : "保存工作流绑定"}
        </Button>
        {dirty && <span className="dirty-flag">未保存的改动</span>}
        {!dirty && boundCount >= 0 && (
          <span className="wf-binding-count">已绑定 {Object.keys(bindings).length} 个</span>
        )}
      </div>
    </div>
  );
}

function bindingsToMap(
  bindings: AgentWorkflowBinding[],
): Record<string, LocalBinding> {
  const map: Record<string, LocalBinding> = {};
  for (const b of bindings) {
    map[b.catalogItemId] = {
      catalogItemId: b.catalogItemId,
      descriptionOverride: b.descriptionOverride ?? "",
      parameterDefaults: b.parameterDefaultsJson ?? "",
    };
  }
  return map;
}
