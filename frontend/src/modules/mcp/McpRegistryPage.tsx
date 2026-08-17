import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Button, PageHeader, Toggle, useConfirm } from "../shared";
import {
  deleteServer,
  fetchServers,
  inspectServerById,
  inspectServerByDraft,
  saveServer,
  setServerEnabled,
} from "./api";
import {
  MCP_JSON_INDENT,
  emptyMcpDraft,
  isJsonObject,
  mcpDraftToJson,
  stringMap,
  type McpDraft,
  type McpServer,
  type McpTool,
} from "./types";

export function McpRegistryPage() {
  const { t } = useTranslation();
  const { confirm, Dialog } = useConfirm();
  const [servers, setServers] = useState<McpServer[]>([]);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<McpServer | null | "new">(null);
  const [draft, setDraft] = useState<McpDraft>(emptyMcpDraft);
  const [tools, setTools] = useState<McpTool[]>([]);
  const [selectedTool, setSelectedTool] = useState<McpTool | null>(null);
  const [tab, setTab] = useState<"config" | "tools">("config");
  const [configMode, setConfigMode] = useState<"form" | "json">("form");
  const [jsonConfig, setJsonConfig] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(
    null,
  );
  const slugifyMcpKey = (value: string) =>
    value
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "");
  const load = async () => {
    try {
      setServers(await fetchServers());
    } catch {
      setNotice({ ok: false, text: t("mcp.loadFailed") });
    }
  };
  useEffect(() => {
    void load();
  }, []);
  const open = (server?: McpServer) => {
    const nextDraft: McpDraft = server
      ? {
          serverKey: server.serverKey,
          name: server.name,
          description: server.description,
          transport: server.transport,
          serverUrl: server.serverUrl ?? "",
          command: server.command ?? "",
          argumentsText: (server.arguments ?? []).join("\n"),
          headersText: "",
          environmentText: "",
          queryParametersText: JSON.stringify(
            server.queryParameters ?? {},
            null,
            2,
          ),
          requestTimeoutSeconds: server.requestTimeoutSeconds,
          initializationTimeoutSeconds: server.initializationTimeoutSeconds,
        }
      : { ...emptyMcpDraft };
    setEditing(server ?? "new");
    setDraft(nextDraft);
    setJsonConfig(mcpDraftToJson(nextDraft));
    setConfigMode("form");
    setTools([]);
    setSelectedTool(null);
    setTab("config");
    setNotice(null);
  };
  const parseJsonDraft = (): McpDraft => {
    const root: unknown = JSON.parse(jsonConfig);
    if (!isJsonObject(root)) throw new Error(t("mcp.jsonObjectRequired"));
    const container = isJsonObject(root.mcpServers) ? root.mcpServers : root;
    const entries = Object.entries(container);
    if (entries.length !== 1) throw new Error(t("mcp.singleServerRequired"));
    const [serverKey, rawConfig] = entries[0];
    if (!isJsonObject(rawConfig)) throw new Error(t("mcp.invalidServerConfig"));
    const transportValue =
      typeof rawConfig.type === "string"
        ? rawConfig.type
        : typeof rawConfig.transport === "string"
          ? rawConfig.transport
          : "";
    const type = transportValue.toLowerCase();
    const transport: McpServer["transport"] =
      typeof rawConfig.command === "string"
        ? "STDIO"
        : type === "sse"
          ? "SSE"
          : "STREAMABLE_HTTP";
    const args = Array.isArray(rawConfig.args)
      ? rawConfig.args.filter(
          (value): value is string => typeof value === "string",
        )
      : [];
    return {
      serverKey,
      name: typeof rawConfig.name === "string" ? rawConfig.name : serverKey,
      description:
        typeof rawConfig.description === "string" ? rawConfig.description : "",
      transport,
      serverUrl: typeof rawConfig.url === "string" ? rawConfig.url : "",
      command: typeof rawConfig.command === "string" ? rawConfig.command : "",
      argumentsText: args.join("\n"),
      headersText: JSON.stringify(stringMap(rawConfig.headers), null, 2),
      environmentText: JSON.stringify(stringMap(rawConfig.env), null, 2),
      queryParametersText: JSON.stringify(
        stringMap(rawConfig.queryParameters),
        null,
        2,
      ),
      requestTimeoutSeconds:
        typeof rawConfig.requestTimeoutSeconds === "number"
          ? rawConfig.requestTimeoutSeconds
          : 15,
      initializationTimeoutSeconds:
        typeof rawConfig.initializationTimeoutSeconds === "number"
          ? rawConfig.initializationTimeoutSeconds
          : 10,
    };
  };
  const currentDraft = () => (configMode === "json" ? parseJsonDraft() : draft);
  const formatJsonConfig = () => {
    try {
      const parsed: unknown = JSON.parse(jsonConfig);
      setJsonConfig(JSON.stringify(parsed, null, MCP_JSON_INDENT));
      setNotice({ ok: true, text: t("mcp.jsonFormatted") });
    } catch {
      setNotice({ ok: false, text: t("mcp.jsonFormatFailed") });
    }
  };
  const validateDraft = (value: McpDraft) => {
    if (!value.name.trim()) return t("mcp.nameRequired");
    if (!value.serverKey.trim()) return t("mcp.serverKeyRequired");
    if (value.transport === "STDIO" && !value.command.trim())
      return t("mcp.commandRequired");
    if (value.transport !== "STDIO" && !value.serverUrl.trim())
      return t("mcp.serverUrlRequired");
    try {
      if (value.headersText.trim()) JSON.parse(value.headersText);
      if (value.environmentText.trim()) JSON.parse(value.environmentText);
      if (value.queryParametersText.trim())
        JSON.parse(value.queryParametersText);
    } catch {
      return t("mcp.invalidJson");
    }
    return null;
  };
  const payload = (value: McpDraft) => ({
    serverKey: value.serverKey.trim(),
    name: value.name.trim(),
    description: value.description,
    transport: value.transport,
    serverUrl: value.serverUrl.trim() || null,
    command: value.command.trim() || null,
    arguments: value.argumentsText
      .split("\n")
      .map((v) => v.trim())
      .filter(Boolean),
    headers: value.headersText.trim() ? JSON.parse(value.headersText) : {},
    environment: value.environmentText.trim()
      ? JSON.parse(value.environmentText)
      : {},
    queryParameters: value.queryParametersText.trim()
      ? JSON.parse(value.queryParametersText)
      : {},
    requestTimeoutSeconds: value.requestTimeoutSeconds,
    initializationTimeoutSeconds: value.initializationTimeoutSeconds,
  });
  const save = async () => {
    let value: McpDraft;
    try {
      value = currentDraft();
    } catch (error) {
      setNotice({
        ok: false,
        text: error instanceof Error ? error.message : t("mcp.invalidJson"),
      });
      return;
    }
    const validation = validateDraft(value);
    if (validation) {
      setNotice({ ok: false, text: validation });
      return;
    }
    setBusy(true);
    setNotice(null);
    try {
      const isNew = editing === "new";
      const saved = await saveServer(
        payload(value),
        isNew,
        isNew ? undefined : (editing as McpServer).id,
      );
      setEditing(saved);
      if (isNew) {
        try {
          const inspection = await inspectServerById(saved.id);
          setTools(inspection.tools);
          setSelectedTool(inspection.tools[0] ?? null);
          setNotice({
            ok: true,
            text: t("mcp.savedAndConnected", {
              count: inspection.tools.length,
            }),
          });
        } catch {
          setNotice({ ok: false, text: t("mcp.savedButConnectionFailed") });
        }
      } else {
        setNotice({ ok: true, text: t("mcp.saved") });
      }
      await load();
    } catch {
      setNotice({ ok: false, text: t("mcp.saveFailed") });
    } finally {
      setBusy(false);
    }
  };
  const inspect = async () => {
    let value: McpDraft;
    try {
      value = currentDraft();
    } catch (error) {
      setNotice({
        ok: false,
        text: error instanceof Error ? error.message : t("mcp.invalidJson"),
      });
      return;
    }
    const validation = validateDraft(value);
    if (validation) {
      setNotice({ ok: false, text: validation });
      return;
    }
    setBusy(true);
    setNotice(null);
    try {
      const saved = editing !== "new" && editing;
      const inspection = saved
        ? await inspectServerById(saved.id)
        : await inspectServerByDraft(payload(value));
      setTools(inspection.tools);
      setSelectedTool(inspection.tools[0] ?? null);
      setTab("tools");
      setNotice({
        ok: true,
        text: t("mcp.connectionSucceeded", { count: inspection.tools.length }),
      });
      if (saved) await load();
    } catch (error) {
      setNotice({
        ok: false,
        text:
          error instanceof Error && error.message
            ? error.message
            : t("mcp.connectionFailed"),
      });
    } finally {
      setBusy(false);
    }
  };
  const remove = async (server: McpServer) => {
    if (!(await confirm({ message: t("mcp.deleteConfirm", { name: server.name }), dangerous: true }))) return;
    await deleteServer(server.id);
    await load();
  };
  const toggle = async (server: McpServer) => {
    await setServerEnabled(server.id, !server.enabled);
    await load();
  };
  const visible = servers.filter((s) =>
    `${s.name} ${s.serverKey} ${s.serverUrl}`
      .toLowerCase()
      .includes(search.toLowerCase()),
  );
  return (
    <>
      <Dialog />
      <PageHeader
        kicker="MCP SERVER / REGISTRY"
        title={t("mcp.title")}
        description={t("mcp.description")}
        action={<Button onClick={() => open()}>＋ {t("mcp.register")}</Button>}
      />
      <div className="mcp-toolbar">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t("mcp.search")}
        />
        <span>{servers.length} MCP Servers</span>
      </div>
      <div className="mcp-table">
        <div className="mcp-row head">
          <span>{t("mcp.server")}</span>
          <span>{t("mcp.transport")}</span>
          <span>{t("mcp.tools")}</span>
          <span>{t("mcp.lastTest")}</span>
          <span>{t("mcp.status")}</span>
          <span>{t("mcp.actions")}</span>
        </div>
        {visible.map((server) => (
          <div className="mcp-row" key={server.id}>
            <span className="mcp-name">
              <i>⌘</i>
              <b>{server.name}</b>
              <small>{server.serverKey}</small>
            </span>
            <span>
              <code>{server.transport.replace("STREAMABLE_", "")}</code>
              <small>{server.serverUrl || server.command}</small>
            </span>
            <span>{t("mcp.toolCount", { count: server.toolCount })}</span>
            <span
              className={`test-state ${server.lastTestStatus.toLowerCase()}`}
            >
              {t(`mcp.testStatus.${server.lastTestStatus.toLowerCase()}`)}
            </span>
            <span>
              <Toggle on={server.enabled} setOn={() => void toggle(server)} />
            </span>
            <span className="row-actions">
              <button
                onClick={() => {
                  window.location.href = `/mcp/${server.id}/debug`;
                }}
              >
                {t("mcp.debug")}
              </button>
              <button onClick={() => open(server)}>{t("mcp.edit")}</button>
              <button className="danger" onClick={() => void remove(server)}>
                {t("mcp.delete")}
              </button>
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="mcp-empty">
            ⌘<b>{t("mcp.empty")}</b>
          </div>
        )}
      </div>
      {editing &&
        createPortal(
          <div
            className="model-modal-mask"
            onMouseDown={() => setEditing(null)}
          >
            <div
              className="mcp-inspector"
              role="dialog"
              onMouseDown={(e) => e.stopPropagation()}
            >
              <header>
                <div>
                  <p className="kicker">
                    MCP INSPECTOR / {editing === "new" ? "REGISTER" : "EDIT"}
                  </p>
                  <h2>
                    {editing === "new" ? t("mcp.register") : editing.name}
                  </h2>
                </div>
                <button
                  className="link-button"
                  onClick={() => setEditing(null)}
                >
                  {t("mcp.close")} ×
                </button>
              </header>
              <nav>
                <button
                  className={tab === "config" ? "active" : ""}
                  onClick={() => setTab("config")}
                >
                  01 {t("mcp.connectionConfig")}
                </button>
                <button
                  className={tab === "tools" ? "active" : ""}
                  onClick={() => setTab("tools")}
                >
                  02 {t("mcp.toolDiscovery")} <em>{tools.length}</em>
                </button>
              </nav>
              {tab === "config" ? (
                <>
                  <div className="mcp-config-mode">
                    <span>{t("mcp.configurationMode")}</span>
                    <div>
                      <button
                        className={configMode === "form" ? "active" : ""}
                        onClick={() => {
                          if (configMode === "json") {
                            try {
                              const parsed = parseJsonDraft();
                              setDraft(parsed);
                              setNotice(null);
                            } catch (error) {
                              setNotice({
                                ok: false,
                                text:
                                  error instanceof Error
                                    ? error.message
                                    : t("mcp.invalidJson"),
                              });
                              return;
                            }
                          }
                          setConfigMode("form");
                        }}
                      >
                        ◫ {t("mcp.formMode")}
                      </button>
                      <button
                        className={configMode === "json" ? "active" : ""}
                        onClick={() => {
                          try {
                            setJsonConfig(mcpDraftToJson(draft));
                            setConfigMode("json");
                            setNotice(null);
                          } catch {
                            setNotice({
                              ok: false,
                              text: t("mcp.invalidJson"),
                            });
                          }
                        }}
                      >
                        {"{}"} {t("mcp.jsonMode")}
                      </button>
                    </div>
                  </div>
                  {configMode === "form" ? (
                    <div className="mcp-form">
                      <label>
                        <span>{t("mcp.name")}</span>
                        <input
                          value={draft.name}
                          onChange={(e) => {
                            const previousSlug = slugifyMcpKey(draft.name);
                            const name = e.target.value;
                            setDraft({
                              ...draft,
                              name,
                              serverKey:
                                !draft.serverKey ||
                                draft.serverKey === previousSlug
                                  ? slugifyMcpKey(name)
                                  : draft.serverKey,
                            });
                          }}
                        />
                      </label>
                      <label>
                        <span>
                          SERVER_KEY{" "}
                          <small>· {t("mcp.serverKeyShortHint")}</small>
                        </span>
                        <input
                          value={draft.serverKey}
                          placeholder="local-mcp"
                          onChange={(e) =>
                            setDraft({ ...draft, serverKey: e.target.value })
                          }
                        />
                      </label>
                      <label className="wide">
                        <span>{t("mcp.descriptionLabel")}</span>
                        <input
                          value={draft.description}
                          onChange={(e) =>
                            setDraft({ ...draft, description: e.target.value })
                          }
                        />
                      </label>
                      <label>
                        <span>{t("mcp.transport")}</span>
                        <select
                          value={draft.transport}
                          onChange={(e) =>
                            setDraft({
                              ...draft,
                              transport: e.target
                                .value as McpServer["transport"],
                            })
                          }
                        >
                          <option value="STREAMABLE_HTTP">
                            Streamable HTTP
                          </option>
                          <option value="SSE">SSE</option>
                          <option value="STDIO">STDIO</option>
                        </select>
                      </label>
                      {draft.transport === "STDIO" ? (
                        <>
                          <label>
                            <span>COMMAND</span>
                            <input
                              value={draft.command}
                              onChange={(e) =>
                                setDraft({ ...draft, command: e.target.value })
                              }
                            />
                          </label>
                          <label className="wide">
                            <span>ARGUMENTS · {t("mcp.onePerLine")}</span>
                            <textarea
                              value={draft.argumentsText}
                              onChange={(e) =>
                                setDraft({
                                  ...draft,
                                  argumentsText: e.target.value,
                                })
                              }
                            />
                          </label>
                          <label className="wide">
                            <span>ENVIRONMENT · JSON</span>
                            <textarea
                              value={draft.environmentText}
                              placeholder={
                                editing !== "new" &&
                                editing.configuredEnvironmentNames.length
                                  ? t("mcp.secretConfigured", {
                                      keys: editing.configuredEnvironmentNames.join(
                                        ", ",
                                      ),
                                    })
                                  : '{\n  "API_KEY": "..."\n}'
                              }
                              onChange={(e) =>
                                setDraft({
                                  ...draft,
                                  environmentText: e.target.value,
                                })
                              }
                            />
                          </label>
                        </>
                      ) : (
                        <>
                          <label>
                            <span>
                              SERVER_URL <b className="field-required">*</b>{" "}
                              <small>
                                ·{" "}
                                {draft.transport === "SSE"
                                  ? "/api/v1/sse"
                                  : "/api/v1/mcp"}
                              </small>
                            </span>
                            <input
                              value={draft.serverUrl}
                              placeholder={t("mcp.serverUrlPlaceholder")}
                              onChange={(e) =>
                                setDraft({
                                  ...draft,
                                  serverUrl: e.target.value,
                                })
                              }
                            />
                          </label>
                          <label className="wide">
                            <span>
                              HEADERS · JSON{" "}
                              <small>({t("mcp.optional")})</small>
                            </span>
                            <textarea
                              value={draft.headersText}
                              placeholder={
                                editing !== "new" &&
                                editing.configuredHeaderNames.length
                                  ? t("mcp.secretConfigured", {
                                      keys: editing.configuredHeaderNames.join(
                                        ", ",
                                      ),
                                    })
                                  : t("mcp.headersPlaceholder")
                              }
                              onChange={(e) =>
                                setDraft({
                                  ...draft,
                                  headersText: e.target.value,
                                })
                              }
                            />
                          </label>
                          <label className="wide">
                            <span>
                              QUERY PARAMETERS · JSON{" "}
                              <small>({t("mcp.optional")})</small>
                            </span>
                            <textarea
                              value={draft.queryParametersText}
                              onChange={(e) =>
                                setDraft({
                                  ...draft,
                                  queryParametersText: e.target.value,
                                })
                              }
                            />
                          </label>
                        </>
                      )}
                      <label>
                        <span>{t("mcp.requestTimeout")}</span>
                        <input
                          type="number"
                          value={draft.requestTimeoutSeconds}
                          onChange={(e) =>
                            setDraft({
                              ...draft,
                              requestTimeoutSeconds: +e.target.value,
                            })
                          }
                        />
                      </label>
                      <label>
                        <span>{t("mcp.initTimeout")}</span>
                        <input
                          type="number"
                          value={draft.initializationTimeoutSeconds}
                          onChange={(e) =>
                            setDraft({
                              ...draft,
                              initializationTimeoutSeconds: +e.target.value,
                            })
                          }
                        />
                      </label>
                    </div>
                  ) : (
                    <div className="mcp-json-config">
                      <div className="mcp-json-head">
                        <div>
                          <p className="kicker">SINGLE MCP SERVER / JSON</p>
                          <b>{t("mcp.jsonEditorTitle")}</b>
                          <small>{t("mcp.jsonEditorHint")}</small>
                        </div>
                        <button type="button" onClick={formatJsonConfig}>
                          ✦ {t("mcp.formatJson")}
                        </button>
                      </div>
                      <textarea
                        value={jsonConfig}
                        onChange={(event) => setJsonConfig(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key !== "Tab") return;
                          event.preventDefault();
                          const input = event.currentTarget;
                          const start = input.selectionStart;
                          const end = input.selectionEnd;
                          const indentation = " ".repeat(MCP_JSON_INDENT);
                          setJsonConfig(
                            `${jsonConfig.slice(0, start)}${indentation}${jsonConfig.slice(end)}`,
                          );
                          requestAnimationFrame(() => {
                            input.selectionStart = input.selectionEnd =
                              start + MCP_JSON_INDENT;
                          });
                        }}
                        spellCheck={false}
                        aria-label={t("mcp.jsonEditorTitle")}
                      />
                    </div>
                  )}
                </>
              ) : (
                <div className="mcp-tool-browser">
                  <aside>
                    <div>
                      {t("mcp.discoveredTools")} <b>{tools.length}</b>
                    </div>
                    {tools.map((tool) => (
                      <button
                        className={
                          selectedTool?.name === tool.name ? "selected" : ""
                        }
                        onClick={() => setSelectedTool(tool)}
                        key={tool.name}
                      >
                        <i>⚡</i>
                        <span>
                          <b>{tool.name}</b>
                          <small>
                            {tool.description || t("mcp.noDescription")}
                          </small>
                        </span>
                      </button>
                    ))}
                  </aside>
                  <main>
                    {selectedTool ? (
                      <>
                        <div>
                          <p className="kicker">TOOL SCHEMA</p>
                          <h3>{selectedTool.name}</h3>
                          <p>{selectedTool.description}</p>
                        </div>
                        <pre>{selectedTool.inputSchemaJson}</pre>
                      </>
                    ) : (
                      <div className="tool-placeholder">
                        ⌁<b>{t("mcp.queryHint")}</b>
                      </div>
                    )}
                  </main>
                </div>
              )}
              {notice && (
                <div
                  className={`mcp-notice ${notice.ok ? "success" : "error"}`}
                >
                  <b>
                    {notice.ok ? "✓" : "×"} {notice.text}
                  </b>
                </div>
              )}
              <footer>
                <Button quiet onClick={() => void inspect()} disabled={busy}>
                  {busy ? t("mcp.testing") : t("mcp.testAndQuery")}
                </Button>
                <Button onClick={() => void save()} disabled={busy}>
                  {busy ? t("mcp.saving") : t("mcp.save")}
                </Button>
              </footer>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
