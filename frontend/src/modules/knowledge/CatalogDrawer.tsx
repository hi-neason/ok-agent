import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Button } from "../shared";
import { listCatalog, updateCatalogDescription } from "./api";
import { SOURCE_TYPE_LABELS, type KnowledgeCatalogItem, type KnowledgeSource } from "./types";

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function formatNumber(n: number, locale: string): string {
  return new Intl.NumberFormat(locale).format(n);
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
  const { t, i18n } = useTranslation();
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
            <h2>{t("knowledge.catalogTitle", { name: source.name })}</h2>
            <small>{t("knowledge.catalogDescription")}</small>
          </div>
          <button className="link-button" onClick={onClose}>
            {t("common.close")} ×
          </button>
        </header>

        {error && <div className="skill-error">× {error}</div>}

        {loading ? (
          <div className="wf-catalog-empty">{t("common.loading")}</div>
        ) : items.length === 0 ? (
          <div className="wf-catalog-empty">
            {t("knowledge.catalogEmpty", { source: SOURCE_TYPE_LABELS[source.sourceType] })}
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
                      {t("knowledge.documentSummary", {
                        documents: item.documentCount,
                        words: formatNumber(item.wordCount, i18n.resolvedLanguage ?? "zh-CN"),
                      })}
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
                        {selected.metadataStatus === "READY" ? "READY" : t("integrations.needsReview")}
                      </span>
                    </div>
                    {!editingDesc && (
                      <button className="link-button" onClick={() => startEdit(selected)}>
                        {t("integrations.editDescription")}
                      </button>
                    )}
                  </div>

                  <div className="kb-stats">
                    <span>{t("knowledge.documents", { count: selected.documentCount })}</span>
                    <span>
                      {t("knowledge.words", {
                        value: formatNumber(selected.wordCount, i18n.resolvedLanguage ?? "zh-CN"),
                      })}
                    </span>
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
                    <label>{t("integrations.usageDescription")}</label>
                    {editingDesc ? (
                      <>
                        <textarea
                          value={descText}
                          rows={4}
                          onChange={(e) => setDescText(e.target.value)}
                          placeholder={t("knowledge.descriptionPlaceholder")}
                        />
                        <div className="wf-catalog-actions">
                          <Button quiet onClick={() => setEditingDesc(false)}>
                            {t("common.cancel")}
                          </Button>
                          <Button onClick={() => void saveDescription()} disabled={saving}>
                            {saving ? t("common.saving") : t("integrations.saveDescription")}
                          </Button>
                        </div>
                      </>
                    ) : (
                      <p className="wf-catalog-desc">
                        {selected.description || (
                          <span className="wf-catalog-empty-inline">
                            {t("integrations.descriptionMissing")}
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
