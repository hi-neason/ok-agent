/** Base path of the 运行观测 module; the session replay lives one level below it. */
export const OBSERVE_BASE_PATH = "/observability";

export function observeSessionPath(sessionId: string): string {
  return `${OBSERVE_BASE_PATH}/${encodeURIComponent(sessionId)}`;
}

export function observeSessionIdFromPath(path: string): string | null {
  const match = path.match(/^\/observability\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}

export function observeTraceIdFromPath(path: string): string | null {
  const match = path.match(/^\/observability\/traces\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}

export function observeTracePath(traceId: string): string {
  return `${OBSERVE_BASE_PATH}/traces/${encodeURIComponent(traceId)}`;
}
