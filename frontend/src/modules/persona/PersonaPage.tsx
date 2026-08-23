import { useEffect, useMemo, useState } from "react";
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
  listPersonas,
  savePersona,
} from "./api";
import type { Persona } from "./types";
import "./persona.css";

export function PersonaPage() {
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
    fetch("/api/v1/agents")
      .then((r) => r.json())
      .then((ag) => setAgents(Array.isArray(ag) ? ag : ag.items ?? []))
      .catch(() => {});
    fetchPersonaCoverage().then(setCoverage).catch(() => {});
  }, []);

  useEffect(() => {
    void fetchUsersPage(userPageNumber, userPageSize)
      .then(setUsersPage)
      .catch(() => setError("加载用户失败"));
  }, [userPageNumber, userPageSize]);

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
        kicker="PERSONA"
        title="用户画像"
        description="用户维度的长期记忆与洞察。每个 Agent 独立抽取并持有画像，并按 Agent 配置的策略注入。点击用户查看。"
      />
      {error && <div className="skill-error">× {error}</div>}

      <div className="persona-list-bar">
        <label className="search-mini" style={{ display: "inline-flex" }}>
          ⌕
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="搜索用户 / userId / 邮箱"
          />
        </label>
        <label className="persona-filter-check">
          <input
            type="checkbox"
            checked={onlyWithPersona}
            onChange={(e) => setOnlyWithPersona(e.target.checked)}
          />
          仅看有画像的用户
        </label>
      </div>

      <section className="run-table persona-table persona-table-cover">
        <div className="table-head">
          <span>账号</span>
          <span>用户标识</span>
          <span>姓名</span>
          <span>邮箱</span>
          <span>画像覆盖</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        {visibleUsers.length === 0 && <div className="um-empty">暂无用户</div>}
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
                      title={`${a.name}：${ids.includes(a.id) ? "已有画像" : "尚无画像"}`}
                    />
                  ))}
                </span>
              </span>
              <span>{u.enabled ? "启用" : "停用"}</span>
              <span className="model-actions">
                <button className="link-button" onClick={() => openPersona(u)}>
                  查看画像
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
      .catch(() => setError("加载画像失败"))
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
        setNotice({ ok: false, text: "偏好格式应为 键:值，每行一条" });
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
      setNotice({ ok: true, text: "画像已保存" });
      onChanged();
    } catch {
      setNotice({ ok: false, text: "保存失败" });
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
      setNotice({ ok: true, text: "已追加到长期记忆" });
    } catch {
      setNotice({ ok: false, text: "追加记忆失败" });
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
        kicker="PERSONA / 用户画像"
        title={user.displayName || user.username}
        description={user.userId}
        action={
          <Button quiet onClick={onClose}>
            ← 返回列表
          </Button>
        }
      />
      {error && <div className="skill-error">× {error}</div>}

      {/* Injection preview — single source of truth for what an agent actually injects */}
      <section className="form-surface persona-preview">
        <div className="persona-preview-head">
          <div>
            <b>注入预览（实际注入 system prompt 的内容）</b>
            <small>以所选 Agent 的配置（注入方式 + 模板）渲染，预览即所得。</small>
          </div>
          <label className="persona-preview-agent">
            <span>预览视角</span>
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
            注入方式：{modeLabel(previewMode)}
          </span>
          {previewAgent && (
            <span className="persona-chip-sub">
              {previewAgent.personaExtractEnabled ? "✓ 已开启抽取" : "✗ 未开启抽取"}
            </span>
          )}
        </div>

        <pre className="persona-preview-block">
          {previewLoading
            ? "加载中…"
            : previewBlock || "（该 Agent 当前不会注入任何画像：未开启注入或该用户尚无画像数据）"}
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
            <b>{currentAgent.name} 的画像</b>
            <div className="persona-detail-meta">
              <span className={`persona-chip chip-${currentAgent.personaInjectionMode.toLowerCase()}`}>
                {modeLabel(currentAgent.personaInjectionMode)}
              </span>
              {persona?.lastExtractedAt && (
                <span className="persona-chip-sub">
                  上次自动抽取：{new Date(persona.lastExtractedAt).toLocaleString("zh-CN")}
                </span>
              )}
            </div>
          </div>

          <div className="persona-grid">
            <label className="field wide">
              <span>一句话总结</span>
              <input
                value={summary}
                onChange={(e) => setSummary(e.target.value)}
                placeholder="AI 生成或人工维护的用户概括"
              />
            </label>

            <label className="field">
              <span>标签（逗号分隔）</span>
              <input
                value={tagsText}
                onChange={(e) => setTagsText(e.target.value)}
                placeholder="VIP, 技术决策者"
              />
            </label>

            <label className="field">
              <span>偏好（键:值，每行一条）</span>
              <textarea
                value={prefsText}
                onChange={(e) => setPrefsText(e.target.value)}
                rows={3}
                placeholder={"沟通风格: 简洁直接\n时区: Asia/Shanghai"}
              />
            </label>

            <label className="field wide">
              <span>关键事实</span>
              <textarea
                value={facts}
                onChange={(e) => setFacts(e.target.value)}
                rows={3}
                placeholder="分号分隔的稳定事实"
              />
            </label>
          </div>

          <div className="sticky-actions">
            <Button onClick={handleSave} disabled={saving}>
              保存画像
            </Button>
            {notice && (
              <span className={`persona-notice ${notice.ok ? "ok" : "err"}`}>
                {notice.text}
              </span>
            )}
          </div>

          <div className="section-block">
            <div className="section-label">
              <b>长期记忆 (MEMORY.md)</b>
              <small>{currentAgent.name} 对该用户的非结构化长文，由对话自动沉淀，可人工校正</small>
            </div>
            <pre className="persona-memory-view">
              {persona?.memory || "（暂无长期记忆）"}
            </pre>
            <div className="persona-memory-add">
              <textarea
                value={memoryDelta}
                onChange={(e) => setMemoryDelta(e.target.value)}
                rows={3}
                placeholder="追加一段记忆增量…"
              />
              <Button
                quiet
                onClick={handleAppendMemory}
                disabled={saving || !memoryDelta.trim()}
              >
                追加记忆
              </Button>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}

function modeLabel(mode: string): string {
  switch (mode) {
    case "GLOBAL":
      return "全局注入（跨 Agent 合并）";
    case "SELF_ONLY":
      return "仅注入本 Agent 画像";
    case "NONE":
    default:
      return "不注入";
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
