export type ProductArea = "agent" | "workbench" | "customer";

export type Page =
  | "agents"
  | "models"
  | "skills"
  | "mcp"
  | "knowledge"
  | "products"
  | "workflows"
  | "release"
  | "observe"
  | "system"
  | "persona"
  | "insight"
  | "sysconfig"
  | "usermgmt"
  | "intents"
  | "channels"
  | "custchat"
  | "inbox"
  | "mychannels"
  | "leads"
  | "tickets"
  | "followups"
  | "performance";

export type NavItem = {
  id: Page;
  area: ProductArea;
  group: "agent" | "component" | "business" | "system" | "workspace" | "customer" | "personal" | "external";
  icon: string;
  kicker: string;
  wip?: boolean;
};

export const productAreas: Array<{ id: ProductArea; defaultPage: Page; icon: string }> = [
  { id: "agent", defaultPage: "agents", icon: "◈" },
  { id: "workbench", defaultPage: "inbox", icon: "▤" },
  { id: "customer", defaultPage: "custchat", icon: "◎" },
];

export const navItems: NavItem[] = [
  { id: "agents", area: "agent", group: "agent", icon: "◈", kicker: "AGENT CONFIG" },
  { id: "release", area: "agent", group: "agent", icon: "↗", kicker: "RELEASE" },
  { id: "observe", area: "agent", group: "agent", icon: "◌", kicker: "OBSERVE" },
  { id: "models", area: "agent", group: "component", icon: "◌", kicker: "MODEL" },
  { id: "skills", area: "agent", group: "component", icon: "✦", kicker: "SKILL" },
  { id: "mcp", area: "agent", group: "component", icon: "⌘", kicker: "MCP" },
  { id: "knowledge", area: "agent", group: "component", icon: "◫", kicker: "KNOWLEDGE" },
  { id: "products", area: "agent", group: "business", icon: "◈", kicker: "PRODUCT" },
  { id: "workflows", area: "agent", group: "component", icon: "⌁", kicker: "WORKFLOW" },
  { id: "channels", area: "agent", group: "business", icon: "⇄", kicker: "CHANNEL" },
  { id: "intents", area: "agent", group: "business", icon: "⌥", kicker: "INTENT" },
  { id: "system", area: "agent", group: "system", icon: "◎", kicker: "ACCESS" },
  { id: "sysconfig", area: "agent", group: "system", icon: "⚙", kicker: "SETTINGS", wip: true },

  { id: "inbox", area: "workbench", group: "workspace", icon: "▤", kicker: "SERVICE INBOX" },
  { id: "mychannels", area: "workbench", group: "workspace", icon: "⇄", kicker: "MY CHANNELS" },
  { id: "followups", area: "workbench", group: "workspace", icon: "↻", kicker: "FOLLOW UPS", wip: true },
  { id: "usermgmt", area: "workbench", group: "customer", icon: "●", kicker: "CUSTOMER" },
  { id: "persona", area: "workbench", group: "customer", icon: "◑", kicker: "PERSONA" },
  { id: "leads", area: "workbench", group: "customer", icon: "↗", kicker: "LEAD", wip: true },
  { id: "tickets", area: "workbench", group: "customer", icon: "◇", kicker: "TICKET", wip: true },
  { id: "insight", area: "workbench", group: "personal", icon: "◍", kicker: "INSIGHT", wip: true },
  { id: "performance", area: "workbench", group: "personal", icon: "⌁", kicker: "PERFORMANCE", wip: true },

  { id: "custchat", area: "customer", group: "external", icon: "▣", kicker: "CUSTOMER CHAT" },
];

export const navItemById = Object.fromEntries(navItems.map((item) => [item.id, item])) as Record<Page, NavItem>;

export const pagePaths: Record<Page, string> = {
  agents: "/agent/agents",
  models: "/agent/models",
  skills: "/agent/skills",
  mcp: "/agent/tools",
  knowledge: "/agent/knowledge",
  products: "/agent/products",
  workflows: "/agent/workflows",
  release: "/agent/releases",
  observe: "/agent/observability",
  system: "/agent/access",
  channels: "/agent/channels",
  intents: "/agent/intents",
  sysconfig: "/agent/settings",
  inbox: "/workbench/inbox",
  mychannels: "/workbench/channels",
  followups: "/workbench/follow-ups",
  usermgmt: "/workbench/customers",
  persona: "/workbench/personas",
  leads: "/workbench/leads",
  tickets: "/workbench/tickets",
  insight: "/workbench/insights",
  performance: "/workbench/performance",
  custchat: "/chat",
};

const legacyPaths: Partial<Record<string, Page>> = {
  "/agents": "agents", "/models": "models", "/skills": "skills", "/mcp": "mcp",
  "/knowledge": "knowledge", "/products": "products", "/workflows": "workflows",
  "/releases": "release", "/observability": "observe", "/system": "system",
  "/persona": "persona", "/channels": "channels", "/intents": "intents",
  "/custchat": "custchat", "/inbox": "inbox", "/insight": "insight",
  "/mychannels": "mychannels",
  "/sysconfig": "sysconfig", "/usermgmt": "usermgmt",
};

const canonicalPaths = Object.fromEntries(
  Object.entries(pagePaths).map(([page, path]) => [path, page]),
) as Record<string, Page>;

export function pageForPath(path: string): Page {
  if (/^\/(?:agent\/)?agents\/[^/]+\/config/.test(path)) return "agents";
  if (/^\/(?:agent\/)?observability\//.test(path)) return "observe";
  if (/^\/(?:workbench\/customers|usermgmt)\//.test(path)) return "usermgmt";
  if (/^\/(?:agent\/tools|mcp)\//.test(path)) return "mcp";
  return canonicalPaths[path] ?? legacyPaths[path] ?? "agents";
}

export function isKnownPath(path: string): boolean {
  return Boolean(canonicalPaths[path] ?? legacyPaths[path]) || pageForPath(path) !== "agents" || path === pagePaths.agents;
}

export function areaForPage(page: Page): ProductArea {
  return navItemById[page].area;
}

export function groupsForArea(area: ProductArea): Array<{ key: NavItem["group"]; items: NavItem[] }> {
  const keys: NavItem["group"][] = area === "agent"
    ? ["agent", "component", "business", "system"]
    : area === "workbench"
      ? ["workspace", "customer", "personal"]
      : ["external"];
  return keys.map((key) => ({ key, items: navItems.filter((item) => item.area === area && item.group === key) }));
}
