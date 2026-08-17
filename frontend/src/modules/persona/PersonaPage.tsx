import { useEffect, useMemo, useState } from "react";
import { Button, PageHeader } from "../shared";
import { fetchUsers } from "../usermgmt/api";
import type { UserItem } from "../usermgmt/types";
import {
  appendPersonaMemory,
  fetchPersona,
  savePersona,
} from "./api";
import type { Persona } from "./types";
import "./persona.css";

export function PersonaPage() {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [query, setQuery] = useState("");
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);

  const [persona, setPersona] = useState<Persona | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [memoryDelta, setMemoryDelta] = useState("");
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);

  const [summary, setSummary] = useState("");
  const [tagsText, setTagsText] = useState("");
  const [prefsText, setPrefsText] = useState("");
  const [facts, setFacts] = useState("");

  useEffect(() => {
    fetchUsers().then(setUsers).catch(() => setNotice({ ok: false, text: "加载用户列表失败" }));
  }, []);

  const visibleUsers = useMemo(
    () =>
      users.filter((u) =>
        `${u.displayName} ${u.username} ${u.userId}`.toLowerCase().includes(query.toLowerCase()),
      ),
    [users, query],
  );

  const loadPersona = (userId: string) => {
    setLoading(true);
    setNotice(null);
    setSelectedUserId(userId);
    fetchPersona(userId)
      .then((p) => {
        setPersona(p);
        setSummary(p.summary ?? "");
        setTagsText((p.tags ?? []).join(", "));
        setPrefsText(prefsToText(p.preferences ?? {}));
        setFacts(p.facts ?? "");
        setMemoryDelta("");
      })
      .catch(() => setNotice({ ok: false, text: "加载画像失败" }))
      .finally(() => setLoading(false));
  };

  const selectedUser = users.find((u) => u.userId === selectedUserId) ?? null;

  const handleSave = async () => {
    if (!selectedUserId) return;
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
      const saved = await savePersona(selectedUserId, {
        summary,
        tags,
        preferences: prefs,
        facts,
      });
      setPersona(saved);
      setNotice({ ok: true, text: "画像已保存" });
    } catch {
      setNotice({ ok: false, text: "保存失败" });
    } finally {
      setSaving(false);
    }
  };

  const handleAppendMemory = async () => {
    if (!selectedUserId || !memoryDelta.trim()) return;
    setSaving(true);
    setNotice(null);
    try {
      const updated = await appendPersonaMemory(selectedUserId, memoryDelta);
      setPersona((p) => (p ? { ...p, memory: updated.memory } : p));
      setMemoryDelta("");
      setNotice({ ok: true, text: "已追加到长期记忆" });
    } catch {
      setNotice({ ok: false, text: "追加记忆失败" });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="persona-page">
      <PageHeader title="用户画像" kicker="PERSONA" description="用户维度的长期记忆与洞察，可注入 Agent 辅助对话" />

      <div className="persona-layout">
        <aside className="persona-sidebar">
          <input
            className="persona-search"
            placeholder="搜索用户 / userId"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <div className="persona-userlist">
            {visibleUsers.map((u) => (
              <button
                key={u.id}
                className={`persona-userrow ${selectedUserId === u.userId ? "active" : ""}`}
                onClick={() => loadPersona(u.userId)}
              >
                <span className="persona-username">{u.displayName || u.username}</span>
                <code className="persona-userid">{u.userId.slice(0, 8)}</code>
              </button>
            ))}
            {visibleUsers.length === 0 && <div className="persona-empty">无用户</div>}
          </div>
        </aside>

        <section className="persona-detail">
          {!selectedUserId && (
            <div className="persona-hint">从左侧选择一个用户，查看或编辑其画像。</div>
          )}
          {selectedUserId && loading && <div className="persona-hint">加载中…</div>}

          {selectedUser && persona && !loading && (
            <>
              <div className="persona-head">
                <div>
                  <b>{selectedUser.displayName || selectedUser.username}</b>
                  <code className="persona-fullid">{selectedUser.userId}</code>
                </div>
                <span className="persona-updated">
                  更新于 {new Date(persona.updatedAt).toLocaleString()}
                </span>
              </div>

              <div className="persona-grid">
                <label className="persona-field persona-field--full">
                  <span>一句话总结</span>
                  <input value={summary} onChange={(e) => setSummary(e.target.value)} placeholder="AI 生成或人工维护的用户概括" />
                </label>

                <label className="persona-field">
                  <span>标签（逗号分隔）</span>
                  <input value={tagsText} onChange={(e) => setTagsText(e.target.value)} placeholder="VIP, 技术决策者" />
                </label>

                <label className="persona-field">
                  <span>偏好（键:值，每行一条）</span>
                  <textarea
                    value={prefsText}
                    onChange={(e) => setPrefsText(e.target.value)}
                    rows={3}
                    placeholder={"沟通风格: 简洁直接\n时区: Asia/Shanghai"}
                  />
                </label>

                <label className="persona-field persona-field--full">
                  <span>关键事实</span>
                  <textarea
                    value={facts}
                    onChange={(e) => setFacts(e.target.value)}
                    rows={3}
                    placeholder="分号分隔的稳定事实"
                  />
                </label>
              </div>

              <div className="persona-actions">
                <Button onClick={handleSave} disabled={saving}>保存画像</Button>
                {notice && (
                  <span className={`persona-notice ${notice.ok ? "ok" : "err"}`}>{notice.text}</span>
                )}
              </div>

              <div className="persona-memory">
                <div className="persona-memory-head">
                  <b>长期记忆 (MEMORY.md)</b>
                  <span className="persona-memory-hint">非结构化长文，由对话自动沉淀，可人工校正</span>
                </div>
                <pre className="persona-memory-view">{persona.memory || "（暂无长期记忆）"}</pre>
                <div className="persona-memory-add">
                  <textarea
                    value={memoryDelta}
                    onChange={(e) => setMemoryDelta(e.target.value)}
                    rows={3}
                    placeholder="追加一段记忆增量…"
                  />
                  <Button quiet onClick={handleAppendMemory} disabled={saving || !memoryDelta.trim()}>
                    追加记忆
                  </Button>
                </div>
              </div>
            </>
          )}
        </section>
      </div>
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
