import { useTranslation } from "react-i18next";
import type { AgentItem } from "../../types";
import { AgentTabProps } from "./AgentPromptTab";

export function AgentWorkspaceTab({ form, setField, errorsByField }: AgentTabProps) {
  const { t } = useTranslation();
  const setMode = (mode: AgentItem["workspaceMode"]) => {
    setField("workspaceMode", mode);
    if (mode === "DISABLED") {
      setField("memoryEnabled", false);
      setField("shellEnabled", false);
    }
  };

  const fieldError = (field: string) => errorsByField[field]?.[0]?.message;

  return (
    <div className="config-section capability-card">
      <div className="capability-hero">
        <span>FS</span>
        <div>
          <b>{t("agents.workspaceTitle")}</b>
          <small>{t("agents.workspaceHint")}</small>
        </div>
      </div>
      <div className="workspace-modes" data-field="workspaceMode">
        {(["DISABLED", "LOCAL_ROOTED", "DOCKER_SANDBOX"] as const).map((mode) => (
          <button
            key={mode}
            className={form.workspaceMode === mode ? "active" : ""}
            onClick={() => setMode(mode)}
          >
            {t(`agents.workspaceMode.${mode}`)}
          </button>
        ))}
      </div>
      {fieldError("workspaceMode") && (
        <div className="field-error">{fieldError("workspaceMode")}</div>
      )}
      {form.workspaceMode !== "DISABLED" && (
        <>
          <div className="runtime-grid">
            <label className="runtime-field" data-field="workspaceIsolationScope">
              <span>{t("agents.isolationScope")}</span>
              <select
                value={form.workspaceIsolationScope}
                onChange={(e) =>
                  setField(
                    "workspaceIsolationScope",
                    e.target.value as AgentItem["workspaceIsolationScope"],
                  )
                }
              >
                <option value="SESSION">SESSION</option>
                <option value="USER">USER</option>
                <option value="AGENT">AGENT</option>
                <option value="GLOBAL">GLOBAL</option>
              </select>
            </label>
            {form.workspaceMode === "DOCKER_SANDBOX" && (
              <>
                <label className="runtime-field" data-field="dockerImage">
                  <span>{t("agents.dockerImage")}</span>
                  <input
                    value={form.dockerImage}
                    onChange={(e) => setField("dockerImage", e.target.value)}
                    placeholder="ubuntu:24.04"
                  />
                </label>
                <label className="runtime-field">
                  <span>{t("agents.sandboxMemory")}</span>
                  <input
                    type="number"
                    min={128}
                    max={32768}
                    value={form.sandboxMemoryMb}
                    onChange={(e) => setField("sandboxMemoryMb", Number(e.target.value))}
                  />
                </label>
                <label className="runtime-field">
                  <span>{t("agents.sandboxCpu")}</span>
                  <input
                    type="number"
                    min={1}
                    max={64}
                    value={form.sandboxCpuCount}
                    onChange={(e) => setField("sandboxCpuCount", Number(e.target.value))}
                  />
                </label>
              </>
            )}
          </div>
          <div className="runtime-switches">
            <label>
              <input
                type="checkbox"
                checked={form.workspaceContextEnabled}
                onChange={(e) => setField("workspaceContextEnabled", e.target.checked)}
              />
              <span>{t("agents.workspaceContext")}</span>
            </label>
            <label>
              <input
                type="checkbox"
                checked={form.shellEnabled}
                onChange={(e) => setField("shellEnabled", e.target.checked)}
              />
              <span>{t("agents.shellTool")}</span>
            </label>
          </div>
          {form.shellEnabled && (
            <div className="runtime-warning">△ {t("agents.shellWarning")}</div>
          )}
        </>
      )}
      <div className="info-strip">{t("agents.workspaceManagedPath")}</div>
      {fieldError("dockerImage") && (
        <div className="field-error">{fieldError("dockerImage")}</div>
      )}
      {fieldError("workspaceIsolationScope") && (
        <div className="field-error">{fieldError("workspaceIsolationScope")}</div>
      )}
    </div>
  );
}
