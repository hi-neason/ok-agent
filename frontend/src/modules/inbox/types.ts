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

export type InboxDetail = {
  item: ConversationWorkItem;
  turns: DialogueTurn[];
};
