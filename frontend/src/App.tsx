import { lazy, Suspense, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  observeSessionIdFromPath,
  observeSessionPath,
  observeTraceIdFromPath,
} from "./modules/observe/routes";
import { useAuth } from "./modules/auth";
import "./agent.css";

const AgentRegistryPage = lazy(() =>
  import("./modules/agent/AgentRegistryPage").then((module) => ({ default: module.AgentRegistryPage })),
);
const AgentConfigPage = lazy(() =>
  import("./modules/agent/AgentConfigPage").then((module) => ({ default: module.AgentConfigPage })),
);
const ModelRegistryPage = lazy(() =>
  import("./modules/model/ModelRegistryPage").then((module) => ({ default: module.ModelRegistryPage })),
);
const SkillRegistryPage = lazy(() =>
  import("./modules/skill/SkillRegistryPage").then((module) => ({ default: module.SkillRegistryPage })),
);
const McpPage = lazy(() =>
  import("./modules/mcp/McpPage").then((module) => ({ default: module.McpPage })),
);
const ObservePage = lazy(() =>
  import("./modules/observe/ObservePage").then((module) => ({ default: module.ObservePage })),
);
const UserManagementPage = lazy(() =>
  import("./modules/usermgmt/UserManagementPage").then((module) => ({ default: module.UserManagementPage })),
);
const UserDetailPage = lazy(() =>
  import("./modules/usermgmt/UserDetailPage").then((module) => ({ default: module.UserDetailPage })),
);
const PersonaPage = lazy(() =>
  import("./modules/persona/PersonaPage").then((module) => ({ default: module.PersonaPage })),
);
const WorkflowSourcesPage = lazy(() =>
  import("./modules/workflow/WorkflowSourcesPage").then((module) => ({ default: module.WorkflowSourcesPage })),
);
const KnowledgeSourcesPage = lazy(() =>
  import("./modules/knowledge/KnowledgeSourcesPage").then((module) => ({ default: module.KnowledgeSourcesPage })),
);
const ProductPage = lazy(() =>
  import("./modules/product/ProductPage").then((module) => ({ default: module.ProductPage })),
);
const IntentPage = lazy(() =>
  import("./modules/intent/IntentPage").then((module) => ({ default: module.IntentPage })),
);
const ChannelPage = lazy(() =>
  import("./modules/channel/ChannelPage").then((module) => ({ default: module.ChannelPage })),
);
const ReleasePage = lazy(() =>
  import("./modules/release/ReleasePage").then((module) => ({ default: module.ReleasePage })),
);
const CustomerChatPage = lazy(() =>
  import("./modules/chat/CustomerChatPage").then((module) => ({ default: module.CustomerChatPage })),
);
const AccountManagementPage = lazy(() =>
  import("./modules/auth/AccountManagementPage").then((module) => ({ default: module.AccountManagementPage })),
);

type Page =
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
  | "custchat";

type NavItem = {
  id: Page;
  icon: string;
  name: string;
  kicker: string;
  wip?: boolean;
};

type NavigationGroup = {
  title: string;
  items: NavItem[];
};

// Full catalog of top-level modules. `wip` marks modules still under construction;
// a few are intentionally hidden from the primary navigation (see `hiddenNavIds`).
const navItems: NavItem[] = [
  { id: "agents", icon: "◈", name: "配置调试", kicker: "AGENT CONFIG" },
  { id: "custchat", icon: "▣", name: "客服对话", kicker: "CUSTOMER CHAT" },
  { id: "release", icon: "↗", name: "发布管理", kicker: "RELEASE" },
  { id: "observe", icon: "◌", name: "运行观测", kicker: "OBSERVE" },
  { id: "models", icon: "◌", name: "模型", kicker: "MODEL" },
  { id: "skills", icon: "✦", name: "技能", kicker: "SKILL" },
  { id: "mcp", icon: "⌘", name: "工具", kicker: "MCP" },
  { id: "knowledge", icon: "◫", name: "知识库 - 集成", kicker: "KNOWLEDGE" },
  { id: "products", icon: "◈", name: "产品管理", kicker: "PRODUCT" },
  { id: "workflows", icon: "⌁", name: "工作流 - 集成", kicker: "WORKFLOW" },
  { id: "persona", icon: "◑", name: "用户画像", kicker: "PERSONA" },
  { id: "channels", icon: "⇄", name: "渠道管理", kicker: "CHANNEL" },
  { id: "intents", icon: "⌥", name: "意图管理", kicker: "INTENT" },
  { id: "insight", icon: "◍", name: "对话洞察", kicker: "INSIGHT", wip: true },
  { id: "system", icon: "◎", name: "账号权限", kicker: "ACCESS" },
  { id: "sysconfig", icon: "⚙", name: "系统配置", kicker: "SETTINGS", wip: true },
  { id: "usermgmt", icon: "👤", name: "用户管理", kicker: "USER MGMT" },
];

const navItemById = Object.fromEntries(
  navItems.map((n) => [n.id, n]),
) as Record<Page, NavItem>;

const navigationGroups: NavigationGroup[] = [
  {
    title: "Agent管理",
    items: (["agents", "release", "observe", "custchat"] as Page[]).map((id) => navItemById[id]),
  },
  {
    title: "Component管理",
    items: (["models", "skills", "mcp", "knowledge", "workflows"] as Page[]).map(
      (id) => navItemById[id],
    ),
  },
  {
    title: "业务管理",
    items: (["usermgmt", "persona", "products", "channels", "intents", "insight"] as Page[]).map((id) => navItemById[id]),
  },
  {
    title: "系统管理",
    items: (["system", "sysconfig"] as Page[]).map((id) => navItemById[id]),
  },
];

const modules = navItems;
const pagePaths: Record<Page, string> = {
  agents: "/agents",
  models: "/models",
  skills: "/skills",
  mcp: "/mcp",
  knowledge: "/knowledge",
  products: "/products",
  workflows: "/workflows",
  release: "/releases",
  observe: "/observability",
  system: "/system",
  persona: "/persona",
  channels: "/channels",
  intents: "/intents",
  custchat: "/custchat",
  insight: "/insight",
  sysconfig: "/sysconfig",
  usermgmt: "/usermgmt",
};
const pathPages = Object.fromEntries(
  Object.entries(pagePaths).map(([page, path]) => [path, page]),
) as Record<string, Page>;

// Modules that own nested routes below their base path, e.g. /mcp/<id>/debug,
// /agents/<id>/config and /observability/<sessionId>. Such a path keeps its module selected
// in the sidebar instead of falling back to the default page.
const nestedPathPrefixes: [string, Page][] = [
  ["/mcp/", "mcp"],
  ["/agents/", "agents"],
  ["/observability/", "observe"],
  ["/usermgmt/", "usermgmt"],
];

const pageForPath = (path: string): Page =>
  nestedPathPrefixes.find(([prefix]) => path.startsWith(prefix))?.[1] ??
  pathPages[path] ??
  "agents";

const isKnownPath = (path: string): boolean =>
  Boolean(pathPages[path]) ||
  nestedPathPrefixes.some(([prefix]) => path.startsWith(prefix));

export default function App() {
  const { t, i18n } = useTranslation();
  const { user, logout } = useAuth();
  const [navCollapsed, setNavCollapsed] = useState(
    () => window.localStorage.getItem("ok-agent.nav-collapsed") === "true",
  );
  const agentConfigMatch = () =>
    window.location.pathname.match(/^\/agents\/([^/]+)\/config(?:\/([a-z]+))?$/);
  const userDetailMatch = () =>
    window.location.pathname.match(/^\/usermgmt\/([^/]+)$/);
  const [page, setPage] = useState<Page>(() =>
    pageForPath(window.location.pathname),
  );
  const [agentConfigId, setAgentConfigId] = useState<string | null>(() =>
    agentConfigMatch()?.[1] ?? null,
  );
  const [userDetailId, setUserDetailId] = useState<string | null>(() =>
    userDetailMatch()?.[1] ?? null,
  );
  const [observeSessionId, setObserveSessionId] = useState<string | null>(() =>
    observeSessionIdFromPath(window.location.pathname),
  );
  const [observeTraceId, setObserveTraceId] = useState<string | null>(() =>
    observeTraceIdFromPath(window.location.pathname),
  );
  useEffect(() => {
    if (!isKnownPath(window.location.pathname))
      window.history.replaceState({}, "", pagePaths.agents);
    const syncPage = () => {
      setPage(pageForPath(window.location.pathname));
      setAgentConfigId(agentConfigMatch()?.[1] ?? null);
      setUserDetailId(userDetailMatch()?.[1] ?? null);
      setObserveSessionId(observeSessionIdFromPath(window.location.pathname));
      setObserveTraceId(observeTraceIdFromPath(window.location.pathname));
    };
    window.addEventListener("popstate", syncPage);
    return () => window.removeEventListener("popstate", syncPage);
  }, []);
  const selected = modules.find((x) => x.id === page)!;
  const moduleName = (module: { id: Page; name: string }) =>
    t(`navigation.${module.id}`, { defaultValue: module.name });
  const groupTitleOf = (p: Page | undefined) =>
    p ? navigationGroups.find((g) => g.items.some((it) => it.id === p))?.title : undefined;
  const navigate = (next: Page) => {
    window.history.pushState({}, "", pagePaths[next]);
    setPage(next);
    setAgentConfigId(null);
    setUserDetailId(null);
    setObserveSessionId(null);
    setObserveTraceId(null);
  };
  const openUserDetail = (id: string) => {
    window.history.pushState({}, "", `/usermgmt/${id}`);
    setPage("usermgmt");
    setUserDetailId(id);
  };
  const backToUsers = () => navigate("usermgmt");
  const openAgentConfig = (id: string) => {
    window.history.pushState({}, "", `/agents/${id}/config`);
    setPage("agents");
    setAgentConfigId(id);
  };
  const backToAgents = () => navigate("agents");
  const openObserveSession = (sessionId: string) => {
    window.history.pushState({}, "", observeSessionPath(sessionId));
    setPage("observe");
    setObserveSessionId(sessionId);
  };
  const backToObserveList = () => navigate("observe");
  const WipPlaceholder = ({ name, kicker }: { name: string; kicker: string }) => (
    <div className="wip-placeholder">
      <div className="wip-placeholder-mark">WIP</div>
      <h1>{name}</h1>
      <p>该模块正在建设中，暂未开放。</p>
      <small>{kicker}</small>
    </div>
  );
  const content = {
    agents: agentConfigId ? (
      <AgentConfigPage agentId={agentConfigId} onBack={backToAgents} />
    ) : (
      <AgentRegistryPage onConfigure={openAgentConfig} />
    ),
    models: <ModelRegistryPage />,
    skills: <SkillRegistryPage />,
    mcp: <McpPage />,
    knowledge: <KnowledgeSourcesPage />,
    products: <ProductPage />,
    workflows: <WorkflowSourcesPage />,
    release: <ReleasePage />,
    observe: (
      <ObservePage
        sessionId={observeSessionId}
        traceId={observeTraceId}
        onOpenSession={openObserveSession}
        onBack={backToObserveList}
      />
    ),
    system: <AccountManagementPage />,
persona: <PersonaPage />,
channels: <ChannelPage />,
intents: <IntentPage />,
    custchat: <CustomerChatPage />,
    insight: <WipPlaceholder name="对话洞察" kicker="INSIGHT" />,
    sysconfig: <WipPlaceholder name="系统配置" kicker="SETTINGS" />,
    usermgmt: userDetailId ? (
      <UserDetailPage id={userDetailId} onBack={backToUsers} />
    ) : (
      <UserManagementPage onOpenUser={openUserDetail} />
    ),
  }[page];
  return (
    <main className={`console-shell ${navCollapsed ? "nav-collapsed" : ""}`}>
      <aside className="main-nav">
        <div className="brand">
          <span className="brand-mark">ok</span>
          <span className="brand-name">AGENT</span>
        </div>
        <button
          className="nav-collapse-button"
          aria-label={t("common.collapseNavigation")}
          title={t("common.collapseNavigation")}
          onClick={() => {
            const next = !navCollapsed;
            setNavCollapsed(next);
            window.localStorage.setItem("ok-agent.nav-collapsed", String(next));
          }}
        >
          {navCollapsed ? "›" : "‹"}
        </button>
        <p className="nav-caption">HARNESS CONTROL PLANE</p>
        <nav>
          {navigationGroups.map((group) => (
            <section key={group.title} style={{ marginBottom: 12 }}>
              <p className="nav-group-title"
                style={{
                  margin: "12px 10px 5px",
                  color: "#92a0b5",
                  fontSize: 10,
                  letterSpacing: ".12em",
                }}
              >
                {group.title.toUpperCase()}
              </p>
              {group.items.map((module) => (
                <button
                  key={module.id}
                  onClick={() => navigate(module.id)}
                  className={
                    page === module.id ? "module-link active" : "module-link"
                  }
                >
                  <i>{module.icon}</i>
                  <span>{moduleName(module)}</span>
                  {module.wip && <span className="wip-badge">WIP</span>}
                </button>
              ))}
            </section>
          ))}
        </nav>
        <div className="nav-footer">
          <i className="ok-dot" /> CN-SH-01 / HEALTHY
          <br />
          <small>AGENTSCOPE JAVA 2.0</small>
        </div>
      </aside>
      <section className="app-content">
        <header className="app-topbar">
          <div>
            <span className="crumb">{t("common.controlPlane")}</span>
            <i>/</i>
            {groupTitleOf(selected.id) && (
              <>
                <span className="crumb-sub">{groupTitleOf(selected.id)}</span>
                <i>/</i>
              </>
            )}
            <b>{moduleName(selected)}</b>
          </div>
          <div>
            <button className="top-search">
              ⌕ {t("common.search")} <kbd>⌘ K</kbd>
            </button>
            <button
              aria-label={t("common.language")}
              className="icon-button"
              onClick={() =>
                i18n.changeLanguage(
                  i18n.resolvedLanguage === "zh-CN" ? "en-US" : "zh-CN",
                )
              }
            >
              {i18n.resolvedLanguage === "zh-CN" ? "EN" : "中"}
            </button>
            <button className="icon-button">◐</button>
            <span className="auth-user-name">{user.displayName}</span>
            <button
              className="avatar auth-avatar"
              onClick={logout}
              title={t("auth.signOut")}
              type="button"
            >
              {(user.displayName || user.username).slice(0, 1).toUpperCase()}
            </button>
          </div>
        </header>
        <section className="page-content" key={page}>
          <Suspense fallback={<div className="empty-state">正在加载模块…</div>}>
            {content}
          </Suspense>
        </section>
      </section>
    </main>
  );
}
