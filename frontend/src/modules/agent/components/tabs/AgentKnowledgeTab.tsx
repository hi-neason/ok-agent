import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "../../../shared";
import {
  listAgentBindings,
  listCatalog,
  listSources,
  replaceAgentBindings,
} from "../../../knowledge/api";
import type {
  AgentKnowledgeBinding,
  AgentKnowledgeBindingDraft,
  KnowledgeCatalogItem,
  KnowledgeSource,
} from "../../../knowledge/types";

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

type LocalBinding = {
  catalogItemId: string;
  descriptionOverride: string;
  topK: string;
  scoreThreshold: string;
};

const EMPTY: LocalBinding = {
  catalogItemId: "",
  descriptionOverride: "",
  topK: "",
  scoreThreshold: "",
};

function isBlank(s: string | null | undefined): boolean {
  return s === null || s === undefined || s.trim() === "";
}

export function AgentKnowledgeTab({ agentId }: { agentId: string }) {
  const { t } = useTranslation();
  const [sources, setSources] = useState<KnowledgeSource[]>([]);
  const [catalog, setCatalog] = useState<KnowledgeCatalogItem[]>([]);
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
          listSources(0, 1000),
          listAgentBindings(agentId),
        ]);
        if (cancelled) return;
        setSources(srcs.content);
        const catalogLists = await Promise.all(
          srcs.content
            .filter((s) => s.enabled)
            .map((s) => listCatalog(s.id).catch(() => [] as KnowledgeCatalogItem[])),
        );
        if (cancelled) return;
        setCatalog(catalogLists.flat());
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

  const toggle = (item: KnowledgeCatalogItem) => {
    setBindings((prev) => {
      const next = { ...prev };
      if (next[item.id]) {
        delete next[item.id];
      } else {
        next[item.id] = { ...EMPTY, catalogItemId: item.id };
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
      for (const b of Object.values(bindings)) {
        if (!isBlank(b.topK)) {
          const n = Number(b.topK);
          if (!Number.isInteger(n) || n < 1 || n > 50) {
            throw new Error(t("agents.binding.topKInvalid"));
          }
        }
        if (!isBlank(b.scoreThreshold)) {
          const n = Number(b.scoreThreshold);
          if (Number.isNaN(n) || n < 0 || n > 1) {
            throw new Error(t("agents.binding.thresholdInvalid"));
          }
        }
      }
      const payload: AgentKnowledgeBindingDraft[] = Object.values(bindings).map((b) => ({
        catalogItemId: b.catalogItemId,
        descriptionOverride: b.descriptionOverride.trim(),
        topK: isBlank(b.topK) ? null : Number(b.topK),
        scoreThreshold: isBlank(b.scoreThreshold) ? null : Number(b.scoreThreshold),
      }));
      const saved = await replaceAgentBindings(agentId, payload);
      setBindings(bindingsToMap(saved));
      setDirty(false);
      setNotice({ ok: true, text: t("agents.binding.saved", { name: t("agents.tab.knowledge") }) });
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="config-section">{t("common.loading")}</div>;

  const activeCatalog = catalog.filter((c) => c.active);

  return (
    <div className="config-section">
      <div className="section-head">
        <b>{t("agents.binding.knowledgeTitle")}</b>
        <small>{t("agents.binding.knowledgeHint")}</small>
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
          {t("agents.binding.knowledgeEmpty")}
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
                    {source?.name ?? item.sourceName} · {t("agents.binding.documents", { count: item.documentCount })}
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
                      {t(expanded === item.id ? "agents.binding.collapseTuning" : "agents.binding.advancedKnowledge")}
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
                            placeholder={t("agents.binding.knowledgeDescriptionPlaceholder")}
                          />
                        </label>
                        <div style={{ display: "flex", gap: 12 }}>
                          <label style={{ flex: 1 }}>
                            <span>{t("agents.binding.topK")}</span>
                            <input
                              type="number"
                              min={1}
                              max={50}
                              value={bindings[item.id]?.topK ?? ""}
                              onChange={(e) =>
                                updateBinding(item.id, { topK: e.target.value })
                              }
                              placeholder="5"
                            />
                          </label>
                          <label style={{ flex: 1 }}>
                            <span>{t("agents.binding.threshold")}</span>
                            <input
                              type="number"
                              step={0.05}
                              min={0}
                              max={1}
                              value={bindings[item.id]?.scoreThreshold ?? ""}
                              onChange={(e) =>
                                updateBinding(item.id, {
                                  scoreThreshold: e.target.value,
                                })
                              }
                              placeholder="0.5"
                            />
                          </label>
                        </div>
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
          {t(saving ? "common.saving" : "agents.binding.saveKnowledge")}
        </Button>
        {dirty && <span className="dirty-flag">{t("common.unsavedChanges")}</span>}
        {!dirty && (
          <span className="wf-binding-count">{t("agents.binding.boundCount", { count: boundCount })}</span>
        )}
      </div>
    </div>
  );
}

function bindingsToMap(
  bindings: AgentKnowledgeBinding[],
): Record<string, LocalBinding> {
  const map: Record<string, LocalBinding> = {};
  for (const b of bindings) {
    map[b.catalogItemId] = {
      catalogItemId: b.catalogItemId,
      descriptionOverride: b.descriptionOverride ?? "",
      topK: b.topK === null || b.topK === undefined ? "" : String(b.topK),
      scoreThreshold:
        b.scoreThreshold === null || b.scoreThreshold === undefined
          ? ""
          : String(b.scoreThreshold),
    };
  }
  return map;
}
