import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Markdown, CollapsibleMarkdown } from "../shared";
import { fetchTurns, searchSessions } from "./api";
import type { DialogueSummary, DialogueTurn } from "./types";
import { TracePanel } from "./TracePanel";

function roleLabel(role: string, t: (key: string) => string): string {
  if (role === "user") return t("observe.roleUser");
  if (role === "assistant") return t("observe.roleAssistant");
  if (role === "error") return t("observe.roleError");
  return role;
}

/**
 * Read-only replay of one dialogue session. It resolves the session header through the same
 * search endpoint the list uses, so a deep link (`/observability/<sessionId>`) renders fully
 * without any extra API.
 */
export function ObserveSessionDetailPage({
  sessionId,
  onBack,
}: {
  sessionId: string;
  onBack: () => void;
}) {
  const { t } = useTranslation();
  const [summary, setSummary] = useState<DialogueSummary | null>(null);
  const [turns, setTurns] = useState<DialogueTurn[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    Promise.all([
      searchSessions({ sessionId, page: 0, size: 1 }),
      fetchTurns(sessionId),
    ])
      .then(([page, messages]) => {
        if (!active) return;
        setSummary(page.content[0] ?? null);
        setTurns(messages);
      })
      .catch(() => active && setError(t("observe.turnsFailed")))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [sessionId, t]);

  const meta: [string, string][] = summary
    ? [
        [t("observe.agent"), summary.agentName ?? "—"],
        [t("observe.user"), summary.userId ?? "—"],
        [t("observe.turns"), String(summary.turnCount)],
        [t("observe.created"), new Date(summary.createdAt).toLocaleString()],
        [t("observe.updated"), new Date(summary.updatedAt).toLocaleString()],
      ]
    : [];

  return (
    <>
      <header className="page-header dialogue-detail-header">
        <div>
          <p className="kicker">{t("observe.replayKicker")}</p>
          <h1>{summary?.title || t("observe.untitled")}</h1>
          <code className="dialogue-detail-id">{sessionId}</code>
        </div>
        <Button quiet onClick={onBack}>
          ‹ {t("observe.back")}
        </Button>
      </header>
      {error && <div className="dialogue-error">× {error}</div>}
      {meta.length > 0 && (
        <div className="dialogue-meta">
          {meta.map(([label, value]) => (
            <article key={label}>
              <small>{label}</small>
              <b>{value}</b>
            </article>
          ))}
        </div>
      )}
      <section className="dialogue-transcript">
        {loading ? (
          <div className="dialogue-empty">
            <span>◌</span>
            <b>{t("observe.loading")}</b>
          </div>
        ) : turns.length === 0 ? (
          <div className="dialogue-empty">
            <span>◌</span>
            <b>{t("observe.noTurns")}</b>
            <p>{t("observe.noTurnsDescription")}</p>
          </div>
        ) : (
          turns.map((turn) => (
            <article className={`dialogue-turn ${turn.role}`} key={turn.id}>
              <div className="dialogue-turn-head">
                <b>{roleLabel(turn.role, t)}</b>
                <span>#{turn.seq}</span>
                <small>{new Date(turn.createdAt).toLocaleString()}</small>
              </div>
              <div className="dialogue-turn-body">
                <CollapsibleMarkdown source={turn.content} />
              </div>
              {(turn.model || turn.latencyMs !== null || turn.tokenUsage !== null) && (
                <div className="dialogue-turn-foot">
                  {turn.model && <span>{turn.model}</span>}
                  {turn.latencyMs !== null && (
                    <span>{t("observe.latency", { ms: turn.latencyMs })}</span>
                  )}
                  {turn.tokenUsage !== null && (
                    <span>{t("observe.tokens", { count: turn.tokenUsage })}</span>
                  )}
                </div>
              )}
              {turn.traceId && <TracePanel traceId={turn.traceId} />}
            </article>
          ))
        )}
      </section>
    </>
  );
}
