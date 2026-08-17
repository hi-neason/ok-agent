import { useEffect, useMemo, useState } from "react";
import { Button, PageHeader } from "../shared";
import { fetchUsers } from "../usermgmt/api";
import type { UserItem } from "../usermgmt/types";
import {
  appendPersonaMemory,
  fetchPersona,
  listPersonas,
  savePersona,
} from "./api";
import type { Persona } from "./types";
import "./persona.css";

type AgentLite = { id: string; name: string };

export function PersonaPage() {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");

  const [selectedUser, setSelectedUser] = useState<UserItem | null>(null);
  const [agents, setAgents] = useState<AgentLite[]>([]);
  const [agentId, setAgentId] = useState<string>("");
  const [persona, setPersona] = useState<Persona | null>(null);
  const [agentPersonaMap, setAgentPersonaMap] = useState<Record<string, Persona>>(
    {},
  );
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [memoryDelta, setMemoryDelta] = useState("");
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);

  const [summary, setSummary] = useState("");
  const [tagsText, setTagsText] = useState("");
  const [prefsText, setPrefsText] = useState("");
  const [facts, setFacts] = useState("");

  useEffect(() => {
    fetchUsers().then(setUsers).catch(() => setError("加载用户列表失败"));
  }, []);

  const visibleUsers = useMemo(
    () =>
      users.filter((u) =>
        `${u.displayName} ${u.username} ${u.userId} ${u.email ?? ""}`
          .toLowerCase()
          .includes(query.toLowerCase()),
      ),
    [users, query],
  );

  const openPersona = async (u: UserItem) => {
    setLoading(true);
    setError("");
    setNotice(null);
    setSelectedUser(u);
    try {
      const [agentRes, personaList] = await Promise.all([
        fetch("/api/v1/agents").then((r) => r.json()),
        listPersonas(u.userId),
      ]);
      const agentList: AgentLite[] = Array.isArray(agentRes)
        ? agentRes.map((a: { id: string; name: string }) => ({
            id: a.id,
            name: a.name,
          }))
        : agentRes.items ?? [];
      setAgents(agentList);

      const map: Record<string, Persona> = {};
      for (const p of personaList) map[p.agentId] = p;
      setAgentPersonaMap(map);

      // Prefer the first agent that already has a persona; else first agent.
      const firstWith = agentList.find((a) => map[a.id]);
      const target = firstWith ?? agentList[0];
      if (target) {
        setAgentId(target.id);
        loadPersonaIntoForm(u.userId, target.id, map[target.id] ?? null);
      } else {
        setAgentId("");
        setPersona(null);
        resetForm();
      }
    } catch {
      setError("加载画像失败");
    } finally {
      setLoading(false);
    }
  };

  const selectAgent = (id: string) => {
    if (!selectedUser) return;
    setAgentId(id);
    setNotice(null);
    loadPersonaIntoForm(
      selectedUser.userId,
      id,
      agentPersonaMap[id] ?? null,
    );
  };

  const loadPersonaIntoForm = async (
    userId: string,
    aid: string,
    cached: Persona | null,
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

  const closePersona = () => {
    setSelectedUser(null);
    setPersona(null);
    setAgents([]);
    setAgentId("");
    setAgentPersonaMap({});
    setNotice(null);
    resetForm();
  };

  const handleSave = async () => {
    if (!selectedUser || !agentId) return;
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
      const saved = await savePersona(selectedUser.userId, agentId, {
        summary,
        tags,
        preferences: prefs,
        facts,
      });
      setPersona(saved);
      setAgentPersonaMap((m) => ({ ...m, [agentId]: saved }));
      setNotice({ ok: true, text: "画像已保存" });
    } catch {
      setNotice({ ok: false, text: "保存失败" });
    } finally {
      setSaving(false);
    }
  };

  const handleAppendMemory = async () => {
    if (!selectedUser || !agentId || !memoryDelta.trim()) return;
    setSaving(true);
    setNotice(null);
    try {
      const updated = await appendPersonaMemory(
        selectedUser.userId,
        agentId,
        memoryDelta,
      );
      const next = { ...(persona as Persona), memory: updated.memory };
      setPersona(next);
      setAgentPersonaMap((m) => ({ ...m, [agentId]: next }));
      setMemoryDelta("");
      setNotice({ ok: true, text: "已追加到长期记忆" });
    } catch {
      setNotice({ ok: false, text: "追加记忆失败" });
    } finally {
      setSaving(false);
    }
  };

  if (selectedUser) {
    return (
      <div className="persona-page">
        <PageHeader
          kicker="PERSONA / 用户画像"
          title={selectedUser.displayName || selectedUser.username}
          description={selectedUser.userId}
          action={
            <Button quiet onClick={closePersona}>
              ← 返回列表
            </Button>
          }
        />
        {error && <div className="skill-error">× {error}</div>}

        <div className="persona-agent-tabs">
          {agents.length === 0 && <span className="um-empty">暂无 Agent</span>}
          {agents.map((a) => {
            const has = !!agentPersonaMap[a.id];
            return (
              <button
                key={a.id}
                className={`persona-agent-tab ${a.id === agentId ? "active" : ""}`}
                onClick={() => selectAgent(a.id)}
              >
                {a.name}
                <span
                  className={`persona-agent-dot ${has ? "has" : "empty"}`}
                  title={has ? "已有画像" : "尚无画像"}
                />
              </button>
            );
          })}
        </div>

        {agentId && (
          <section className="form-surface persona-detail">
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
                <small>当前 Agent 对该用户的非结构化长文，由对话自动沉淀，可人工校正</small>
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

  return (
    <div className="persona-page">
      <PageHeader
        kicker="PERSONA"
        title="用户画像"
        description="用户维度的长期记忆与洞察，每个 Agent 独立抽取并持有，按 Agent 配置的策略注入。点击用户查看或编辑画像。"
      />
      {error && <div className="skill-error">× {error}</div>}

      <label className="search-mini" style={{ marginBottom: 10, display: "inline-flex" }}>
        ⌕
        <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="搜索用户 / userId / 邮箱" />
      </label>

      <section className="run-table persona-table">
        <div className="table-head">
          <span>账号</span>
          <span>用户标识</span>
          <span>姓名</span>
          <span>邮箱</span>
          <span>所属用户组</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        {loading && <div className="um-empty">加载中…</div>}
        {!loading && visibleUsers.length === 0 && <div className="um-empty">暂无用户</div>}
        {!loading &&
          visibleUsers.map((u) => (
            <div className="table-row" key={u.id}>
              <span>
                <b>{u.username}</b>
              </span>
              <code>{u.userId}</code>
              <span>{u.displayName}</span>
              <span>{u.email || "—"}</span>
              <span>{u.groupName || "—"}</span>
              <span>{u.enabled ? "启用" : "停用"}</span>
              <span className="model-actions">
                <button className="link-button" onClick={() => openPersona(u)}>
                  查看画像
                </button>
              </span>
            </div>
          ))}
      </section>
    </div>
  );
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
