export type DialogueSummary = {
  sessionId: string;
  agentId: string | null;
  agentName: string | null;
  title: string;
  userId: string | null;
  createdAt: string;
  updatedAt: string;
  turnCount: number;
};

export type DialogueTurn = {
  id: number;
  sessionId: string;
  seq: number;
  role: "user" | "assistant" | "error" | string;
  content: string;
  model: string | null;
  latencyMs: number | null;
  tokenUsage: number | null;
  traceId: string | null;
  createdAt: string;
};

export type SpanType =
  | "AGENT"
  | "MODEL"
  | "MCP"
  | "SKILL"
  | "WORKFLOW"
  | "RAG"
  | "TOOL";
export type SpanStatus = "OK" | "ERROR" | "CANCELLED";

export type TraceSpan = {
  spanId: string;
  parentSpanId: string | null;
  type: SpanType | string;
  name: string;
  startUs: number;
  endUs: number;
  durationUs: number;
  status: SpanStatus | string;
  attributes: string | null;
  input: string | null;
  output: string | null;
};

export type SessionQuery = {
  sessionId?: string;
  userId?: string;
  agentId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
};

export type SessionPage = {
  content: DialogueSummary[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};
