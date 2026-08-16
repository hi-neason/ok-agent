import { useEffect, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { AgentRegistryPage, AgentConfigPage } from "./agent";
import "./agent.css";

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
  businessDomain: string;
  archiveName: string | null;
  archiveSize: number;
  enabled: boolean;
  updatedAt?: string;
};

type SkillFileItem = {
  path: string;
  mediaType: string;
  size: number;
};

type SkillFileContent = SkillFileItem & {
  previewable: boolean;
  content: string | null;
  version: number;
  updatedAt: string;
};

type SkillTreeNode = {
  name: string;
  path: string;
  file?: SkillFileItem;
  children: SkillTreeNode[];
};

function buildSkillTree(files: SkillFileItem[]): SkillTreeNode[] {
  const root: SkillTreeNode[] = [];
  files.forEach((file) => {
    let level = root;
    let path = "";
    file.path.split("/").forEach((name, index, segments) => {
      path = path ? `${path}/${name}` : name;
      let node = level.find((item) => item.name === name);
      if (!node) {
        node = { name, path, children: [] };
        level.push(node);
      }
      if (index === segments.length - 1) node.file = file;
      level = node.children;
    });
  });
  return root;
}

function SkillTree({
  nodes,
  selectedPath,
  onSelect,
}: {
  nodes: SkillTreeNode[];
  selectedPath?: string;
  onSelect: (path: string) => void;
}) {
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());

  const toggleFolder = (path: string) => {
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return next;
    });
  };

  const renderNodes = (items: SkillTreeNode[], depth: number) =>
    items.map((node) => {
      const folder = !node.file;
      const isCollapsed = collapsed.has(node.path);
      return (
        <div key={node.path}>
          <button
            className={`${selectedPath === node.path ? "selected" : ""} ${folder ? "skill-tree-folder" : ""}`}
            style={{ paddingLeft: 10 + depth * 17 }}
            onClick={() =>
              folder ? toggleFolder(node.path) : onSelect(node.path)
            }
            aria-expanded={folder ? !isCollapsed : undefined}
          >
            <span>{folder ? (isCollapsed ? "▸" : "▾") : "◇"}</span>
            {node.name}
          </button>
          {node.children.length > 0 &&
            !isCollapsed &&
            renderNodes(node.children, depth + 1)}
        </div>
      );
    });

  return <>{renderNodes(nodes, 0)}</>;
}

function SkillsPage() {
  const { t } = useTranslation();
  const [skills, setSkills] = useState<SkillItem[]>([]);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [editing, setEditing] = useState<SkillItem | null>(null);
  const [viewing, setViewing] = useState<SkillItem | null>(null);
  const [files, setFiles] = useState<SkillFileItem[]>([]);
  const [selectedFile, setSelectedFile] = useState<SkillFileContent | null>(
    null,
  );
  const [fileDraft, setFileDraft] = useState<string | null>(null);
  const [fileSaving, setFileSaving] = useState(false);
  const [fileError, setFileError] = useState("");
  const [fileSuccess, setFileSuccess] = useState("");
  const [archive, setArchive] = useState<File | null>(null);
  const [uploadName, setUploadName] = useState("");
  const [uploadDescription, setUploadDescription] = useState("");
  const [businessDomain, setBusinessDomain] = useState("");
  const [query, setQuery] = useState("");
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

  const visibleSkills = skills.filter((skill) =>
    `${skill.name} ${skill.skillKey} ${skill.description}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );

  const saveMetadata = async () => {
    if (!editing || saving) return;
    setSaving(true);
    setError("");
    try {
      const response = await fetch(`/api/v1/skills/${editing.id}/metadata`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: editing.name,
          description: editing.description,
          businessDomain: editing.businessDomain,
        }),
      });
      if (!response.ok) throw new Error();
      const saved = (await response.json()) as SkillItem;
      setSkills((current) =>
        current.map((skill) => (skill.id === saved.id ? saved : skill)),
      );
      setEditing(null);
    } catch {
      setError(t("skills.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  const importArchive = async (overwrite = false) => {
    if (!archive || !businessDomain.trim() || saving) return;
    setSaving(true);
    setError("");
    const form = new FormData();
    form.append("file", archive);
    form.append("name", uploadName);
    form.append("description", uploadDescription);
    form.append("businessDomain", businessDomain.trim());
    form.append("overwrite", String(overwrite));
    try {
      const response = await fetch("/api/v1/skills/import", {
        method: "POST",
        body: form,
      });
      if (response.status === 409 && !overwrite) {
        if (window.confirm(t("skills.overwriteConfirm"))) {
          setSaving(false);
          await importArchive(true);
          return;
        }
        return;
      }
      if (!response.ok) {
        const failure = (await response.json().catch(() => null)) as {
          code?: string;
        } | null;
        if (failure?.code === "SKILL_MD_NOT_AT_ROOT") {
          throw new Error(t("skills.skillMdNotAtRoot"));
        }
        throw new Error(t("skills.importFailed"));
      }
      const saved = (await response.json()) as SkillItem;
      setSkills((current) => [
        saved,
        ...current.filter((item) => item.id !== saved.id),
      ]);
      setUploadOpen(false);
      setArchive(null);
      setUploadName("");
      setUploadDescription("");
      setBusinessDomain("");
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : t("skills.importFailed"),
      );
    } finally {
      setSaving(false);
    }
  };

  const openSkill = async (skill: SkillItem) => {
    setViewing(skill);
    setSelectedFile(null);
    const response = await fetch(`/api/v1/skills/${skill.id}/files`);
    if (!response.ok) {
      setError(t("skills.filesFailed"));
      return;
    }
    const manifest = (await response.json()) as SkillFileItem[];
    setFiles(manifest);
    const first =
      manifest.find((file) => file.path === "SKILL.md") ?? manifest[0];
    if (first) await openFile(skill.id, first.path);
  };

  const openFile = async (skillId: string, path: string) => {
    setFileDraft(null);
    setFileError("");
    setFileSuccess("");
    const response = await fetch(
      `/api/v1/skills/${skillId}/file?path=${encodeURIComponent(path)}`,
    );
    if (response.ok)
      setSelectedFile((await response.json()) as SkillFileContent);
  };

  const saveFile = async () => {
    if (!viewing || !selectedFile || fileDraft === null || fileSaving) return;
    setFileSaving(true);
    setFileError("");
    setFileSuccess("");
    try {
      const response = await fetch(`/api/v1/skills/${viewing.id}/file`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          path: selectedFile.path,
          content: fileDraft,
          version: selectedFile.version,
        }),
      });
      if (response.status === 409) throw new Error(t("skills.fileConflict"));
      if (!response.ok) throw new Error(t("skills.fileSaveFailed"));
      const saved = (await response.json()) as SkillFileContent;
      setSelectedFile(saved);
      setFileDraft(null);
      setFileSuccess(t("skills.fileSaved", { version: saved.version }));
      const refreshed = await fetch("/api/v1/skills");
      if (refreshed.ok) {
        const items = (await refreshed.json()) as SkillItem[];
        setSkills(items);
        setViewing(items.find((item) => item.id === viewing.id) ?? viewing);
      }
    } catch (failure) {
      setFileError(
        failure instanceof Error ? failure.message : t("skills.fileSaveFailed"),
      );
    } finally {
      setFileSaving(false);
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
              setUploadOpen(true);
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
        </div>
        <div className="table-head skill-table-row">
          <span>{t("skills.skill")}</span>
          <span>{t("skills.domain")}</span>
          <span>{t("skills.archive")}</span>
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
              <span className="skill-domain">#{skill.businessDomain}</span>
              <code>{skill.archiveName ?? "—"}</code>
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
                  onClick={() => void openSkill(skill)}
                >
                  {t("skills.view")}
                </button>
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
      {uploadOpen &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setUploadOpen(false)}
          >
            <div
              className="form-surface model-editor skill-editor"
              role="dialog"
              aria-modal="true"
              aria-label={t("skills.importTitle")}
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">SKILL PACKAGE / IMPORT</p>
                  <h2>{t("skills.importTitle")}</h2>
                </div>
                <button
                  className="link-button"
                  onClick={() => setUploadOpen(false)}
                >
                  {t("skills.close")} ×
                </button>
              </div>
              <div className="skill-import-strip">
                <div>
                  <b>{archive?.name ?? t("skills.noArchive")}</b>
                  <small>{t("skills.archiveHint")}</small>
                </div>
                <label className="ui-button quiet file-button">
                  {t("skills.selectFile")}
                  <input
                    type="file"
                    accept=".zip,application/zip"
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      if (file) setArchive(file);
                    }}
                  />
                </label>
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>{t("skills.name")}</span>
                  <input
                    value={uploadName}
                    onChange={(event) => setUploadName(event.target.value)}
                    placeholder={t("skills.parsedPlaceholder")}
                  />
                </label>
                <label className="field">
                  <span>{t("skills.skillDescription")}</span>
                  <input
                    value={uploadDescription}
                    onChange={(event) =>
                      setUploadDescription(event.target.value)
                    }
                    placeholder={t("skills.parsedPlaceholder")}
                  />
                </label>
                <label className="field">
                  <span>{t("skills.domain")}</span>
                  <input
                    value={businessDomain}
                    onChange={(event) => setBusinessDomain(event.target.value)}
                    placeholder={t("skills.domainPlaceholder")}
                  />
                </label>
              </div>
              {error && (
                <div className="skill-error modal-error">× {error}</div>
              )}
              <div className="sticky-actions">
                <Button quiet onClick={() => setUploadOpen(false)}>
                  {t("skills.cancel")}
                </Button>
                <Button
                  onClick={() => void importArchive()}
                  disabled={saving || !archive || !businessDomain.trim()}
                >
                  {saving ? t("skills.parsing") : t("skills.import")}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
      {viewing &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setViewing(null)}
          >
            <div
              className="skill-browser"
              role="dialog"
              aria-modal="true"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <header>
                <div>
                  <p className="kicker">SKILL PACKAGE / EXPLORER</p>
                  <h2>{viewing.name}</h2>
                  <span className="skill-domain">
                    #{viewing.businessDomain}
                  </span>
                </div>
                <button
                  className="link-button"
                  onClick={() => setViewing(null)}
                >
                  {t("skills.close")} ×
                </button>
              </header>
              <div className="skill-browser-body">
                <aside>
                  <p>
                    {t("skills.files")} · {files.length}
                  </p>
                  <SkillTree
                    nodes={buildSkillTree(files)}
                    selectedPath={selectedFile?.path}
                    onSelect={(path) => void openFile(viewing.id, path)}
                  />
                </aside>
                <main>
                  <div className="file-preview-head">
                    <div>
                      <code>{selectedFile?.path ?? "—"}</code>
                      <small>
                        {selectedFile
                          ? `${selectedFile.mediaType} · ${selectedFile.size} B`
                          : ""}
                      </small>
                    </div>
                    {selectedFile?.previewable && fileDraft === null && (
                      <button
                        className="file-edit-button"
                        onClick={() => {
                          setFileDraft(selectedFile.content ?? "");
                          setFileSuccess("");
                        }}
                      >
                        ✎ {t("skills.editFile")}
                      </button>
                    )}
                    {fileDraft !== null && (
                      <div className="file-edit-actions">
                        <button
                          onClick={() => {
                            setFileDraft(null);
                            setFileError("");
                          }}
                        >
                          {t("skills.cancel")}
                        </button>
                        <button
                          className="primary"
                          onClick={() => void saveFile()}
                          disabled={fileSaving}
                        >
                          {fileSaving
                            ? t("skills.saving")
                            : t("skills.saveFile")}
                        </button>
                      </div>
                    )}
                  </div>
                  {fileError && (
                    <div className="file-edit-error">× {fileError}</div>
                  )}
                  {fileSuccess && (
                    <div className="file-edit-success" role="status">
                      ✓ {fileSuccess}
                    </div>
                  )}
                  {fileDraft !== null ? (
                    <textarea
                      className="skill-file-editor"
                      value={fileDraft}
                      onChange={(event) => setFileDraft(event.target.value)}
                      spellCheck={false}
                    />
                  ) : selectedFile?.previewable ? (
                    <pre>{selectedFile.content}</pre>
                  ) : (
                    <div className="binary-preview">
                      {t("skills.binaryPreview")}
                    </div>
                  )}
                </main>
              </div>
            </div>
          </div>,
          document.body,
        )}
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
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">SKILL METADATA / EDIT</p>
                  <h2>{editing.name}</h2>
                </div>
                <button
                  className="link-button"
                  onClick={() => setEditing(null)}
                >
                  {t("skills.close")} ×
                </button>
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>{t("skills.name")}</span>
                  <input
                    value={editing.name}
                    onChange={(event) =>
                      setEditing({ ...editing, name: event.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>{t("skills.domain")}</span>
                  <input
                    value={editing.businessDomain}
                    onChange={(event) =>
                      setEditing({
                        ...editing,
                        businessDomain: event.target.value,
                      })
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
              </div>
              <div className="sticky-actions">
                <Button quiet onClick={() => setEditing(null)}>
                  {t("skills.cancel")}
                </Button>
                <Button onClick={() => void saveMetadata()} disabled={saving}>
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

type McpServer = {
  id: string;
  serverKey: string;
  name: string;
  description: string;
  transport: "STREAMABLE_HTTP" | "SSE" | "STDIO";
  serverUrl: string;
  command: string;
  arguments: string[];
  queryParameters: Record<string, string>;
  configuredHeaderNames: string[];
  configuredEnvironmentNames: string[];
  enabled: boolean;
  requestTimeoutSeconds: number;
  initializationTimeoutSeconds: number;
  lastTestStatus: string;
  lastTestedAt?: string;
  toolCount: number;
  updatedAt: string;
};
type McpTool = {
  name: string;
  description: string;
  inputSchemaJson: string;
  discoveredAt: string;
};
type McpDraft = {
  serverKey: string;
  name: string;
  description: string;
  transport: McpServer["transport"];
  serverUrl: string;
  command: string;
  argumentsText: string;
  headersText: string;
  environmentText: string;
  queryParametersText: string;
  requestTimeoutSeconds: number;
  initializationTimeoutSeconds: number;
};
const emptyMcpDraft: McpDraft = {
  serverKey: "",
  name: "",
  description: "",
  transport: "STREAMABLE_HTTP",
  serverUrl: "",
  command: "",
  argumentsText: "",
  headersText: "",
  environmentText: "",
  queryParametersText: "",
  requestTimeoutSeconds: 15,
  initializationTimeoutSeconds: 10,
};
const MCP_JSON_INDENT = 6;

const isJsonObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const stringMap = (value: unknown): Record<string, string> => {
  if (!isJsonObject(value)) return {};
  return Object.fromEntries(
    Object.entries(value).filter(
      (entry): entry is [string, string] => typeof entry[1] === "string",
    ),
  );
};

const mcpDraftToJson = (draft: McpDraft) => {
  const key = draft.serverKey.trim() || "my-mcp-server";
  const connection =
    draft.transport === "STDIO"
      ? {
          command: draft.command,
          args: draft.argumentsText.split("\n").filter(Boolean),
          env: draft.environmentText.trim()
            ? JSON.parse(draft.environmentText)
            : {},
        }
      : {
          type: draft.transport === "SSE" ? "sse" : "streamable-http",
          url: draft.serverUrl,
          headers: draft.headersText.trim()
            ? JSON.parse(draft.headersText)
            : {},
          queryParameters: draft.queryParametersText.trim()
            ? JSON.parse(draft.queryParametersText)
            : {},
        };
  return JSON.stringify(
    {
      mcpServers: {
        [key]: {
          name: draft.name || key,
          description: draft.description,
          ...connection,
          requestTimeoutSeconds: draft.requestTimeoutSeconds,
          initializationTimeoutSeconds: draft.initializationTimeoutSeconds,
        },
      },
    },
    null,
    MCP_JSON_INDENT,
  );
};

function McpRegistryPage() {
  const { t } = useTranslation();
  const [servers, setServers] = useState<McpServer[]>([]);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<McpServer | null | "new">(null);
  const [draft, setDraft] = useState<McpDraft>(emptyMcpDraft);
  const [tools, setTools] = useState<McpTool[]>([]);
  const [selectedTool, setSelectedTool] = useState<McpTool | null>(null);
  const [tab, setTab] = useState<"config" | "tools">("config");
  const [configMode, setConfigMode] = useState<"form" | "json">("form");
  const [jsonConfig, setJsonConfig] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(
    null,
  );
  const slugifyMcpKey = (value: string) =>
    value
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "");
  const load = async () => {
    try {
      const response = await fetch("/api/v1/mcp-servers");
      if (!response.ok) throw new Error();
      setServers(await response.json());
    } catch {
      setNotice({ ok: false, text: t("mcp.loadFailed") });
    }
  };
  useEffect(() => {
    void load();
  }, []);
  const open = (server?: McpServer) => {
    const nextDraft: McpDraft = server
      ? {
          serverKey: server.serverKey,
          name: server.name,
          description: server.description,
          transport: server.transport,
          serverUrl: server.serverUrl ?? "",
          command: server.command ?? "",
          argumentsText: (server.arguments ?? []).join("\n"),
          headersText: "",
          environmentText: "",
          queryParametersText: JSON.stringify(
            server.queryParameters ?? {},
            null,
            2,
          ),
          requestTimeoutSeconds: server.requestTimeoutSeconds,
          initializationTimeoutSeconds: server.initializationTimeoutSeconds,
        }
      : { ...emptyMcpDraft };
    setEditing(server ?? "new");
    setDraft(nextDraft);
    setJsonConfig(mcpDraftToJson(nextDraft));
    setConfigMode("form");
    setTools([]);
    setSelectedTool(null);
    setTab("config");
    setNotice(null);
  };
  const parseJsonDraft = (): McpDraft => {
    const root: unknown = JSON.parse(jsonConfig);
    if (!isJsonObject(root)) throw new Error(t("mcp.jsonObjectRequired"));
    const container = isJsonObject(root.mcpServers) ? root.mcpServers : root;
    const entries = Object.entries(container);
    if (entries.length !== 1) throw new Error(t("mcp.singleServerRequired"));
    const [serverKey, rawConfig] = entries[0];
    if (!isJsonObject(rawConfig)) throw new Error(t("mcp.invalidServerConfig"));
    const transportValue =
      typeof rawConfig.type === "string"
        ? rawConfig.type
        : typeof rawConfig.transport === "string"
          ? rawConfig.transport
          : "";
    const type = transportValue.toLowerCase();
    const transport: McpServer["transport"] =
      typeof rawConfig.command === "string"
        ? "STDIO"
        : type === "sse"
          ? "SSE"
          : "STREAMABLE_HTTP";
    const args = Array.isArray(rawConfig.args)
      ? rawConfig.args.filter(
          (value): value is string => typeof value === "string",
        )
      : [];
    return {
      serverKey,
      name: typeof rawConfig.name === "string" ? rawConfig.name : serverKey,
      description:
        typeof rawConfig.description === "string" ? rawConfig.description : "",
      transport,
      serverUrl: typeof rawConfig.url === "string" ? rawConfig.url : "",
      command: typeof rawConfig.command === "string" ? rawConfig.command : "",
      argumentsText: args.join("\n"),
      headersText: JSON.stringify(stringMap(rawConfig.headers), null, 2),
      environmentText: JSON.stringify(stringMap(rawConfig.env), null, 2),
      queryParametersText: JSON.stringify(
        stringMap(rawConfig.queryParameters),
        null,
        2,
      ),
      requestTimeoutSeconds:
        typeof rawConfig.requestTimeoutSeconds === "number"
          ? rawConfig.requestTimeoutSeconds
          : 15,
      initializationTimeoutSeconds:
        typeof rawConfig.initializationTimeoutSeconds === "number"
          ? rawConfig.initializationTimeoutSeconds
          : 10,
    };
  };
  const currentDraft = () => (configMode === "json" ? parseJsonDraft() : draft);
  const formatJsonConfig = () => {
    try {
      const parsed: unknown = JSON.parse(jsonConfig);
      setJsonConfig(JSON.stringify(parsed, null, MCP_JSON_INDENT));
      setNotice({ ok: true, text: t("mcp.jsonFormatted") });
    } catch {
      setNotice({ ok: false, text: t("mcp.jsonFormatFailed") });
    }
  };
  const validateDraft = (value: McpDraft) => {
    if (!value.name.trim()) return t("mcp.nameRequired");
    if (!value.serverKey.trim()) return t("mcp.serverKeyRequired");
    if (value.transport === "STDIO" && !value.command.trim())
      return t("mcp.commandRequired");
    if (value.transport !== "STDIO" && !value.serverUrl.trim())
      return t("mcp.serverUrlRequired");
    try {
      if (value.headersText.trim()) JSON.parse(value.headersText);
      if (value.environmentText.trim()) JSON.parse(value.environmentText);
      if (value.queryParametersText.trim())
        JSON.parse(value.queryParametersText);
    } catch {
      return t("mcp.invalidJson");
    }
    return null;
  };
  const payload = (value: McpDraft) => ({
    serverKey: value.serverKey.trim(),
    name: value.name.trim(),
    description: value.description,
    transport: value.transport,
    serverUrl: value.serverUrl.trim() || null,
    command: value.command.trim() || null,
    arguments: value.argumentsText
      .split("\n")
      .map((v) => v.trim())
      .filter(Boolean),
    headers: value.headersText.trim() ? JSON.parse(value.headersText) : {},
    environment: value.environmentText.trim()
      ? JSON.parse(value.environmentText)
      : {},
    queryParameters: value.queryParametersText.trim()
      ? JSON.parse(value.queryParametersText)
      : {},
    requestTimeoutSeconds: value.requestTimeoutSeconds,
    initializationTimeoutSeconds: value.initializationTimeoutSeconds,
  });
  const save = async () => {
    let value: McpDraft;
    try {
      value = currentDraft();
    } catch (error) {
      setNotice({
        ok: false,
        text: error instanceof Error ? error.message : t("mcp.invalidJson"),
      });
      return;
    }
    const validation = validateDraft(value);
    if (validation) {
      setNotice({ ok: false, text: validation });
      return;
    }
    setBusy(true);
    setNotice(null);
    try {
      const isNew = editing === "new";
      const response = await fetch(
        isNew
          ? "/api/v1/mcp-servers"
          : `/api/v1/mcp-servers/${(editing as McpServer).id}`,
        {
          method: isNew ? "POST" : "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload(value)),
        },
      );
      if (!response.ok) throw new Error();
      const saved: McpServer = await response.json();
      setEditing(saved);
      if (isNew) {
        try {
          const inspectionResponse = await fetch(
            `/api/v1/mcp-servers/${saved.id}/inspect`,
            { method: "POST" },
          );
          const inspection = await inspectionResponse.json();
          if (inspectionResponse.ok && inspection.success) {
            setTools(inspection.tools);
            setSelectedTool(inspection.tools[0] ?? null);
            setNotice({
              ok: true,
              text: t("mcp.savedAndConnected", {
                count: inspection.tools.length,
              }),
            });
          } else {
            setNotice({ ok: false, text: t("mcp.savedButConnectionFailed") });
          }
        } catch {
          setNotice({ ok: false, text: t("mcp.savedButConnectionFailed") });
        }
      } else {
        setNotice({ ok: true, text: t("mcp.saved") });
      }
      await load();
    } catch {
      setNotice({ ok: false, text: t("mcp.saveFailed") });
    } finally {
      setBusy(false);
    }
  };
  const inspect = async () => {
    let value: McpDraft;
    try {
      value = currentDraft();
    } catch (error) {
      setNotice({
        ok: false,
        text: error instanceof Error ? error.message : t("mcp.invalidJson"),
      });
      return;
    }
    const validation = validateDraft(value);
    if (validation) {
      setNotice({ ok: false, text: validation });
      return;
    }
    setBusy(true);
    setNotice(null);
    try {
      const saved = editing !== "new" && editing;
      const response = await fetch(
        saved
          ? `/api/v1/mcp-servers/${saved.id}/inspect`
          : "/api/v1/mcp-servers/inspect",
        saved
          ? { method: "POST" }
          : {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(payload(value)),
            },
      );
      const result = await response.json();
      if (!response.ok || !result.success)
        throw new Error(result.message || t("mcp.connectionFailed"));
      setTools(result.tools);
      setSelectedTool(result.tools[0] ?? null);
      setTab("tools");
      setNotice({
        ok: true,
        text: t("mcp.connectionSucceeded", { count: result.tools.length }),
      });
      if (saved) await load();
    } catch (error) {
      setNotice({
        ok: false,
        text:
          error instanceof Error && error.message
            ? error.message
            : t("mcp.connectionFailed"),
      });
    } finally {
      setBusy(false);
    }
  };
  const remove = async (server: McpServer) => {
    if (!confirm(t("mcp.deleteConfirm", { name: server.name }))) return;
    await fetch(`/api/v1/mcp-servers/${server.id}`, { method: "DELETE" });
    await load();
  };
  const toggle = async (server: McpServer) => {
    await fetch(
      `/api/v1/mcp-servers/${server.id}/enabled?value=${!server.enabled}`,
      { method: "PATCH" },
    );
    await load();
  };
  const visible = servers.filter((s) =>
    `${s.name} ${s.serverKey} ${s.serverUrl}`
      .toLowerCase()
      .includes(search.toLowerCase()),
  );
  return (
    <>
      <PageHeader
        kicker="MCP SERVER / REGISTRY"
        title={t("mcp.title")}
        description={t("mcp.description")}
        action={<Button onClick={() => open()}>＋ {t("mcp.register")}</Button>}
      />
      <div className="mcp-toolbar">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t("mcp.search")}
        />
        <span>{servers.length} MCP Servers</span>
      </div>
      <div className="mcp-table">
        <div className="mcp-row head">
          <span>{t("mcp.server")}</span>
          <span>{t("mcp.transport")}</span>
          <span>{t("mcp.tools")}</span>
          <span>{t("mcp.lastTest")}</span>
          <span>{t("mcp.status")}</span>
          <span>{t("mcp.actions")}</span>
        </div>
        {visible.map((server) => (
          <div className="mcp-row" key={server.id}>
            <span className="mcp-name">
              <i>⌘</i>
              <b>{server.name}</b>
              <small>{server.serverKey}</small>
            </span>
            <span>
              <code>{server.transport.replace("STREAMABLE_", "")}</code>
              <small>{server.serverUrl || server.command}</small>
            </span>
            <span>{t("mcp.toolCount", { count: server.toolCount })}</span>
            <span
              className={`test-state ${server.lastTestStatus.toLowerCase()}`}
            >
              {t(`mcp.testStatus.${server.lastTestStatus.toLowerCase()}`)}
            </span>
            <span>
              <Toggle on={server.enabled} setOn={() => void toggle(server)} />
            </span>
            <span className="row-actions">
              <button
                onClick={() => {
                  window.location.href = `/mcp/${server.id}/debug`;
                }}
              >
                {t("mcp.debug")}
              </button>
              <button onClick={() => open(server)}>{t("mcp.edit")}</button>
              <button className="danger" onClick={() => void remove(server)}>
                {t("mcp.delete")}
              </button>
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="mcp-empty">
            ⌘<b>{t("mcp.empty")}</b>
          </div>
        )}
      </div>
      {editing &&
        createPortal(
          <div
            className="model-modal-mask"
            onMouseDown={() => setEditing(null)}
          >
            <div
              className="mcp-inspector"
              role="dialog"
              onMouseDown={(e) => e.stopPropagation()}
            >
              <header>
                <div>
                  <p className="kicker">
                    MCP INSPECTOR / {editing === "new" ? "REGISTER" : "EDIT"}
                  </p>
                  <h2>
                    {editing === "new" ? t("mcp.register") : editing.name}
                  </h2>
                </div>
                <button
                  className="link-button"
                  onClick={() => setEditing(null)}
                >
                  {t("mcp.close")} ×
                </button>
              </header>
              <nav>
                <button
                  className={tab === "config" ? "active" : ""}
                  onClick={() => setTab("config")}
                >
                  01 {t("mcp.connectionConfig")}
                </button>
                <button
                  className={tab === "tools" ? "active" : ""}
                  onClick={() => setTab("tools")}
                >
                  02 {t("mcp.toolDiscovery")} <em>{tools.length}</em>
                </button>
              </nav>
              {tab === "config" ? (
                <>
                  <div className="mcp-config-mode">
                    <span>{t("mcp.configurationMode")}</span>
                    <div>
                      <button
                        className={configMode === "form" ? "active" : ""}
                        onClick={() => {
                          if (configMode === "json") {
                            try {
                              const parsed = parseJsonDraft();
                              setDraft(parsed);
                              setNotice(null);
                            } catch (error) {
                              setNotice({
                                ok: false,
                                text:
                                  error instanceof Error
                                    ? error.message
                                    : t("mcp.invalidJson"),
                              });
                              return;
                            }
                          }
                          setConfigMode("form");
                        }}
                      >
                        ◫ {t("mcp.formMode")}
                      </button>
                      <button
                        className={configMode === "json" ? "active" : ""}
                        onClick={() => {
                          try {
                            setJsonConfig(mcpDraftToJson(draft));
                            setConfigMode("json");
                            setNotice(null);
                          } catch {
                            setNotice({
                              ok: false,
                              text: t("mcp.invalidJson"),
                            });
                          }
                        }}
                      >
                        {"{}"} {t("mcp.jsonMode")}
                      </button>
                    </div>
                  </div>
                  {configMode === "form" ? (
                    <div className="mcp-form">
                      <label>
                        <span>{t("mcp.name")}</span>
                        <input
                          value={draft.name}
                          onChange={(e) => {
                            const previousSlug = slugifyMcpKey(draft.name);
                            const name = e.target.value;
                            setDraft({
                              ...draft,
                              name,
                              serverKey:
                                !draft.serverKey ||
                                draft.serverKey === previousSlug
                                  ? slugifyMcpKey(name)
                                  : draft.serverKey,
                            });
                          }}
                        />
                      </label>
                      <label>
                        <span>
                          SERVER_KEY{" "}
                          <small>· {t("mcp.serverKeyShortHint")}</small>
                        </span>
                        <input
                          value={draft.serverKey}
                          placeholder="local-mcp"
                          onChange={(e) =>
                            setDraft({ ...draft, serverKey: e.target.value })
                          }
                        />
                      </label>
                      <label className="wide">
                        <span>{t("mcp.descriptionLabel")}</span>
                        <input
                          value={draft.description}
                          onChange={(e) =>
                            setDraft({ ...draft, description: e.target.value })
                          }
                        />
                      </label>
                      <label>
                        <span>{t("mcp.transport")}</span>
                        <select
                          value={draft.transport}
                          onChange={(e) =>
                            setDraft({
                              ...draft,
                              transport: e.target
                                .value as McpServer["transport"],
                            })
                          }
                        >
                          <option value="STREAMABLE_HTTP">
                            Streamable HTTP
                          </option>
                          <option value="SSE">SSE</option>
                          <option value="STDIO">STDIO</option>
                        </select>
                      </label>
                      {draft.transport === "STDIO" ? (
                        <>
                          <label>
                            <span>COMMAND</span>
                            <input
                              value={draft.command}
                              onChange={(e) =>
                                setDraft({ ...draft, command: e.target.value })
                              }
                            />
                          </label>
                          <label className="wide">
                            <span>ARGUMENTS · {t("mcp.onePerLine")}</span>
                            <textarea
                              value={draft.argumentsText}
                              onChange={(e) =>
                                setDraft({
                                  ...draft,
                                  argumentsText: e.target.value,
                                })
                              }
                            />
                          </label>
                          <label className="wide">
                            <span>ENVIRONMENT · JSON</span>
                            <textarea
                              value={draft.environmentText}
                              placeholder={
                                editing !== "new" &&
                                editing.configuredEnvironmentNames.length
                                  ? t("mcp.secretConfigured", {
                                      keys: editing.configuredEnvironmentNames.join(
                                        ", ",
                                      ),
                                    })
                                  : '{\n  "API_KEY": "..."\n}'
                              }
                              onChange={(e) =>
                                setDraft({
                                  ...draft,
                                  environmentText: e.target.value,
                                })
                              }
                            />
                          </label>
                        </>
                      ) : (
                        <>
                          <label>
                            <span>
                              SERVER_URL <b className="field-required">*</b>{" "}
                              <small>
                                ·{" "}
                                {draft.transport === "SSE"
                                  ? "/api/v1/sse"
                                  : "/api/v1/mcp"}
                              </small>
                            </span>
                            <input
                              value={draft.serverUrl}
                              placeholder={t("mcp.serverUrlPlaceholder")}
                              onChange={(e) =>
                                setDraft({
                                  ...draft,
                                  serverUrl: e.target.value,
                                })
                              }
                            />
                          </label>
                          <label className="wide">
                            <span>
                              HEADERS · JSON{" "}
                              <small>({t("mcp.optional")})</small>
                            </span>
                            <textarea
                              value={draft.headersText}
                              placeholder={
                                editing !== "new" &&
                                editing.configuredHeaderNames.length
                                  ? t("mcp.secretConfigured", {
                                      keys: editing.configuredHeaderNames.join(
                                        ", ",
                                      ),
                                    })
                                  : t("mcp.headersPlaceholder")
                              }
                              onChange={(e) =>
                                setDraft({
                                  ...draft,
                                  headersText: e.target.value,
                                })
                              }
                            />
                          </label>
                          <label className="wide">
                            <span>
                              QUERY PARAMETERS · JSON{" "}
                              <small>({t("mcp.optional")})</small>
                            </span>
                            <textarea
                              value={draft.queryParametersText}
                              onChange={(e) =>
                                setDraft({
                                  ...draft,
                                  queryParametersText: e.target.value,
                                })
                              }
                            />
                          </label>
                        </>
                      )}
                      <label>
                        <span>{t("mcp.requestTimeout")}</span>
                        <input
                          type="number"
                          value={draft.requestTimeoutSeconds}
                          onChange={(e) =>
                            setDraft({
                              ...draft,
                              requestTimeoutSeconds: +e.target.value,
                            })
                          }
                        />
                      </label>
                      <label>
                        <span>{t("mcp.initTimeout")}</span>
                        <input
                          type="number"
                          value={draft.initializationTimeoutSeconds}
                          onChange={(e) =>
                            setDraft({
                              ...draft,
                              initializationTimeoutSeconds: +e.target.value,
                            })
                          }
                        />
                      </label>
                    </div>
                  ) : (
                    <div className="mcp-json-config">
                      <div className="mcp-json-head">
                        <div>
                          <p className="kicker">SINGLE MCP SERVER / JSON</p>
                          <b>{t("mcp.jsonEditorTitle")}</b>
                          <small>{t("mcp.jsonEditorHint")}</small>
                        </div>
                        <button type="button" onClick={formatJsonConfig}>
                          ✦ {t("mcp.formatJson")}
                        </button>
                      </div>
                      <textarea
                        value={jsonConfig}
                        onChange={(event) => setJsonConfig(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key !== "Tab") return;
                          event.preventDefault();
                          const input = event.currentTarget;
                          const start = input.selectionStart;
                          const end = input.selectionEnd;
                          const indentation = " ".repeat(MCP_JSON_INDENT);
                          setJsonConfig(
                            `${jsonConfig.slice(0, start)}${indentation}${jsonConfig.slice(end)}`,
                          );
                          requestAnimationFrame(() => {
                            input.selectionStart = input.selectionEnd =
                              start + MCP_JSON_INDENT;
                          });
                        }}
                        spellCheck={false}
                        aria-label={t("mcp.jsonEditorTitle")}
                      />
                    </div>
                  )}
                </>
              ) : (
                <div className="mcp-tool-browser">
                  <aside>
                    <div>
                      {t("mcp.discoveredTools")} <b>{tools.length}</b>
                    </div>
                    {tools.map((tool) => (
                      <button
                        className={
                          selectedTool?.name === tool.name ? "selected" : ""
                        }
                        onClick={() => setSelectedTool(tool)}
                        key={tool.name}
                      >
                        <i>⚡</i>
                        <span>
                          <b>{tool.name}</b>
                          <small>
                            {tool.description || t("mcp.noDescription")}
                          </small>
                        </span>
                      </button>
                    ))}
                  </aside>
                  <main>
                    {selectedTool ? (
                      <>
                        <div>
                          <p className="kicker">TOOL SCHEMA</p>
                          <h3>{selectedTool.name}</h3>
                          <p>{selectedTool.description}</p>
                        </div>
                        <pre>{selectedTool.inputSchemaJson}</pre>
                      </>
                    ) : (
                      <div className="tool-placeholder">
                        ⌁<b>{t("mcp.queryHint")}</b>
                      </div>
                    )}
                  </main>
                </div>
              )}
              {notice && (
                <div
                  className={`mcp-notice ${notice.ok ? "success" : "error"}`}
                >
                  <b>
                    {notice.ok ? "✓" : "×"} {notice.text}
                  </b>
                </div>
              )}
              <footer>
                <Button quiet onClick={() => void inspect()} disabled={busy}>
                  {busy ? t("mcp.testing") : t("mcp.testAndQuery")}
                </Button>
                <Button onClick={() => void save()} disabled={busy}>
                  {busy ? t("mcp.saving") : t("mcp.save")}
                </Button>
              </footer>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}

function McpPage() {
  const match = window.location.pathname.match(/^\/mcp\/([^/]+)\/debug$/);
  return match ? <McpDebugPage serverId={match[1]} /> : <McpRegistryPage />;
}

function McpDebugPage({ serverId }: { serverId: string }) {
  const { t } = useTranslation();
  const [server, setServer] = useState<McpServer | null>(null);
  const [tools, setTools] = useState<McpTool[]>([]);
  const [selected, setSelected] = useState<McpTool | null>(null);
  const [query, setQuery] = useState("");
  const [argumentsJson, setArgumentsJson] = useState("{}");
  const [result, setResult] = useState("");
  const [resultOk, setResultOk] = useState<boolean | null>(null);
  const [durationMs, setDurationMs] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const argumentTemplate = (tool: McpTool) => {
    try {
      const schema: unknown = JSON.parse(tool.inputSchemaJson);
      if (!isJsonObject(schema) || !isJsonObject(schema.properties))
        return "{}";
      const values = Object.fromEntries(
        Object.entries(schema.properties).map(([name, property]) => {
          if (!isJsonObject(property)) return [name, null];
          if (property.default !== undefined) return [name, property.default];
          if (property.type === "number" || property.type === "integer")
            return [name, 0];
          if (property.type === "boolean") return [name, false];
          if (property.type === "array") return [name, []];
          if (property.type === "object") return [name, {}];
          return [name, ""];
        }),
      );
      return JSON.stringify(values, null, 4);
    } catch {
      return "{}";
    }
  };

  const chooseTool = (tool: McpTool) => {
    setSelected(tool);
    setArgumentsJson(argumentTemplate(tool));
    setResult("");
    setResultOk(null);
    setDurationMs(null);
  };

  const loadTools = async (refresh = false) => {
    setBusy(true);
    setError("");
    try {
      if (refresh) {
        const inspectionResponse = await fetch(
          `/api/v1/mcp-servers/${serverId}/inspect`,
          { method: "POST" },
        );
        const inspection = await inspectionResponse.json();
        if (!inspectionResponse.ok || !inspection.success)
          throw new Error(t("mcp.connectionFailed"));
        setTools(inspection.tools);
        const next = inspection.tools[0] ?? null;
        if (next) chooseTool(next);
      } else {
        const response = await fetch(`/api/v1/mcp-servers/${serverId}/tools`);
        if (!response.ok) throw new Error();
        const loaded: McpTool[] = await response.json();
        setTools(loaded);
        if (loaded[0]) chooseTool(loaded[0]);
      }
    } catch (loadError) {
      setError(
        loadError instanceof Error && loadError.message
          ? loadError.message
          : t("mcp.toolsLoadFailed"),
      );
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    void (async () => {
      try {
        const response = await fetch("/api/v1/mcp-servers");
        const loaded: McpServer[] = await response.json();
        const current = loaded.find((item) => item.id === serverId) ?? null;
        setServer(current);
        if (!current) setError(t("mcp.serverNotFound"));
        else await loadTools(false);
      } catch {
        setError(t("mcp.serverNotFound"));
      }
    })();
  }, [serverId]);

  const runTool = async () => {
    if (!selected) return;
    let args: unknown;
    try {
      args = JSON.parse(argumentsJson);
      if (!isJsonObject(args)) throw new Error();
    } catch {
      setError(t("mcp.argumentsInvalid"));
      return;
    }
    setBusy(true);
    setError("");
    setResult("");
    try {
      const response = await fetch(
        `/api/v1/mcp-servers/${serverId}/tools/${encodeURIComponent(selected.name)}/call`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ arguments: args }),
        },
      );
      const call = await response.json();
      setResultOk(Boolean(call.success));
      setDurationMs(call.durationMs);
      try {
        setResult(JSON.stringify(JSON.parse(call.resultJson), null, 4));
      } catch {
        setResult(call.resultJson || call.message);
      }
    } catch {
      setResultOk(false);
      setError(t("mcp.toolCallFailed"));
    } finally {
      setBusy(false);
    }
  };

  const visibleTools = tools.filter((tool) =>
    `${tool.name} ${tool.description}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );

  return (
    <div className="mcp-debug-page">
      <header className="mcp-debug-header">
        <div className="mcp-debug-identity">
          <button
            className="mcp-debug-back"
            onClick={() => (window.location.href = "/mcp")}
            aria-label={t("mcp.backToRegistry")}
          >
            ←
          </button>
          <div>
            <div className="mcp-debug-eyebrow">
              <p className="kicker">MCP DEBUG WORKBENCH</p>
              <span>{server?.transport}</span>
            </div>
            <div className="mcp-debug-name-line">
              <h1>{server?.name ?? t("mcp.loading")}</h1>
              <code>{server?.serverUrl || server?.command}</code>
            </div>
          </div>
        </div>
        <div className="mcp-debug-connection">
          <span
            className={server?.lastTestStatus === "SUCCESS" ? "online" : ""}
          />
          <div>
            <b>
              {server?.lastTestStatus === "SUCCESS"
                ? t("mcp.connected")
                : t("mcp.notConnected")}
            </b>
            <small>{t("mcp.toolCount", { count: tools.length })}</small>
          </div>
          <Button quiet onClick={() => void loadTools(true)} disabled={busy}>
            {busy ? t("mcp.refreshing") : t("mcp.reconnect")}
          </Button>
        </div>
      </header>
      {error && <div className="mcp-debug-error">× {error}</div>}
      <div className="mcp-debug-workbench">
        <aside className="mcp-debug-tools">
          <div>
            <span>TOOL CATALOG</span>
            <b>{tools.length}</b>
          </div>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t("mcp.searchTools")}
          />
          <section>
            {visibleTools.map((tool) => (
              <button
                key={tool.name}
                className={selected?.name === tool.name ? "selected" : ""}
                onClick={() => chooseTool(tool)}
              >
                <i>⚡</i>
                <span>
                  <b>{tool.name}</b>
                  <small>{tool.description || t("mcp.noDescription")}</small>
                </span>
              </button>
            ))}
          </section>
        </aside>
        <main className="mcp-debug-schema">
          {selected ? (
            <>
              <div className="debug-panel-title">
                <div>
                  <p className="kicker">TOOL DEFINITION</p>
                  <span>JSON SCHEMA</span>
                </div>
                <h2>{selected.name}</h2>
                <p>{selected.description || t("mcp.noDescription")}</p>
              </div>
              <div className="schema-label">INPUT SCHEMA</div>
              <pre>{selected.inputSchemaJson}</pre>
            </>
          ) : (
            <div className="debug-empty">{t("mcp.selectTool")}</div>
          )}
        </main>
        <aside className="mcp-debug-runner">
          <div className="debug-panel-title">
            <div>
              <p className="kicker">REQUEST LAB</p>
              <span>JSON</span>
            </div>
            <h2>{t("mcp.arguments")}</h2>
          </div>
          <textarea
            value={argumentsJson}
            onChange={(event) => setArgumentsJson(event.target.value)}
            spellCheck={false}
          />
          <button
            className="mcp-run-button"
            disabled={!selected || busy}
            onClick={() => void runTool()}
          >
            {busy ? t("mcp.running") : `▶ ${t("mcp.runTool")}`}
          </button>
          <div className="mcp-result-head">
            <b>{t("mcp.result")}</b>
            {durationMs !== null && (
              <span className={resultOk ? "success" : "error"}>
                {resultOk ? "✓" : "×"} {durationMs} ms
              </span>
            )}
          </div>
          <pre className={resultOk === false ? "failed" : ""}>
            {result || t("mcp.resultPlaceholder")}
          </pre>
        </aside>
      </div>
    </div>
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
  const [navCollapsed, setNavCollapsed] = useState(
    () => window.localStorage.getItem("ok-agent.nav-collapsed") === "true",
  );
  const agentConfigMatch = () =>
    window.location.pathname.match(/^\/agents\/([^/]+)\/config$/);
  const pageForPath = (path: string): Page =>
    path.startsWith("/mcp/") || path.startsWith("/agents/")
      ? path.startsWith("/mcp/")
        ? "mcp"
        : "agents"
      : (pathPages[path] ?? "agents");
  const [page, setPage] = useState<Page>(() =>
    pageForPath(window.location.pathname),
  );
  const [agentConfigId, setAgentConfigId] = useState<string | null>(() =>
    agentConfigMatch()?.[1] ?? null,
  );
  useEffect(() => {
    if (
      !pathPages[window.location.pathname] &&
      !window.location.pathname.startsWith("/mcp/") &&
      !window.location.pathname.startsWith("/agents/")
    )
      window.history.replaceState({}, "", pagePaths.agents);
    const syncPage = () => {
      setPage(pageForPath(window.location.pathname));
      setAgentConfigId(agentConfigMatch()?.[1] ?? null);
    };
    window.addEventListener("popstate", syncPage);
    return () => window.removeEventListener("popstate", syncPage);
  }, []);
  const selected = modules.find((x) => x.id === page)!;
  const moduleName = (module: { id: Page; name: string }) =>
    t(`navigation.${module.id}`, { defaultValue: module.name });
  const navigate = (next: Page) => {
    window.history.pushState({}, "", pagePaths[next]);
    setPage(next);
    setAgentConfigId(null);
  };
  const openAgentConfig = (id: string) => {
    window.history.pushState({}, "", `/agents/${id}/config`);
    setPage("agents");
    setAgentConfigId(id);
  };
  const backToAgents = () => navigate("agents");
  const content = {
    agents: agentConfigId ? (
      <AgentConfigPage agentId={agentConfigId} onBack={backToAgents} />
    ) : (
      <AgentRegistryPage onConfigure={openAgentConfig} />
    ),
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
