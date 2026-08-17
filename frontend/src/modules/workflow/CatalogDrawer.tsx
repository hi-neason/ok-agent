import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Button } from "../shared";
import { listCatalog, updateCatalogDescription } from "./api";
import { SOURCE_TYPE_LABELS, type WorkflowCatalogItem, type WorkflowSource } from "./types";

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function prettySchema(raw: string | null): string {
  if (!raw) return "";
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

export function CatalogDrawer({
  source,
  onClose,
  onChanged,
}: {
  source: WorkflowSource;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [items, setItems] = useState<WorkflowCatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<WorkflowCatalogItem | null>(null);
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

  const startEdit = (item: WorkflowCatalogItem) => {
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
            <p className="kicker">WORKFLOW CATALOG / {source.sourceKey}</p>
            <h2>{source.name} · 流程目录</h2>
            <small>
              流程的「适用场景描述」由平台管理员在此维护一次，所有 Agent
              共享；同步会自动发现入参 schema。
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
            尚未发现流程。先在列表点击「同步」从{" "}
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
                    <small>{item.remoteWorkflowId}</small>
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
                      <code>{selected.remoteWorkflowId}</code>
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
                          placeholder="描述这个工作流做什么、什么场景下该选用它、需要用户提供什么信息…"
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

                  <div className="wf-catalog-field">
                    <label>入参 Schema（同步自动发现）</label>
                    <pre className="wf-catalog-schema">
                      {prettySchema(selected.inputSchemaJson) || "（无）"}
                    </pre>
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
