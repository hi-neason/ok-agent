import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";

export type AgentItem = {
  id: string;
  agentKey: string;
  name: string;
  description: string;
  businessDomain: string;
  systemPrompt: string;
  welcomeMessage: string;
  modelAssetId: string | null;
  temperature: number | null;
  topP: number | null;
  topK: number | null;
  maxTokens: number | null;
  maxIters: number;
  modelTimeoutSeconds: number;
  toolTimeoutSeconds: number;
  maxRetries: number;
  permissionMode: "DEFAULT" | "EXPLORE" | "ACCEPT_EDITS" | "DONT_ASK" | "BYPASS";
  parallelToolCalls: boolean;
  compactionEnabled: boolean;
  maxContextTokens: number;
  toolResultEvictionEnabled: boolean;
  tracingEnabled: boolean;
  mcpServerIds: string[];
  skillIds: string[];
  mcpToolFilters: Record<string, string[]>;
  memoryEnabled: boolean;
  memoryFlushMode: "ALWAYS" | "THROTTLED" | "NEVER";
  memoryFlushIntervalMinutes: number;
  memoryConsolidationIntervalMinutes: number;
  memoryDailyRetentionDays: number;
  memorySessionRetentionDays: number;
  workspaceMode: "DISABLED" | "LOCAL_ROOTED" | "DOCKER_SANDBOX";
  workspaceIsolationScope: "SESSION" | "USER" | "AGENT" | "GLOBAL";
  workspaceContextEnabled: boolean;
  shellEnabled: boolean;
  dockerImage: string;
  sandboxMemoryMb: number;
  sandboxCpuCount: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

type Option = { id: string; name: string; sub?: string };

const isJsonObject = (v: unknown): v is Record<string, unknown> =>
  typeof v === "object" && v !== null && !Array.isArray(v);

async function jsonOrThrow(res: Response) {
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `HTTP ${res.status}`);
  }
  return res.status === 204 ? undefined : res.json();
}

function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  return createPortal(
    <div className="model-modal-mask" onMouseDown={onClose}>
      <div
        className="form-surface model-editor"
        role="dialog"
        aria-modal="true"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="form-title">
          <div>
            <p className="kicker">AGENT</p>
            <h2>{title}</h2>
          </div>
          <button className="link-button" onClick={onClose}>
            ×
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  );
}

export function AgentRegistryPage({
  onConfigure,
}: {
  onConfigure: (id: string) => void;
}) {
  const { t } = useTranslation();
  const [agents, setAgents] = useState<AgentItem[]>([]);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<AgentItem | "new" | null>(null);
  const [form, setForm] = useState({ name: "", description: "", businessDomain: "" });
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      const res = await fetch("/api/v1/agents");
      if (!res.ok) throw new Error();
      setAgents(await res.json());
    } catch {
      setError(t("agents.loadFailed"));
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const openNew = () => {
    setForm({ name: "", description: "", businessDomain: "" });
    setEditing("new");
    setError("");
  };

  const openEdit = (a: AgentItem) => {
    setForm({
      name: a.name,
      description: a.description,
      businessDomain: a.businessDomain,
    });
    setEditing(a);
    setError("");
  };

  const save = async () => {
    if (!form.name.trim()) {
      setError(t("agents.nameRequired"));
      return;
    }
    setSaving(true);
    setError("");
    try {
      const isNew = editing === "new";
      const id = isNew ? "" : (editing as AgentItem).id;
      const url = isNew ? "/api/v1/agents" : `/api/v1/agents/${id}`;
      const method = isNew ? "POST" : "PUT";
      const payload = {
        name: form.name.trim(),
        description: form.description.trim(),
        businessDomain: form.businessDomain.trim() || "GENERAL",
      };
      const res = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!res.ok) throw new Error();
      await load();
      setEditing(null);
    } catch {
      setError(t("agents.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (a: AgentItem) => {
    if (!window.confirm(t("agents.deleteConfirm", { name: a.name }))) return;
    const res = await fetch(`/api/v1/agents/${a.id}`, { method: "DELETE" });
    if (res.ok) await load();
  };

  return (
    <>
      <header className="page-header">
        <div>
          <p className="kicker">HARNESS AGENT / REGISTRY</p>
          <h1>{t("agents.title")}</h1>
          <p className="page-description">{t("agents.description")}</p>
        </div>
        <button className="ui-button" onClick={openNew}>
          ＋ {t("agents.create")}
        </button>
      </header>

      {error && <div className="skill-error">× {error}</div>}

      <section className="run-table">
        <div className="table-tools">
          <div className="search-mini">◌ {agents.length} AGENTS</div>
        </div>
        <div
          className="table-head"
          style={{ gridTemplateColumns: "1.8fr 1fr 1fr 110px auto" }}
        >
          <span>{t("agents.agent")}</span>
          <span>{t("agents.domain")}</span>
          <span>{t("agents.model")}</span>
          <span>{t("agents.updated")}</span>
          <span />
        </div>
        {agents.map((a) => (
          <div
            className="table-row"
            key={a.id}
            style={{ gridTemplateColumns: "1.8fr 1fr 1fr 110px auto" }}
          >
            <span>
              <b>{a.name}</b>
              <small>
                {a.agentKey} · {a.description || "—"}
              </small>
            </span>
            <span style={{ color: "#5b7aa6" }}>#{a.businessDomain}</span>
            <span>
              <small>{a.modelAssetId ? "configured" : "—"}</small>
            </span>
            <span>
              <small>{new Date(a.updatedAt).toLocaleString()}</small>
            </span>
            <span style={{ display: "flex", gap: 8, whiteSpace: "nowrap" }}>
              <button
                className="link-button"
                onClick={() => onConfigure(a.id)}
                style={{ fontSize: 10 }}
              >
                {t("agents.configure")}
              </button>
              <button
                className="link-button"
                onClick={() => openEdit(a)}
                style={{ fontSize: 10 }}
              >
                {t("agents.edit")}
              </button>
              <button
                className="link-button danger-link"
                onClick={() => void remove(a)}
                style={{ fontSize: 10 }}
              >
                {t("agents.delete")}
              </button>
            </span>
          </div>
        ))}
      </section>

      {editing && (
        <Modal
          title={editing === "new" ? t("agents.create") : t("agents.edit")}
          onClose={() => setEditing(null)}
        >
          <p className="modal-intro">{t("agents.createIntro")}</p>
          <div className="field-grid">
            <label className="field wide">
              <span>{t("agents.nameLabel")}</span>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder={t("agents.namePlaceholder")}
                autoFocus
              />
              <small>{t("agents.nameHint")}</small>
            </label>
            <label className="field wide">
              <span>{t("agents.descriptionLabel")}</span>
              <textarea
                className="cfg-textarea"
                rows={3}
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                placeholder={t("agents.descriptionPlaceholder")}
              />
              <small>{t("agents.descriptionHint")}</small>
            </label>
            <label className="field wide">
              <span>{t("agents.domainLabel")}</span>
              <input
                value={form.businessDomain}
                onChange={(e) => setForm({ ...form, businessDomain: e.target.value })}
                placeholder={t("agents.domainPlaceholder")}
              />
              <small>{t("agents.domainHint")}</small>
            </label>
          </div>
          {error && <div className="skill-error">× {error}</div>}
          <div className="sticky-actions">
            <button className="ui-button quiet" onClick={() => setEditing(null)}>
              {t("agents.cancel")}
            </button>
            <button className="ui-button" onClick={save} disabled={saving}>
              {saving ? t("agents.saving") : t("agents.save")}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}

type ChatMessage = { role: "user" | "assistant"; content: string; error?: boolean };

export function AgentConfigPage({ agentId, onBack }: { agentId: string; onBack: () => void }) {
  const { t } = useTranslation();
  const [agent, setAgent] = useState<AgentItem | null>(null);
  const [models, setModels] = useState<Option[]>([]);
  const [mcpServers, setMcpServers] = useState<Option[]>([]);
  const [skills, setSkills] = useState<Option[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);
  const [activeTab, setActiveTab] = useState<"core" | "skills" | "mcp" | "memory" | "workspace" | "runtime">("core");

  // editable draft
  const [systemPrompt, setSystemPrompt] = useState("");
  const [welcomeMessage, setWelcomeMessage] = useState("");
  const [modelAssetId, setModelAssetId] = useState("");
  const [temperature, setTemperature] = useState(0.7);
  const [topP, setTopP] = useState(1);
  const [topK, setTopK] = useState(40);
  const [maxTokens, setMaxTokens] = useState(2048);
  const [maxIters, setMaxIters] = useState(10);
  const [modelTimeoutSeconds, setModelTimeoutSeconds] = useState(120);
  const [toolTimeoutSeconds, setToolTimeoutSeconds] = useState(60);
  const [maxRetries, setMaxRetries] = useState(2);
  const [permissionMode, setPermissionMode] = useState<AgentItem["permissionMode"]>("BYPASS");
  const [parallelToolCalls, setParallelToolCalls] = useState(true);
  const [compactionEnabled, setCompactionEnabled] = useState(true);
  const [maxContextTokens, setMaxContextTokens] = useState(8000);
  const [toolResultEvictionEnabled, setToolResultEvictionEnabled] = useState(true);
  const [tracingEnabled, setTracingEnabled] = useState(true);
  const [boundMcp, setBoundMcp] = useState<Set<string>>(new Set());
  const [boundSkills, setBoundSkills] = useState<Set<string>>(new Set());
  const [mcpToolFilters, setMcpToolFilters] = useState<Record<string, string[]>>({});
  const [mcpTools, setMcpTools] = useState<Record<string, string[]>>({});
  const [memoryEnabled, setMemoryEnabled] = useState(false);
  const [memoryFlushMode, setMemoryFlushMode] = useState<AgentItem["memoryFlushMode"]>("THROTTLED");
  const [memoryFlushIntervalMinutes, setMemoryFlushIntervalMinutes] = useState(30);
  const [memoryConsolidationIntervalMinutes, setMemoryConsolidationIntervalMinutes] = useState(30);
  const [memoryDailyRetentionDays, setMemoryDailyRetentionDays] = useState(90);
  const [memorySessionRetentionDays, setMemorySessionRetentionDays] = useState(180);
  const [workspaceMode, setWorkspaceMode] = useState<AgentItem["workspaceMode"]>("DISABLED");
  const [workspaceIsolationScope, setWorkspaceIsolationScope] = useState<AgentItem["workspaceIsolationScope"]>("SESSION");
  const [workspaceContextEnabled, setWorkspaceContextEnabled] = useState(true);
  const [shellEnabled, setShellEnabled] = useState(false);
  const [dockerImage, setDockerImage] = useState("");
  const [sandboxMemoryMb, setSandboxMemoryMb] = useState(512);
  const [sandboxCpuCount, setSandboxCpuCount] = useState(1);

  // chat
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [configWidth, setConfigWidth] = useState(54);
  const layoutRef = useRef<HTMLDivElement>(null);
  const sessionIdRef = useRef<string>("");
  const threadRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      try {
        const [agentRes, modelRes, mcpRes, skillRes] = await Promise.all([
          fetch(`/api/v1/agents/${agentId}`),
          fetch("/api/v1/models"),
          fetch("/api/v1/mcp-servers"),
          fetch("/api/v1/skills"),
        ]);
        if (!agentRes.ok) throw new Error("agent not found");
        const a: AgentItem = await agentRes.json();
        setAgent(a);
        setSystemPrompt(a.systemPrompt || "");
        setWelcomeMessage(a.welcomeMessage || "");
        setModelAssetId(a.modelAssetId || "");
        setTemperature(a.temperature ?? 0.7);
        setTopP(a.topP ?? 1);
        setTopK(a.topK ?? 40);
        setMaxTokens(a.maxTokens ?? 2048);
        setMaxIters(a.maxIters ?? 10);
        setModelTimeoutSeconds(a.modelTimeoutSeconds ?? 120);
        setToolTimeoutSeconds(a.toolTimeoutSeconds ?? 60);
        setMaxRetries(a.maxRetries ?? 2);
        setPermissionMode(a.permissionMode ?? "BYPASS");
        setParallelToolCalls(a.parallelToolCalls ?? true);
        setCompactionEnabled(a.compactionEnabled ?? true);
        setMaxContextTokens(a.maxContextTokens ?? 8000);
        setToolResultEvictionEnabled(a.toolResultEvictionEnabled ?? true);
        setTracingEnabled(a.tracingEnabled ?? true);
        setBoundMcp(new Set(a.mcpServerIds));
        setBoundSkills(new Set(a.skillIds));
        setMcpToolFilters(a.mcpToolFilters ?? {});
        setMemoryEnabled(a.memoryEnabled ?? false);
        setMemoryFlushMode(a.memoryFlushMode ?? "THROTTLED");
        setMemoryFlushIntervalMinutes(a.memoryFlushIntervalMinutes ?? 30);
        setMemoryConsolidationIntervalMinutes(a.memoryConsolidationIntervalMinutes ?? 30);
        setMemoryDailyRetentionDays(a.memoryDailyRetentionDays ?? 90);
        setMemorySessionRetentionDays(a.memorySessionRetentionDays ?? 180);
        setWorkspaceMode(a.workspaceMode ?? "DISABLED");
        setWorkspaceIsolationScope(a.workspaceIsolationScope ?? "SESSION");
        setWorkspaceContextEnabled(a.workspaceContextEnabled ?? true);
        setShellEnabled(a.shellEnabled ?? false);
        setDockerImage(a.dockerImage ?? "");
        setSandboxMemoryMb(a.sandboxMemoryMb ?? 512);
        setSandboxCpuCount(a.sandboxCpuCount ?? 1);

        const modelList: Array<Record<string, unknown>> = modelRes.ok ? await modelRes.json() : [];
        setModels(
          modelList
            .filter((m) => m.enabled !== false)
            .map((m) => ({
              id: String(m.id),
              name: String(m.name),
              sub: `${m.provider} / ${m.modelId}`,
            })),
        );
        const mcpList: Array<Record<string, unknown>> = mcpRes.ok ? await mcpRes.json() : [];
        setMcpServers(
          mcpList
            .filter((m) => m.enabled !== false)
            .map((m) => ({ id: String(m.id), name: String(m.name), sub: String(m.serverKey) })),
        );
        const toolEntries = await Promise.all(
          mcpList.map(async (m) => {
            const id = String(m.id);
            const response = await fetch(`/api/v1/mcp-servers/${id}/tools`);
            const tools: Array<Record<string, unknown>> = response.ok ? await response.json() : [];
            return [id, tools.map((tool) => String(tool.name))] as const;
          }),
        );
        setMcpTools(Object.fromEntries(toolEntries));
        const skillList: Array<Record<string, unknown>> = skillRes.ok ? await skillRes.json() : [];
        setSkills(
          skillList
            .filter((s) => s.enabled !== false)
            .map((s) => ({
              id: String(s.id),
              name: String(s.name),
              sub: String(s.skillKey),
            })),
        );

        if (a.welcomeMessage) {
          setMessages([{ role: "assistant", content: a.welcomeMessage }]);
        }
      } catch {
        setNotice({ ok: false, text: t("agents.loadFailed") });
      } finally {
        setLoading(false);
      }
    })();
  }, [agentId, t]);

  useEffect(() => {
    threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, sending]);

  const toggle = (set: (s: Set<string>) => void, current: Set<string>, id: string) => {
    const next = new Set(current);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    set(next);
  };

  const save = async () => {
    if (
      agent &&
      permissionMode !== agent.permissionMode &&
      !window.confirm(
        t("agents.permissionChangeConfirm", {
          from: agent.permissionMode,
          to: permissionMode,
          name: agent.name,
        }),
      )
    ) {
      return;
    }
    if (
      agent &&
      (workspaceMode !== agent.workspaceMode || shellEnabled !== agent.shellEnabled) &&
      !window.confirm(t("agents.workspaceChangeConfirm", { name: agent.name }))
    ) return;
    setSaving(true);
    setNotice(null);
    try {
      const payload = {
        systemPrompt,
        welcomeMessage,
        modelAssetId: modelAssetId || null,
        temperature,
        topP,
        topK,
        maxTokens,
        maxIters,
        modelTimeoutSeconds,
        toolTimeoutSeconds,
        maxRetries,
        permissionMode,
        parallelToolCalls,
        compactionEnabled,
        maxContextTokens,
        toolResultEvictionEnabled,
        tracingEnabled,
        mcpServerIds: [...boundMcp],
        skillIds: [...boundSkills],
        mcpToolFilters,
        memoryEnabled,
        memoryFlushMode,
        memoryFlushIntervalMinutes,
        memoryConsolidationIntervalMinutes,
        memoryDailyRetentionDays,
        memorySessionRetentionDays,
        workspaceMode,
        workspaceIsolationScope,
        workspaceContextEnabled,
        shellEnabled,
        dockerImage,
        sandboxMemoryMb,
        sandboxCpuCount,
      };
      const res = await fetch(`/api/v1/agents/${agentId}/configuration`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!res.ok) throw new Error();
      setAgent(await res.json());
      setNotice({ ok: true, text: t("agents.configSaved") });
    } catch {
      setNotice({ ok: false, text: t("agents.saveFailed") });
    } finally {
      setSaving(false);
    }
  };

  const send = async () => {
    const text = input.trim();
    if (!text || sending) return;
    setInput("");
    const history = [...messages, { role: "user" as const, content: text }];
    setMessages(history);
    setSending(true);
    try {
      const res = await fetch(`/api/v1/agents/${agentId}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: text, sessionId: sessionIdRef.current || null }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok || !isJsonObject(data)) {
        const msg =
          (isJsonObject(data) && typeof data.detail === "string" && data.detail) ||
          (isJsonObject(data) && typeof data.message === "string" && data.message) ||
          t("agents.chatFailed");
        setMessages([...history, { role: "assistant", content: msg, error: true }]);
        return;
      }
      if (data.sessionId) sessionIdRef.current = String(data.sessionId);
      setMessages([
        ...history,
        { role: "assistant", content: String(data.reply ?? "") },
      ]);
    } catch {
      setMessages([
        ...history,
        { role: "assistant", content: t("agents.chatFailed"), error: true },
      ]);
    } finally {
      setSending(false);
    }
  };

  if (loading) return <div className="page-content">{t("agents.loading")}</div>;
  if (!agent) return <div className="page-content">{t("agents.notFound")}</div>;

  const resizeWorkbench = (clientX: number) => {
    const bounds = layoutRef.current?.getBoundingClientRect();
    if (!bounds) return;
    const percentage = ((clientX - bounds.left) / bounds.width) * 100;
    setConfigWidth(Math.min(72, Math.max(34, percentage)));
  };

  return (
    <div
      className="agent-config-layout"
      ref={layoutRef}
      style={{ gridTemplateColumns: `minmax(0, ${configWidth}fr) 10px minmax(0, ${100 - configWidth}fr)` }}
    >
      {/* LEFT: development panel */}
      <div className="agent-config-workbench">
        <nav className="agent-config-tabs" aria-label={t("agents.configTabs") }>
          {(["core", "skills", "mcp", "memory", "workspace", "runtime"] as const).map((tab) => (
            <button key={tab} className={activeTab === tab ? "active" : ""} onClick={() => setActiveTab(tab)}>
              <span>{tab === "core" ? "01" : tab === "skills" ? "02" : tab === "mcp" ? "03" : tab === "memory" ? "04" : tab === "workspace" ? "05" : "06"}</span>
              <strong>{t(`agents.tab.${tab}`)}</strong>
              {tab === "mcp" && boundMcp.size > 0 && <em>{boundMcp.size}</em>}
            </button>
          ))}
        </nav>
      <section className="agent-dev-panel">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <button className="link-button" onClick={onBack} style={{ fontSize: 12 }}>
            ← {t("agents.back")}
          </button>
          <h2 style={{ margin: 0, fontSize: 18 }}>{agent.name}</h2>
          <code style={{ fontSize: 11, color: "#7a9abc" }}>{agent.agentKey}</code>
        </div>

        <div className="config-section" hidden={activeTab !== "core"}>
          <div className="section-head">
            <b>{t("agents.systemPrompt")}</b>
            <small>{t("agents.systemPromptHint")}</small>
          </div>
          <textarea
            className="cfg-textarea tall"
            value={systemPrompt}
            onChange={(e) => setSystemPrompt(e.target.value)}
            placeholder={t("agents.systemPromptPlaceholder")}
          />
        </div>

        <div className="config-section" hidden={activeTab !== "core"}>
          <div className="section-head">
            <b>{t("agents.welcomeMessage")}</b>
            <small>{t("agents.welcomeHint")}</small>
          </div>
          <textarea
            className="cfg-textarea"
            value={welcomeMessage}
            onChange={(e) => setWelcomeMessage(e.target.value)}
            placeholder={t("agents.welcomePlaceholder")}
          />
        </div>

        <div className="config-section" hidden={activeTab !== "core"}>
          <div className="section-head">
            <b>{t("agents.model")}</b>
          </div>
          <select
            className="cfg-select"
            value={modelAssetId}
            onChange={(e) => setModelAssetId(e.target.value)}
          >
            <option value="">{t("agents.selectModel")}</option>
            {models.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
                {m.sub ? ` — ${m.sub}` : ""}
              </option>
            ))}
          </select>
          <div className="param-grid">
            <label className="param-row">
              <span>
                {t("agents.temperature")} <em>{temperature.toFixed(2)}</em>
              </span>
              <input
                type="range"
                min={0}
                max={2}
                step={0.05}
                value={temperature}
                onChange={(e) => setTemperature(Number(e.target.value))}
              />
            </label>
            <label className="param-row">
              <span>
                {t("agents.topP")} <em>{topP.toFixed(2)}</em>
              </span>
              <input
                type="range"
                min={0}
                max={1}
                step={0.05}
                value={topP}
                onChange={(e) => setTopP(Number(e.target.value))}
              />
            </label>
            <label className="param-row wide">
              <span>
                {t("agents.maxTokens")} <em>{maxTokens}</em>
              </span>
              <input
                type="range"
                min={256}
                max={8192}
                step={128}
                value={maxTokens}
                onChange={(e) => setMaxTokens(Number(e.target.value))}
              />
            </label>
            <label className="runtime-field">
              <span>{t("agents.topK")}</span>
              <input
                type="number"
                min={1}
                max={1000}
                value={topK}
                onChange={(e) => setTopK(Number(e.target.value))}
              />
            </label>
          </div>
        </div>

        <div className="config-section runtime-policy-card" hidden={activeTab !== "runtime"}>
          <div className="section-head runtime-policy-head">
            <span className="runtime-icon">⌁</span>
            <div>
              <b>{t("agents.runtimePolicy")}</b>
              <small>{t("agents.runtimePolicyHint")}</small>
            </div>
          </div>
          <div className="runtime-grid">
            <label className="runtime-field">
              <span>{t("agents.maxIters")}</span>
              <input type="number" min={1} max={100} value={maxIters} onChange={(e) => setMaxIters(Number(e.target.value))} />
            </label>
            <label className="runtime-field">
              <span>{t("agents.maxRetries")}</span>
              <input type="number" min={0} max={10} value={maxRetries} onChange={(e) => setMaxRetries(Number(e.target.value))} />
            </label>
            <label className="runtime-field">
              <span>{t("agents.modelTimeout")}</span>
              <input type="number" min={1} max={1800} value={modelTimeoutSeconds} onChange={(e) => setModelTimeoutSeconds(Number(e.target.value))} />
            </label>
            <label className="runtime-field">
              <span>{t("agents.toolTimeout")}</span>
              <input type="number" min={1} max={1800} value={toolTimeoutSeconds} onChange={(e) => setToolTimeoutSeconds(Number(e.target.value))} />
            </label>
            <label className="runtime-field">
              <span>{t("agents.maxContextTokens")}</span>
              <input type="number" min={1000} max={2000000} step={1000} value={maxContextTokens} onChange={(e) => setMaxContextTokens(Number(e.target.value))} />
            </label>
            <label className="runtime-field">
              <span>{t("agents.permissionMode")}</span>
              <select value={permissionMode} onChange={(e) => setPermissionMode(e.target.value as AgentItem["permissionMode"])}>
                <option value="DEFAULT">DEFAULT</option>
                <option value="EXPLORE">EXPLORE</option>
                <option value="ACCEPT_EDITS">ACCEPT_EDITS</option>
                <option value="DONT_ASK">DONT_ASK</option>
                <option value="BYPASS">BYPASS</option>
              </select>
            </label>
          </div>
          {permissionMode === "BYPASS" && <div className="runtime-warning">△ {t("agents.bypassWarning")}</div>}
          <div className="runtime-switches">
            <label><input type="checkbox" checked={parallelToolCalls} onChange={(e) => setParallelToolCalls(e.target.checked)} /><span>{t("agents.parallelToolCalls")}</span></label>
            <label><input type="checkbox" checked={compactionEnabled} onChange={(e) => setCompactionEnabled(e.target.checked)} /><span>{t("agents.compaction")}</span></label>
            <label><input type="checkbox" checked={toolResultEvictionEnabled} onChange={(e) => setToolResultEvictionEnabled(e.target.checked)} /><span>{t("agents.toolResultEviction")}</span></label>
            <label><input type="checkbox" checked={tracingEnabled} onChange={(e) => setTracingEnabled(e.target.checked)} /><span>{t("agents.tracing")}</span></label>
          </div>
        </div>

        <div className="config-section" hidden={activeTab !== "mcp"}>
          <div className="section-head">
            <b>{t("agents.mcpServers")}</b>
            <small>{t("agents.mcpHint")}</small>
          </div>
          <div className="binding-list">
            {mcpServers.length === 0 && (
              <small style={{ padding: 8 }}>{t("agents.noMcp")}</small>
            )}
            {mcpServers.map((m) => (
              <div key={m.id} className={`binding-item binding-card ${boundMcp.has(m.id) ? "selected" : ""}`}>
                <input
                  type="checkbox"
                  checked={boundMcp.has(m.id)}
                  onChange={() => {
                    const wasBound = boundMcp.has(m.id);
                    toggle(setBoundMcp, boundMcp, m.id);
                    if (wasBound) setMcpToolFilters((previous) => {
                      const updated = { ...previous };
                      delete updated[m.id];
                      return updated;
                    });
                  }}
                />
                <span className="meta">
                  <b>{m.name}</b>
                  {m.sub && <small>{m.sub}</small>}
                </span>
                {boundMcp.has(m.id) && (mcpTools[m.id] ?? []).length > 0 && (
                  <div className="tool-allowlist">
                    <small>{t("agents.toolAccess")}</small>
                    {(mcpTools[m.id] ?? []).map((tool) => {
                      const filter = mcpToolFilters[m.id];
                      const checked = !filter || filter.includes(tool);
                      return <label key={tool}><input type="checkbox" checked={checked} onChange={() => {
                        const all = mcpTools[m.id] ?? [];
                        const current = filter ?? all;
                        const next = checked ? current.filter((name) => name !== tool) : [...current, tool];
                        if (next.length === 0) {
                          const selectedServers = new Set(boundMcp);
                          selectedServers.delete(m.id);
                          setBoundMcp(selectedServers);
                        }
                        setMcpToolFilters((previous) => {
                          const updated = { ...previous };
                          if (next.length === 0 || next.length === all.length) delete updated[m.id];
                          else updated[m.id] = next;
                          return updated;
                        });
                      }} />{tool}</label>;
                    })}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>

        <div className="config-section" hidden={activeTab !== "skills"}>
          <div className="section-head">
            <b>{t("agents.skills")}</b>
            <small>{t("agents.skillsHint")}</small>
          </div>
          <div className="binding-list">
            {skills.length === 0 && (
              <small style={{ padding: 8 }}>{t("agents.noSkills")}</small>
            )}
            {skills.map((s) => (
              <label key={s.id} className="binding-item">
                <input
                  type="checkbox"
                  checked={boundSkills.has(s.id)}
                  onChange={() => toggle(setBoundSkills, boundSkills, s.id)}
                />
                <span className="meta">
                  <b>{s.name}</b>
                  {s.sub && <small>{s.sub}</small>}
                </span>
              </label>
            ))}
          </div>
        </div>

        <div className="config-section capability-card" hidden={activeTab !== "memory"}>
          <div className="capability-hero"><span>MEM</span><div><b>{t("agents.memoryTitle")}</b><small>{t("agents.memoryHint")}</small></div><label className="switch-line"><input type="checkbox" checked={memoryEnabled} onChange={(e) => { setMemoryEnabled(e.target.checked); if (e.target.checked && workspaceMode === "DISABLED") setWorkspaceMode("LOCAL_ROOTED"); }} />{t("agents.enabled")}</label></div>
          <div className="runtime-grid muted-when-disabled" aria-disabled={!memoryEnabled}>
            <label className="runtime-field"><span>{t("agents.memoryFlushMode")}</span><select disabled={!memoryEnabled} value={memoryFlushMode} onChange={(e) => setMemoryFlushMode(e.target.value as AgentItem["memoryFlushMode"])}><option value="ALWAYS">ALWAYS</option><option value="THROTTLED">THROTTLED</option><option value="NEVER">NEVER</option></select></label>
            <label className="runtime-field"><span>{t("agents.memoryFlushInterval")}</span><input disabled={!memoryEnabled || memoryFlushMode !== "THROTTLED"} type="number" min={1} max={1440} value={memoryFlushIntervalMinutes} onChange={(e) => setMemoryFlushIntervalMinutes(Number(e.target.value))} /></label>
            <label className="runtime-field"><span>{t("agents.memoryConsolidation")}</span><input disabled={!memoryEnabled} type="number" min={1} max={1440} value={memoryConsolidationIntervalMinutes} onChange={(e) => setMemoryConsolidationIntervalMinutes(Number(e.target.value))} /></label>
            <label className="runtime-field"><span>{t("agents.dailyRetention")}</span><input disabled={!memoryEnabled} type="number" min={1} max={3650} value={memoryDailyRetentionDays} onChange={(e) => setMemoryDailyRetentionDays(Number(e.target.value))} /></label>
            <label className="runtime-field"><span>{t("agents.sessionRetention")}</span><input disabled={!memoryEnabled} type="number" min={1} max={3650} value={memorySessionRetentionDays} onChange={(e) => setMemorySessionRetentionDays(Number(e.target.value))} /></label>
          </div>
          {memoryEnabled && <div className="info-strip">✓ {t("agents.memoryWorkspaceNotice")}</div>}
        </div>

        <div className="config-section capability-card" hidden={activeTab !== "workspace"}>
          <div className="capability-hero"><span>FS</span><div><b>{t("agents.workspaceTitle")}</b><small>{t("agents.workspaceHint")}</small></div></div>
          <div className="workspace-modes">
            {(["DISABLED", "LOCAL_ROOTED", "DOCKER_SANDBOX"] as const).map((mode) => <button key={mode} className={workspaceMode === mode ? "active" : ""} onClick={() => { setWorkspaceMode(mode); if (mode === "DISABLED") { setMemoryEnabled(false); setShellEnabled(false); } }}>{t(`agents.workspaceMode.${mode}`)}</button>)}
          </div>
          {workspaceMode !== "DISABLED" && <>
            <div className="runtime-grid">
              <label className="runtime-field"><span>{t("agents.isolationScope")}</span><select value={workspaceIsolationScope} onChange={(e) => setWorkspaceIsolationScope(e.target.value as AgentItem["workspaceIsolationScope"])}><option value="SESSION">SESSION</option><option value="USER">USER</option><option value="AGENT">AGENT</option><option value="GLOBAL">GLOBAL</option></select></label>
              {workspaceMode === "DOCKER_SANDBOX" && <><label className="runtime-field"><span>{t("agents.dockerImage")}</span><input value={dockerImage} onChange={(e) => setDockerImage(e.target.value)} placeholder="ubuntu:24.04" /></label><label className="runtime-field"><span>{t("agents.sandboxMemory")}</span><input type="number" min={128} max={32768} value={sandboxMemoryMb} onChange={(e) => setSandboxMemoryMb(Number(e.target.value))} /></label><label className="runtime-field"><span>{t("agents.sandboxCpu")}</span><input type="number" min={1} max={64} value={sandboxCpuCount} onChange={(e) => setSandboxCpuCount(Number(e.target.value))} /></label></>}
            </div>
            <div className="runtime-switches"><label><input type="checkbox" checked={workspaceContextEnabled} onChange={(e) => setWorkspaceContextEnabled(e.target.checked)} /><span>{t("agents.workspaceContext")}</span></label><label><input type="checkbox" checked={shellEnabled} onChange={(e) => setShellEnabled(e.target.checked)} /><span>{t("agents.shellTool")}</span></label></div>
            {shellEnabled && <div className="runtime-warning">△ {t("agents.shellWarning")}</div>}
          </>}
          <div className="info-strip">{t("agents.workspaceManagedPath")}</div>
        </div>

        {notice && (
          <div
            className={notice.ok ? "connection-result connection-result--success" : "skill-error"}
          >
            {notice.ok ? "✓" : "×"} {notice.text}
          </div>
        )}

        <div className="config-save-bar">
          <button className="ui-button" onClick={save} disabled={saving}>
            {saving ? t("agents.saving") : t("agents.saveConfig")}
          </button>
        </div>
      </section>
      </div>

      <div
        className="agent-panel-resizer"
        role="separator"
        aria-label={t("agents.resizePanels")}
        aria-orientation="vertical"
        tabIndex={0}
        onPointerDown={(event) => {
          event.currentTarget.setPointerCapture(event.pointerId);
          const move = (moveEvent: PointerEvent) => resizeWorkbench(moveEvent.clientX);
          const finish = () => {
            window.removeEventListener("pointermove", move);
            window.removeEventListener("pointerup", finish);
          };
          window.addEventListener("pointermove", move);
          window.addEventListener("pointerup", finish);
        }}
        onKeyDown={(event) => {
          if (event.key === "ArrowLeft") setConfigWidth((value) => Math.max(34, value - 2));
          if (event.key === "ArrowRight") setConfigWidth((value) => Math.min(72, value + 2));
        }}
      ><i /></div>

      {/* RIGHT: chat test panel */}
      <aside className="agent-chat-panel">
        <header>
          <b>{t("agents.debugChat")}</b>
          <button
            className="link-button"
            onClick={() => {
              if (sessionIdRef.current) {
                fetch(`/api/v1/agents/sessions/${sessionIdRef.current}`, {
                  method: "DELETE",
                }).catch(() => undefined);
                sessionIdRef.current = "";
              }
              setMessages(
                welcomeMessage
                  ? [{ role: "assistant", content: welcomeMessage }]
                  : [],
              );
            }}
            style={{ fontSize: 11 }}
          >
            ↻ {t("agents.newSession")}
          </button>
        </header>
        <div className="chat-thread" ref={threadRef}>
          {messages.length === 0 && (
            <div className="chat-empty">
              <i>◈</i>
              {t("agents.chatEmpty")}
            </div>
          )}
          {messages.map((m, i) => (
            <div
              key={i}
              className={`chat-bubble ${m.error ? "error" : m.role}`}
            >
              {m.content}
            </div>
          ))}
          {sending && <div className="chat-bubble assistant">···</div>}
        </div>
        <div className="chat-composer">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                void send();
              }
            }}
            placeholder={t("agents.typeMessage")}
          />
          <button
            className="ui-button send"
            onClick={send}
            disabled={sending || !input.trim()}
          >
            {t("agents.send")}
          </button>
        </div>
      </aside>
    </div>
  );
}
