import { useTranslation } from "react-i18next";
import type { Option } from "../../types";
import { AgentTabProps } from "./AgentPromptTab";

export function AgentMcpTab({
  form,
  setField,
  errorsByField,
  mcpServers,
  mcpTools,
}: AgentTabProps & { mcpServers: Option[]; mcpTools: Record<string, string[]> }) {
  const { t } = useTranslation();

  const toggleServer = (id: string) => {
    const wasBound = form.boundMcp.has(id);
    const nextBound = new Set(form.boundMcp);
    if (wasBound) nextBound.delete(id);
    else nextBound.add(id);
    setField("boundMcp", nextBound);
    if (wasBound) {
      const nextFilters = { ...form.mcpToolFilters };
      delete nextFilters[id];
      setField("mcpToolFilters", nextFilters);
    }
  };

  const toggleTool = (serverId: string, tool: string) => {
    const all = mcpTools[serverId] ?? [];
    const filter = form.mcpToolFilters[serverId];
    const checked = !filter || filter.includes(tool);
    const current = filter ?? all;
    const nextTools = checked
      ? current.filter((name) => name !== tool)
      : [...current, tool];
    const nextFilters = { ...form.mcpToolFilters };
    if (nextTools.length === 0) {
      const selectedServers = new Set(form.boundMcp);
      selectedServers.delete(serverId);
      setField("boundMcp", selectedServers);
      delete nextFilters[serverId];
    } else if (nextTools.length === all.length) {
      delete nextFilters[serverId];
    } else {
      nextFilters[serverId] = nextTools;
    }
    setField("mcpToolFilters", nextFilters);
  };

  return (
    <div className="config-section">
      <div className="section-head">
        <b>{t("agents.mcpServers")}</b>
        <small>{t("agents.mcpHint")}</small>
      </div>
      <div className="binding-list" data-field="mcpServerIds">
        {mcpServers.length === 0 && (
          <small style={{ padding: 8 }}>{t("agents.noMcp")}</small>
        )}
        {mcpServers.map((m) => (
          <div
            key={m.id}
            className={`binding-item binding-card ${form.boundMcp.has(m.id) ? "selected" : ""}`}
          >
            <input
              type="checkbox"
              checked={form.boundMcp.has(m.id)}
              onChange={() => toggleServer(m.id)}
            />
            <span className="meta">
              <b>{m.name}</b>
              {m.sub && <small>{m.sub}</small>}
            </span>
            {form.boundMcp.has(m.id) && (mcpTools[m.id] ?? []).length > 0 && (
              <div className="tool-allowlist">
                <small>{t("agents.toolAccess")}</small>
                {(mcpTools[m.id] ?? []).map((tool) => {
                  const filter = form.mcpToolFilters[m.id];
                  const checked = !filter || filter.includes(tool);
                  return (
                    <label key={tool}>
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleTool(m.id, tool)}
                      />
                      {tool}
                    </label>
                  );
                })}
              </div>
            )}
          </div>
        ))}
      </div>
      {errorsByField["mcpServerIds"]?.[0]?.message && (
        <div className="field-error">{errorsByField["mcpServerIds"][0].message}</div>
      )}
      {errorsByField["mcpToolFilters"]?.[0]?.message && (
        <div className="field-error">{errorsByField["mcpToolFilters"][0].message}</div>
      )}
    </div>
  );
}
