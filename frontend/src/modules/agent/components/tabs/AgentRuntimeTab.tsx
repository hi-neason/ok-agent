import { useTranslation } from "react-i18next";
import type { AgentItem } from "../../types";
import { AgentTabProps } from "./AgentPromptTab";

export function AgentRuntimeTab({ form, setField, errorsByField }: AgentTabProps) {
  const { t } = useTranslation();
  const fieldError = (field: string) => errorsByField[field]?.[0]?.message;

  return (
    <div className="config-section runtime-policy-card">
      <div className="section-head runtime-policy-head">
        <span className="runtime-icon">⌁</span>
        <div>
          <b>{t("agents.runtimePolicy")}</b>
          <small>{t("agents.runtimePolicyHint")}</small>
        </div>
      </div>
      <div className="runtime-grid">
        <label className="runtime-field">
          <span>{t("agents.maxIters")}</span>
          <input
            type="number"
            min={1}
            max={100}
            value={form.maxIters}
            onChange={(e) => setField("maxIters", Number(e.target.value))}
          />
        </label>
        <label className="runtime-field">
          <span>{t("agents.maxRetries")}</span>
          <input
            type="number"
            min={0}
            max={10}
            value={form.maxRetries}
            onChange={(e) => setField("maxRetries", Number(e.target.value))}
          />
        </label>
        <label className="runtime-field">
          <span>{t("agents.modelTimeout")}</span>
          <input
            type="number"
            min={1}
            max={1800}
            value={form.modelTimeoutSeconds}
            onChange={(e) => setField("modelTimeoutSeconds", Number(e.target.value))}
          />
        </label>
        <label className="runtime-field">
          <span>{t("agents.toolTimeout")}</span>
          <input
            type="number"
            min={1}
            max={1800}
            value={form.toolTimeoutSeconds}
            onChange={(e) => setField("toolTimeoutSeconds", Number(e.target.value))}
          />
        </label>
        <label className="runtime-field" data-field="maxContextTokens">
          <span>{t("agents.maxContextTokens")}</span>
          <input
            type="number"
            min={1000}
            max={2000000}
            step={1000}
            value={form.maxContextTokens}
            onChange={(e) => setField("maxContextTokens", Number(e.target.value))}
          />
        </label>
        <label className="runtime-field" data-field="permissionMode">
          <span>{t("agents.permissionMode")}</span>
          <select
            value={form.permissionMode}
            onChange={(e) =>
              setField("permissionMode", e.target.value as AgentItem["permissionMode"])
            }
          >
            <option value="DEFAULT">DEFAULT</option>
            <option value="EXPLORE">EXPLORE</option>
            <option value="ACCEPT_EDITS">ACCEPT_EDITS</option>
            <option value="DONT_ASK">DONT_ASK</option>
            <option value="BYPASS">BYPASS</option>
          </select>
        </label>
      </div>
      {form.permissionMode === "BYPASS" && (
        <div className="runtime-warning">△ {t("agents.bypassWarning")}</div>
      )}
      <div className="runtime-switches">
        <label>
          <input
            type="checkbox"
            checked={form.parallelToolCalls}
            onChange={(e) => setField("parallelToolCalls", e.target.checked)}
          />
          <span>{t("agents.parallelToolCalls")}</span>
        </label>
        <label>
          <input
            type="checkbox"
            checked={form.compactionEnabled}
            onChange={(e) => setField("compactionEnabled", e.target.checked)}
          />
          <span>{t("agents.compaction")}</span>
        </label>
        <label>
          <input
            type="checkbox"
            checked={form.toolResultEvictionEnabled}
            onChange={(e) => setField("toolResultEvictionEnabled", e.target.checked)}
          />
          <span>{t("agents.toolResultEviction")}</span>
        </label>
        <label>
          <input
            type="checkbox"
            checked={form.tracingEnabled}
            onChange={(e) => setField("tracingEnabled", e.target.checked)}
          />
          <span>{t("agents.tracing")}</span>
        </label>
      </div>
      {fieldError("maxContextTokens") && (
        <div className="field-error">{fieldError("maxContextTokens")}</div>
      )}
      {fieldError("permissionMode") && (
        <div className="field-error">{fieldError("permissionMode")}</div>
      )}
      {fieldError("toolTimeoutSeconds") && (
        <div className="field-error">{fieldError("toolTimeoutSeconds")}</div>
      )}
    </div>
  );
}
