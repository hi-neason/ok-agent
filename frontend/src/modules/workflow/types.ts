export type WorkflowSourceType = "DIFY";

export type TestStatus = "OK" | "FAILED" | "UNSUPPORTED" | "UNKNOWN";

export type WorkflowSource = {
  id: string;
  sourceKey: string;
  name: string;
  sourceType: WorkflowSourceType;
  baseUrl: string;
  enabled: boolean;
  hasApiKey: boolean;
  executeTimeoutSeconds: number;
  connectTimeoutSeconds: number;
  lastTestStatus: TestStatus;
  lastTestMessage: string | null;
  lastTestedAt: string | null;
  lastSyncedAt: string | null;
  workflowCount: number;
  updatedAt: string;
};

export type WorkflowSourceDraft = {
  sourceKey: string;
  name: string;
  sourceType: WorkflowSourceType;
  baseUrl: string;
  apiKey: string;
  executeTimeoutSeconds: number;
  connectTimeoutSeconds: number;
};

export type WorkflowCatalogItem = {
  id: string;
  sourceId: string;
  sourceName: string;
  remoteWorkflowId: string;
  name: string;
  remoteMode: string | null;
  active: boolean;
  tags: string[];
  remoteDescription: string | null;
  description: string;
  inputSchemaJson: string | null;
  metadataStatus: "NEEDS_REVIEW" | "READY";
  updatedAt: string;
};

export type AgentWorkflowBinding = {
  id: string;
  agentId: string;
  catalogItemId: string;
  remoteWorkflowId: string;
  workflowName: string;
  sourceName: string;
  descriptionOverride: string | null;
  parameterDefaultsJson: string | null;
  enabled: boolean;
  metadataStatus: "NEEDS_REVIEW" | "READY";
  active: boolean;
  updatedAt: string;
};

export type AgentWorkflowBindingDraft = {
  catalogItemId: string;
  descriptionOverride: string;
  parameterDefaults: string;
};

export const emptySourceDraft = (): WorkflowSourceDraft => ({
  sourceKey: "",
  name: "",
  sourceType: "DIFY",
  baseUrl: "https://api.dify.ai/v1",
  apiKey: "",
  executeTimeoutSeconds: 90,
  connectTimeoutSeconds: 10,
});

export const SOURCE_TYPE_LABELS: Record<WorkflowSourceType, string> = {
  DIFY: "Dify",
};
