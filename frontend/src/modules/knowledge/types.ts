export type KnowledgeSourceType = "DIFY";

export type TestStatus = "SUCCESS" | "FAILED" | "UNSUPPORTED" | "UNTESTED";

export type KnowledgeSource = {
  id: string;
  sourceKey: string;
  name: string;
  sourceType: KnowledgeSourceType;
  baseUrl: string;
  enabled: boolean;
  hasApiKey: boolean;
  retrieveTimeoutSeconds: number;
  connectTimeoutSeconds: number;
  lastTestStatus: TestStatus;
  lastTestMessage: string | null;
  lastTestedAt: string | null;
  lastSyncedAt: string | null;
  knowledgeCount: number;
  updatedAt: string;
};

export type KnowledgeSourceDraft = {
  sourceKey: string;
  name: string;
  sourceType: KnowledgeSourceType;
  baseUrl: string;
  apiKey: string;
  retrieveTimeoutSeconds: number;
  connectTimeoutSeconds: number;
};

export type KnowledgeCatalogItem = {
  id: string;
  sourceId: string;
  sourceName: string;
  remoteKnowledgeId: string;
  name: string;
  active: boolean;
  tags: string[];
  remoteDescription: string | null;
  description: string;
  documentCount: number;
  wordCount: number;
  metadataStatus: "NEEDS_REVIEW" | "READY";
  updatedAt: string;
};

export type AgentKnowledgeBinding = {
  id: string;
  agentId: string;
  catalogItemId: string;
  remoteKnowledgeId: string;
  knowledgeName: string;
  sourceName: string;
  descriptionOverride: string | null;
  topK: number | null;
  scoreThreshold: number | null;
  enabled: boolean;
  metadataStatus: "NEEDS_REVIEW" | "READY";
  active: boolean;
  updatedAt: string;
};

export type AgentKnowledgeBindingDraft = {
  catalogItemId: string;
  descriptionOverride: string;
  topK: number | null;
  scoreThreshold: number | null;
};

export const emptySourceDraft = (): KnowledgeSourceDraft => ({
  sourceKey: "",
  name: "",
  sourceType: "DIFY",
  baseUrl: "https://api.dify.ai/v1",
  apiKey: "",
  retrieveTimeoutSeconds: 30,
  connectTimeoutSeconds: 10,
});

export const SOURCE_TYPE_LABELS: Record<KnowledgeSourceType, string> = {
  DIFY: "Dify",
};
