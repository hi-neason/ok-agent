export type Persona = {
  userId: string;
  tags: string[];
  preferences: Record<string, string>;
  facts: string;
  summary: string;
  memory: string;
  updatedAt: string;
};

export type UpsertPersona = {
  tags?: string[];
  preferences?: Record<string, string>;
  facts?: string;
  summary?: string;
};
