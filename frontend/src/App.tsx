import { useEffect, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";

type Page =
  | "agents"
  | "models"
  | "skills"
  | "mcp"
  | "knowledge"
  | "workflows"
  | "memory"
  | "workspace"
  | "teams"
  | "release"
  | "observe"
  | "system";

type NavigationGroup = {
  title: string;
  items: { id: Page; icon: string; name: string; kicker: string }[];
};

const navigationGroups: NavigationGroup[] = [
  {
    title: "组件管理",
    items: [
      { id: "models", icon: "◌", name: "模型管理", kicker: "MODEL REGISTRY" },
      { id: "skills", icon: "✦", name: "技能仓库", kicker: "SKILL MARKET" },
      { id: "mcp", icon: "⌘", name: "MCP 与工具", kicker: "TOOLS CONFIG" },
      { id: "knowledge", icon: "◫", name: "知识库", kicker: "KNOWLEDGE BASE" },
      {
        id: "workflows",
        icon: "⌁",
        name: "工作流",
        kicker: "WORKFLOW LIBRARY",
      },
    ],
  },
  {
    title: "智能体管理",
    items: [
      { id: "agents", icon: "◈", name: "智能体", kicker: "HARNESS AGENT" },
      {
        id: "memory",
        icon: "◫",
        name: "记忆与上下文",
        kicker: "MEMORY + CONTEXT",
      },
      { id: "workspace", icon: "▤", name: "工作空间", kicker: "WORKSPACE" },
      {
        id: "teams",
        icon: "⊹",
        name: "子 Agent 与协作",
        kicker: "COLLABORATION",
      },
    ],
  },
  {
    title: "发布与可观测",
    items: [
      {
        id: "release",
        icon: "↗",
        name: "发布与环境",
        kicker: "RELEASE SNAPSHOT",
      },
      { id: "observe", icon: "◌", name: "运行观测", kicker: "RUNTIME OBSERVE" },
    ],
  },
  {
    title: "系统管理",
    items: [
      {
        id: "system",
        icon: "◎",
        name: "账号与权限",
        kicker: "SYSTEM GOVERNANCE",
      },
    ],
  },
];

const modules = navigationGroups.flatMap((group) => group.items);
const pagePaths: Record<Page, string> = {
  agents: "/agents",
  models: "/models",
  skills: "/skills",
  mcp: "/mcp",
  knowledge: "/knowledge",
  workflows: "/workflows",
  memory: "/memory",
  workspace: "/workspace",
  teams: "/teams",
  release: "/releases",
  observe: "/observability",
  system: "/system",
};
const pathPages = Object.fromEntries(
  Object.entries(pagePaths).map(([page, path]) => [path, page]),
) as Record<string, Page>;

function Button({
  children,
  quiet = false,
  onClick,
  disabled = false,
}: {
  children: ReactNode;
  quiet?: boolean;
  onClick?: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={quiet ? "ui-button quiet" : "ui-button"}
    >
      {children}
    </button>
  );
}

function Toggle({
  on,
  setOn,
  label,
}: {
  on: boolean;
  setOn: (next: boolean) => void;
  label?: string;
}) {
  return (
    <button
      aria-label={label}
      onClick={() => setOn(!on)}
      className={`toggle ${on ? "on" : ""}`}
    >
      <i />
    </button>
  );
}

function Field({
  label,
  value,
  hint,
  wide = false,
}: {
  label: string;
  value: string;
  hint?: string;
  wide?: boolean;
}) {
  return (
    <label className={`field ${wide ? "wide" : ""}`}>
      <span>{label}</span>
      <input defaultValue={value} />
      {hint && <small>{hint}</small>}
    </label>
  );
}

function PageHeader({
  kicker,
  title,
  description,
  action,
}: {
  kicker: string;
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        <p className="kicker">{kicker}</p>
        <h1>{title}</h1>
        <p className="page-description">{description}</p>
      </div>
      {action}
    </header>
  );
}

function AgentPage({ go }: { go: (page: Page) => void }) {
  const [selected, setSelected] = useState("customer-service");
  const agents = [
    ["customer-service", "客户服务中枢", "PROD", "24"],
    ["knowledge-router", "知识路由器", "CANARY", "8"],
    ["finance-analyst", "财务分析师", "DRAFT", "—"],
  ];
  return (
    <>
      <PageHeader
        kicker="HARNESS AGENT / REGISTRY"
        title="智能体编排台"
        description="围绕 HarnessAgent 定义可发布的运行规格，并将配置固定为不可变快照。"
        action={<Button>＋ 创建智能体</Button>}
      />
      <div className="agent-workbench">
        <aside className="sub-rail">
          <div className="search-mini">⌕ 搜索智能体</div>
          <p>
            AGENT REGISTRY <b>03</b>
          </p>
          {agents.map(([id, name, state, runs]) => (
            <button
              key={id}
              onClick={() => setSelected(id)}
              className={
                selected === id ? "agent-select selected" : "agent-select"
              }
            >
              <span className="agent-icon">◈</span>
              <span>
                <b>{name}</b>
                <small>{id}</small>
              </span>
              <em className={state.toLowerCase()}>{state}</em>
            </button>
          ))}
          <button className="add-list">＋ 新建 Draft</button>
        </aside>
        <section className="agent-detail">
          <div className="detail-title">
            <div className="large-agent-icon">◈</div>
            <div>
              <p className="kicker">AGENT / {selected.toUpperCase()}</p>
              <h2>
                客户服务中枢 <span className="tag blue">PROD</span>
              </h2>
              <small>customer-service · revision 18 · snapshot 8f1a09c</small>
            </div>
            <div className="detail-actions">
              <Button quiet>◷ 历史版本</Button>
              <Button>发布变更 →</Button>
            </div>
          </div>
          <div className="agent-tabs">
            <button className="tab active">概览</button>
            <button className="tab" onClick={() => go("workspace")}>
              工作空间
            </button>
            <button className="tab" onClick={() => go("mcp")}>
              工具
            </button>
            <button className="tab" onClick={() => go("memory")}>
              记忆
            </button>
            <button className="tab" onClick={() => go("observe")}>
              运行记录
            </button>
          </div>
          <div className="agent-overview">
            <article className="harness-card blueprint">
              <p className="kicker">HARNESS SPEC</p>
              <div className="spec-flow">
                <span>Prompt</span>
                <i>→</i>
                <span>Model</span>
                <i>→</i>
                <span>Tools</span>
                <i>→</i>
                <span>Memory</span>
              </div>
              <div className="spec-lines">
                <p>
                  <b>modelPolicy</b> <code>qwen-production@v3</code>
                </p>
                <p>
                  <b>toolPolicy</b> <code>safe-crm@v2</code>
                </p>
                <p>
                  <b>memoryPolicy</b> <code>user-profile@v4</code>
                </p>
                <p>
                  <b>environment</b> <code>prod-cn-sh@v2</code>
                </p>
              </div>
              <button onClick={() => go("release")} className="link-button">
                查看 ReleaseSnapshot →
              </button>
            </article>
            <article className="harness-card">
              <p className="kicker">RUNTIME HEALTH</p>
              <div className="health-score">
                99.98<small>%</small>
              </div>
              <div className="health-bars">
                <span>
                  <i style={{ width: "96%" }} />
                  模型
                </span>
                <span>
                  <i style={{ width: "100%" }} />
                  工具
                </span>
                <span>
                  <i style={{ width: "83%" }} />
                  上下文
                </span>
              </div>
              <small>当前 24 个活跃会话 · P95 1.84s</small>
            </article>
            <article className="harness-card activity">
              <p className="kicker">LATEST CHANGE</p>
              <b>memory-policy@v4</b>
              <p>更新每日记忆整合频率，从 30 分钟调整为 2 小时。</p>
              <small>由 NEASON · 14:32:06</small>
              <button className="link-button">查看差异 →</button>
            </article>
          </div>
        </section>
      </div>
    </>
  );
}

type ModelItem = {
  id: string;
  name: string;
  type: "LLM" | "SPEECH" | "VISION" | "OCR" | "AUDIO_VIDEO";
  provider: string;
  modelId: string;
  endpoint: string;
  apiKey: string;
  apiKeyConfigured?: boolean;
  enabled: boolean;
  updated: string;
};
type ModelApiItem = Omit<ModelItem, "updated" | "apiKey"> & {
  updatedAt: string;
};
const llmProviders = [
  ["OpenAI", "gpt-4.1", "https://api.openai.com/v1"],
  ["Anthropic", "claude-sonnet-4-20250514", "https://api.anthropic.com/v1"],
  [
    "Google Gemini",
    "gemini-2.5-pro",
    "https://generativelanguage.googleapis.com/v1beta",
  ],
  [
    "阿里云百炼（Qwen）",
    "qwen-plus",
    "https://dashscope.aliyuncs.com/compatible-mode/v1",
  ],
  ["DeepSeek", "deepseek-chat", "https://api.deepseek.com/v1"],
  ["月之暗面（Kimi）", "moonshot-v1-8k", "https://api.moonshot.cn/v1"],
  ["智谱 AI（GLM）", "glm-4-plus", "https://open.bigmodel.cn/api/paas/v4"],
  ["MiniMax", "MiniMax-Text-01", "https://api.minimaxi.com/v1"],
  [
    "字节火山引擎",
    "doubao-1-5-pro-32k-250115",
    "https://ark.cn-beijing.volces.com/api/v3",
  ],
  ["Mistral AI", "mistral-large-latest", "https://api.mistral.ai/v1"],
  ["xAI（Grok）", "grok-3", "https://api.x.ai/v1"],
  ["Ollama（本地）", "llama3.3", "http://127.0.0.1:11434/v1"],
] as const;
const modelSeed: ModelItem[] = [
  {
    id: "qwen-prod",
    name: "Qwen Production",
    type: "LLM",
    provider: "DashScope",
    modelId: "qwen-plus",
    endpoint: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    apiKey: "",
    enabled: true,
    updated: "2 min ago",
  },
  {
    id: "whisper",
    name: "Whisper Transcription",
    type: "SPEECH",
    provider: "OpenAI",
    modelId: "whisper-1",
    endpoint: "https://api.openai.com/v1",
    apiKey: "",
    enabled: true,
    updated: "18 min ago",
  },
  {
    id: "invoice-ocr",
    name: "Invoice OCR",
    type: "OCR",
    provider: "Alibaba Cloud",
    modelId: "ocr-invoice",
    endpoint: "https://ocr-api.internal/v1",
    apiKey: "",
    enabled: false,
    updated: "yesterday",
  },
  {
    id: "video",
    name: "Video Understanding",
    type: "AUDIO_VIDEO",
    provider: "Qwen",
    modelId: "qwen-vl-max",
    endpoint: "https://dashscope.aliyuncs.com/api/v1",
    apiKey: "",
    enabled: true,
    updated: "3 days ago",
  },
];

function ModelsPage() {
  const { t } = useTranslation();
  const [models, setModels] = useState(modelSeed);
  const [type, setType] = useState<"ALL" | ModelItem["type"]>("ALL");
  const [editing, setEditing] = useState<ModelItem | null>(null);
  const [testResult, setTestResult] = useState<{
    state: "testing" | "success" | "error";
    message: string;
  } | null>(null);
  useEffect(() => {
    fetch("/api/v1/models")
      .then((response) =>
        response.ok
          ? (response.json() as Promise<ModelApiItem[]>)
          : Promise.reject(),
      )
      .then((data) =>
        setModels(
          data.map((item) => ({
            ...item,
            apiKey: "",
            updated: new Date(item.updatedAt).toLocaleString(),
          })),
        ),
      )
      .catch(() => undefined);
  }, []);
  const visible = models.filter(
    (model) => type === "ALL" || model.type === type,
  );
  const save = async () => {
    if (!editing || testResult?.state === "testing") return;
    const existing = Boolean(editing.id);
    const response = await fetch(
      existing ? `/api/v1/models/${editing.id}` : "/api/v1/models",
      {
        method: existing ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(editing),
      },
    );
    if (!response.ok) {
      setTestResult({
        state: "error",
        message: t("models.saveFailed"),
      });
      return;
    }
    const saved = await response.json();
    setModels((current) =>
      existing
        ? current.map((x) =>
            x.id === saved.id ? { ...saved, updated: "now" } : x,
          )
        : [{ ...saved, updated: "now" }, ...current],
    );
    setEditing(null);
  };
  const applyLlmProvider = (provider: string) => {
    const preset = llmProviders.find(([name]) => name === provider);
    if (editing && preset) {
      setEditing({
        ...editing,
        provider: preset[0],
        modelId: preset[1],
        endpoint: preset[2],
      });
    }
  };
  const testConnection = async () => {
    if (!editing) return;
    setTestResult({ state: "testing", message: t("models.connectionTesting") });
    try {
      const useSavedCredential = Boolean(editing.id && !editing.apiKey.trim());
      const response = await fetch(
        useSavedCredential
          ? `/api/v1/models/${editing.id}/test-connection`
          : "/api/v1/models/test-connection",
        {
          method: "POST",
          ...(useSavedCredential
            ? {}
            : {
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(editing),
              }),
        },
      );
      const responseText = await response.text();
      let result: {
        success?: boolean;
        statusCode?: number;
        message?: string;
        detail?: string;
        title?: string;
      } = {};
      try {
        result = responseText ? JSON.parse(responseText) : {};
      } catch {
        result = { message: responseText };
      }
      const success = response.ok && result.success === true;
      const message =
        result.message ||
        result.detail ||
        result.title ||
        `请求失败（HTTP ${response.status}）`;
      const statusCode = result.statusCode || response.status;
      const statusSuffix =
        statusCode && !message.includes(`HTTP ${statusCode}`)
          ? `（HTTP ${statusCode}）`
          : "";
      setTestResult(
        success
          ? { state: "success", message: t("models.connectionSucceeded") }
          : {
              state: "error",
              message: `${message || t("models.connectionFailed")}${statusSuffix}`,
            },
      );
    } catch {
      setTestResult({
        state: "error",
        message: t("models.connectionFailed"),
      });
    }
  };
  return (
    <>
      <PageHeader
        kicker="MODEL ASSETS / REGISTRY"
        title="模型管理"
        description="统一管理文本、语音、视觉、OCR 和音视频模型。Agent 仅引用已启用的模型资产与其版本。"
        action={
          <Button
            onClick={() => {
              setTestResult(null);
              setEditing({
                id: "",
                name: "",
                type: "LLM",
                provider: "OpenAI",
                modelId: "",
                endpoint: "",
                apiKey: "",
                enabled: true,
                updated: "now",
              });
            }}
          >
            ＋ 新增模型
          </Button>
        }
      />
      <section className="run-table">
        <div className="table-tools">
          <div className="search-mini">◌ 共 {models.length} 个模型</div>
          <label className="model-type-filter">
            类型
            <select
              value={type}
              onChange={(event) => setType(event.target.value as typeof type)}
            >
              <option value="ALL">全部类型</option>
              <option value="LLM">大语言模型</option>
              <option value="SPEECH">语音模型</option>
              <option value="VISION">视觉模型</option>
              <option value="OCR">OCR 模型</option>
              <option value="AUDIO_VIDEO">音视频模型</option>
            </select>
          </label>
        </div>
        <div className="table-head model-table-row">
          <span>模型名称</span>
          <span>类型</span>
          <span>提供商</span>
          <span>模型 ID</span>
          <span>密钥引用</span>
          <span>启用状态</span>
          <span>操作</span>
        </div>
        {visible.map((model) => (
          <div className="table-row model-table-row" key={model.id}>
            <span>
              <b>{model.name}</b>
              <small>{model.endpoint}</small>
            </span>
            <span>{model.type.replace("_", " / ")}</span>
            <span>{model.provider}</span>
            <code>{model.modelId}</code>
            <code>{model.apiKeyConfigured ? "已配置" : "未配置"}</code>
            <Toggle
              on={model.enabled}
              setOn={(next) =>
                setModels((current) =>
                  current.map((x) =>
                    x.id === model.id ? { ...x, enabled: next } : x,
                  ),
                )
              }
              label={`Enable ${model.name}`}
            />
            <span className="model-actions">
              <button
                className="link-button"
                onClick={() => {
                  setTestResult(null);
                  setEditing(model);
                }}
              >
                编辑
              </button>
              <button
                className="link-button"
                onClick={() =>
                  setModels((current) =>
                    current.filter((x) => x.id !== model.id),
                  )
                }
              >
                删除
              </button>
            </span>
          </div>
        ))}
      </section>
      {editing &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setEditing(null)}
          >
            <div
              className="form-surface model-editor"
              role="dialog"
              aria-modal="true"
              aria-label="模型配置"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">
                    {editing.id ? "编辑模型" : "新增模型"}
                  </p>
                  <h2>{editing.id ? editing.name : "新增模型"}</h2>
                </div>
                <button
                  className="link-button"
                  onClick={() => setEditing(null)}
                >
                  关闭 ×
                </button>
              </div>
              <div className="provider-pills">
                {(
                  ["LLM", "SPEECH", "VISION", "OCR", "AUDIO_VIDEO"] as const
                ).map((x) => (
                  <button
                    key={x}
                    onClick={() => setEditing({ ...editing, type: x })}
                    className={
                      editing.type === x ? "provider active" : "provider"
                    }
                  >
                    {x.replace("_", " / ")}
                  </button>
                ))}
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>名称</span>
                  <input
                    value={editing.name}
                    onChange={(e) =>
                      setEditing({ ...editing, name: e.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>模型提供商</span>
                  {editing.type === "LLM" ? (
                    <>
                      <select
                        value={
                          llmProviders.some(
                            ([name]) => name === editing.provider,
                          )
                            ? editing.provider
                            : "CUSTOM"
                        }
                        onChange={(event) =>
                          event.target.value === "CUSTOM"
                            ? setEditing({ ...editing, provider: "" })
                            : applyLlmProvider(event.target.value)
                        }
                      >
                        {llmProviders.map(([name]) => (
                          <option key={name} value={name}>
                            {name}
                          </option>
                        ))}
                        <option value="CUSTOM">自定义</option>
                      </select>
                      {!llmProviders.some(
                        ([name]) => name === editing.provider,
                      ) && (
                        <input
                          placeholder="请输入自定义模型厂商"
                          value={editing.provider}
                          onChange={(event) =>
                            setEditing({
                              ...editing,
                              provider: event.target.value,
                            })
                          }
                        />
                      )}
                    </>
                  ) : (
                    <input
                      value={editing.provider}
                      onChange={(e) =>
                        setEditing({ ...editing, provider: e.target.value })
                      }
                    />
                  )}
                </label>
                <label className="field">
                  <span>模型（MODEL_ID）</span>
                  <input
                    value={editing.modelId}
                    onChange={(e) =>
                      setEditing({ ...editing, modelId: e.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>API 密钥（API_KEY）</span>
                  <input
                    type="password"
                    autoComplete="new-password"
                    value={editing.apiKey}
                    placeholder={
                      editing.apiKeyConfigured
                        ? t("models.apiKeyConfigured")
                        : undefined
                    }
                    onChange={(e) =>
                      setEditing({ ...editing, apiKey: e.target.value })
                    }
                  />
                </label>
                <label className="field wide">
                  <span>服务地址（BASE_URL）</span>
                  <input
                    value={editing.endpoint}
                    onChange={(e) =>
                      setEditing({ ...editing, endpoint: e.target.value })
                    }
                  />
                </label>
              </div>
              {testResult && (
                <div
                  className={`connection-result connection-result--${testResult.state}`}
                  role="status"
                  aria-live="polite"
                >
                  <span className="connection-result__icon" aria-hidden="true">
                    {testResult.state === "success"
                      ? "✓"
                      : testResult.state === "error"
                        ? "×"
                        : "···"}
                  </span>
                  <div>
                    <b>{testResult.message}</b>
                    <p>{t("models.connectionHint")}</p>
                  </div>
                </div>
              )}
              <div className="sticky-actions">
                <Button
                  quiet
                  onClick={testConnection}
                  disabled={testResult?.state === "testing"}
                >
                  {testResult?.state === "testing"
                    ? t("models.connectionTesting")
                    : testResult?.state === "error"
                      ? t("models.connectionRetry")
                      : t("models.connectionTest")}
                </Button>
                <Button onClick={save}>保存模型</Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}

type SkillItem = {
  id: string;
  skillKey: string;
  name: string;
  description: string;
  assetVersion: string;
  sourceType: "MANUAL" | "FILE_IMPORT" | "GIT";
  sourceUri: string;
  entryFile: string;
  content: string;
  enabled: boolean;
  updatedAt?: string;
};

const emptySkill: SkillItem = {
  id: "",
  skillKey: "",
  name: "",
  description: "",
  assetVersion: "v1",
  sourceType: "MANUAL",
  sourceUri: "",
  entryFile: "SKILL.md",
  content: "",
  enabled: true,
};

function SkillsPage() {
  const { t } = useTranslation();
  const [skills, setSkills] = useState<SkillItem[]>([]);
  const [editing, setEditing] = useState<SkillItem | null>(null);
  const [query, setQuery] = useState("");
  const [source, setSource] = useState<"ALL" | SkillItem["sourceType"]>("ALL");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetch("/api/v1/skills")
      .then((response) =>
        response.ok
          ? (response.json() as Promise<SkillItem[]>)
          : Promise.reject(),
      )
      .then(setSkills)
      .catch(() => setError(t("skills.loadFailed")));
  }, [t]);

  const visibleSkills = skills.filter(
    (skill) =>
      (source === "ALL" || skill.sourceType === source) &&
      `${skill.name} ${skill.skillKey} ${skill.description}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );

  const saveSkill = async () => {
    if (!editing || saving) return;
    setSaving(true);
    setError("");
    try {
      const response = await fetch(
        editing.id ? `/api/v1/skills/${editing.id}` : "/api/v1/skills",
        {
          method: editing.id ? "PUT" : "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(editing),
        },
      );
      if (!response.ok) throw new Error();
      const saved = (await response.json()) as SkillItem;
      setSkills((current) =>
        editing.id
          ? current.map((skill) => (skill.id === saved.id ? saved : skill))
          : [saved, ...current],
      );
      setEditing(null);
    } catch {
      setError(t("skills.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  const setSkillEnabled = async (skill: SkillItem, enabled: boolean) => {
    const response = await fetch(
      `/api/v1/skills/${skill.id}/enabled?value=${enabled}`,
      { method: "PATCH" },
    );
    if (!response.ok) {
      setError(t("skills.statusFailed"));
      return;
    }
    const saved = (await response.json()) as SkillItem;
    setSkills((current) =>
      current.map((item) => (item.id === saved.id ? saved : item)),
    );
  };

  const deleteSkill = async (skill: SkillItem) => {
    if (!window.confirm(t("skills.deleteConfirm", { name: skill.name })))
      return;
    const response = await fetch(`/api/v1/skills/${skill.id}`, {
      method: "DELETE",
    });
    if (!response.ok) {
      setError(t("skills.deleteFailed"));
      return;
    }
    setSkills((current) => current.filter((item) => item.id !== skill.id));
  };

  const importFile = async (file: File) => {
    const content = await file.text();
    const fallbackKey = file.name
      .replace(/\.md$/i, "")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-|-$/g, "");
    setEditing((current) =>
      current
        ? {
            ...current,
            sourceType: "FILE_IMPORT",
            entryFile: file.name,
            content,
            skillKey: current.skillKey || fallbackKey,
            name: current.name || fallbackKey,
          }
        : current,
    );
  };

  return (
    <>
      <PageHeader
        kicker="SKILL ASSETS / REPOSITORY"
        title={t("skills.title")}
        description={t("skills.description")}
        action={
          <Button
            onClick={() => {
              setError("");
              setEditing({ ...emptySkill });
            }}
          >
            ＋ {t("skills.create")}
          </Button>
        }
      />
      {error && <div className="skill-error">× {error}</div>}
      <section className="run-table skill-registry">
        <div className="table-tools">
          <label className="search-mini skill-search">
            ⌕
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t("skills.search")}
            />
          </label>
          <label className="model-type-filter">
            {t("skills.source")}
            <select
              value={source}
              onChange={(event) =>
                setSource(event.target.value as typeof source)
              }
            >
              <option value="ALL">{t("skills.allSources")}</option>
              <option value="MANUAL">{t("skills.manual")}</option>
              <option value="FILE_IMPORT">{t("skills.fileImport")}</option>
              <option value="GIT">Git</option>
            </select>
          </label>
        </div>
        <div className="table-head skill-table-row">
          <span>{t("skills.skill")}</span>
          <span>{t("skills.version")}</span>
          <span>{t("skills.source")}</span>
          <span>{t("skills.entryFile")}</span>
          <span>{t("skills.updated")}</span>
          <span>{t("skills.status")}</span>
          <span>{t("skills.actions")}</span>
        </div>
        {visibleSkills.length === 0 ? (
          <div className="skill-empty">
            <span>✦</span>
            <b>{t("skills.emptyTitle")}</b>
            <p>{t("skills.emptyDescription")}</p>
          </div>
        ) : (
          visibleSkills.map((skill) => (
            <div className="table-row skill-table-row" key={skill.id}>
              <span className="skill-identity">
                <i>✦</i>
                <span>
                  <b>{skill.name}</b>
                  <code>{skill.skillKey}</code>
                  <small>{skill.description}</small>
                </span>
              </span>
              <code>{skill.assetVersion}</code>
              <span
                className={`skill-source ${skill.sourceType.toLowerCase()}`}
              >
                {skill.sourceType.replace("_", " ")}
              </span>
              <code>{skill.entryFile}</code>
              <span>
                {skill.updatedAt
                  ? new Date(skill.updatedAt).toLocaleString()
                  : "—"}
              </span>
              <Toggle
                on={skill.enabled}
                setOn={(next) => setSkillEnabled(skill, next)}
                label={`${t("skills.status")} ${skill.name}`}
              />
              <span className="model-actions">
                <button
                  className="link-button"
                  onClick={() => {
                    setError("");
                    setEditing(skill);
                  }}
                >
                  {t("skills.edit")}
                </button>
                <button
                  className="link-button danger-link"
                  onClick={() => deleteSkill(skill)}
                >
                  {t("skills.delete")}
                </button>
              </span>
            </div>
          ))
        )}
      </section>
      {editing &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setEditing(null)}
          >
            <div
              className="form-surface model-editor skill-editor"
              role="dialog"
              aria-modal="true"
              aria-label={t("skills.editor")}
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">
                    {editing.id ? "EDIT SKILL ASSET" : "IMPORT SKILL ASSET"}
                  </p>
                  <h2>{editing.id ? editing.name : t("skills.create")}</h2>
                </div>
                <button
                  className="link-button"
                  onClick={() => setEditing(null)}
                >
                  {t("skills.close")} ×
                </button>
              </div>
              <div className="skill-import-strip">
                <div>
                  <b>SKILL.md</b>
                  <small>{t("skills.importHint")}</small>
                </div>
                <label className="ui-button quiet file-button">
                  {t("skills.selectFile")}
                  <input
                    type="file"
                    accept=".md,text/markdown,text/plain"
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      if (file) void importFile(file);
                    }}
                  />
                </label>
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>{t("skills.key")}</span>
                  <input
                    value={editing.skillKey}
                    onChange={(event) =>
                      setEditing({ ...editing, skillKey: event.target.value })
                    }
                    placeholder="customer-support"
                  />
                </label>
                <label className="field">
                  <span>{t("skills.name")}</span>
                  <input
                    value={editing.name}
                    onChange={(event) =>
                      setEditing({ ...editing, name: event.target.value })
                    }
                  />
                </label>
                <label className="field wide">
                  <span>{t("skills.skillDescription")}</span>
                  <input
                    value={editing.description}
                    onChange={(event) =>
                      setEditing({
                        ...editing,
                        description: event.target.value,
                      })
                    }
                  />
                </label>
                <label className="field">
                  <span>{t("skills.version")}</span>
                  <input
                    value={editing.assetVersion}
                    onChange={(event) =>
                      setEditing({
                        ...editing,
                        assetVersion: event.target.value,
                      })
                    }
                  />
                </label>
                <label className="field">
                  <span>{t("skills.source")}</span>
                  <select
                    value={editing.sourceType}
                    onChange={(event) =>
                      setEditing({
                        ...editing,
                        sourceType: event.target
                          .value as SkillItem["sourceType"],
                      })
                    }
                  >
                    <option value="MANUAL">{t("skills.manual")}</option>
                    <option value="FILE_IMPORT">
                      {t("skills.fileImport")}
                    </option>
                    <option value="GIT">Git</option>
                  </select>
                </label>
                <label className="field">
                  <span>{t("skills.entryFile")}</span>
                  <input
                    value={editing.entryFile}
                    onChange={(event) =>
                      setEditing({ ...editing, entryFile: event.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>{t("skills.sourceUri")}</span>
                  <input
                    value={editing.sourceUri ?? ""}
                    onChange={(event) =>
                      setEditing({ ...editing, sourceUri: event.target.value })
                    }
                    placeholder="https://github.com/org/repo"
                  />
                </label>
                <label className="field wide skill-content-field">
                  <span>{t("skills.content")}</span>
                  <textarea
                    value={editing.content}
                    onChange={(event) =>
                      setEditing({ ...editing, content: event.target.value })
                    }
                    placeholder="# Skill instructions..."
                  />
                </label>
              </div>
              {error && (
                <div className="skill-error modal-error">× {error}</div>
              )}
              <div className="sticky-actions">
                <Button quiet onClick={() => setEditing(null)}>
                  {t("skills.cancel")}
                </Button>
                <Button onClick={saveSkill} disabled={saving}>
                  {saving ? t("skills.saving") : t("skills.save")}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}

function McpPage() {
  const [server, setServer] = useState("crm");
  return (
    <>
      <PageHeader
        kicker="TOOLS CONFIG / MCP"
        title="MCP 与工具策略"
        description="受管 MCP 以 SecretRef 连接，并使用 allow/deny 对可执行工具面进行最小化控制。"
        action={<Button>＋ 注册 MCP</Button>}
      />
      <div className="config-layout">
        <aside className="sub-rail">
          <p>
            MCP SERVERS <b>03</b>
          </p>
          {[
            ["crm", "CRM 服务", "HTTP"],
            ["knowledge", "知识检索", "SSE"],
            ["internal-db", "内网数据工具", "STDIO"],
          ].map(([id, name, type]) => (
            <button
              onClick={() => setServer(id)}
              className={
                server === id ? "agent-select selected" : "agent-select"
              }
              key={id}
            >
              <span className="agent-icon">⌘</span>
              <span>
                <b>{name}</b>
                <small>{type} · healthy</small>
              </span>
              <i className="ok-dot" />
            </button>
          ))}
        </aside>
        <section className="form-surface">
          <div className="form-title">
            <div>
              <p className="kicker">MCP BINDING / {server.toUpperCase()}</p>
              <h2>{server === "crm" ? "CRM 服务" : "知识检索服务"}</h2>
            </div>
            <span className="tag green">HEALTHY</span>
          </div>
          <div className="field-grid">
            <Field
              label="传输协议"
              value={server === "crm" ? "streamable-http" : "sse"}
            />
            <Field label="SecretRef" value="secrets/prod/crm-api" />
            <Field
              label="服务地址"
              value={
                server === "crm"
                  ? "https://mcp.ok-agent.internal/crm"
                  : "https://mcp.ok-agent.internal/search"
              }
              wide
            />
            <Field label="调用超时" value="PT15S" />
            <Field label="初始化超时" value="PT8S" />
          </div>
          <section className="tool-policy">
            <div className="section-label">
              <b>允许的工具</b>
              <small>enableTools · 未列出的 server tools 不会注册</small>
            </div>
            {[
              "search_customer",
              "get_ticket",
              "create_ticket",
              "update_ticket",
            ].map((tool, index) => (
              <label key={tool} className="tool-check">
                <input defaultChecked={index < 3} type="checkbox" />
                <span>{tool}</span>
                <small>{index === 2 ? "ask · requires HITL" : "allow"}</small>
              </label>
            ))}
          </section>
          <div className="sticky-actions">
            <Button quiet>测试连接</Button>
            <Button>保存 MCP 绑定</Button>
          </div>
        </section>
      </div>
    </>
  );
}

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

function MemoryPage() {
  const [tab, setTab] = useState<"memory" | "context">("memory");
  return (
    <>
      <PageHeader
        kicker="MEMORY + CONTEXT POLICY"
        title="记忆与上下文"
        description="区分跨会话长期记忆和会话内上下文压缩，分别控制成本、保留与可恢复性。"
      />
      <div className="tab-switch">
        <button
          onClick={() => setTab("memory")}
          className={tab === "memory" ? "active" : ""}
        >
          长期记忆 / MemoryConfig
        </button>
        <button
          onClick={() => setTab("context")}
          className={tab === "context" ? "active" : ""}
        >
          上下文压缩 / CompactionConfig
        </button>
      </div>
      {tab === "memory" ? (
        <section className="policy-canvas">
          <article className="pipeline-card">
            <p className="kicker">LONG-TERM MEMORY PIPELINE</p>
            <div className="memory-flow">
              <span>对话结束</span>
              <i>→</i>
              <span>Flush</span>
              <i>→</i>
              <span>Daily Ledger</span>
              <i>→</i>
              <span>Consolidation</span>
              <i>→</i>
              <span>MEMORY.md</span>
            </div>
            <small>
              MEMORY.md 会在每个 reasoning step
              注入系统提示词；每日日志仅作为可审计原始事实。
            </small>
          </article>
          <div className="form-columns">
            <section className="form-surface compact">
              <div className="section-label">
                <b>记忆抽取</b>
                <small>MemoryFlushMiddleware</small>
              </div>
              <Field label="辅助模型" value="qwen-turbo" />
              <Field label="Flush Trigger" value="THROTTLED / PT10M" />
              <Field
                label="抽取提示词"
                value="提取长期有效的用户偏好、任务事实和明确决策。"
                wide
              />
              <div className="switch-line">
                <div>
                  <b>每次调用后抽取</b>
                  <small>关闭后仍会在压缩前和溢出恢复时执行</small>
                </div>
                <Toggle on={false} setOn={() => {}} />
              </div>
            </section>
            <section className="form-surface compact">
              <div className="section-label">
                <b>整合与保留</b>
                <small>MemoryMaintenanceMiddleware</small>
              </div>
              <Field label="MEMORY.md token 上限" value="4000" />
              <Field label="最小整合间隔" value="PT2H" />
              <Field label="每日账本保留" value="90 days" />
              <Field label="会话日志保留" value="180 days" />
            </section>
          </div>
        </section>
      ) : (
        <section className="policy-canvas">
          <article className="pipeline-card blue-tone">
            <p className="kicker">CONVERSATION COMPACTION</p>
            <div className="memory-flow">
              <span>Context Window</span>
              <i>→</i>
              <span>Flush</span>
              <i>→</i>
              <span>Offload</span>
              <i>→</i>
              <span>Summary</span>
              <i>→</i>
              <span>Recent Tail</span>
            </div>
            <small>
              默认动态依据模型 context window 触发，并保留按 token
              计算的最近消息尾部。
            </small>
          </article>
          <div className="form-columns">
            <section className="form-surface compact">
              <Field label="压缩触发 token" value="dynamic (window - 20k)" />
              <Field label="最近上下文保留" value="dynamic / 25% usable" />
              <Field label="摘要模型" value="qwen-turbo" />
            </section>
            <section className="form-surface compact">
              <Field label="工具结果卸载阈值" value="80,000 chars" />
              <Field label="参数截断" value="2,000 chars" />
              <div className="switch-line">
                <div>
                  <b>压缩前写入原始消息</b>
                  <small>保留 JSONL 用于运行审计与 session_search</small>
                </div>
                <Toggle on={true} setOn={() => {}} />
              </div>
            </section>
          </div>
        </section>
      )}
    </>
  );
}

function WorkspacePage() {
  return (
    <>
      <PageHeader
        kicker="WORKSPACE / FILESYSTEM"
        title="工作空间与执行环境"
        description="将 Workspace Context、文件系统隔离和 Sandbox 能力绑定到部署环境，而非暴露给普通编辑者。"
        action={<Button>编辑环境模板</Button>}
      />
      <div className="workspace-grid">
        <section className="file-tree panel-lite">
          <div className="tree-title">
            <b>workspace / customer-service</b>
            <span className="tag blue">REMOTE FS</span>
          </div>
          {[
            "AGENTS.md",
            "MEMORY.md",
            "tools.json",
            "knowledge/",
            "skills/",
            "subagents/",
            "plans/",
            "agents/customer-service/sessions/",
          ].map((x, index) => (
            <p key={x} className={index < 3 ? "file" : "folder"}>
              <span>{index < 3 ? "□" : "⌁"}</span>
              {x}
              {index === 1 && <em>injected</em>}
            </p>
          ))}
        </section>
        <section className="workspace-config">
          <article className="form-surface">
            <div className="section-label">
              <b>运行时投影</b>
              <small>WorkspaceContextMiddleware</small>
            </div>
            <Field
              label="Workspace Template"
              value="workspace-template/customer-service@v5"
              wide
            />
            <Field
              label="额外上下文文件"
              value="knowledge/product.md, knowledge/ticket-rules.md"
              wide
            />
            <Field label="最大上下文预算" value="8,000 tokens" />
            <div className="switch-line">
              <div>
                <b>@ 路径展开</b>
                <small>将 @file 指向的 workspace 内容安全地载入上下文</small>
              </div>
              <Toggle on={true} setOn={() => {}} />
            </div>
          </article>
          <article className="form-surface">
            <div className="section-label">
              <b>隔离与 Sandbox</b>
              <small>SandboxFilesystemSpec</small>
            </div>
            <div className="environment-card">
              <span>ISOLATION SCOPE</span>
              <b>USER</b>
              <small>
                同一用户跨 Session 共享受控文件视图；生产环境禁止
                LocalFilesystemSpec。
              </small>
            </div>
            <div className="switch-line">
              <div>
                <b>工作空间投影</b>
                <small>
                  启动 sandbox 时投影 AGENTS.md、skills、knowledge 等根目录
                </small>
              </div>
              <Toggle on={true} setOn={() => {}} />
            </div>
          </article>
        </section>
      </div>
    </>
  );
}

function TeamsPage() {
  const [plan, setPlan] = useState(false);
  return (
    <>
      <PageHeader
        kicker="COLLABORATION / PLAN MODE"
        title="子 Agent 与协作"
        description="声明受限角色、后台任务和计划模式；复杂协作在发布前完成权限与最大深度校验。"
        action={<Button>＋ 添加子 Agent</Button>}
      />
      <div className="team-canvas">
        <section className="team-map">
          <div className="parent-node">
            <i>◈</i>
            <b>客户服务中枢</b>
            <small>lead / qwen-plus</small>
          </div>
          <div className="branch one" />
          <div className="branch two" />
          <div className="child-node node-one">
            <i>◫</i>
            <b>知识检索</b>
            <small>sync · read-only tools</small>
          </div>
          <div className="child-node node-two">
            <i>⌘</i>
            <b>工单执行</b>
            <small>background · HITL</small>
          </div>
          <p className="kicker map-label">SUBAGENT DECLARATIONS</p>
        </section>
        <section className="form-surface">
          <div className="section-label">
            <b>协作运行策略</b>
            <small>SubagentsMiddleware / MessageBus</small>
          </div>
          <Field label="最大委派深度" value="2" />
          <Field label="后台任务超时" value="PT5M" />
          <Field
            label="Task Repository"
            value="distributed/task-store@v1"
            wide
          />
          <div className="switch-line">
            <div>
              <b>动态子 Agent</b>
              <small>
                读取 workspace/subagents 下的声明，并在每个 call 前重载
              </small>
            </div>
            <Toggle on={true} setOn={() => {}} />
          </div>
          <div className="switch-line">
            <div>
              <b>Plan Mode</b>
              <small>
                为会话注册 plan_enter / plan_write / plan_exit；默认严格只读
              </small>
            </div>
            <Toggle on={plan} setOn={setPlan} />
          </div>
          {plan && (
            <div className="warning-note">
              Plan Mode 当前启用。Shell
              仍保持拒绝；退出计划模式后才允许修改或执行敏感工具。
            </div>
          )}
        </section>
      </div>
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

function ObservePage() {
  const [query, setQuery] = useState("");
  const runs = [
    ["run_839f", "customer-service", "completed", "1.42s", "3,821"],
    ["run_827a", "customer-service", "waiting_hitl", "—", "1,224"],
    ["run_812d", "knowledge-router", "completed", "0.89s", "1,096"],
    ["run_7fe1", "finance-analyst", "failed", "4.33s", "6,421"],
  ];
  return (
    <>
      <PageHeader
        kicker="RUNTIME OBSERVE / TRACE"
        title="运行观测"
        description="以 tenant、snapshot、session、run 和 trace 为关联键，回放完整的模型、工具与权限决策链路。"
        action={<Button>导出 Trace</Button>}
      />
      <div className="observe-summary">
        <article>
          <span>24</span>
          <small>ACTIVE RUNS</small>
        </article>
        <article>
          <span>99.2%</span>
          <small>TOOL SUCCESS</small>
        </article>
        <article>
          <span>1.84s</span>
          <small>P95 LATENCY</small>
        </article>
        <article>
          <span>¥18.67</span>
          <small>TODAY COST</small>
        </article>
      </div>
      <section className="run-table">
        <div className="table-tools">
          <div className="search-mini">
            ⌕{" "}
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="搜索 Run ID、Session 或 Agent"
            />
          </div>
          <button className="filter-chip">近 24 小时⌄</button>
          <button className="filter-chip">全部状态⌄</button>
        </div>
        <div className="table-head">
          <span>RUN</span>
          <span>AGENT / SNAPSHOT</span>
          <span>STATUS</span>
          <span>LATENCY</span>
          <span>TOKENS</span>
          <span />
        </div>
        {runs
          .filter((x) => x.join(" ").includes(query))
          .map((row) => (
            <button className="table-row" key={row[0]}>
              <code>{row[0]}</code>
              <span>
                <b>{row[1]}</b>
                <small>snapshot 8f1a09c</small>
              </span>
              <em className={row[2]}>{row[2]}</em>
              <span>{row[3]}</span>
              <span>{row[4]}</span>
              <i>→</i>
            </button>
          ))}
      </section>
    </>
  );
}

export default function App() {
  const { t, i18n } = useTranslation();
  const [page, setPage] = useState<Page>(
    () => pathPages[window.location.pathname] ?? "agents",
  );
  useEffect(() => {
    if (!pathPages[window.location.pathname])
      window.history.replaceState({}, "", pagePaths.agents);
    const syncPage = () =>
      setPage(pathPages[window.location.pathname] ?? "agents");
    window.addEventListener("popstate", syncPage);
    return () => window.removeEventListener("popstate", syncPage);
  }, []);
  const selected = modules.find((x) => x.id === page)!;
  const moduleName = (module: { id: Page; name: string }) =>
    t(`navigation.${module.id}`, { defaultValue: module.name });
  const navigate = (next: Page) => {
    window.history.pushState({}, "", pagePaths[next]);
    setPage(next);
  };
  const content = {
    agents: <AgentPage go={navigate} />,
    models: <ModelsPage />,
    skills: <SkillsPage />,
    mcp: <McpPage />,
    knowledge: <KnowledgePage />,
    workflows: <WorkflowsPage />,
    memory: <MemoryPage />,
    workspace: <WorkspacePage />,
    teams: <TeamsPage />,
    release: <ReleasePage />,
    observe: <ObservePage />,
    system: <SystemPage />,
  }[page];
  return (
    <main className="console-shell">
      <aside className="main-nav">
        <div className="brand">
          <span className="brand-mark">ok</span>
          <span>AGENT</span>
        </div>
        <p className="nav-caption">HARNESS CONTROL PLANE</p>
        <nav>
          {navigationGroups.map((group) => (
            <section key={group.title} style={{ marginBottom: 12 }}>
              <p
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
