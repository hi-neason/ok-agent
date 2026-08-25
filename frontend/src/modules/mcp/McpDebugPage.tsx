import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "../shared";
import { callTool, fetchServers, fetchTools, inspectServerById } from "./api";
import { isJsonObject, type McpServer, type McpTool } from "./types";

export function McpDebugPage({ serverId }: { serverId: string }) {
  const { t } = useTranslation();
  const [server, setServer] = useState<McpServer | null>(null);
  const [tools, setTools] = useState<McpTool[]>([]);
  const [selected, setSelected] = useState<McpTool | null>(null);
  const [query, setQuery] = useState("");
  const [argumentsJson, setArgumentsJson] = useState("{}");
  const [result, setResult] = useState("");
  const [resultOk, setResultOk] = useState<boolean | null>(null);
  const [durationMs, setDurationMs] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const argumentTemplate = (tool: McpTool) => {
    try {
      const schema: unknown = JSON.parse(tool.inputSchemaJson);
      if (!isJsonObject(schema) || !isJsonObject(schema.properties))
        return "{}";
      const values = Object.fromEntries(
        Object.entries(schema.properties).map(([name, property]) => {
          if (!isJsonObject(property)) return [name, null];
          if (property.default !== undefined) return [name, property.default];
          if (property.type === "number" || property.type === "integer")
            return [name, 0];
          if (property.type === "boolean") return [name, false];
          if (property.type === "array") return [name, []];
          if (property.type === "object") return [name, {}];
          return [name, ""];
        }),
      );
      return JSON.stringify(values, null, 4);
    } catch {
      return "{}";
    }
  };

  const chooseTool = (tool: McpTool) => {
    setSelected(tool);
    setArgumentsJson(argumentTemplate(tool));
    setResult("");
    setResultOk(null);
    setDurationMs(null);
  };

  const loadTools = async (refresh = false) => {
    setBusy(true);
    setError("");
    try {
      if (refresh) {
        const inspection = await inspectServerById(serverId);
        setTools(inspection.tools);
        const next = inspection.tools[0] ?? null;
        if (next) chooseTool(next);
      } else {
        const loaded = await fetchTools(serverId);
        setTools(loaded);
        if (loaded[0]) chooseTool(loaded[0]);
      }
    } catch (loadError) {
      setError(
        loadError instanceof Error && loadError.message
          ? loadError.message
          : t("mcp.toolsLoadFailed"),
      );
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    void (async () => {
      try {
        const loaded = await fetchServers(0, 1000);
        const current = loaded.content.find((item) => item.id === serverId) ?? null;
        setServer(current);
        if (!current) setError(t("mcp.serverNotFound"));
        else await loadTools(false);
      } catch {
        setError(t("mcp.serverNotFound"));
      }
    })();
  }, [serverId]);

  const runTool = async () => {
    if (!selected) return;
    let args: unknown;
    try {
      args = JSON.parse(argumentsJson);
      if (!isJsonObject(args)) throw new Error();
    } catch {
      setError(t("mcp.argumentsInvalid"));
      return;
    }
    setBusy(true);
    setError("");
    setResult("");
    try {
      const call = await callTool(serverId, selected.name, args);
      setResultOk(Boolean(call.success));
      setDurationMs(call.durationMs ?? null);
      try {
        setResult(JSON.stringify(JSON.parse(call.resultJson), null, 4));
      } catch {
        setResult(call.resultJson || call.message || "");
      }
    } catch {
      setResultOk(false);
      setError(t("mcp.toolCallFailed"));
    } finally {
      setBusy(false);
    }
  };

  const visibleTools = tools.filter((tool) =>
    `${tool.name} ${tool.description}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );

  return (
    <div className="mcp-debug-page">
      <header className="mcp-debug-header">
        <div className="mcp-debug-identity">
          <button
            className="mcp-debug-back"
            onClick={() => (window.location.href = "/mcp")}
            aria-label={t("mcp.backToRegistry")}
          >
            ←
          </button>
          <div>
            <div className="mcp-debug-eyebrow">
              <p className="kicker">{t("kickers.mcpDebug")}</p>
              <span>{server?.transport}</span>
            </div>
            <div className="mcp-debug-name-line">
              <h1>{server?.name ?? t("mcp.loading")}</h1>
              <code>{server?.serverUrl || server?.command}</code>
            </div>
          </div>
        </div>
        <div className="mcp-debug-connection">
          <span
            className={server?.lastTestStatus === "SUCCESS" ? "online" : ""}
          />
          <div>
            <b>
              {server?.lastTestStatus === "SUCCESS"
                ? t("mcp.connected")
                : t("mcp.notConnected")}
            </b>
            <small>{t("mcp.toolCount", { count: tools.length })}</small>
          </div>
          <Button quiet onClick={() => void loadTools(true)} disabled={busy}>
            {busy ? t("mcp.refreshing") : t("mcp.reconnect")}
          </Button>
        </div>
      </header>
      {error && <div className="mcp-debug-error">× {error}</div>}
      <div className="mcp-debug-workbench">
        <aside className="mcp-debug-tools">
          <div>
            <span>TOOL CATALOG</span>
            <b>{tools.length}</b>
          </div>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t("mcp.searchTools")}
          />
          <section>
            {visibleTools.map((tool) => (
              <button
                key={tool.name}
                className={selected?.name === tool.name ? "selected" : ""}
                onClick={() => chooseTool(tool)}
              >
                <i>⚡</i>
                <span>
                  <b>{tool.name}</b>
                  <small>{tool.description || t("mcp.noDescription")}</small>
                </span>
              </button>
            ))}
          </section>
        </aside>
        <main className="mcp-debug-schema">
          {selected ? (
            <>
              <div className="debug-panel-title">
                <div>
                  <p className="kicker">{t("kickers.toolDefinition")}</p>
                  <span>{t("kickers.jsonSchema")}</span>
                </div>
                <h2>{selected.name}</h2>
                <p>{selected.description || t("mcp.noDescription")}</p>
              </div>
              <div className="schema-label">INPUT SCHEMA</div>
              <pre>{selected.inputSchemaJson}</pre>
            </>
          ) : (
            <div className="debug-empty">{t("mcp.selectTool")}</div>
          )}
        </main>
        <aside className="mcp-debug-runner">
          <div className="debug-panel-title">
            <div>
              <p className="kicker">{t("kickers.requestLab")}</p>
              <span>JSON</span>
            </div>
            <h2>{t("mcp.arguments")}</h2>
          </div>
          <textarea
            value={argumentsJson}
            onChange={(event) => setArgumentsJson(event.target.value)}
            spellCheck={false}
          />
          <button
            className="mcp-run-button"
            disabled={!selected || busy}
            onClick={() => void runTool()}
          >
            {busy ? t("mcp.running") : `▶ ${t("mcp.runTool")}`}
          </button>
          <div className="mcp-result-head">
            <b>{t("mcp.result")}</b>
            {durationMs !== null && (
              <span className={resultOk ? "success" : "error"}>
                {resultOk ? "✓" : "×"} {durationMs} ms
              </span>
            )}
          </div>
          <pre className={resultOk === false ? "failed" : ""}>
            {result || t("mcp.resultPlaceholder")}
          </pre>
        </aside>
      </div>
    </div>
  );
}
