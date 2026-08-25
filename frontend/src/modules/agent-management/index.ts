/** Public lazy page loaders for the enterprise Agent-management module. */
export const agentManagementPages = {
  agents: () => import("../agent/AgentRegistryPage").then((module) => ({ default: module.AgentRegistryPage })),
  agentConfig: () => import("../agent/AgentConfigPage").then((module) => ({ default: module.AgentConfigPage })),
  models: () => import("../model/ModelRegistryPage").then((module) => ({ default: module.ModelRegistryPage })),
  skills: () => import("../skill/SkillRegistryPage").then((module) => ({ default: module.SkillRegistryPage })),
  tools: () => import("../mcp/McpPage").then((module) => ({ default: module.McpPage })),
  knowledge: () => import("../knowledge/KnowledgeSourcesPage").then((module) => ({ default: module.KnowledgeSourcesPage })),
  products: () => import("../product/ProductPage").then((module) => ({ default: module.ProductPage })),
  workflows: () => import("../workflow/WorkflowSourcesPage").then((module) => ({ default: module.WorkflowSourcesPage })),
  releases: () => import("../release/ReleasePage").then((module) => ({ default: module.ReleasePage })),
  observability: () => import("../observe/ObservePage").then((module) => ({ default: module.ObservePage })),
  channels: () => import("../channel/ChannelPage").then((module) => ({ default: module.ChannelPage })),
  intents: () => import("../intent/IntentPage").then((module) => ({ default: module.IntentPage })),
  access: () => import("../auth/AccountManagementPage").then((module) => ({ default: module.AccountManagementPage })),
};
