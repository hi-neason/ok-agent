import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import type { ChatMessage } from "../types";

export function AgentDebugPanel({
  messages,
  input,
  sending,
  onInputChange,
  onSend,
  onNewSession,
}: {
  messages: ChatMessage[];
  input: string;
  sending: boolean;
  onInputChange: (value: string) => void;
  onSend: () => void;
  onNewSession: () => void;
}) {
  const { t } = useTranslation();
  const threadRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, sending]);

  return (
    <>
      <header>
        <b>{t("agents.debugChat")}</b>
        <button className="link-button" onClick={onNewSession} style={{ fontSize: 11 }}>
          ↻ {t("agents.newSession")}
        </button>
      </header>
      <div className="chat-thread" ref={threadRef}>
        {messages.length === 0 && (
          <div className="chat-empty">
            <i>◈</i>
            {t("agents.chatEmpty")}
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
          placeholder={t("agents.typeMessage")}
        />
        <button
          className="ui-button send"
          onClick={onSend}
          disabled={sending || !input.trim()}
        >
          {t("agents.send")}
        </button>
      </div>
    </>
  );
}
