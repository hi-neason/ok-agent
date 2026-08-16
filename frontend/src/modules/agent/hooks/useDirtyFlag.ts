import { useCallback, useEffect, useState } from "react";

/**
 * Tracks whether the agent configuration has unsaved edits and warns the user
 * when they try to leave the page with pending changes.
 */
export function useDirtyFlag() {
  const [dirty, setDirty] = useState(false);

  const markDirty = useCallback(() => setDirty(true), []);
  const resetDirty = useCallback(() => setDirty(false), []);

  useEffect(() => {
    if (!dirty) return;
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);

  return { dirty, markDirty, resetDirty };
}
