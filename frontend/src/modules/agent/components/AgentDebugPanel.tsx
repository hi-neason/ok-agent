import { useEffect, useRef, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import type { ChatMessage } from "../types";

const selectStyle: CSSProperties = {
  fontSize: 12,
  padding: "4px 8px",
  marginLeft: 6,
  background: "var(--surface, #1b2230)",
  color: "inherit",
  border: "1px solid var(--border, #2c3340)",
  borderRadius: 6,
  maxWidth: 220,
};

export function AgentDebugPanel({
  messages,
  input,
  sending,
  onInputChange,
  onSend,
  onNewSession,
  users,
  selectedUserKey,
  onSelectUser,
  sessions,
  selectedSessionId,
  onSelectSession,
}: {
  messages: ChatMessage[];
  input: string;
  sending: boolean;
  onInputChange: (value: string) => void;
  onSend: () => void;
  onNewSession: () => void;
  users: { userKey: string; username: string; displayName: string }[];
  selectedUserKey: string | null;
  onSelectUser: (userKey: string) => void;
  sessions: { sessionId: string; title: string; turnCount: number }[];
  selectedSessionId: string | null;
  onSelectSession: (sessionId: string) => void;
}) {
  const { t } = useTranslation();
  const threadRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, sending]);

  const userHasNoSelection = !selectedUserKey;

  return (
    <>
      <header>
        <b>{t("agents.debugChat")}</b>
        <button className="link-button" onClick={onNewSession} style={{ fontSize: 11 }}>
          ↻ {t("agents.newSession")}
        </button>
      </header>

      <div
        className="debug-toolbar"
        style={{
          display: "flex",
          flexWrap: "wrap",
          gap: 12,
          padding: "8px 12px",
          borderBottom: "1px solid var(--border, #2c3340)",
          fontSize: 12,
        }}
      >
        <label>
          调试用户
          <select
            style={selectStyle}
            value={selectedUserKey ?? ""}
            onChange={(e) => onSelectUser(e.target.value)}
          >
            {users.length === 0 && <option value="">（无用户）</option>}
            {users.map((u) => (
              <option key={u.userKey} value={u.userKey}>
                {u.username === "debug" ? "DEBUG用户（内置）" : u.displayName || u.username}
              </option>
            ))}
          </select>
        </label>
        <label>
          历史会话
          <select
            style={selectStyle}
            value={selectedSessionId ?? ""}
            onChange={(e) => onSelectSession(e.target.value)}
          >
            <option value="">— 新会话 / 选择历史 —</option>
            {sessions.map((s) => (
              <option key={s.sessionId} value={s.sessionId}>
                {s.title}（{s.turnCount}）
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="chat-thread" ref={threadRef}>
        {messages.length === 0 && (
          <div className="chat-empty">
            <i>◈</i>
            {userHasNoSelection ? "请先在上方选择调试用户" : t("agents.chatEmpty")}
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`chat-bubble ${m.error ? "error" : m.role}`}>
            {m.content}
          </div>
        ))}
        {sending && <div className="chat-bubble assistant">···</div>}
      </div>
      <div className="chat-composer">
        <textarea
          value={input}
          onChange={(e) => onInputChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              onSend();
            }
          }}
          placeholder={userHasNoSelection ? "请先选择调试用户" : t("agents.typeMessage")}
          disabled={userHasNoSelection}
        />
        <button
          className="ui-button send"
          onClick={onSend}
          disabled={sending || !input.trim() || userHasNoSelection}
        >
          {t("agents.send")}
        </button>
      </div>
    </>
  );
}
