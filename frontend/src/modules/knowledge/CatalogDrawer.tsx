import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Button } from "../shared";
import { listCatalog, updateCatalogDescription } from "./api";
import { SOURCE_TYPE_LABELS, type KnowledgeCatalogItem, type KnowledgeSource } from "./types";

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function formatNumber(n: number): string {
  return new Intl.NumberFormat("zh-CN").format(n);
}

export function CatalogDrawer({
  source,
  onClose,
  onChanged,
}: {
  source: KnowledgeSource;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [items, setItems] = useState<KnowledgeCatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<KnowledgeCatalogItem | null>(null);
  const [editingDesc, setEditingDesc] = useState(false);
  const [descText, setDescText] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = async () => {
    setLoading(true);
    setError("");
    try {
      const list = await listCatalog(source.id);
      setItems(list);
      setSelected((cur) => {
        if (cur) return list.find((i) => i.id === cur.id) ?? list[0] ?? null;
        return list[0] ?? null;
      });
    } catch (e) {
      setError(msg(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source.id]);

  const startEdit = (item: KnowledgeCatalogItem) => {
    setDescText(item.description || item.remoteDescription || "");
    setEditingDesc(true);
  };

  const saveDescription = async () => {
    if (!selected) return;
    setSaving(true);
    try {
      await updateCatalogDescription(selected.id, descText);
      setEditingDesc(false);
      await load();
      onChanged();
    } catch (e) {
      setError(msg(e));
    } finally {
      setSaving(false);
    }
  };

  return createPortal(
    <div className="model-modal-mask" onMouseDown={onClose}>
      <div
        className="wf-catalog-drawer"
        role="dialog"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <header>
          <div>
            <p className="kicker">KNOWLEDGE CATALOG / {source.sourceKey}</p>
            <h2>{source.name} · 知识库目录</h2>
            <small>
              知识库的「适用场景描述」由平台管理员在此维护一次，所有 Agent
              共享；模型据此判断何时检索该知识库。
            </small>
          </div>
          <button className="link-button" onClick={onClose}>
            关闭 ×
          </button>
        </header>

        {error && <div className="skill-error">× {error}</div>}

        {loading ? (
          <div className="wf-catalog-empty">加载中…</div>
        ) : items.length === 0 ? (
          <div className="wf-catalog-empty">
            尚未发现知识库。先在列表点击「同步」从{" "}
            {SOURCE_TYPE_LABELS[source.sourceType]} 拉取。
          </div>
        ) : (
          <div className="wf-catalog-body">
            <aside>
              {items.map((item) => (
                <button
                  key={item.id}
                  className={selected?.id === item.id ? "selected" : ""}
                  onClick={() => {
                    setSelected(item);
                    setEditingDesc(false);
                  }}
                >
                  <i>⌁</i>
                  <span>
                    <b>{item.name}</b>
                    <small>
                      {item.documentCount} 文档 · {formatNumber(item.wordCount)} 字
                    </small>
                  </span>
                </button>
              ))}
            </aside>
            <main>
              {selected && (
                <>
                  <div className="wf-catalog-detail-head">
                    <div>
                      <h3>{selected.name}</h3>
                      <code>{selected.remoteKnowledgeId}</code>
                      <span
                        className={`tag ${selected.metadataStatus === "READY" ? "green" : ""}`}
                      >
                        {selected.metadataStatus === "READY" ? "READY" : "NEEDS REVIEW"}
                      </span>
                    </div>
                    {!editingDesc && (
                      <button className="link-button" onClick={() => startEdit(selected)}>
                        编辑描述
                      </button>
                    )}
                  </div>

                  <div className="kb-stats">
                    <span>{selected.documentCount} 个文档</span>
                    <span>{formatNumber(selected.wordCount)} 字</span>
                    {selected.tags.length > 0 && (
                      <span className="kb-tags">
                        {selected.tags.map((t) => (
                          <code key={t}>{t}</code>
                        ))}
                      </span>
                    )}
                  </div>

                  {selected.remoteDescription && (
                    <p className="wf-catalog-remote-desc">{selected.remoteDescription}</p>
                  )}

                  <div className="wf-catalog-field">
                    <label>适用场景描述（所有 Agent 共享）</label>
                    {editingDesc ? (
                      <>
                        <textarea
                          value={descText}
                          rows={4}
                          onChange={(e) => setDescText(e.target.value)}
                          placeholder="描述这个知识库包含什么内容、什么问题该检索它…"
                        />
                        <div className="wf-catalog-actions">
                          <Button quiet onClick={() => setEditingDesc(false)}>
                            取消
                          </Button>
                          <Button onClick={() => void saveDescription()} disabled={saving}>
                            {saving ? "保存中…" : "保存描述"}
                          </Button>
                        </div>
                      </>
                    ) : (
                      <p className="wf-catalog-desc">
                        {selected.description || (
                          <span className="wf-catalog-empty-inline">
                            尚未填写描述，建议补充适用场景以便模型正确选用。
                          </span>
                        )}
                      </p>
                    )}
                  </div>
                </>
              )}
            </main>
          </div>
        )}
      </div>
    </div>,
    document.body,
  );
}
