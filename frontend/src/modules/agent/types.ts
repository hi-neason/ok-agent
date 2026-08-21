export type AgentTab =
  | "core"
  | "skills"
  | "mcp"
  | "workflows"
  | "knowledge"
  | "products"
  | "memory"
  | "workspace"
  | "runtime"
  | "subagents";

export const AGENT_TABS: AgentTab[] = [
  "core",
  "skills",
  "mcp",
  "workflows",
  "knowledge",
  "products",
  "memory",
  "workspace",
  "runtime",
  "subagents",
];

export type AgentItem = {
  id: string;
  agentKey: string;
  name: string;
  description: string;
  businessDomain: string;
  systemPrompt: string;
  welcomeMessage: string;
  modelAssetId: string | null;
  modelName: string | null;
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
  personaExtractEnabled: boolean;
  personaInjectionMode: "NONE" | "SELF_ONLY" | "GLOBAL";
  personaPromptTemplate: string;
  workspaceMode: "DISABLED" | "LOCAL_ROOTED" | "DOCKER_SANDBOX";
  workspaceIsolationScope: "SESSION" | "USER" | "AGENT" | "GLOBAL";
  workspaceContextEnabled: boolean;
  shellEnabled: boolean;
  dockerImage: string;
  sandboxMemoryMb: number;
  sandboxCpuCount: number;
  enabled: boolean;
  subagentsJson: string;
  createdAt: string;
  updatedAt: string;
  updatedBy: string | null;
};

export type Option = { id: string; name: string; sub?: string };

/**
 * A sub-agent reference on a router (main-sub) agent. Points at an existing AgentAsset by id;
 * the referenced agent runs with its OWN model/MCP/skills/systemPrompt. `intentKeys` lists the
 * intents this sub-agent claims (a sub-agent may handle many intents; one intent may not be
 * claimed by two sub-agents under the same router).
 */
export type AgentSubagentConfig = {
  agentId: string | null;
  intentKeys: string[];
};

export type ChatMessage = {
  role: "user" | "assistant";
  content: string;
  error?: boolean;
};

export type ValidationIssue = {
  field: string;
  code: string;
  message: string;
  tab: AgentTab;
};

export type ValidationCheck = {
  name: string;
  passed: boolean;
  detail: string;
};

export type ValidationResponse = {
  valid: boolean;
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
  checks: ValidationCheck[];
  durationMs: number;
};

export type AgentForm = {
  systemPrompt: string;
  welcomeMessage: string;
  modelAssetId: string;
  temperature: number;
  topP: number;
  topK: number;
  maxTokens: number;
  maxIters: number;
  modelTimeoutSeconds: number;
  toolTimeoutSeconds: number;
  maxRetries: number;
  permissionMode: AgentItem["permissionMode"];
  parallelToolCalls: boolean;
  compactionEnabled: boolean;
  maxContextTokens: number;
  toolResultEvictionEnabled: boolean;
  tracingEnabled: boolean;
  boundMcp: Set<string>;
  boundSkills: Set<string>;
  mcpToolFilters: Record<string, string[]>;
  memoryEnabled: boolean;
  memoryFlushMode: AgentItem["memoryFlushMode"];
  memoryFlushIntervalMinutes: number;
  memoryConsolidationIntervalMinutes: number;
  memoryDailyRetentionDays: number;
  memorySessionRetentionDays: number;
  personaExtractEnabled: boolean;
  personaInjectionMode: "NONE" | "SELF_ONLY" | "GLOBAL";
  personaPromptTemplate: string;
  workspaceMode: AgentItem["workspaceMode"];
  workspaceIsolationScope: AgentItem["workspaceIsolationScope"];
  workspaceContextEnabled: boolean;
  shellEnabled: boolean;
  dockerImage: string;
  sandboxMemoryMb: number;
  sandboxCpuCount: number;
  subagents: AgentSubagentConfig[];
};
