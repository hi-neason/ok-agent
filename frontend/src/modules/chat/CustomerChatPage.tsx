import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { PageHeader, RichChatMessage } from "../shared";
import { loadAgents, loadUsers, type AgentOption, type DebugUser } from "../agent/api";
import "./chat.css";

type ChatMessage = { role: "user" | "assistant"; content: string; error?: boolean };
type RoutingInfo = {
  intentKey: string | null;
  intentName: string | null;
  confidence: number;
  targetSubagentKey: string | null;
  fallback: boolean;
};

export function CustomerChatPage() {
  const { t } = useTranslation();
  const [agents, setAgents] = useState<AgentOption[]>([]);
  const [users, setUsers] = useState<DebugUser[]>([]);
  const [agentId, setAgentId] = useState<string>("");
  const [userId, setUserId] = useState<string>("");
  const [channelId, setChannelId] = useState<string>("web");
  const [sessionId, setSessionId] = useState<string>(() => "cs-" + Math.random().toString(36).slice(2, 10));
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [routing, setRouting] = useState<RoutingInfo | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    void (async () => {
      try {
        const [list, uRes] = await Promise.all([loadAgents(), loadUsers()]);
        setAgents(list);
        setUsers(uRes);
        if (list.length > 0) setAgentId(list[0].id);
        if (uRes.length > 0) setUserId(uRes[0].userId);
      } catch {
        setNotice(t("chat.loadFailed"));
      }
    })();
  }, [t]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const send = async (actionValue?: string) => {
    const text = (actionValue ?? input).trim();
    if (!text || sending) return;
    if (!agentId) {
      setNotice(t("chat.selectAgentFirst"));
      return;
    }
    if (!userId) {
      setNotice(t("chat.selectUserFirst"));
      return;
    }
    setInput("");
    setNotice(null);
    setMessages((m) => [...m, { role: "user", content: text }]);
    setSending(true);
    try {
      const res = await fetch("/api/v1/customer-chat/messages", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ agentId, userId, channelId, sessionId, message: text }),
      });
      const data = (await res.json().catch(() => null)) as
        | {
            reply?: string;
            intentKey?: string | null;
            intentName?: string | null;
            confidence?: number;
            targetSubagentKey?: string | null;
            fallback?: boolean;
            detail?: string;
            message?: string;
          }
        | null;
      if (!res.ok || !data) {
        throw new Error(data?.message || data?.detail || t("chat.requestFailed"));
      }
      setMessages((m) => [...m, { role: "assistant", content: data.reply ?? "" }]);
      setRouting({
        intentKey: data.intentKey ?? null,
        intentName: data.intentName ?? null,
        confidence: data.confidence ?? 0,
        targetSubagentKey: data.targetSubagentKey ?? null,
        fallback: data.fallback ?? false,
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : t("chat.requestFailed");
      setMessages((m) => [...m, { role: "assistant", content: msg, error: true }]);
    } finally {
      setSending(false);
    }
  };

  return (
    <>
      <PageHeader
        kicker={t("chat.kicker")}
        title={t("chat.title")}
        description={t("chat.description")}
      />
      {notice && <div className="skill-error">{notice}</div>}
      <div className="cs-controls">
        <label>
          <span>{t("chat.routingAgent")}</span>
          <select value={agentId} onChange={(e) => setAgentId(e.target.value)}>
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}（{a.agentKey}）
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>{t("chat.user")}</span>
          <select value={userId} onChange={(e) => setUserId(e.target.value)}>
            {users.map((u) => (
              <option key={u.userId} value={u.userId}>
                {u.displayName || u.username}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>{t("chat.channelId")}</span>
          <input value={channelId} onChange={(e) => setChannelId(e.target.value)} />
        </label>
        <label>
          <span>{t("chat.sessionId")}</span>
          <input value={sessionId} onChange={(e) => setSessionId(e.target.value)} />
        </label>
      </div>

      {routing && (
        <div className={routing.fallback ? "cs-routing fallback" : "cs-routing"}>
          {t("chat.routing", { intent: routing.intentName ?? t("chat.unmatched") })}
          {routing.intentKey && <code>{routing.intentKey}</code>}
          <span className="cs-conf">
            {t("chat.confidence", { value: Math.round(routing.confidence * 100) })}
          </span>
          {routing.targetSubagentKey && (
            <span>{t("chat.subagent", { key: routing.targetSubagentKey })}</span>
          )}
          {routing.fallback && <span className="cs-fallback-tag">{t("chat.fallback")}</span>}
        </div>
      )}

      <div className="cs-chat">
        <div className="cs-messages">
          {messages.length === 0 && <div className="empty-state">{t("chat.empty")}</div>}
          {messages.map((m, i) => (
            <div key={i} className={m.role === "user" ? "cs-msg user" : "cs-msg assistant"}>
              <span className="cs-role">
                {m.role === "user" ? t("chat.user") : t("chat.assistant")}
              </span>
              {m.role === "assistant" && !m.error
                ? <RichChatMessage source={m.content} onAction={(value) => void send(value)} />
                : <p>{m.content}</p>}
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
        <div className="cs-input-bar">
          <textarea
            rows={2}
            value={input}
            placeholder={t("chat.placeholder")}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                void send();
              }
            }}
          />
          <button className="ui-button" onClick={() => void send()} disabled={sending}>
            {sending ? t("chat.replying") : t("chat.send")}
          </button>
        </div>
      </div>
    </>
  );
}
