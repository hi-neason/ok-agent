import { McpDebugPage } from "./McpDebugPage";
import { McpRegistryPage } from "./McpRegistryPage";

export function McpPage() {
  const match = window.location.pathname.match(/^\/mcp\/([^/]+)\/debug$/);
  return match ? (
    <McpDebugPage serverId={match[1]} />
  ) : (
    <McpRegistryPage />
  );
}
