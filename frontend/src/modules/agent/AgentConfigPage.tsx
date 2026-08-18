import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  type AgentForm,
  type AgentItem,
  type AgentTab,
  type ChatMessage,
  type Option,
  type ValidationIssue,
  type ValidationResponse,
} from "./types";
import {
  deleteSession,
  loadAgent,
  loadMcpServers,
  loadMcpTools,
  loadModels,
  loadSkills,
  loadUsers,
  saveAgentConfig,
  sendChat,
  validateAgentConfig,
  type DebugUser,
} from "./api";
import { searchSessions, fetchTurns } from "../observe/api";
import type { DialogueSummary } from "../observe/types";
import { useDirtyFlag } from "./hooks/useDirtyFlag";
import { useTabRouting } from "./hooks/useTabRouting";
import { AgentConfigTabs } from "./components/AgentConfigTabs";
import { AgentDebugPanel } from "./components/AgentDebugPanel";
import { ResizableWorkbench } from "./components/ResizableWorkbench";
import { AgentPromptTab } from "./components/tabs/AgentPromptTab";
import { AgentSkillsTab } from "./components/tabs/AgentSkillsTab";
import { AgentMcpTab } from "./components/tabs/AgentMcpTab";
import { AgentWorkflowTab } from "./components/tabs/AgentWorkflowTab";
import { AgentKnowledgeTab } from "./components/tabs/AgentKnowledgeTab";
import { AgentMemoryTab } from "./components/tabs/AgentMemoryTab";
import { AgentWorkspaceTab } from "./components/tabs/AgentWorkspaceTab";
import { AgentRuntimeTab } from "./components/tabs/AgentRuntimeTab";
import { useConfirm } from "../shared";

function initialForm(agent: AgentItem): AgentForm {
  return {
    systemPrompt: agent.systemPrompt || "",
    welcomeMessage: agent.welcomeMessage || "",
    modelAssetId: agent.modelAssetId || "",
    temperature: agent.temperature ?? 0.7,
    topP: agent.topP ?? 1,
    topK: agent.topK ?? 40,
    maxTokens: agent.maxTokens ?? 2048,
    maxIters: agent.maxIters ?? 10,
    modelTimeoutSeconds: agent.modelTimeoutSeconds ?? 120,
    toolTimeoutSeconds: agent.toolTimeoutSeconds ?? 60,
    maxRetries: agent.maxRetries ?? 2,
    permissionMode: agent.permissionMode ?? "BYPASS",
    parallelToolCalls: agent.parallelToolCalls ?? true,
    compactionEnabled: agent.compactionEnabled ?? true,
    maxContextTokens: agent.maxContextTokens ?? 8000,
    toolResultEvictionEnabled: agent.toolResultEvictionEnabled ?? true,
    tracingEnabled: agent.tracingEnabled ?? true,
    boundMcp: new Set(agent.mcpServerIds),
    boundSkills: new Set(agent.skillIds),
    mcpToolFilters: agent.mcpToolFilters ?? {},
    memoryEnabled: agent.memoryEnabled ?? false,
    memoryFlushMode: agent.memoryFlushMode ?? "THROTTLED",
    memoryFlushIntervalMinutes: agent.memoryFlushIntervalMinutes ?? 30,
    memoryConsolidationIntervalMinutes: agent.memoryConsolidationIntervalMinutes ?? 30,
    memoryDailyRetentionDays: agent.memoryDailyRetentionDays ?? 90,
    memorySessionRetentionDays: agent.memorySessionRetentionDays ?? 180,
    personaExtractEnabled: agent.personaExtractEnabled ?? false,
    personaInjectionMode: agent.personaInjectionMode ?? "NONE",
    personaPromptTemplate: agent.personaPromptTemplate ?? "",
    workspaceMode: agent.workspaceMode ?? "DISABLED",
    workspaceIsolationScope: agent.workspaceIsolationScope ?? "SESSION",
    workspaceContextEnabled: agent.workspaceContextEnabled ?? true,
    shellEnabled: agent.shellEnabled ?? false,
    dockerImage: agent.dockerImage ?? "",
    sandboxMemoryMb: agent.sandboxMemoryMb ?? 512,
    sandboxCpuCount: agent.sandboxCpuCount ?? 1,
  };
}

export function AgentConfigPage({
  agentId,
  onBack,
}: {
  agentId: string;
  onBack: () => void;
}) {
  const { t } = useTranslation();
  const { confirm, Dialog } = useConfirm();
  const [agent, setAgent] = useState<AgentItem | null>(null);
  const [form, setForm] = useState<AgentForm | null>(null);
  const [models, setModels] = useState<Option[]>([]);
  const [mcpServers, setMcpServers] = useState<Option[]>([]);
  const [skills, setSkills] = useState<Option[]>([]);
  const [mcpTools, setMcpTools] = useState<Record<string, string[]>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [validating, setValidating] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);
  const [validation, setValidation] = useState<ValidationResponse | null>(null);

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const sessionIdRef = useRef<string>("");

  const [users, setUsers] = useState<DebugUser[]>([]);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [sessions, setSessions] = useState<DialogueSummary[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);

  const { dirty, markDirty, resetDirty } = useDirtyFlag();
  const { tab, navigateTab } = useTabRouting(agentId);

  const setField = useCallback(
    <K extends keyof AgentForm>(key: K, value: AgentForm[K]) => {
      setForm((prev) => (prev ? { ...prev, [key]: value } : prev));
      markDirty();
    },
    [markDirty],
  );

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      setLoading(true);
      try {
        const [agentRes, modelList, mcpList, skillList, userList] = await Promise.all([
          loadAgent(agentId),
          loadModels(),
          loadMcpServers(),
          loadSkills(),
          loadUsers(),
        ]);
        if (cancelled) return;
        setAgent(agentRes);
        setForm(initialForm(agentRes));
        setModels(modelList);
        setMcpServers(mcpList);
        setSkills(skillList);
        setUsers(userList);
        // 不默认选 DEBUG 用户；进入时按该 Agent 的所有调试会话查询，直接回放最近一条。
        setSelectedUserId(null);
        {
          const page = await searchSessions({ agentId, size: 50 });
          if (cancelled) return;
          setSessions(page.content);
          if (page.content.length > 0) {
            const first = page.content[0].sessionId;
            setSelectedSessionId(first);
            sessionIdRef.current = first;
            try {
              const turns = await fetchTurns(first);
              if (cancelled) return;
              const loaded: ChatMessage[] = turns.map((turn) => ({
                role: turn.role === "user" ? "user" : "assistant",
                content: turn.content,
                error: turn.role === "error",
              }));
              setMessages(loaded);
            } catch {
              if (!cancelled) setMessages([]);
            }
          } else if (agentRes.welcomeMessage) {
            setMessages([{ role: "assistant", content: agentRes.welcomeMessage }]);
          }
        }
        const tools = await loadMcpTools(mcpList);
        if (cancelled) return;
        setMcpTools(tools);
      } catch {
        if (!cancelled) setNotice({ ok: false, text: t("agents.loadFailed") });
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [agentId, t]);

  const errorsByField = useMemo(() => {
    const map: Record<string, ValidationIssue[]> = {};
    for (const e of validation?.errors ?? []) {
      (map[e.field] ??= []).push(e);
    }
    return map;
  }, [validation]);

  // 给调试预览下拉用：userId -> {displayName,username}，让会话下拉文字里能拼出"是谁的对话"
  const userMap = useMemo(() => {
    const map: Record<string, { displayName: string; username: string }> = {};
    for (const u of users) {
      map[u.userId] = { displayName: u.displayName, username: u.username };
    }
    return map;
  }, [users]);

  const errorsByTab = useMemo(() => {
    const counts: Record<AgentTab, number> = {
      core: 0,
      skills: 0,
      mcp: 0,
      workflows: 0,
      knowledge: 0,
      memory: 0,
      workspace: 0,
      runtime: 0,
    };
    for (const e of validation?.errors ?? []) {
      counts[e.tab] = (counts[e.tab] ?? 0) + 1;
    }
    return counts;
  }, [validation]);

  const handleTabClick = async (next: AgentTab) => {
    if (next === tab) return;
    if (dirty) {
      const ok = await confirm({ message: t("agents.unsavedLeaveConfirm") });
      if (!ok) return;
      // 用户确认离开：丢弃未保存的改动，回到干净状态，避免反复弹窗
      if (agent) setForm(initialForm(agent));
      resetDirty();
    }
    navigateTab(next);
  };

  const handleBack = async () => {
    if (dirty && !(await confirm({ message: t("agents.unsavedLeaveConfirm") }))) return;
    onBack();
  };

  const runValidation = async (): Promise<ValidationResponse | null> => {
    if (!form) return null;
    setValidating(true);
    setNotice(null);
    try {
      const result = await validateAgentConfig(agentId, form);
      setValidation(result);
      return result;
    } catch {
      setNotice({ ok: false, text: t("agents.validateFailed") });
      return null;
    } finally {
      setValidating(false);
    }
  };

  const focusField = (issue: ValidationIssue) => {
    if (issue.tab !== tab) navigateTab(issue.tab);
    window.setTimeout(() => {
      const el = document.querySelector(
        `[data-field="${issue.field}"]`,
      ) as HTMLElement | null;
      if (el) {
        el.scrollIntoView({ behavior: "smooth", block: "center" });
        el.focus?.();
      }
    }, 60);
  };

  const save = async () => {
    if (!form || !agent) return;
    if (
      agent &&
      permissionModeChanged(agent, form) &&
      !(await confirm({
        message: t("agents.permissionChangeConfirm", {
          from: agent.permissionMode,
          to: form.permissionMode,
          name: agent.name,
        }),
      }))
    ) {
      return;
    }
    if (
      agent &&
      workspaceOrShellChanged(agent, form) &&
      !(await confirm({
        message: t("agents.workspaceChangeConfirm", { name: agent.name }),
      }))
    ) {
      return;
    }

    const result = await runValidation();
    if (!result) return;
    if (!result.valid) {
      setNotice({ ok: false, text: t("agents.validationBlocked") });
      if (result.errors[0]) focusField(result.errors[0]);
      return;
    }
    if (
      result.warnings.length > 0 &&
      !(await confirm({
        message: t("agents.validationWarningsConfirm", {
          count: result.warnings.length,
        }),
      }))
    ) {
      return;
    }

    setSaving(true);
    setNotice(null);
    try {
      const updated = await saveAgentConfig(agentId, form);
      setAgent(updated);
      resetDirty();
      setValidation(null);
      setNotice({ ok: true, text: t("agents.configSaved") });
    } catch {
      setNotice({ ok: false, text: t("agents.saveFailed") });
    } finally {
      setSaving(false);
    }
  };

  const send = async () => {
    const text = input.trim();
    if (!text || sending || !form || !selectedUserId) return;
    setInput("");
    const history = [...messages, { role: "user" as const, content: text }];
    setMessages(history);
    setSending(true);
    try {
      const data = await sendChat(agentId, text, sessionIdRef.current || null, selectedUserId);
      if (data.sessionId) sessionIdRef.current = data.sessionId;
      setMessages([...history, { role: "assistant", content: data.reply }]);
      const page = await searchSessions({ agentId, userId: selectedUserId, size: 50 });
      setSessions(page.content);
    } catch (err) {
      const message = err instanceof Error ? err.message : t("agents.chatFailed");
      setMessages([...history, { role: "assistant", content: message, error: true }]);
    } finally {
      setSending(false);
    }
  };

  const newSession = () => {
    if (!selectedUserId) {
      setNotice({ ok: false, text: "请先选择调试用户" });
      return;
    }
    if (sessionIdRef.current) {
      void deleteSession(sessionIdRef.current);
      sessionIdRef.current = "";
    }
    setSelectedSessionId(null);
    setMessages(form?.welcomeMessage ? [{ role: "assistant", content: form.welcomeMessage }] : []);
  };

  const selectUser = async (userId: string) => {
    setSelectedUserId(userId);
    setSelectedSessionId(null);
    sessionIdRef.current = "";
    setMessages(form?.welcomeMessage ? [{ role: "assistant", content: form.welcomeMessage }] : []);
    try {
      const page = await searchSessions({ agentId, userId, size: 50 });
      setSessions(page.content);
      if (page.content.length > 0) {
        const first = page.content[0].sessionId;
        setSelectedSessionId(first);
        sessionIdRef.current = first;
        try {
          const turns = await fetchTurns(first);
          const loaded: ChatMessage[] = turns.map((turn) => ({
            role: turn.role === "user" ? "user" : "assistant",
            content: turn.content,
            error: turn.role === "error",
          }));
          setMessages(loaded);
        } catch {
          setMessages([]);
        }
      }
    } catch {
      setSessions([]);
    }
  };

  const selectSession = async (sessionId: string) => {
    if (!sessionId) {
      setSelectedSessionId(null);
      return;
    }
    setSelectedSessionId(sessionId);
    try {
      const turns = await fetchTurns(sessionId);
      const loaded: ChatMessage[] = turns.map((turn) => ({
        role: turn.role === "user" ? "user" : "assistant",
        content: turn.content,
        error: turn.role === "error",
      }));
      setMessages(loaded);
      sessionIdRef.current = sessionId;
    } catch {
      setNotice({ ok: false, text: "加载历史会话失败" });
    }
  };

  if (loading) return <div className="page-content">{t("agents.loading")}</div>;
  if (!agent || !form) return <div className="page-content">{t("agents.notFound")}</div>;

  return (
    <>
      <Dialog />
      <ResizableWorkbench
      left={
        <>
          <AgentConfigTabs
            active={tab}
            onSelect={handleTabClick}
            errorCounts={errorsByTab}
          />
          <section className="agent-dev-panel">
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <button className="link-button" onClick={handleBack} style={{ fontSize: 12 }}>
                ← {t("agents.back")}
              </button>
              <h2 style={{ margin: 0, fontSize: 18 }}>{agent.name}</h2>
              <code style={{ fontSize: 11, color: "#7a9abc" }}>{agent.agentKey}</code>
            </div>

            {tab === "core" && (
              <AgentPromptTab form={form} setField={setField} errorsByField={errorsByField} models={models} />
            )}
            {tab === "skills" && (
              <AgentSkillsTab form={form} setField={setField} errorsByField={errorsByField} skills={skills} />
            )}
            {tab === "mcp" && (
              <AgentMcpTab
                form={form}
                setField={setField}
                errorsByField={errorsByField}
                mcpServers={mcpServers}
                mcpTools={mcpTools}
              />
            )}
            {tab === "workflows" && <AgentWorkflowTab agentId={agentId} />}
            {tab === "knowledge" && <AgentKnowledgeTab agentId={agentId} />}
            {tab === "memory" && (
              <AgentMemoryTab form={form} setField={setField} errorsByField={errorsByField} />
            )}
            {tab === "workspace" && (
              <AgentWorkspaceTab form={form} setField={setField} errorsByField={errorsByField} />
            )}
            {tab === "runtime" && (
              <AgentRuntimeTab form={form} setField={setField} errorsByField={errorsByField} />
            )}

            {tab !== "workflows" && tab !== "knowledge" && (
              <>
                {validation &&
                  (validation.errors.length > 0 || validation.warnings.length > 0) && (
                    <ValidationSummary
                      validation={validation}
                      onSelectIssue={focusField}
                      t={t}
                    />
                  )}

                {notice && (
                  <div
                    className={
                      notice.ok
                        ? "connection-result connection-result--success"
                        : "skill-error"
                    }
                  >
                    {notice.ok ? "✓" : "×"} {notice.text}
                  </div>
                )}

                <div className="config-save-bar">
                  <button
                    className="ui-button quiet"
                    onClick={runValidation}
                    disabled={validating || saving}
                  >
                    {validating ? t("agents.validating") : t("agents.validate")}
                  </button>
                  <button className="ui-button" onClick={save} disabled={saving}>
                    {saving ? t("agents.saving") : t("agents.saveConfig")}
                  </button>
                  {dirty && <span className="dirty-flag">{t("agents.unsaved")}</span>}
                </div>
              </>
            )}
          </section>
        </>
      }
      right={
        <AgentDebugPanel
          messages={messages}
          input={input}
          sending={sending}
          onInputChange={setInput}
          onSend={send}
          onNewSession={newSession}
          users={users}
          selectedUserId={selectedUserId}
          onSelectUser={selectUser}
          sessions={sessions}
          selectedSessionId={selectedSessionId}
          onSelectSession={selectSession}
          userMap={userMap}
        />
      }
    />
    </>
  );
}

function ValidationSummary({
  validation,
  onSelectIssue,
  t,
}: {
  validation: ValidationResponse;
  onSelectIssue: (issue: ValidationIssue) => void;
  t: (key: string, opts?: Record<string, unknown>) => string;
}) {
  return (
    <div className="validation-summary">
      {validation.errors.length > 0 && (
        <div className="validation-group validation-group--error">
          <b>{t("agents.validationErrors", { count: validation.errors.length })}</b>
          <ul>
            {validation.errors.map((e, i) => (
              <li key={i}>
                <button className="link-button" onClick={() => onSelectIssue(e)}>
                  {t(`agents.validation.${e.code}`, { defaultValue: e.message })}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
      {validation.warnings.length > 0 && (
        <div className="validation-group validation-group--warning">
          <b>{t("agents.validationWarnings", { count: validation.warnings.length })}</b>
          <ul>
            {validation.warnings.map((w, i) => (
              <li key={i}>
                <button className="link-button" onClick={() => onSelectIssue(w)}>
                  {t(`agents.validation.${w.code}`, { defaultValue: w.message })}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function permissionModeChanged(agent: AgentItem, form: AgentForm): boolean {
  return form.permissionMode !== agent.permissionMode;
}

function workspaceOrShellChanged(agent: AgentItem, form: AgentForm): boolean {
  return (
    form.workspaceMode !== agent.workspaceMode || form.shellEnabled !== agent.shellEnabled
  );
}
