import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchTrace } from "./api";
import type { SpanStatus, TraceSpan } from "./types";
import "./trace.css";

function formatDuration(us: number): string {
  if (us >= 1_000_000) return `${(us / 1_000_000).toFixed(2)}s`;
  if (us >= 1_000) return `${(us / 1000).toFixed(1)}ms`;
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

function SpanRow({
  span,
  traceStart,
  traceEnd,
  depth,
}: {
  span: TraceSpan;
  traceStart: number;
  traceEnd: number;
  depth: number;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const total = Math.max(traceEnd - traceStart, 1);
  const left = ((span.startUs - traceStart) / total) * 100;
  const width = Math.max((span.durationUs / total) * 100, 1.5);
  const attrs = parseAttributes(span.attributes);
  const tokens =
    typeof attrs["gen_ai.usage.total_tokens"] === "number"
      ? (attrs["gen_ai.usage.total_tokens"] as number)
      : null;

  return (
    <div className={`trace-row type-${span.type.toLowerCase()} ${statusClass(span.status)}`}>
      <button
        className="trace-row-head"
        onClick={() => setOpen((v) => !v)}
        title={span.name}
      >
        <span className="trace-row-gutter" style={{ paddingLeft: depth * 16 }}>
          <i className="trace-caret">{open ? "▾" : "▸"}</i>
          <span className={`trace-type-badge type-${span.type.toLowerCase()}`}>
            {typeLabel(span.type, t)}
          </span>
          <span className="trace-name">{span.name}</span>
        </span>
        <span className="trace-row-meta">
          {tokens !== null && <span className="trace-tokens">{tokens} tok</span>}
          <span className={`trace-status ${statusClass(span.status)}`}>
            {span.status}
          </span>
          <span className="trace-duration">{formatDuration(span.durationUs)}</span>
        </span>
      </button>

      <div className="trace-waterfall">
        <span
          className={`trace-bar type-${span.type.toLowerCase()} ${statusClass(span.status)}`}
          style={{ left: `${left}%`, width: `${width}%` }}
        />
      </div>

      {open && (
        <div className="trace-detail">
          {Object.keys(attrs).length > 0 && (
            <div className="trace-detail-section">
              <h4>{t("observe.traceAttributes")}</h4>
              <pre>{prettyJson(span.attributes)}</pre>
            </div>
          )}
          {span.input && (
            <div className="trace-detail-section">
              <h4>{t("observe.traceInput")}</h4>
              <pre>{prettyJson(span.input)}</pre>
            </div>
          )}
          {span.output && (
            <div className="trace-detail-section">
              <h4>{t("observe.traceOutput")}</h4>
              <pre>{prettyJson(span.output)}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
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

function renderTree(
  roots: TraceSpan[],
  children: Map<string, TraceSpan[]>,
  traceStart: number,
  traceEnd: number,
  depth = 0,
): ReactNode[] {
  const nodes: ReactNode[] = [];
  for (const span of roots) {
    nodes.push(
      <SpanRow
        key={span.spanId}
        span={span}
        traceStart={traceStart}
        traceEnd={traceEnd}
        depth={depth}
      />,
    );
    const kids = children.get(span.spanId);
    if (kids && kids.length > 0) {
      nodes.push(...renderTree(kids, children, traceStart, traceEnd, depth + 1));
    }
  }
  return nodes;
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

export function TracePanel({ traceId }: { traceId: string }) {
  const { t } = useTranslation();
  const [enabled, setEnabled] = useState(false);
  const [spans, setSpans] = useState<TraceSpan[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!enabled) return;
    let active = true;
    setError("");
    fetchTrace(traceId)
      .then((data) => active && setSpans(data))
      .catch(() => active && setError(t("observe.traceFailed")));
    return () => {
      active = false;
    };
  }, [enabled, traceId, t]);

  if (!enabled) {
    return (
      <button className="trace-toggle" onClick={() => setEnabled(true)}>
        ▸ {t("observe.viewTrace")}
      </button>
    );
  }

  let body: ReactNode;
  if (error) {
    body = <div className="trace-empty error">× {error}</div>;
  } else if (!spans) {
    body = <div className="trace-empty">{t("observe.traceLoading")}</div>;
  } else if (spans.length === 0) {
    body = <div className="trace-empty">{t("observe.traceEmpty")}</div>;
  } else {
    const { roots, children } = buildTree(spans);
    const traceStart = spans[0].startUs;
    const traceEnd = spans.reduce((max, s) => Math.max(max, s.endUs), traceStart);
    const stats = aggregate(spans);
    body = (
      <>
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
        <div className="trace-list">
          {renderTree(roots, children, traceStart, traceEnd)}
        </div>
      </>
    );
  }

  return (
    <div className="trace-panel">
      <div className="trace-panel-head">
        <span className="trace-panel-title">
          <i className="trace-dot" /> {t("observe.executionTrace")}
        </span>
        <button className="trace-collapse" onClick={() => setEnabled(false)}>
          {t("observe.collapse")}
        </button>
      </div>
      {body}
    </div>
  );
}
