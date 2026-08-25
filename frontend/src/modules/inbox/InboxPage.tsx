import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { PageHeader } from "../shared";
import { Markdown } from "../shared/Markdown";
import {
  assignWorkItem,
  changeWorkPriority,
  changeWorkStatus,
  claimWorkItem,
  getOutcome,
  getTurns,
  listOperators,
  listWorkItems,
  saveOutcome,
} from "./api";
import type {
  ConversationOutcomeDraft,
  ConversationWorkItem,
  CustomerSentiment,
  InboxOperator,
  WorkPriority,
  WorkStatus,
} from "./types";
import "./inbox.css";

const QUEUES: Array<WorkStatus | "ALL"> = [
  "ALL",
  "WAITING_HUMAN",
  "IN_PROGRESS",
  "WAITING_CUSTOMER",
  "OPEN",
  "RESOLVED",
  "CLOSED",
];

const PRIORITIES: WorkPriority[] = ["LOW", "NORMAL", "HIGH", "URGENT"];
const SENTIMENTS: CustomerSentiment[] = ["UNKNOWN", "POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED"];

const EMPTY_OUTCOME: ConversationOutcomeDraft = {
  summary: null,
  customerNeed: null,
  intentLabel: null,
  productInterest: null,
  budget: null,
  purchaseTimeline: null,
  sentiment: "UNKNOWN",
  resolutionCode: null,
  nextAction: null,
  followUpAt: null,
};

const NEXT_STATUS: Record<WorkStatus, WorkStatus[]> = {
  OPEN: ["WAITING_HUMAN", "IN_PROGRESS", "RESOLVED", "CLOSED"],
  WAITING_HUMAN: ["IN_PROGRESS", "RESOLVED", "CLOSED"],
  IN_PROGRESS: ["WAITING_CUSTOMER", "WAITING_HUMAN", "RESOLVED", "CLOSED"],
  WAITING_CUSTOMER: ["IN_PROGRESS", "WAITING_HUMAN", "RESOLVED", "CLOSED"],
  RESOLVED: ["IN_PROGRESS", "CLOSED"],
  CLOSED: ["IN_PROGRESS"],
};

function initials(value: string): string {
  return value.trim().slice(0, 1).toUpperCase() || "?";
}

export function InboxPage() {
  const { t, i18n } = useTranslation();
  const [queue, setQueue] = useState<WorkStatus | "ALL">("WAITING_HUMAN");
  const [items, setItems] = useState<ConversationWorkItem[]>([]);
  const [operators, setOperators] = useState<InboxOperator[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [turns, setTurns] = useState<Awaited<ReturnType<typeof getTurns>>>([]);
  const [outcome, setOutcome] = useState<ConversationOutcomeDraft>(EMPTY_OUTCOME);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [mutating, setMutating] = useState(false);
  const [savingOutcome, setSavingOutcome] = useState(false);
  const [outcomeSaved, setOutcomeSaved] = useState(false);
  const [error, setError] = useState("");

  const selected = useMemo(
    () => items.find((item) => item.sessionId === selectedId) ?? null,
    [items, selectedId],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [page, availableOperators] = await Promise.all([
        listWorkItems(queue === "ALL" ? undefined : queue),
        listOperators(),
      ]);
      setItems(page.content);
      setOperators(availableOperators);
      setSelectedId((current) =>
        current && page.content.some((item) => item.sessionId === current)
          ? current
          : page.content[0]?.sessionId ?? null,
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("inbox.loadFailed"));
    } finally {
      setLoading(false);
    }
  }, [queue, t]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!selectedId) {
      setTurns([]);
      setOutcome(EMPTY_OUTCOME);
      return;
    }
    let active = true;
    setDetailLoading(true);
    Promise.all([getTurns(selectedId), getOutcome(selectedId)])
      .then(([nextTurns, nextOutcome]) => {
        if (!active) return;
        setTurns(nextTurns);
        setOutcome(nextOutcome);
        setOutcomeSaved(false);
      })
      .catch(() => active && setError(t("inbox.turnsFailed")))
      .finally(() => active && setDetailLoading(false));
    return () => {
      active = false;
    };
  }, [selectedId, t]);

  const updateSelected = (next: ConversationWorkItem) => {
    setItems((current) =>
      current.map((item) => (item.sessionId === next.sessionId ? next : item)),
    );
  };

  const changeOutcome = <K extends keyof ConversationOutcomeDraft>(
    field: K,
    value: ConversationOutcomeDraft[K],
  ) => {
    setOutcome((current) => ({ ...current, [field]: value }));
    setOutcomeSaved(false);
  };

  const persistOutcome = async () => {
    if (!selected) return;
    setSavingOutcome(true);
    setError("");
    try {
      const saved = await saveOutcome(selected.sessionId, outcome);
      setOutcome(saved);
      setOutcomeSaved(true);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("inbox.outcome.saveFailed"));
    } finally {
      setSavingOutcome(false);
    }
  };

  const mutate = async (operation: () => Promise<ConversationWorkItem>) => {
    setMutating(true);
    setError("");
    try {
      const next = await operation();
      if (queue !== "ALL" && next.status !== queue) {
        await load();
      } else {
        updateSelected(next);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("common.unknownError"));
    } finally {
      setMutating(false);
    }
  };

  const locale = i18n.resolvedLanguage ?? "zh-CN";

  return (
    <div className="inbox-page">
      <PageHeader
        kicker={t("inbox.kicker")}
        title={t("inbox.title")}
        description={t("inbox.description")}
        action={
          <button className="ui-button quiet" onClick={() => void load()} disabled={loading}>
            ↻ {t("inbox.refresh")}
          </button>
        }
      />

      <div className="inbox-queue-tabs" role="tablist" aria-label={t("inbox.queues")}> 
        {QUEUES.map((status) => (
          <button
            key={status}
            role="tab"
            aria-selected={queue === status}
            className={queue === status ? "active" : ""}
            onClick={() => setQueue(status)}
          >
            <i className={`status-dot status-${status.toLowerCase()}`} />
            {t(`inbox.status.${status}`)}
          </button>
        ))}
      </div>

      {error && <div className="inbox-error">× {error}</div>}

      <section className="inbox-workbench">
        <aside className="inbox-list-panel">
          <header>
            <span>{t("inbox.queueTitle")}</span>
            <b>{items.length}</b>
          </header>
          <div className="inbox-list">
            {loading ? (
              <div className="inbox-state">{t("common.loading")}</div>
            ) : items.length === 0 ? (
              <div className="inbox-state">
                <i>✓</i>
                <b>{t("inbox.queueClear")}</b>
                <small>{t("inbox.queueClearHint")}</small>
              </div>
            ) : (
              items.map((item) => {
                const customer = item.customerName || item.userId || t("inbox.anonymous");
                return (
                  <button
                    key={item.sessionId}
                    className={`inbox-list-item ${selectedId === item.sessionId ? "selected" : ""}`}
                    onClick={() => setSelectedId(item.sessionId)}
                  >
                    <span className="inbox-customer-avatar">{initials(customer)}</span>
                    <span className="inbox-list-copy">
                      <span>
                        <b>{customer}</b>
                        <time>{new Date(item.updatedAt).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" })}</time>
                      </span>
                      <strong>{item.title || t("inbox.untitled")}</strong>
                      <small>
                        {item.agentName || "—"} · {t("inbox.turnCount", { count: item.turnCount })}
                      </small>
                    </span>
                    <i className={`priority-stripe priority-${item.priority.toLowerCase()}`} />
                  </button>
                );
              })
            )}
          </div>
        </aside>

        <main className="inbox-conversation-panel">
          {!selected ? (
            <div className="inbox-state inbox-detail-empty">
              <i>◇</i>
              <b>{t("inbox.selectConversation")}</b>
              <small>{t("inbox.selectConversationHint")}</small>
            </div>
          ) : (
            <>
              <header className="inbox-conversation-head">
                <div>
                  <small>{selected.sessionId}</small>
                  <h2>{selected.title || t("inbox.untitled")}</h2>
                </div>
                <span className={`inbox-status-badge status-${selected.status.toLowerCase()}`}>
                  {t(`inbox.status.${selected.status}`)}
                </span>
              </header>
              <div className="inbox-thread">
                {detailLoading ? (
                  <div className="inbox-state">{t("common.loading")}</div>
                ) : turns.length === 0 ? (
                  <div className="inbox-state">{t("inbox.noMessages")}</div>
                ) : (
                  turns.map((turn) => (
                    <article key={turn.id} className={`inbox-message ${turn.role}`}>
                      <div className="inbox-message-meta">
                        <b>{t(`inbox.role.${turn.role}`, { defaultValue: turn.role })}</b>
                        <time>{new Date(turn.createdAt).toLocaleString(locale)}</time>
                      </div>
                      <div className="inbox-message-body">
                        {turn.role === "assistant" ? <Markdown source={turn.content} /> : turn.content}
                      </div>
                    </article>
                  ))
                )}
              </div>
            </>
          )}
        </main>

        <aside className="inbox-control-panel">
          {!selected ? null : (
            <>
              <section className="inbox-customer-card">
                <span className="inbox-customer-avatar large">
                  {initials(selected.customerName || selected.userId || "?")}
                </span>
                <div>
                  <h3>{selected.customerName || t("inbox.anonymous")}</h3>
                  <small>{selected.userId || "—"}</small>
                </div>
              </section>

              <section className="inbox-control-section">
                <label>{t("inbox.assignee")}</label>
                <select
                  value={selected.assigneeAccountId ?? ""}
                  disabled={mutating || selected.status === "CLOSED"}
                  onChange={(event) => void mutate(() =>
                    assignWorkItem(selected.sessionId, event.target.value || null))}
                >
                  <option value="">{t("inbox.unassigned")}</option>
                  {operators.map((operator) => (
                    <option key={operator.id} value={operator.id}>
                      {operator.displayName} · {operator.username}
                    </option>
                  ))}
                </select>
                {!selected.assigneeAccountId && selected.status !== "CLOSED" && (
                  <button
                    className="inbox-claim-button"
                    disabled={mutating}
                    onClick={() => void mutate(() => claimWorkItem(selected.sessionId))}
                  >
                    {t("inbox.claim")}
                  </button>
                )}
              </section>

              <section className="inbox-control-section">
                <label>{t("inbox.priority")}</label>
                <div className="inbox-priority-grid">
                  {PRIORITIES.map((priority) => (
                    <button
                      key={priority}
                      className={selected.priority === priority ? "active" : ""}
                      disabled={mutating}
                      onClick={() => void mutate(() =>
                        changeWorkPriority(selected.sessionId, priority))}
                    >
                      <i className={`priority-dot priority-${priority.toLowerCase()}`} />
                      {t(`inbox.priorityValue.${priority}`)}
                    </button>
                  ))}
                </div>
              </section>

              <section className="inbox-control-section">
                <label>{t("inbox.nextAction")}</label>
                <div className="inbox-status-actions">
                  {NEXT_STATUS[selected.status].map((status) => (
                    <button
                      key={status}
                      disabled={mutating}
                      className={status === "RESOLVED" ? "resolve" : status === "CLOSED" ? "close" : ""}
                      onClick={() => void mutate(() => changeWorkStatus(selected.sessionId, status))}
                    >
                      {t(`inbox.action.${status}`)}
                    </button>
                  ))}
                </div>
              </section>

              <section className="inbox-control-section inbox-outcome-form">
                <div className="inbox-section-heading">
                  <label>{t("inbox.outcome.title")}</label>
                  <small>{t("inbox.outcome.hint")}</small>
                </div>
                <label>
                  <span>{t("inbox.outcome.summary")}</span>
                  <textarea
                    rows={3}
                    value={outcome.summary ?? ""}
                    onChange={(event) => changeOutcome("summary", event.target.value || null)}
                  />
                </label>
                <label>
                  <span>{t("inbox.outcome.customerNeed")}</span>
                  <textarea
                    rows={3}
                    value={outcome.customerNeed ?? ""}
                    onChange={(event) => changeOutcome("customerNeed", event.target.value || null)}
                  />
                </label>
                <div className="inbox-outcome-pair">
                  <label>
                    <span>{t("inbox.outcome.intent")}</span>
                    <input value={outcome.intentLabel ?? ""} onChange={(event) => changeOutcome("intentLabel", event.target.value || null)} />
                  </label>
                  <label>
                    <span>{t("inbox.outcome.sentiment")}</span>
                    <select value={outcome.sentiment} onChange={(event) => changeOutcome("sentiment", event.target.value as CustomerSentiment)}>
                      {SENTIMENTS.map((sentiment) => <option key={sentiment} value={sentiment}>{t(`inbox.outcome.sentimentValue.${sentiment}`)}</option>)}
                    </select>
                  </label>
                </div>
                <label>
                  <span>{t("inbox.outcome.productInterest")}</span>
                  <input value={outcome.productInterest ?? ""} onChange={(event) => changeOutcome("productInterest", event.target.value || null)} />
                </label>
                <div className="inbox-outcome-pair">
                  <label>
                    <span>{t("inbox.outcome.budget")}</span>
                    <input value={outcome.budget ?? ""} onChange={(event) => changeOutcome("budget", event.target.value || null)} />
                  </label>
                  <label>
                    <span>{t("inbox.outcome.timeline")}</span>
                    <input value={outcome.purchaseTimeline ?? ""} onChange={(event) => changeOutcome("purchaseTimeline", event.target.value || null)} />
                  </label>
                </div>
                <label>
                  <span>{t("inbox.outcome.resolution")}</span>
                  <input value={outcome.resolutionCode ?? ""} onChange={(event) => changeOutcome("resolutionCode", event.target.value || null)} />
                </label>
                <label>
                  <span>{t("inbox.outcome.nextAction")}</span>
                  <textarea rows={2} value={outcome.nextAction ?? ""} onChange={(event) => changeOutcome("nextAction", event.target.value || null)} />
                </label>
                <label>
                  <span>{t("inbox.outcome.followUpAt")}</span>
                  <input
                    type="datetime-local"
                    value={outcome.followUpAt ? outcome.followUpAt.slice(0, 16) : ""}
                    onChange={(event) => changeOutcome("followUpAt", event.target.value ? new Date(event.target.value).toISOString() : null)}
                  />
                </label>
                <button className="inbox-save-outcome" disabled={savingOutcome} onClick={() => void persistOutcome()}>
                  {savingOutcome ? t("inbox.outcome.saving") : outcomeSaved ? `✓ ${t("inbox.outcome.saved")}` : t("inbox.outcome.save")}
                </button>
              </section>

              <section className="inbox-control-section inbox-facts">
                <label>{t("inbox.context")}</label>
                <dl>
                  <div><dt>{t("inbox.agent")}</dt><dd>{selected.agentName || "—"}</dd></div>
                  <div><dt>{t("inbox.createdAt")}</dt><dd>{new Date(selected.createdAt).toLocaleString(locale)}</dd></div>
                  <div><dt>{t("inbox.updatedAt")}</dt><dd>{new Date(selected.updatedAt).toLocaleString(locale)}</dd></div>
                </dl>
              </section>
            </>
          )}
        </aside>
      </section>
    </div>
  );
}
