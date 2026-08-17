import { useEffect, useMemo, useState } from "react";
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
          listSources(),
          listAgentBindings(agentId),
        ]);
        if (cancelled) return;
        setSources(srcs);
        const catalogLists = await Promise.all(
          srcs
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
            throw new Error("topK 需为 1–50 的整数（或留空使用默认值）");
          }
        }
        if (!isBlank(b.scoreThreshold)) {
          const n = Number(b.scoreThreshold);
          if (Number.isNaN(n) || n < 0 || n > 1) {
            throw new Error("scoreThreshold 需为 0–1 的数值（或留空）");
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
      setNotice({ ok: true, text: "知识库绑定已保存" });
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="config-section">加载中…</div>;

  const activeCatalog = catalog.filter((c) => c.active);

  return (
    <div className="config-section">
      <div className="section-head">
        <b>外部知识库</b>
        <small>
          勾选后，Agent 运行时将获得 list_knowledge_bases / search_knowledge
          工具，模型按需检索（agentic RAG），并基于返回片段作答。检索参数与描述由「知识库
          - 集成」目录统一维护，此处仅做绑定与可选微调。
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
          暂无可用知识库。请先到「知识库 - 集成」添加源并同步（且源需启用）。
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
                    {source?.name ?? item.sourceName} · {item.documentCount} 文档
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
                      {expanded === item.id ? "收起微调" : "高级：描述覆盖 / 检索参数"}
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
                            placeholder="覆盖该 Agent 看到的知识库描述"
                          />
                        </label>
                        <div style={{ display: "flex", gap: 12 }}>
                          <label style={{ flex: 1 }}>
                            <span>topK（返回片段数，留空=默认）</span>
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
                            <span>scoreThreshold（0–1，留空=不过滤）</span>
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
          {saving ? "保存中…" : "保存知识库绑定"}
        </Button>
        {dirty && <span className="dirty-flag">未保存的改动</span>}
        {!dirty && (
          <span className="wf-binding-count">已绑定 {boundCount} 个</span>
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
