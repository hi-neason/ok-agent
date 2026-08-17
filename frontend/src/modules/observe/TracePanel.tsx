import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchTrace } from "./api";
import type { SpanStatus, TraceSpan } from "./types";
import "./trace.css";

function formatDuration(us: number): string {
  if (us >= 1_000_000) return `${(us / 1_000_000).toFixed(2)}s`;
  if (us >= 1_000) return `${(us / 1_000).toFixed(1)}ms`;
  return `${us}μs`;
}

function typeLabel(type: string, t: (k: string) => string): string {
  if (type === "AGENT") return t("observe.traceAgent");
  if (type === "MODEL") return t("observe.traceModel");
  if (type === "TOOL") return t("observe.traceTool");
  return type;
}

function parseAttributes(raw: string | null): Record<string, unknown> {
  if (!raw) return {};
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function prettyJson(raw: string | null): string {
  if (!raw) return "";
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

function statusClass(status: string): string {
  if (status === "ERROR") return "error";
  if (status === "CANCELLED") return "cancelled";
  return "ok";
}

function buildTree(spans: TraceSpan[]): {
  roots: TraceSpan[];
  children: Map<string, TraceSpan[]>;
} {
  const children = new Map<string, TraceSpan[]>();
  const roots: TraceSpan[] = [];
  for (const span of spans) {
    if (span.parentSpanId) {
      const list = children.get(span.parentSpanId) ?? [];
      list.push(span);
      children.set(span.parentSpanId, list);
    } else {
      roots.push(span);
    }
  }
  return { roots, children };
}

function aggregate(spans: TraceSpan[]): {
  totalTokens: number;
  errorCount: number;
  spanCount: number;
} {
  let totalTokens = 0;
  let errorCount = 0;
  for (const span of spans) {
    const attrs = parseAttributes(span.attributes);
    const tokens = attrs["gen_ai.usage.total_tokens"];
    if (typeof tokens === "number") totalTokens += tokens;
    if (span.status === ("ERROR" as SpanStatus)) errorCount += 1;
  }
  return { totalTokens, errorCount, spanCount: spans.length };
}

type DetailTab = "input" | "output" | "attributes";

function SpanDetail({ span, t }: { span: TraceSpan; t: (k: string) => string }) {
  const hasInput = Boolean(span.input);
  const hasOutput = Boolean(span.output);
  const hasAttrs = Object.keys(parseAttributes(span.attributes)).length > 0;
  const [tab, setTab] = useState<DetailTab>(
    hasInput ? "input" : hasOutput ? "output" : "attributes",
  );

  const tabs: { key: DetailTab; label: string; visible: boolean }[] = [
    { key: "input", label: t("observe.traceInput"), visible: hasInput },
    { key: "output", label: t("observe.traceOutput"), visible: hasOutput },
    { key: "attributes", label: t("observe.traceAttributes"), visible: hasAttrs },
  ];
  const visibleTabs = tabs.filter((x) => x.visible);

  return (
    <div className="trace-detail-pane">
      <div className="trace-detail-head">
        <span className={`trace-type-badge type-${span.type.toLowerCase()}`}>
          {typeLabel(span.type, t)}
        </span>
        <span className="trace-detail-name" title={span.name}>
          {span.name}
        </span>
        <span className={`trace-status ${statusClass(span.status)}`}>{span.status}</span>
        <span className="trace-detail-duration">{formatDuration(span.durationUs)}</span>
      </div>
      {visibleTabs.length > 0 && (
        <div className="trace-detail-tabs">
          {visibleTabs.map((x) => (
            <button
              key={x.key}
              className={tab === x.key ? "active" : ""}
              onClick={() => setTab(x.key)}
            >
              {x.label}
            </button>
          ))}
        </div>
      )}
      <div className="trace-detail-body">
        {tab === "input" && <pre>{prettyJson(span.input)}</pre>}
        {tab === "output" && <pre>{prettyJson(span.output)}</pre>}
        {tab === "attributes" && <pre>{prettyJson(span.attributes)}</pre>}
      </div>
    </div>
  );
}

function TraceDrawer({
  traceId,
  onClose,
}: {
  traceId: string;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [spans, setSpans] = useState<TraceSpan[] | null>(null);
  const [error, setError] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  useEffect(() => {
    let active = true;
    setError("");
    fetchTrace(traceId)
      .then((data) => {
        if (!active) return;
        setSpans(data);
        if (data.length > 0) setSelectedId(data[0].spanId);
      })
      .catch(() => active && setError(t("observe.traceFailed")));
    return () => {
      active = false;
    };
  }, [traceId, t]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [onClose]);

  const tree = useMemo(() => (spans ? buildTree(spans) : null), [spans]);
  const selected = useMemo(
    () => spans?.find((s) => s.spanId === selectedId) ?? null,
    [spans, selectedId],
  );

  const traceStart = spans && spans.length > 0 ? spans[0].startUs : 0;
  const traceEnd = useMemo(
    () => (spans && spans.length > 0
      ? spans.reduce((max, s) => Math.max(max, s.endUs), traceStart)
      : 0),
    [spans, traceStart],
  );
  const stats = useMemo(() => (spans ? aggregate(spans) : null), [spans]);

  const toggleCollapse = (id: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const renderRows = (
    nodes: TraceSpan[],
    depth: number,
    out: React.ReactNode[],
  ) => {
    for (const span of nodes) {
      const kids = tree?.children.get(span.spanId) ?? [];
      const hasKids = kids.length > 0;
      const isCollapsed = collapsed.has(span.spanId);
      const total = Math.max(traceEnd - traceStart, 1);
      const left = ((span.startUs - traceStart) / total) * 100;
      const width = Math.max((span.durationUs / total) * 100, 1.2);
      const attrs = parseAttributes(span.attributes);
      const tokens =
        typeof attrs["gen_ai.usage.total_tokens"] === "number"
          ? (attrs["gen_ai.usage.total_tokens"] as number)
          : null;

      out.push(
        <div
          key={span.spanId}
          className={`trace-tree-row type-${span.type.toLowerCase()} ${statusClass(
            span.status,
          )} ${selectedId === span.spanId ? "selected" : ""}`}
          style={{ paddingLeft: depth * 16 + 8 }}
          onClick={() => setSelectedId(span.spanId)}
        >
          <div className="trace-tree-main">
            <button
              className={`trace-caret ${hasKids ? "" : "leaf"}`}
              onClick={(e) => {
                e.stopPropagation();
                if (hasKids) toggleCollapse(span.spanId);
              }}
            >
              {hasKids ? (isCollapsed ? "▸" : "▾") : ""}
            </button>
            <span className={`trace-type-badge type-${span.type.toLowerCase()}`}>
              {typeLabel(span.type, t)}
            </span>
            <span className="trace-tree-name" title={span.name}>
              {span.name}
            </span>
            <span className="trace-tree-meta">
              {tokens !== null && <span className="trace-tokens">{tokens} tok</span>}
              <span className={`trace-status ${statusClass(span.status)}`}>
                {span.status}
              </span>
              <span className="trace-duration">{formatDuration(span.durationUs)}</span>
            </span>
          </div>
          <div className="trace-waterfall">
            <span
              className={`trace-bar type-${span.type.toLowerCase()} ${statusClass(
                span.status,
              )}`}
              style={{ left: `${left}%`, width: `${width}%` }}
            />
          </div>
        </div>,
      );
      if (hasKids && !isCollapsed) renderRows(kids, depth + 1, out);
    }
    return out;
  };

  let body: React.ReactNode;
  if (error) {
    body = <div className="trace-drawer-placeholder error">× {error}</div>;
  } else if (!spans) {
    body = <div className="trace-drawer-placeholder">{t("observe.traceLoading")}</div>;
  } else if (spans.length === 0) {
    body = <div className="trace-drawer-placeholder">{t("observe.traceEmpty")}</div>;
  } else {
    body = (
      <div className="trace-drawer-body">
        <div className="trace-drawer-tree">
          <div className="trace-tree-head">
            <span>{t("observe.executionTrace")}</span>
            <span>{t("observe.traceDuration")}</span>
          </div>
          <div className="trace-tree-list">
            {renderRows(tree!.roots, 0, [])}
          </div>
        </div>
        <div className="trace-drawer-detail">
          {selected ? (
            <SpanDetail span={selected} t={t} />
          ) : (
            <div className="trace-drawer-placeholder">{t("observe.traceEmpty")}</div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="trace-drawer-overlay" onClick={onClose}>
      <aside
        className="trace-drawer"
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="trace-drawer-topbar">
          <div className="trace-drawer-title">
            <span className="trace-drawer-kicker">{t("observe.executionTrace")}</span>
            <code className="trace-drawer-id" title={traceId}>
              Trace ID {traceId}
            </code>
          </div>
          <button className="trace-drawer-close" onClick={onClose} aria-label={t("observe.close")}>
            ×
          </button>
        </header>
        {stats && (
          <div className="trace-summary">
            <article>
              <small>{t("observe.traceSpans")}</small>
              <b>{stats.spanCount}</b>
            </article>
            <article>
              <small>{t("observe.traceTokens")}</small>
              <b>{stats.totalTokens}</b>
            </article>
            <article>
              <small>{t("observe.traceErrors")}</small>
              <b className={stats.errorCount > 0 ? "has-error" : ""}>
                {stats.errorCount}
              </b>
            </article>
            <article>
              <small>{t("observe.traceDuration")}</small>
              <b>{formatDuration(traceEnd - traceStart)}</b>
            </article>
          </div>
        )}
        {body}
      </aside>
    </div>
  );
}

/**
 * In-place trigger button. The trace itself renders in a right-side drawer
 * (lazy-loaded on open) instead of expanding inline under the message.
 */
export function TracePanel({ traceId }: { traceId: string }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  return (
    <>
      <button className="trace-toggle" onClick={() => setOpen(true)}>
        ▸ {t("observe.viewTrace")}
      </button>
      {open && <TraceDrawer traceId={traceId} onClose={() => setOpen(false)} />}
    </>
  );
}
