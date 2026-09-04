import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { Button, PageHeader, Pagination } from "../shared";
import type { Page } from "../shared";
import { fetchUsersPage } from "../usermgmt/api";
import type { UserItem } from "../usermgmt/types";
import type { AgentItem } from "../agent/types";
import {
  appendPersonaMemory,
  fetchInjectionPreview,
  fetchPersona,
  fetchPersonaCoverage,
  fetchPersonaAgents,
  listPersonas,
  savePersona,
} from "./api";
import type { Persona } from "./types";
import "./persona.css";

export function PersonaPage() {
  const { t } = useTranslation();
  const [usersPage, setUsersPage] = useState<Page<UserItem> | null>(null);
  const [userPageNumber, setUserPageNumber] = useState(0);
  const [userPageSize, setUserPageSize] = useState(20);
  const [agents, setAgents] = useState<AgentItem[]>([]);
  const [coverage, setCoverage] = useState<Record<string, string[]>>({});
  const [query, setQuery] = useState("");
  const [onlyWithPersona, setOnlyWithPersona] = useState(false);
  const [error, setError] = useState("");

  const [selectedUser, setSelectedUser] = useState<UserItem | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([fetchPersonaAgents(), fetchPersonaCoverage()])
      .then(([nextAgents, nextCoverage]) => {
        if (!active) return;
        setAgents(nextAgents);
        setCoverage(nextCoverage);
      })
      .catch(() => { if (active) setError(t("persona.loadFailed")); });
    return () => { active = false; };
  }, [t]);

  useEffect(() => {
    void fetchUsersPage(userPageNumber, userPageSize)
      .then(setUsersPage)
      .catch(() => setError(t("persona.loadUsersFailed")));
  }, [userPageNumber, userPageSize, t]);

  const coverageCount = (userId: string) => coverage[userId]?.length ?? 0;

  const visibleUsers = useMemo(() => {
    return (usersPage?.content ?? []).filter((u) => {
      const hay = `${u.displayName} ${u.username} ${u.userId} ${u.email ?? ""}`.toLowerCase();
      if (!hay.includes(query.toLowerCase())) return false;
      if (onlyWithPersona && coverageCount(u.userId) === 0) return false;
      return true;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usersPage, query, onlyWithPersona, coverage]);

  const openPersona = (u: UserItem) => {
    if (agents.length === 0) {
      setError(t("persona.loadFailed"));
      return;
    }
    setError("");
    setSelectedUser(u);
  };

  const closePersona = () => {
    setSelectedUser(null);
  };

  if (selectedUser) {
    return (
      <PersonaDetail
        user={selectedUser}
        agents={agents}
        onClose={closePersona}
        onChanged={() => fetchPersonaCoverage().then(setCoverage).catch(() => {})}
      />
    );
  }

  return (
    <div className="persona-page">
      <PageHeader
        kicker={t("persona.kicker")}
        title={t("persona.title")}
        description={t("persona.description")}
      />
      {error && <div className="skill-error">× {error}</div>}

      <div className="persona-list-bar">
        <label className="search-mini" style={{ display: "inline-flex" }}>
          ⌕
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t("persona.search")}
          />
        </label>
        <label className="persona-filter-check">
          <input
            type="checkbox"
            checked={onlyWithPersona}
            onChange={(e) => setOnlyWithPersona(e.target.checked)}
          />
          {t("persona.onlyWithPersona")}
        </label>
      </div>

      <section className="run-table persona-table persona-table-cover">
        <div className="table-head">
          <span>{t("persona.account")}</span>
          <span>{t("persona.userId")}</span>
          <span>{t("persona.name")}</span>
          <span>{t("persona.email")}</span>
          <span>{t("persona.coverage")}</span>
          <span>{t("common.status")}</span>
          <span>{t("common.actions")}</span>
        </div>
        {visibleUsers.length === 0 && <div className="um-empty">{t("users.noUsers")}</div>}
        {visibleUsers.map((u) => {
          const ids = coverage[u.userId] ?? [];
          return (
            <div className="table-row" key={u.id}>
              <span>
                <b>{u.username}</b>
              </span>
              <code>{u.userId}</code>
              <span>{u.displayName}</span>
              <span>{u.email || "—"}</span>
              <span className="persona-cover-cell">
                <span className="persona-cover-count">
                  {ids.length}/{agents.length}
                </span>
                <span className="persona-cover-dots">
                  {agents.map((a) => (
                    <span
                      key={a.id}
                      className={`persona-cover-dot ${ids.includes(a.id) ? "on" : "off"}`}
                      title={`${a.name}: ${ids.includes(a.id) ? t("persona.existing") : t("persona.missing")}`}
                    />
                  ))}
                </span>
              </span>
              <span>{u.enabled ? t("common.enabled") : t("common.disabled")}</span>
              <span className="model-actions">
                <button className="link-button" onClick={() => openPersona(u)}>
                  {t("persona.view")}
                </button>
              </span>
            </div>
          );
        })}
      </section>

      {usersPage && (
        <Pagination
          page={usersPage.number}
          totalPages={usersPage.totalPages}
          totalElements={usersPage.totalElements}
          size={usersPage.size}
          loading={false}
          onPageChange={setUserPageNumber}
          onSizeChange={(size) => {
            setUserPageSize(size);
            setUserPageNumber(0);
          }}
        />
      )}
    </div>
  );
}

function PersonaDetail({
  user,
  agents,
  onClose,
  onChanged,
}: {
  user: UserItem;
  agents: AgentItem[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const { t, i18n } = useTranslation();
  const [error, setError] = useState("");
  const [previewAgentId, setPreviewAgentId] = useState<string>("");
  const [previewMode, setPreviewMode] = useState<string>("NONE");
  const [previewBlock, setPreviewBlock] = useState<string>("");
  const [previewLoading, setPreviewLoading] = useState(false);

  const [agentId, setAgentId] = useState<string>("");
  const [persona, setPersona] = useState<Persona | null>(null);
  const [personaMap, setPersonaMap] = useState<Record<string, Persona>>({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [memoryDelta, setMemoryDelta] = useState("");
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);

  const [summary, setSummary] = useState("");
  const [tagsText, setTagsText] = useState("");
  const [prefsText, setPrefsText] = useState("");
  const [facts, setFacts] = useState("");

  const agentMap = useMemo(() => {
    const m: Record<string, AgentItem> = {};
    for (const a of agents) m[a.id] = a;
    return m;
  }, [agents]);

  // Initial load: personas for all agents, pick first with data.
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    listPersonas(user.userId)
      .then(async (list) => {
        if (cancelled) return;
        const map: Record<string, Persona> = {};
        for (const p of list) map[p.agentId] = p;
        setPersonaMap(map);

        const firstWith = agents.find((a) => map[a.id]);
        const target = firstWith ?? agents[0];
        if (target) {
          setAgentId(target.id);
          setPreviewAgentId(target.id);
          await loadAgent(target.id, map[target.id] ?? null, user.userId);
        }
      })
      .catch(() => setError(t("persona.loadFailed")))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.userId, agents]);

  // Reload injection preview when preview agent changes.
  useEffect(() => {
    if (!previewAgentId) return;
    setPreviewLoading(true);
    fetchInjectionPreview(user.userId, previewAgentId)
      .then((r) => {
        setPreviewMode(r.mode);
        setPreviewBlock(r.block);
      })
      .catch(() => {
        setPreviewMode("NONE");
        setPreviewBlock("");
      })
      .finally(() => setPreviewLoading(false));
  }, [previewAgentId, user.userId, persona, agentId]);

  const loadAgent = async (
    aid: string,
    cached: Persona | null,
    userId: string,
  ) => {
    if (cached) {
      setPersona(cached);
      fillForm(cached);
      return;
    }
    try {
      const p = await fetchPersona(userId, aid);
      setPersona(p);
      fillForm(p);
    } catch {
      setPersona(null);
      resetForm();
    }
  };

  const selectAgent = (id: string) => {
    setAgentId(id);
    setNotice(null);
    loadAgent(id, null, user.userId);
  };

  const fillForm = (p: Persona) => {
    setSummary(p.summary ?? "");
    setTagsText((p.tags ?? []).join(", "));
    setPrefsText(prefsToText(p.preferences ?? {}));
    setFacts(p.facts ?? "");
    setMemoryDelta("");
  };

  const resetForm = () => {
    setSummary("");
    setTagsText("");
    setPrefsText("");
    setFacts("");
    setMemoryDelta("");
  };

  const handleSave = async () => {
    if (!agentId) return;
    setSaving(true);
    setNotice(null);
    try {
      const prefs = parsePrefs(prefsText);
      if (prefs === null) {
        setNotice({ ok: false, text: t("persona.prefsInvalid") });
        return;
      }
      const tags = tagsText
        .split(/[,，\n]/)
        .map((s) => s.trim())
        .filter(Boolean);
      const saved = await savePersona(user.userId, agentId, {
        summary,
        tags,
        preferences: prefs,
        facts,
      });
      setPersona(saved);
      setPersonaMap((m) => ({ ...m, [agentId]: saved }));
      setNotice({ ok: true, text: t("persona.saved") });
      onChanged();
    } catch {
      setNotice({ ok: false, text: t("persona.saveFailed") });
    } finally {
      setSaving(false);
    }
  };

  const handleAppendMemory = async () => {
    if (!agentId || !memoryDelta.trim()) return;
    setSaving(true);
    setNotice(null);
    try {
      const updated = await appendPersonaMemory(user.userId, agentId, memoryDelta);
      const next = { ...(persona as Persona), memory: updated.memory };
      setPersona(next);
      setPersonaMap((m) => ({ ...m, [agentId]: next }));
      setMemoryDelta("");
      setNotice({ ok: true, text: t("persona.memoryAppended") });
    } catch {
      setNotice({ ok: false, text: t("persona.memoryAppendFailed") });
    } finally {
      setSaving(false);
    }
  };

  const currentAgent = agentMap[agentId];
  const previewAgent = agentMap[previewAgentId];
  const hasPersona = (p: Persona | null | undefined) =>
    !!(p && (p.summary || p.memory || (p.tags?.length ?? 0) > 0 || (p.facts && p.facts.trim())));

  return (
    <div className="persona-page">
      <PageHeader
        kicker={t("kickers.personaDetail", { title: t("persona.title") })}
        title={user.displayName || user.username}
        description={user.userId}
        action={
          <Button quiet onClick={onClose}>
            ← {t("persona.back")}
          </Button>
        }
      />
      {error && <div className="skill-error">× {error}</div>}

      {/* Injection preview — single source of truth for what an agent actually injects */}
      <section className="form-surface persona-preview">
        <div className="persona-preview-head">
          <div>
            <b>{t("persona.previewTitle")}</b>
            <small>{t("persona.previewHint")}</small>
          </div>
          <label className="persona-preview-agent">
            <span>{t("persona.previewAgent")}</span>
            <select
              value={previewAgentId}
              onChange={(e) => setPreviewAgentId(e.target.value)}
            >
              {agents.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="persona-preview-meta">
          <span className={`persona-chip chip-${previewMode.toLowerCase()}`}>
            {t("persona.injectionMode", { mode: modeLabel(previewMode, t) })}
          </span>
          {previewAgent && (
            <span className="persona-chip-sub">
              {previewAgent.personaExtractEnabled
                ? `✓ ${t("persona.extractionEnabled")}`
                : `✗ ${t("persona.extractionDisabled")}`}
            </span>
          )}
        </div>

        <pre className="persona-preview-block">
          {previewLoading
            ? t("common.loading")
            : previewBlock || t("persona.previewEmpty")}
        </pre>
      </section>

      {/* Agent selector + detail editor */}
      <div className="persona-agent-tabs">
        {agents.map((a) => (
          <button
            key={a.id}
            className={`persona-agent-tab ${a.id === agentId ? "active" : ""}`}
            onClick={() => selectAgent(a.id)}
          >
            {a.name}
            <span
              className={`persona-agent-dot ${
                hasPersona(personaMap[a.id]) ? "has" : "empty"
              }`}
            />
          </button>
        ))}
      </div>

      {agentId && currentAgent && (
        <section className="form-surface persona-detail">
          <div className="persona-detail-head">
            <b>{t("persona.agentPersona", { name: currentAgent.name })}</b>
            <div className="persona-detail-meta">
              <span className={`persona-chip chip-${currentAgent.personaInjectionMode.toLowerCase()}`}>
                {modeLabel(currentAgent.personaInjectionMode, t)}
              </span>
              {persona?.lastExtractedAt && (
                <span className="persona-chip-sub">
                  {t("persona.lastExtracted", {
                    time: new Date(persona.lastExtractedAt).toLocaleString(i18n.resolvedLanguage),
                  })}
                </span>
              )}
            </div>
          </div>

          <div className="persona-grid">
            <label className="field wide">
              <span>{t("persona.summary")}</span>
              <input
                value={summary}
                onChange={(e) => setSummary(e.target.value)}
                placeholder={t("persona.summaryPlaceholder")}
              />
            </label>

            <label className="field">
              <span>{t("persona.tags")}</span>
              <input
                value={tagsText}
                onChange={(e) => setTagsText(e.target.value)}
                placeholder={t("persona.tagsPlaceholder")}
              />
            </label>

            <label className="field">
              <span>{t("persona.preferences")}</span>
              <textarea
                value={prefsText}
                onChange={(e) => setPrefsText(e.target.value)}
                rows={3}
                placeholder={t("persona.preferencesPlaceholder")}
              />
            </label>

            <label className="field wide">
              <span>{t("persona.facts")}</span>
              <textarea
                value={facts}
                onChange={(e) => setFacts(e.target.value)}
                rows={3}
                placeholder={t("persona.factsPlaceholder")}
              />
            </label>
          </div>

          <div className="sticky-actions">
            <Button onClick={handleSave} disabled={saving}>
              {t("persona.save")}
            </Button>
            {notice && (
              <span className={`persona-notice ${notice.ok ? "ok" : "err"}`}>
                {notice.text}
              </span>
            )}
          </div>

          <div className="section-block">
            <div className="section-label">
              <b>{t("persona.memory")}</b>
              <small>{t("persona.memoryHint", { name: currentAgent.name })}</small>
            </div>
            <pre className="persona-memory-view">
              {persona?.memory || t("persona.memoryEmpty")}
            </pre>
            <div className="persona-memory-add">
              <textarea
                value={memoryDelta}
                onChange={(e) => setMemoryDelta(e.target.value)}
                rows={3}
                placeholder={t("persona.memoryPlaceholder")}
              />
              <Button
                quiet
                onClick={handleAppendMemory}
                disabled={saving || !memoryDelta.trim()}
              >
                {t("persona.appendMemory")}
              </Button>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}

function modeLabel(mode: string, t: TFunction): string {
  switch (mode) {
    case "GLOBAL":
      return t("persona.modes.GLOBAL");
    case "SELF_ONLY":
      return t("persona.modes.SELF_ONLY");
    case "NONE":
    default:
      return t("persona.modes.NONE");
  }
}

function prefsToText(prefs: Record<string, string>): string {
  return Object.entries(prefs)
    .map(([k, v]) => `${k}: ${v}`)
    .join("\n");
}

function parsePrefs(text: string): Record<string, string> | null {
  const out: Record<string, string> = {};
  for (const raw of text.split(/\n+/)) {
    const line = raw.trim();
    if (!line) continue;
    const idx = line.indexOf(":");
    if (idx < 0) return null;
    const key = line.slice(0, idx).trim();
    const value = line.slice(idx + 1).trim();
    if (key) out[key] = value;
  }
  return out;
}
