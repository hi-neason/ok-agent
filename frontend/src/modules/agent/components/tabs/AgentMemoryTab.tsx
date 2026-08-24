import { useTranslation } from "react-i18next";
import type { AgentItem } from "../../types";
import { AgentTabProps } from "./AgentPromptTab";

export function AgentMemoryTab({ form, setField }: AgentTabProps) {
  const { t } = useTranslation();

  return (
    <div className="config-section capability-card">
      <div className="capability-hero capability-deprecated">
        <span>MEM</span>
        <div>
          <b>{t("agents.memoryTitle")}</b>
          <small>{t("agents.memoryHint")}</small>
        </div>
        <label className="switch-line" aria-disabled>
          <input type="checkbox" disabled checked={false} />
          <span className="deprecated-badge">{t("agents.memoryDeprecatedBadge")}</span>
        </label>
      </div>
      <div className="runtime-grid muted-when-disabled" aria-disabled>
        <label className="runtime-field">
          <span>{t("agents.memoryFlushMode")}</span>
          <select disabled value={form.memoryFlushMode}>
            <option value="ALWAYS">ALWAYS</option>
            <option value="THROTTLED">THROTTLED</option>
            <option value="NEVER">NEVER</option>
          </select>
        </label>
        <label className="runtime-field">
          <span>{t("agents.memoryFlushInterval")}</span>
          <input disabled type="number" value={form.memoryFlushIntervalMinutes} />
        </label>
        <label className="runtime-field">
          <span>{t("agents.memoryConsolidation")}</span>
          <input disabled type="number" value={form.memoryConsolidationIntervalMinutes} />
        </label>
        <label className="runtime-field">
          <span>{t("agents.dailyRetention")}</span>
          <input disabled type="number" value={form.memoryDailyRetentionDays} />
        </label>
        <label className="runtime-field">
          <span>{t("agents.sessionRetention")}</span>
          <input disabled type="number" value={form.memorySessionRetentionDays} />
        </label>
      </div>
      <div className="info-strip deprecated-strip">
        ⊘ {t("agents.memoryDeprecatedNotice")}
      </div>

      <div className="config-section capability-card">
        <div className="capability-hero">
          <span>USR</span>
          <div>
            <b>{t("agents.userMemory.title")}</b>
            <small>{t("agents.userMemory.hint")}</small>
          </div>
        </div>
        <div className="runtime-grid">
          <label className="switch-line" style={{ gridColumn: "1 / -1" }}>
            <input
              type="checkbox"
              data-field="personaExtractEnabled"
              checked={form.personaExtractEnabled}
              onChange={(e) => setField("personaExtractEnabled", e.target.checked)}
            />
            {t("agents.userMemory.enable")}
          </label>

          <label className="runtime-field" style={{ gridColumn: "1 / -1" }}>
            <span>{t("agents.userMemory.mode")}</span>
            <select
              value={form.personaInjectionMode}
              onChange={(e) =>
                setField(
                  "personaInjectionMode",
                  e.target.value as AgentItem["personaInjectionMode"],
                )
              }
            >
              <option value="NONE">{t("agents.userMemory.modes.NONE")}</option>
              <option value="SELF_ONLY">{t("agents.userMemory.modes.SELF_ONLY")}</option>
              <option value="GLOBAL">{t("agents.userMemory.modes.GLOBAL")}</option>
            </select>
          </label>

          <label
            className="runtime-field"
            style={{
              gridColumn: "1 / -1",
              opacity: form.personaInjectionMode === "NONE" ? 0.5 : 1,
            }}
          >
            <span>{t("agents.userMemory.template")}</span>
            <textarea
              disabled={form.personaInjectionMode === "NONE"}
              rows={4}
              value={form.personaPromptTemplate}
              placeholder={t("agents.userMemory.templatePlaceholder")}
              onChange={(e) => setField("personaPromptTemplate", e.target.value)}
            />
          </label>
        </div>
      </div>
    </div>
  );
}
