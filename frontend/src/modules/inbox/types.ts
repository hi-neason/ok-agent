import type { DialogueTurn } from "../observe/types";

export type WorkStatus =
  | "OPEN"
  | "WAITING_HUMAN"
  | "IN_PROGRESS"
  | "WAITING_CUSTOMER"
  | "RESOLVED"
  | "CLOSED";

export type WorkPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";

export type ConversationWorkItem = {
  sessionId: string;
  agentId: string | null;
  agentName: string | null;
  title: string;
  userId: string | null;
  customerName: string | null;
  status: WorkStatus;
  priority: WorkPriority;
  assigneeAccountId: string | null;
  assigneeName: string | null;
  handoffRequestedAt: string | null;
  assignedAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
  turnCount: number;
  version: number;
};

export type ConversationWorkItemPage = {
  content: ConversationWorkItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export type InboxOperator = {
  id: string;
  username: string;
  displayName: string;
  role: "ADMIN" | "EDITOR" | "VIEWER";
};

export type CustomerSentiment = "UNKNOWN" | "POSITIVE" | "NEUTRAL" | "NEGATIVE" | "MIXED";

export type ConversationOutcome = {
  sessionId: string;
  summary: string | null;
  customerNeed: string | null;
  intentLabel: string | null;
  productInterest: string | null;
  budget: string | null;
  purchaseTimeline: string | null;
  sentiment: CustomerSentiment;
  resolutionCode: string | null;
  nextAction: string | null;
  followUpAt: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
  version: number;
};

export type ConversationOutcomeDraft = Omit<
  ConversationOutcome,
  "sessionId" | "updatedBy" | "updatedAt" | "version"
>;

export type CustomerCaseType = "LEAD" | "TICKET";

export type CustomerCase = {
  id: string;
  type: CustomerCaseType;
  status: "NEW" | "OPEN";
  title: string;
  customerUserId: string | null;
  sourceSessionId: string;
  description: string | null;
  priority: WorkPriority;
  ownerAccountId: string | null;
  createdAt: string;
};

export type InboxDetail = {
  item: ConversationWorkItem;
  turns: DialogueTurn[];
};
