import { useEffect, useRef, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import type { ChatMessage } from "../types";
import { Markdown, CollapsibleMarkdown } from "../../shared";

const selectStyle: CSSProperties = {
  fontSize: 12,
  padding: "4px 8px",
  marginLeft: 6,
  background: "#fff",
  color: "#1677ff",
  border: "1px solid #1677ff",
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
  selectedUserId,
  onSelectUser,
  sessions,
  selectedSessionId,
  onSelectSession,
  userMap,
}: {
  messages: ChatMessage[];
  input: string;
  sending: boolean;
  onInputChange: (value: string) => void;
  onSend: () => void;
  onNewSession: () => Promise<void>;
  users: { userId: string; username: string; displayName: string }[];
  selectedUserId: string | null;
  onSelectUser: (userId: string) => void;
  sessions: { sessionId: string; title: string; turnCount: number; userId: string | null }[];
  selectedSessionId: string | null;
  onSelectSession: (sessionId: string) => void;
  userMap: Record<string, { displayName: string; username: string }>;
}) {
  const { t } = useTranslation();
  const threadRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, sending]);

  const userHasNoSelection = !selectedUserId;

  return (
    <>
      <header>
        <b>{t("agents.debugChat")}</b>
        <button
          className="link-button"
          onClick={() => void onNewSession()}
          style={{ fontSize: 11 }}
        >
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
            value={selectedUserId ?? ""}
            onChange={(e) => onSelectUser(e.target.value)}
          >
            <option value="">全部用户（跨用户）</option>
            {users.map((u) => (
              <option key={u.userId} value={u.userId}>
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
            {sessions.map((s) => {
              const owner = s.userId ? userMap[s.userId] : undefined;
              const ownerLabel = owner
                ? owner.displayName || owner.username
                : s.userId ?? "—";
              return (
                <option key={s.sessionId} value={s.sessionId}>
                  {s.title}（{s.turnCount}）— {ownerLabel}
                </option>
              );
            })}
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
            {m.role === "assistant" && !m.error ? (
              <CollapsibleMarkdown source={m.content} />
            ) : (
              m.content
            )}
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
