export type PersonaInjectionMode = "NONE" | "SELF_ONLY" | "GLOBAL";

export type Persona = {
  userId: string;
  agentId: string;
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
