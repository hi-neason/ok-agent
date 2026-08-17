import { ObserveSessionDetailPage } from "./ObserveSessionDetailPage";
import { ObserveSessionsPage } from "./ObserveSessionsPage";
import { TracePage } from "./TracePanel";

/** Base path of the 运行观测 module; the session replay lives one level below it. */
export const OBSERVE_BASE_PATH = "/observability";

/** Route shape owned by this module so the shell does not hardcode the URL layout. */
export function observeSessionPath(sessionId: string): string {
  return `${OBSERVE_BASE_PATH}/${encodeURIComponent(sessionId)}`;
}

export function observeSessionIdFromPath(path: string): string | null {
  const match = path.match(/^\/observability\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}

/** Returns the trace id from a directly addressable trace detail URL. */
export function observeTraceIdFromPath(path: string): string | null {
  const match = path.match(/^\/observability\/traces\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}

/** Builds the browser URL for a standalone execution-trace page. */
export function observeTracePath(traceId: string): string {
  return `${OBSERVE_BASE_PATH}/traces/${encodeURIComponent(traceId)}`;
}

/**
 * Dispatcher for the 运行观测 module: the dialogue history list, or the replay of one session.
 * The shell owns the URL and the selected session id (mirroring how agent configuration is
 * routed), so navigating away through the sidebar reliably returns to the list.
 */
export function ObservePage({
  sessionId,
  traceId,
  onOpenSession,
  onBack,
}: {
  sessionId: string | null;
  traceId: string | null;
  onOpenSession: (sessionId: string) => void;
  onBack: () => void;
}) {
  return traceId ? (
    <TracePage traceId={traceId} onBack={onBack} />
  ) : sessionId ? (
    <ObserveSessionDetailPage sessionId={sessionId} onBack={onBack} />
  ) : (
    <ObserveSessionsPage onOpenSession={onOpenSession} />
  );
}
