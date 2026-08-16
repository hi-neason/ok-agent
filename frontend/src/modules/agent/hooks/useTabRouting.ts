import { useCallback, useEffect, useState } from "react";
import { AGENT_TABS, type AgentTab } from "../types";

function readTabFromUrl(): AgentTab {
  const match = window.location.pathname.match(
    /^\/agents\/[^/]+\/config(?:\/([a-z]+))?$/,
  );
  const segment = match?.[1] as AgentTab | undefined;
  return segment && AGENT_TABS.includes(segment) ? segment : "core";
}

/**
 * Keeps the active configuration tab in the URL (`/agents/{id}/config/{tab}`),
 * supports browser back/forward, and falls back to the default tab for unknown segments.
 */
export function useTabRouting(agentId: string) {
  const [tab, setTab] = useState<AgentTab>(() => readTabFromUrl());

  useEffect(() => {
    const current = readTabFromUrl();
    const expected = `/agents/${agentId}/config/${current}`;
    if (window.location.pathname !== expected) {
      window.history.replaceState({}, "", expected);
    }
    const onPop = () => setTab(readTabFromUrl());
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, [agentId]);

  const navigateTab = useCallback(
    (next: AgentTab) => {
      const target = AGENT_TABS.includes(next) ? next : "core";
      window.history.pushState({}, "", `/agents/${agentId}/config/${target}`);
      setTab(target);
    },
    [agentId],
  );

  return { tab, navigateTab };
}
