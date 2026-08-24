import { ObserveSessionDetailPage } from "./ObserveSessionDetailPage";
import { ObserveSessionsPage } from "./ObserveSessionsPage";
import { TracePage } from "./TracePanel";

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
