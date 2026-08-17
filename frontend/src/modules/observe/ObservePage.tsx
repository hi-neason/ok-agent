import { ObserveSessionDetailPage } from "./ObserveSessionDetailPage";
import { ObserveSessionsPage } from "./ObserveSessionsPage";

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

/**
 * Dispatcher for the 运行观测 module: the dialogue history list, or the replay of one session.
 * The shell owns the URL and the selected session id (mirroring how agent configuration is
 * routed), so navigating away through the sidebar reliably returns to the list.
 */
export function ObservePage({
  sessionId,
  onOpenSession,
  onBack,
}: {
  sessionId: string | null;
  onOpenSession: (sessionId: string) => void;
  onBack: () => void;
}) {
  return sessionId ? (
    <ObserveSessionDetailPage sessionId={sessionId} onBack={onBack} />
  ) : (
    <ObserveSessionsPage onOpenSession={onOpenSession} />
  );
}
