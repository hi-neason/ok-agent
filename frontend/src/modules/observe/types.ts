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
  createdAt: string;
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
