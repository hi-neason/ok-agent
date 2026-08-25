import { lazy, Suspense, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  observeSessionIdFromPath,
  observeSessionPath,
  observeTraceIdFromPath,
} from "./modules/observe/routes";
import { useAuth } from "./modules/auth";
import { agentManagementPages } from "./modules/agent-management";
import { customerChatPages } from "./modules/customer-chat";
import { operatorWorkbenchPages } from "./modules/operator-workbench";
import {
  areaForPage,
  groupsForArea,
  isKnownPath,
  navItemById,
  pageForPath,
  pagePaths,
  productAreas,
  type Page,
  type ProductArea,
} from "./app/navigation";
import "./agent.css";
import "./profile.css";

const AgentRegistryPage = lazy(agentManagementPages.agents);
const AgentConfigPage = lazy(agentManagementPages.agentConfig);
const ModelRegistryPage = lazy(agentManagementPages.models);
const SkillRegistryPage = lazy(agentManagementPages.skills);
const McpPage = lazy(agentManagementPages.tools);
const KnowledgeSourcesPage = lazy(agentManagementPages.knowledge);
const ProductPage = lazy(agentManagementPages.products);
const WorkflowSourcesPage = lazy(agentManagementPages.workflows);
const ReleasePage = lazy(agentManagementPages.releases);
const ObservePage = lazy(agentManagementPages.observability);
const ChannelPage = lazy(agentManagementPages.channels);
const IntentPage = lazy(agentManagementPages.intents);
const AccountManagementPage = lazy(agentManagementPages.access);
const InboxPage = lazy(operatorWorkbenchPages.inbox);
const UserManagementPage = lazy(operatorWorkbenchPages.customers);
const UserDetailPage = lazy(operatorWorkbenchPages.customerDetail);
const PersonaPage = lazy(operatorWorkbenchPages.personas);
const CustomerChatPage = lazy(customerChatPages.chat);

export default function App() {
  const { t, i18n } = useTranslation();
  const { user, logout } = useAuth();
  const [profileOpen, setProfileOpen] = useState(false);
  const profileMenuRef = useRef<HTMLDivElement>(null);
  const [navCollapsed, setNavCollapsed] = useState(
    () => window.localStorage.getItem("ok-agent.nav-collapsed") === "true",
  );
  const agentConfigMatch = () =>
    window.location.pathname.match(/^\/(?:agent\/)?agents\/([^/]+)\/config(?:\/([a-z]+))?$/);
  const userDetailMatch = () =>
    window.location.pathname.match(/^\/(?:workbench\/customers|usermgmt)\/([^/]+)$/);
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
  useEffect(() => {
    if (!profileOpen) return;
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!profileMenuRef.current?.contains(event.target as Node)) {
        setProfileOpen(false);
      }
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setProfileOpen(false);
    };
    document.addEventListener("mousedown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [profileOpen]);
  const selected = navItemById[page];
  const area = areaForPage(page);
  const navigationGroups = groupsForArea(area);
  const moduleName = (module: { id: Page }) =>
    t(`navigation.${module.id}`);
  const navigate = (next: Page) => {
    window.history.pushState({}, "", pagePaths[next]);
    setPage(next);
    setAgentConfigId(null);
    setUserDetailId(null);
    setObserveSessionId(null);
    setObserveTraceId(null);
  };
  const switchArea = (nextArea: ProductArea) => {
    const target = productAreas.find((candidate) => candidate.id === nextArea);
    if (target) navigate(target.defaultPage);
  };
  const openUserDetail = (id: string) => {
    window.history.pushState({}, "", `${pagePaths.usermgmt}/${id}`);
    setPage("usermgmt");
    setUserDetailId(id);
  };
  const backToUsers = () => navigate("usermgmt");
  const openAgentConfig = (id: string) => {
    window.history.pushState({}, "", `${pagePaths.agents}/${id}/config`);
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
      <p>{t("common.wipDescription")}</p>
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
    inbox: <InboxPage />,
    leads: <WipPlaceholder name={moduleName(navItemById.leads)} kicker="LEAD" />,
    tickets: <WipPlaceholder name={moduleName(navItemById.tickets)} kicker="TICKET" />,
    followups: <WipPlaceholder name={moduleName(navItemById.followups)} kicker="FOLLOW UPS" />,
    performance: <WipPlaceholder name={moduleName(navItemById.performance)} kicker="PERFORMANCE" />,
    insight: <WipPlaceholder name={moduleName(navItemById.insight)} kicker="INSIGHT" />,
    sysconfig: <WipPlaceholder name={moduleName(navItemById.sysconfig)} kicker="SETTINGS" />,
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
        <p className="nav-caption">{t(`productAreas.${area}.caption`)}</p>
        <nav>
          {navigationGroups.map((group) => (
            <section key={group.key} style={{ marginBottom: 12 }}>
              <p className="nav-group-title"
                style={{
                  margin: "12px 10px 5px",
                  color: "#92a0b5",
                  fontSize: 10,
                  letterSpacing: ".12em",
                }}
              >
                {t(`navigationGroups.${group.key}`).toUpperCase()}
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
          <div className="topbar-context">
            <div className="product-switcher" aria-label={t("productAreas.switcher")}>
              {productAreas.map((productArea) => (
                <button
                  key={productArea.id}
                  className={area === productArea.id ? "active" : ""}
                  onClick={() => switchArea(productArea.id)}
                  aria-pressed={area === productArea.id}
                >
                  <i>{productArea.icon}</i>
                  <span>{t(`productAreas.${productArea.id}.name`)}</span>
                </button>
              ))}
            </div>
            <div className="topbar-crumbs">
              <span className="crumb">{t(`productAreas.${area}.name`)}</span>
              <i>/</i>
              <b>{moduleName(selected)}</b>
            </div>
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
              {i18n.resolvedLanguage === "zh-CN" ? t("common.switchToEnglish") : t("common.switchToChinese")}
            </button>
            <button className="icon-button">◐</button>
            <div className="profile-menu" ref={profileMenuRef}>
              <button
                className="profile-trigger"
                onClick={() => setProfileOpen((open) => !open)}
                title={t("auth.profile")}
                type="button"
                aria-haspopup="menu"
                aria-expanded={profileOpen}
              >
                <span className="auth-user-name">{user.displayName}</span>
                <span className="avatar auth-avatar" aria-hidden="true">
                  {(user.displayName || user.username).slice(0, 1).toUpperCase()}
                </span>
              </button>
              {profileOpen && (
                <div className="profile-dropdown" role="menu">
                  <div className="profile-summary">
                    <span className="avatar profile-avatar" aria-hidden="true">
                      {(user.displayName || user.username).slice(0, 1).toUpperCase()}
                    </span>
                    <span>
                      <b>{user.displayName}</b>
                      <small>@{user.username}</small>
                    </span>
                  </div>
                  <div className="profile-role">
                    <span>{t("auth.role")}</span>
                    <b>{t(`accounts.roles.${user.role}`)}</b>
                  </div>
                  <button
                    className="profile-signout"
                    type="button"
                    role="menuitem"
                    onClick={() => {
                      setProfileOpen(false);
                      logout();
                    }}
                  >
                    <span aria-hidden="true">↪</span>
                    {t("auth.signOut")}
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>
        <section className="page-content" key={page}>
          <Suspense fallback={<div className="empty-state">{t("common.moduleLoading")}</div>}>
            {content}
          </Suspense>
        </section>
      </section>
    </main>
  );
}
