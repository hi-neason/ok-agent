export type McpServer = {
  id: string;
  serverKey: string;
  name: string;
  description: string;
  transport: "STREAMABLE_HTTP" | "SSE" | "STDIO";
  serverUrl: string;
  command: string;
  arguments: string[];
  queryParameters: Record<string, string>;
  configuredHeaderNames: string[];
  configuredEnvironmentNames: string[];
  enabled: boolean;
  requestTimeoutSeconds: number;
  initializationTimeoutSeconds: number;
  lastTestStatus: string;
  lastTestedAt?: string;
  toolCount: number;
  updatedAt: string;
};

export type McpTool = {
  name: string;
  description: string;
  inputSchemaJson: string;
  discoveredAt: string;
};

export type McpDraft = {
  serverKey: string;
  name: string;
  description: string;
  transport: McpServer["transport"];
  serverUrl: string;
  command: string;
  argumentsText: string;
  headersText: string;
  environmentText: string;
  queryParametersText: string;
  requestTimeoutSeconds: number;
  initializationTimeoutSeconds: number;
};

export const emptyMcpDraft: McpDraft = {
  serverKey: "",
  name: "",
  description: "",
  transport: "STREAMABLE_HTTP",
  serverUrl: "",
  command: "",
  argumentsText: "",
  headersText: "",
  environmentText: "",
  queryParametersText: "",
  requestTimeoutSeconds: 15,
  initializationTimeoutSeconds: 10,
};

export const MCP_JSON_INDENT = 6;

export const isJsonObject = (
  value: unknown,
): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

export const stringMap = (value: unknown): Record<string, string> => {
  if (!isJsonObject(value)) return {};
  return Object.fromEntries(
    Object.entries(value).filter(
      (entry): entry is [string, string] => typeof entry[1] === "string",
    ),
  );
};

export const mcpDraftToJson = (draft: McpDraft) => {
  const key = draft.serverKey.trim() || "my-mcp-server";
  const connection =
    draft.transport === "STDIO"
      ? {
          command: draft.command,
          args: draft.argumentsText.split("\n").filter(Boolean),
          env: draft.environmentText.trim()
            ? JSON.parse(draft.environmentText)
            : {},
        }
      : {
          type: draft.transport === "SSE" ? "sse" : "streamable-http",
          url: draft.serverUrl,
          headers: draft.headersText.trim()
            ? JSON.parse(draft.headersText)
            : {},
          queryParameters: draft.queryParametersText.trim()
            ? JSON.parse(draft.queryParametersText)
            : {},
        };
  return JSON.stringify(
    {
      mcpServers: {
        [key]: {
          name: draft.name || key,
          description: draft.description,
          ...connection,
          requestTimeoutSeconds: draft.requestTimeoutSeconds,
          initializationTimeoutSeconds: draft.initializationTimeoutSeconds,
        },
      },
    },
    null,
    MCP_JSON_INDENT,
  );
};
