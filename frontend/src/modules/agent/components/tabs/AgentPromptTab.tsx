import { useTranslation } from "react-i18next";
import type { AgentForm, Option, ValidationIssue } from "../../types";

export type AgentTabProps = {
  form: AgentForm;
  setField: <K extends keyof AgentForm>(key: K, value: AgentForm[K]) => void;
  errorsByField: Record<string, ValidationIssue[]>;
};

export function AgentPromptTab({
  form,
  setField,
  errorsByField,
  models,
}: AgentTabProps & { models: Option[] }) {
  const { t } = useTranslation();
  const fieldError = (field: string) =>
    errorsByField[field]?.[0]?.message;

  return (
    <>
      <div className="config-section">
        <div className="section-head">
          <b>{t("agents.systemPrompt")}</b>
          <small>{t("agents.systemPromptHint")}</small>
        </div>
        <textarea
          className="cfg-textarea tall"
          value={form.systemPrompt}
          onChange={(e) => setField("systemPrompt", e.target.value)}
          placeholder={t("agents.systemPromptPlaceholder")}
        />
      </div>

      <div className="config-section">
        <div className="section-head">
          <b>{t("agents.welcomeMessage")}</b>
          <small>{t("agents.welcomeHint")}</small>
        </div>
        <textarea
          className="cfg-textarea"
          value={form.welcomeMessage}
          onChange={(e) => setField("welcomeMessage", e.target.value)}
          placeholder={t("agents.welcomePlaceholder")}
        />
      </div>

      <div className="config-section">
        <div className="section-head">
          <b>{t("agents.model")}</b>
        </div>
        <select
          className="cfg-select"
          data-field="modelAssetId"
          value={form.modelAssetId}
          onChange={(e) => setField("modelAssetId", e.target.value)}
        >
          <option value="">{t("agents.selectModel")}</option>
          {models.map((m) => (
            <option key={m.id} value={m.id}>
              {m.name}
              {m.sub ? ` — ${m.sub}` : ""}
            </option>
          ))}
        </select>
        {fieldError("modelAssetId") && (
          <div className="field-error">{fieldError("modelAssetId")}</div>
        )}
        <div className="param-grid">
          <label className="param-row">
            <span>
              {t("agents.temperature")} <em>{form.temperature.toFixed(2)}</em>
            </span>
            <input
              type="range"
              min={0}
              max={2}
              step={0.05}
              value={form.temperature}
              onChange={(e) => setField("temperature", Number(e.target.value))}
            />
          </label>
          <label className="param-row">
            <span>
              {t("agents.topP")} <em>{form.topP.toFixed(2)}</em>
            </span>
            <input
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={form.topP}
              onChange={(e) => setField("topP", Number(e.target.value))}
            />
          </label>
          <label className="param-row wide">
            <span>
              {t("agents.maxTokens")} <em>{form.maxTokens}</em>
            </span>
            <input
              type="range"
              min={256}
              max={8192}
              step={128}
              value={form.maxTokens}
              onChange={(e) => setField("maxTokens", Number(e.target.value))}
            />
          </label>
          <label className="runtime-field" data-field="topK">
            <span>{t("agents.topK")}</span>
            <input
              type="number"
              min={1}
              max={1000}
              value={form.topK}
              onChange={(e) => setField("topK", Number(e.target.value))}
            />
          </label>
        </div>
      </div>
    </>
  );
}
