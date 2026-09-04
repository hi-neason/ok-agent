import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "../../../shared";
import {
  listAgentBindings,
  listCatalog,
  listAllSources,
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
  const { t } = useTranslation();
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
          listAllSources(),
          listAgentBindings(agentId),
        ]);
        if (cancelled) return;
        setSources(srcs);
        const catalogLists = await Promise.all(
          srcs.filter((s) => s.enabled).map((s) => listCatalog(s.id)),
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
      setNotice({ ok: true, text: t("agents.binding.saved", { name: t("agents.tab.workflows") }) });
    } catch (e) {
      setNotice({
        ok: false,
        text: e instanceof SyntaxError ? t("agents.binding.invalidJson", { message: e.message }) : msg(e),
      });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="config-section">{t("common.loading")}</div>;

  const activeCatalog = catalog.filter((c) => c.active);

  return (
    <div className="config-section">
      <div className="section-head">
        <b>{t("agents.binding.workflowTitle")}</b>
        <small>{t("agents.binding.workflowHint")}</small>
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
          {t("agents.binding.workflowEmpty")}
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
                        t("agents.binding.noDescription")}
                    </p>
                    <button
                      type="button"
                      className="link-button"
                      onClick={() =>
                        setExpanded(expanded === item.id ? null : item.id)
                      }
                    >
                      {t(expanded === item.id ? "agents.binding.collapseTuning" : "agents.binding.advancedWorkflow")}
                    </button>
                    {expanded === item.id && (
                      <div className="wf-binding-overrides">
                        <label>
                          <span>{t("agents.binding.agentDescription")}</span>
                          <textarea
                            rows={2}
                            value={bindings[item.id]?.descriptionOverride ?? ""}
                            onChange={(e) =>
                              updateBinding(item.id, {
                                descriptionOverride: e.target.value,
                              })
                            }
                            placeholder={t("agents.binding.workflowDescriptionPlaceholder")}
                          />
                        </label>
                        <label>
                          <span>{t("agents.binding.parameterDefaults")}</span>
                          <textarea
                            rows={3}
                            spellCheck={false}
                            value={bindings[item.id]?.parameterDefaults ?? ""}
                            onChange={(e) =>
                              updateBinding(item.id, {
                                parameterDefaults: e.target.value,
                              })
                            }
                            placeholder='{"city": "Chongqing"}'
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
          {t(saving ? "common.saving" : "agents.binding.saveWorkflow")}
        </Button>
        {dirty && <span className="dirty-flag">{t("common.unsavedChanges")}</span>}
        {!dirty && boundCount >= 0 && (
          <span className="wf-binding-count">{t("agents.binding.boundCount", { count: Object.keys(bindings).length })}</span>
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
