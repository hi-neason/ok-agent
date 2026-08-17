import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import type { AgentItem } from "./types";

function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  return createPortal(
    <div className="model-modal-mask" onMouseDown={onClose}>
      <div
        className="form-surface model-editor"
        role="dialog"
        aria-modal="true"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="form-title">
          <div>
            <p className="kicker">AGENT</p>
            <h2>{title}</h2>
          </div>
          <button className="link-button" onClick={onClose}>
            ×
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  );
}

export function AgentRegistryPage({
  onConfigure,
}: {
  onConfigure: (id: string) => void;
}) {
  const { t } = useTranslation();
  const [agents, setAgents] = useState<AgentItem[]>([]);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<AgentItem | "new" | null>(null);
  const [form, setForm] = useState({ name: "", description: "", businessDomain: "" });
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      const res = await fetch("/api/v1/agents");
      if (!res.ok) throw new Error();
      setAgents((await res.json()) as AgentItem[]);
    } catch {
      setError(t("agents.loadFailed"));
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const openNew = () => {
    setForm({ name: "", description: "", businessDomain: "" });
    setEditing("new");
    setError("");
  };

  const openEdit = (a: AgentItem) => {
    setForm({
      name: a.name,
      description: a.description,
      businessDomain: a.businessDomain,
    });
    setEditing(a);
    setError("");
  };

  const save = async () => {
    if (!form.name.trim()) {
      setError(t("agents.nameRequired"));
      return;
    }
    setSaving(true);
    setError("");
    try {
      const isNew = editing === "new";
      const id = isNew ? "" : (editing as AgentItem).id;
      const url = isNew ? "/api/v1/agents" : `/api/v1/agents/${id}`;
      const method = isNew ? "POST" : "PUT";
      const payload = {
        name: form.name.trim(),
        description: form.description.trim(),
        businessDomain: form.businessDomain.trim() || "GENERAL",
      };
      const res = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!res.ok) throw new Error();
      await load();
      setEditing(null);
    } catch {
      setError(t("agents.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (a: AgentItem) => {
    if (!window.confirm(t("agents.deleteConfirm", { name: a.name }))) return;
    const res = await fetch(`/api/v1/agents/${a.id}`, { method: "DELETE" });
    if (res.ok) await load();
  };

  return (
    <>
      <header className="page-header">
        <div>
          <p className="kicker">HARNESS AGENT / REGISTRY</p>
          <h1>{t("agents.title")}</h1>
          <p className="page-description">{t("agents.description")}</p>
        </div>
        <button className="ui-button" onClick={openNew}>
          ＋ {t("agents.create")}
        </button>
      </header>

      {error && <div className="skill-error">× {error}</div>}

      <section className="run-table">
        <div className="table-tools">
          <div className="search-mini">◌ {agents.length} AGENTS</div>
        </div>
        <div
          className="table-head"
          style={{ gridTemplateColumns: "1.8fr 1fr 1fr 110px auto" }}
        >
          <span>{t("agents.agent")}</span>
          <span>{t("agents.domain")}</span>
          <span>{t("agents.model")}</span>
          <span>{t("agents.updated")}</span>
          <span />
        </div>
        {agents.map((a) => (
          <div
            className="table-row"
            key={a.id}
            style={{ gridTemplateColumns: "1.8fr 1fr 1fr 110px auto" }}
          >
            <span>
              <b>{a.name}</b>
              <small>
                {a.agentKey} · {a.description || "—"}
              </small>
            </span>
            <span style={{ color: "#5b7aa6" }}>#{a.businessDomain}</span>
            <span>
              <small>{a.modelAssetId ? "configured" : "—"}</small>
            </span>
            <span>
              <small>{new Date(a.updatedAt).toLocaleString()}</small>
            </span>
            <span style={{ display: "flex", gap: 8, whiteSpace: "nowrap" }}>
              <button
                className="link-button"
                onClick={() => openEdit(a)}
                style={{ fontSize: 10 }}
              >
                {t("agents.edit")}
              </button>
              <button
                className="link-button"
                onClick={() => onConfigure(a.id)}
                style={{ fontSize: 10 }}
              >
                {t("agents.configure")}
              </button>
              <button
                className="link-button danger-link"
                onClick={() => void remove(a)}
                style={{ fontSize: 10 }}
              >
                {t("agents.delete")}
              </button>
            </span>
          </div>
        ))}
      </section>

      {editing && (
        <Modal
          title={editing === "new" ? t("agents.create") : t("agents.edit")}
          onClose={() => setEditing(null)}
        >
          <p className="modal-intro">{t("agents.createIntro")}</p>
          <div className="field-grid">
            <label className="field wide">
              <span>{t("agents.nameLabel")}</span>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder={t("agents.namePlaceholder")}
                autoFocus
              />
              <small>{t("agents.nameHint")}</small>
            </label>
            <label className="field wide">
              <span>{t("agents.descriptionLabel")}</span>
              <textarea
                className="cfg-textarea"
                rows={3}
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                placeholder={t("agents.descriptionPlaceholder")}
              />
              <small>{t("agents.descriptionHint")}</small>
            </label>
            <label className="field wide">
              <span>{t("agents.domainLabel")}</span>
              <input
                value={form.businessDomain}
                onChange={(e) => setForm({ ...form, businessDomain: e.target.value })}
                placeholder={t("agents.domainPlaceholder")}
              />
              <small>{t("agents.domainHint")}</small>
            </label>
          </div>
          {error && <div className="skill-error">× {error}</div>}
          <div className="sticky-actions">
            <button className="ui-button quiet" onClick={() => setEditing(null)}>
              {t("agents.cancel")}
            </button>
            <button className="ui-button" onClick={save} disabled={saving}>
              {saving ? t("agents.saving") : t("agents.save")}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
