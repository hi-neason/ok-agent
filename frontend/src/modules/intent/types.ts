export type IntentDto = {
  id: string;
  parentId: string | null;
  intentKey: string;
  name: string;
  description: string;
  examples: string[];
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
};

export type IntentNode = { node: IntentDto; children: IntentNode[] };

export type CreateIntentRequest = {
  intentKey: string;
  name: string;
  parentId: string | null;
  description: string;
  examples: string[];
  sortOrder: number;
};

export type UpdateIntentRequest = {
  name: string;
  parentId: string | null;
  description: string;
  examples: string[];
  sortOrder: number;
};
