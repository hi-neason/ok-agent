import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { AgentRegistryPage, AgentConfigPage } from "./modules/agent";
import { ModelRegistryPage } from "./modules/model";
import { SkillRegistryPage } from "./modules/skill";
import { McpPage } from "./modules/mcp";
import {
  ObservePage,
  observeSessionIdFromPath,
  observeSessionPath,
} from "./modules/observe";
import { UserManagementPage } from "./modules/usermgmt";
import { PersonaPage } from "./modules/persona";
import { Button, PageHeader } from "./modules/shared";
import "./agent.css";

type Page =
  | "agents"
  | "models"
  | "skills"
  | "mcp"
  | "knowledge"
  | "workflows"
  | "release"
  | "observe"
  | "system"
  | "persona"
  | "insight"
  | "sysconfig"
  | "usermgmt";

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
  { id: "release", icon: "↗", name: "发布管理", kicker: "RELEASE", wip: true },
  { id: "observe", icon: "◌", name: "运行观测", kicker: "OBSERVE" },
  { id: "models", icon: "◌", name: "模型", kicker: "MODEL" },
  { id: "skills", icon: "✦", name: "技能", kicker: "SKILL" },
  { id: "mcp", icon: "⌘", name: "工具", kicker: "MCP" },
  { id: "knowledge", icon: "◫", name: "知识库", kicker: "KNOWLEDGE", wip: true },
  { id: "workflows", icon: "⌁", name: "工作流", kicker: "WORKFLOW", wip: true },
  { id: "persona", icon: "◑", name: "用户画像", kicker: "PERSONA" },
  { id: "insight", icon: "◍", name: "对话洞察", kicker: "INSIGHT", wip: true },
  { id: "system", icon: "◎", name: "账号权限", kicker: "ACCESS", wip: true },
  { id: "sysconfig", icon: "⚙", name: "系统配置", kicker: "SETTINGS", wip: true },
  { id: "usermgmt", icon: "👤", name: "用户管理", kicker: "USER MGMT" },
];

const navItemById = Object.fromEntries(
  navItems.map((n) => [n.id, n]),
) as Record<Page, NavItem>;

const navigationGroups: NavigationGroup[] = [
  {
    title: "Agent管理",
    items: (["agents", "release", "observe"] as Page[]).map((id) => navItemById[id]),
  },
  {
    title: "Component管理",
    items: (["models", "skills", "mcp", "knowledge", "workflows"] as Page[]).map(
      (id) => navItemById[id],
    ),
  },
  {
    title: "业务管理",
    items: (["usermgmt", "persona", "insight"] as Page[]).map((id) => navItemById[id]),
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
  workflows: "/workflows",
  release: "/releases",
  observe: "/observability",
  system: "/system",
  persona: "/persona",
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
];

const pageForPath = (path: string): Page =>
  nestedPathPrefixes.find(([prefix]) => path.startsWith(prefix))?.[1] ??
  pathPages[path] ??
  "agents";

const isKnownPath = (path: string): boolean =>
  Boolean(pathPages[path]) ||
  nestedPathPrefixes.some(([prefix]) => path.startsWith(prefix));

function KnowledgePage() {
  return (
    <>
      <PageHeader
        kicker="KNOWLEDGE BASE / GLOBAL ASSET"
        title="知识库"
        description="维护可复用的知识集合、检索配置与访问范围；智能体仅绑定已经发布的知识库版本。"
        action={<Button>＋ 创建知识库</Button>}
      />
      <div className="content-split">
        <section className="catalog-panel">
          {[
            ["产品帮助中心", "product-help@12", "1,846 文档 · 已发布"],
            ["工单规则库", "ticket-policy@7", "94 文档 · 已发布"],
            ["财务制度库", "finance-policy@3", "318 文档 · 审核中"],
          ].map(([name, code, meta], index) => (
            <article className="skill-row" key={code}>
              <div className="skill-glyph">{index === 0 ? "◫" : "▤"}</div>
              <div>
                <b>{name}</b>
                <code>{code}</code>
                <small>{meta}</small>
              </div>
              <span className={index === 2 ? "tag" : "tag green"}>
                {index === 2 ? "REVIEW" : "READY"}
              </span>
            </article>
          ))}
        </section>
        <aside className="binding-panel">
          <p className="kicker">RETRIEVAL PROFILE</p>
          <h2>产品帮助中心</h2>
          <div className="priority-list">
            <div>
              <b>01</b>
              <span>
                切片策略<small>512 tokens / overlap 64</small>
              </span>
            </div>
            <div>
              <b>02</b>
              <span>
                向量模型<small>text-embedding-v3</small>
              </span>
            </div>
            <div className="highlight">
              <b>03</b>
              <span>
                召回与重排<small>topK 12 → rerank 5</small>
              </span>
            </div>
          </div>
          <section className="policy-note">
            <b>被 4 个智能体引用</b>
            <p>生产引用锁定到知识库版本与索引快照，可安全回溯。</p>
          </section>
          <Button>查看版本与索引</Button>
        </aside>
      </div>
    </>
  );
}

function WorkflowsPage() {
  return (
    <>
      <PageHeader
        kicker="WORKFLOW LIBRARY / REUSABLE"
        title="工作流"
        description="将稳定的流程编排沉淀为可复用组件，可作为智能体工具或发布流程中的受控步骤。"
        action={<Button>＋ 新建工作流</Button>}
      />
      <div className="content-split">
        <section className="catalog-panel">
          {[
            ["工单创建与确认", "ticket-create@v5", "12 节点 · 3 个 Agent 引用"],
            ["高风险操作审批", "human-approval@v3", "6 节点 · HITL Gate"],
            ["售后问题分流", "after-sales-router@v8", "9 节点 · 生产中"],
          ].map(([name, code, meta], index) => (
            <article className="skill-row" key={code}>
              <div className="skill-glyph">⌁</div>
              <div>
                <b>{name}</b>
                <code>{code}</code>
                <small>{meta}</small>
              </div>
              <span className={index === 1 ? "tag blue" : "tag green"}>
                {index === 1 ? "GATED" : "RUNNING"}
              </span>
            </article>
          ))}
        </section>
        <aside className="binding-panel">
          <p className="kicker">WORKFLOW SNAPSHOT</p>
          <h2>工单创建与确认</h2>
          <div className="memory-flow">
            <span>校验</span>
            <i>→</i>
            <span>创建</span>
            <i>→</i>
            <span>确认</span>
          </div>
          <section className="policy-note">
            <b>发布约束</b>
            <p>
              工作流作为全局资产独立版本化；Agent ReleaseSnapshot
              记录其精确引用。
            </p>
          </section>
          <Button>进入流程编排</Button>
        </aside>
      </div>
    </>
  );
}

function SystemPage() {
  return (
    <>
      <PageHeader
        kicker="SYSTEM GOVERNANCE / RBAC"
        title="账号与权限"
        description="管理租户、项目成员、角色与敏感配置访问，确保发布与运行操作均可审计。"
        action={<Button>＋ 邀请成员</Button>}
      />
      <div className="observe-summary">
        <article>
          <span>18</span>
          <small>ACTIVE MEMBERS</small>
        </article>
        <article>
          <span>4</span>
          <small>ROLES</small>
        </article>
        <article>
          <span>6</span>
          <small>SECRET SCOPES</small>
        </article>
        <article>
          <span>100%</span>
          <small>AUDIT COVERAGE</small>
        </article>
      </div>
      <section className="run-table">
        <div className="table-tools">
          <div className="search-mini">◎ 当前项目：ok-agent / prod-cn-sh</div>
          <button className="filter-chip">角色与权限⌄</button>
        </div>
        <div className="table-head">
          <span>角色</span>
          <span>适用范围</span>
          <span>组件管理</span>
          <span>Agent 配置</span>
          <span>发布</span>
          <span>观测</span>
        </div>
        {[
          ["平台管理员", "全局", "管理", "管理", "审批", "全部"],
          ["Agent 开发者", "项目", "引用", "管理", "申请", "项目"],
          ["发布负责人", "环境", "只读", "只读", "执行", "全部"],
          ["观察者", "项目", "只读", "只读", "—", "项目"],
        ].map((row) => (
          <div className="table-row" key={row[0]}>
            <b>{row[0]}</b>
            <span>{row[1]}</span>
            <span>{row[2]}</span>
            <span>{row[3]}</span>
            <span>{row[4]}</span>
            <span>{row[5]}</span>
          </div>
        ))}
      </section>
    </>
  );
}

function ReleasePage() {
  const [step, setStep] = useState(2);
  return (
    <>
      <PageHeader
        kicker="RELEASE SNAPSHOT / DEPLOYMENT"
        title="发布与环境"
        description="将所有资产引用解析、冻结并签名为 ReleaseSnapshot；运行态只读取快照而不读取 Draft。"
        action={<Button>创建发布</Button>}
      />
      <div className="release-workspace">
        <section className="release-timeline">
          {[
            ["01", "Draft revision", "18"],
            ["02", "Validation", "passed"],
            ["03", "Release snapshot", "8f1a09c"],
            ["04", "Deployment", "prod-cn-sh"],
          ].map((x, index) => (
            <button
              onClick={() => setStep(index)}
              className={index <= step ? "release-step done" : "release-step"}
              key={x[0]}
            >
              <b>{x[0]}</b>
              <span>
                {x[1]}
                <small>{x[2]}</small>
              </span>
              {index === step && <i>●</i>}
            </button>
          ))}
        </section>
        <section className="snapshot-panel">
          <div className="form-title">
            <div>
              <p className="kicker">SNAPSHOT / 8F1A09C</p>
              <h2>生产发布包</h2>
            </div>
            <span className="tag green">READY</span>
          </div>
          <div className="manifest-list">
            {[
              ["agentRevision", "customer-service@18"],
              ["modelPolicy", "qwen-production@3"],
              ["skillBindings", "product-faq@11, ticket-writing@3"],
              ["mcpBindings", "crm@7"],
              ["memoryPolicy", "user-profile@4"],
              ["environment", "prod-cn-sh@2"],
            ].map(([key, value]) => (
              <p key={key}>
                <b>{key}</b>
                <code>{value}</code>
                <span>✓ locked</span>
              </p>
            ))}
          </div>
          <div className="deploy-bar">
            <p>
              <span>DEPLOYMENT TRAFFIC</span>
              <b>100% / prod</b>
            </p>
            <div>
              <i />
              <i />
              <i />
              <i />
              <i />
              <i />
              <i />
              <i />
              <i />
              <i />
            </div>
          </div>
          <div className="sticky-actions">
            <Button quiet>查看差异</Button>
            <Button>部署到生产 →</Button>
          </div>
        </section>
      </div>
    </>
  );
}

export default function App() {
  const { t, i18n } = useTranslation();
  const [navCollapsed, setNavCollapsed] = useState(
    () => window.localStorage.getItem("ok-agent.nav-collapsed") === "true",
  );
  const agentConfigMatch = () =>
    window.location.pathname.match(/^\/agents\/([^/]+)\/config(?:\/([a-z]+))?$/);
  const [page, setPage] = useState<Page>(() =>
    pageForPath(window.location.pathname),
  );
  const [agentConfigId, setAgentConfigId] = useState<string | null>(() =>
    agentConfigMatch()?.[1] ?? null,
  );
  const [observeSessionId, setObserveSessionId] = useState<string | null>(() =>
    observeSessionIdFromPath(window.location.pathname),
  );
  useEffect(() => {
    if (!isKnownPath(window.location.pathname))
      window.history.replaceState({}, "", pagePaths.agents);
    const syncPage = () => {
      setPage(pageForPath(window.location.pathname));
      setAgentConfigId(agentConfigMatch()?.[1] ?? null);
      setObserveSessionId(observeSessionIdFromPath(window.location.pathname));
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
    setObserveSessionId(null);
  };
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
    knowledge: <KnowledgePage />,
    workflows: <WorkflowsPage />,
    release: <ReleasePage />,
    observe: (
      <ObservePage
        sessionId={observeSessionId}
        onOpenSession={openObserveSession}
        onBack={backToObserveList}
      />
    ),
    system: <SystemPage />,
    persona: <PersonaPage />,
    insight: <WipPlaceholder name="对话洞察" kicker="INSIGHT" />,
    sysconfig: <WipPlaceholder name="系统配置" kicker="SETTINGS" />,
    usermgmt: <UserManagementPage />,
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
            <span className="avatar">N</span>
          </div>
        </header>
        <section className="page-content" key={page}>
          {content}
        </section>
      </section>
    </main>
  );
}
