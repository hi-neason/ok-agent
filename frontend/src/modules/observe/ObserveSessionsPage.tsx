import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, PageHeader, Pagination } from "../shared";
import { searchSessions } from "./api";
import type { DialogueSummary, SessionPage } from "./types";

const DEFAULT_PAGE_SIZE = 20;

type Filters = {
  sessionId: string;
  userId: string;
  from: string;
  to: string;
};

const emptyFilters: Filters = { sessionId: "", userId: "", from: "", to: "" };

/**
 * Converts a `<input type="date">` value into an explicit instant so the server never has to
 * guess a timezone. `from` becomes local midnight; `to` becomes the next local midnight and is
 * treated as an exclusive upper bound by the API.
 */
function dayBoundary(day: string, offsetDays: number): string | undefined {
  if (!day) return undefined;
  const date = new Date(`${day}T00:00:00`);
  if (Number.isNaN(date.getTime())) return undefined;
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString();
}

export function ObserveSessionsPage({
  onOpenSession,
}: {
  onOpenSession: (sessionId: string) => void;
}) {
  const { t } = useTranslation();
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [applied, setApplied] = useState<Filters>(emptyFilters);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [result, setResult] = useState<SessionPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(
    async (target: Filters, targetPage: number, targetSize: number) => {
      setLoading(true);
      setError("");
      try {
        setResult(
          await searchSessions({
            sessionId: target.sessionId,
            userId: target.userId,
            from: dayBoundary(target.from, 0),
            to: dayBoundary(target.to, 1),
            page: targetPage,
            size: targetSize,
          }),
        );
      } catch {
        setError(t("observe.loadFailed"));
      } finally {
        setLoading(false);
      }
    },
    [t],
  );

  useEffect(() => {
    void load(applied, page, pageSize);
  }, [load, applied, page, pageSize]);

  const runSearch = () => {
    setApplied(filters);
    setPage(0);
  };

  const reset = () => {
    setFilters(emptyFilters);
    setApplied(emptyFilters);
    setPage(0);
  };

  const sessions: DialogueSummary[] = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;
  const total = result?.totalElements ?? 0;

  return (
    <>
      <PageHeader
        kicker={t("observe.historyKicker")}
        title={t("observe.title")}
        description={t("observe.description")}
        action={
          <Button quiet onClick={() => void load(applied, page, pageSize)} disabled={loading}>
            ↻ {t("observe.refresh")}
          </Button>
        }
      />
      {error && <div className="dialogue-error">× {error}</div>}
      <section className="run-table dialogue-table">
        <div className="table-tools dialogue-filters">
          <label className="search-mini">
            ⌕
            <input
              value={filters.sessionId}
              onChange={(event) =>
                setFilters({ ...filters, sessionId: event.target.value })
              }
              onKeyDown={(event) => event.key === "Enter" && runSearch()}
              placeholder={t("observe.sessionIdPlaceholder")}
            />
          </label>
          <label className="search-mini">
            ◑
            <input
              value={filters.userId}
              onChange={(event) =>
                setFilters({ ...filters, userId: event.target.value })
              }
              onKeyDown={(event) => event.key === "Enter" && runSearch()}
              placeholder={t("observe.userIdPlaceholder")}
            />
          </label>
          <label className="search-mini dialogue-date">
            {t("observe.from")}
            <input
              type="date"
              value={filters.from}
              max={filters.to || undefined}
              onChange={(event) =>
                setFilters({ ...filters, from: event.target.value })
              }
            />
          </label>
          <label className="search-mini dialogue-date">
            {t("observe.to")}
            <input
              type="date"
              value={filters.to}
              min={filters.from || undefined}
              onChange={(event) =>
                setFilters({ ...filters, to: event.target.value })
              }
            />
          </label>
          <Button onClick={runSearch} disabled={loading}>
            {loading ? t("observe.searching") : t("observe.search")}
          </Button>
          <button className="filter-chip" onClick={reset} disabled={loading}>
            {t("observe.reset")}
          </button>
        </div>
        <div className="table-head dialogue-row">
          <span>{t("observe.session")}</span>
          <span>{t("observe.topic")}</span>
          <span>{t("observe.agent")}</span>
          <span>{t("observe.channelType")}</span>
          <span>{t("observe.user")}</span>
          <span>{t("observe.turns")}</span>
          <span>{t("observe.updated")}</span>
          <span />
        </div>
        {sessions.length === 0 ? (
          <div className="dialogue-empty">
            <span>◌</span>
            <b>{loading ? t("observe.searching") : t("observe.emptyTitle")}</b>
            <p>{t("observe.emptyDescription")}</p>
          </div>
        ) : (
          sessions.map((session) => (
            <button
              className="table-row dialogue-row"
              key={session.sessionId}
              onClick={() => onOpenSession(session.sessionId)}
            >
              <code className="dialogue-session-id">{session.sessionId}</code>
              <span>
                <b>{session.title || t("observe.untitled")}</b>
                <small>{new Date(session.createdAt).toLocaleString()}</small>
              </span>
              <span>{session.agentName ?? "—"}</span>
              <span>{t(`observe.channelTypes.${session.channelType ?? (session.sessionId.startsWith("dbg-") ? "DEBUG" : session.sessionId.startsWith("web:") ? "WEB" : "UNKNOWN")}`, { defaultValue: t("observe.channelTypes.UNKNOWN") })}</span>
              <span>{session.userId ?? "—"}</span>
              <span>{session.turnCount}</span>
              <span>{new Date(session.updatedAt).toLocaleString()}</span>
              <i>→</i>
            </button>
          ))
        )}
        <Pagination
          page={page}
          totalPages={totalPages}
          totalElements={total}
          size={pageSize}
          loading={loading}
          onPageChange={setPage}
          onSizeChange={(size) => {
            setPageSize(size);
            setPage(0);
          }}
        />
      </section>
    </>
  );
}
