import { useEffect, useRef, useState } from "react";
import { PageHeader } from "../shared";
import { loadUsers, type DebugUser } from "../agent/api";
import "./chat.css";

type ChatMessage = { role: "user" | "assistant"; content: string; error?: boolean };
type RoutingInfo = {
  intentKey: string | null;
  intentName: string | null;
  confidence: number;
  targetSubagentKey: string | null;
  fallback: boolean;
};

type AgentOption = { id: string; name: string; agentKey: string };

export function CustomerChatPage() {
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
        const [aRes, uRes] = await Promise.all([
          fetch("/api/v1/agents").then((r) => (r.ok ? r.json() : [])),
          loadUsers(),
        ]);
        const list = (aRes as Array<Record<string, unknown>>).map((a) => ({
          id: String(a.id),
          name: String(a.name),
          agentKey: String(a.agentKey),
        }));
        setAgents(list);
        setUsers(uRes);
        if (list.length > 0) setAgentId(list[0].id);
        if (uRes.length > 0) setUserId(uRes[0].userId);
      } catch {
        setNotice("加载路由智能体/用户失败");
      }
    })();
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const send = async () => {
    const text = input.trim();
    if (!text || sending) return;
    if (!agentId) {
      setNotice("请先选择一个路由智能体");
      return;
    }
    if (!userId) {
      setNotice("请先选择一个用户");
      return;
    }
    setInput("");
    setNotice(null);
    setMessages((m) => [...m, { role: "user", content: text }]);
    setSending(true);
    try {
      const res = await fetch("/api/v1/chat", {
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
          }
        | null;
      if (!res.ok || !data) {
        throw new Error(data?.detail || "请求失败");
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
      const msg = e instanceof Error ? e.message : "请求失败";
      setMessages((m) => [...m, { role: "assistant", content: msg, error: true }]);
    } finally {
      setSending(false);
    }
  };

  return (
    <>
      <PageHeader
        kicker="PRODUCTION / CUSTOMER CHAT"
        title="客服对话"
        description="基于渠道(channel)与会话(session)的生产对话入口：系统按意图自动路由到路由智能体下的子 Agent。"
      />
      {notice && <div className="skill-error">{notice}</div>}
      <div className="cs-controls">
        <label>
          <span>路由智能体（主 Agent）</span>
          <select value={agentId} onChange={(e) => setAgentId(e.target.value)}>
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}（{a.agentKey}）
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>用户</span>
          <select value={userId} onChange={(e) => setUserId(e.target.value)}>
            {users.map((u) => (
              <option key={u.userId} value={u.userId}>
                {u.displayName || u.username}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>渠道 channelId</span>
          <input value={channelId} onChange={(e) => setChannelId(e.target.value)} />
        </label>
        <label>
          <span>会话 sessionId</span>
          <input value={sessionId} onChange={(e) => setSessionId(e.target.value)} />
        </label>
      </div>

      {routing && (
        <div className={routing.fallback ? "cs-routing fallback" : "cs-routing"}>
          路由：{routing.intentName ?? "（未匹配）"}
          {routing.intentKey && <code>{routing.intentKey}</code>}
          <span className="cs-conf">置信度 {Math.round(routing.confidence * 100)}%</span>
          {routing.targetSubagentKey && <span>→ 子Agent {routing.targetSubagentKey}</span>}
          {routing.fallback && <span className="cs-fallback-tag">已转兜底</span>}
        </div>
      )}

      <div className="cs-chat">
        <div className="cs-messages">
          {messages.length === 0 && <div className="empty-state">开始一段客服对话</div>}
          {messages.map((m, i) => (
            <div key={i} className={m.role === "user" ? "cs-msg user" : "cs-msg assistant"}>
              <span className="cs-role">{m.role === "user" ? "用户" : "智能体"}</span>
              <p>{m.content}</p>
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
        <div className="cs-input-bar">
          <textarea
            rows={2}
            value={input}
            placeholder="输入用户问题…"
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                void send();
              }
            }}
          />
          <button className="ui-button" onClick={send} disabled={sending}>
            {sending ? "回复中…" : "发送"}
          </button>
        </div>
      </div>
    </>
  );
}
