import { useTranslation } from "react-i18next";
import type { AgentItem } from "../../types";
import { AgentTabProps } from "./AgentPromptTab";

export function AgentMemoryTab({ form, setField, errorsByField }: AgentTabProps) {
  const { t } = useTranslation();

  return (
    <div className="config-section capability-card">
      <div className="capability-hero">
        <span>MEM</span>
        <div>
          <b>{t("agents.memoryTitle")}</b>
          <small>{t("agents.memoryHint")}</small>
        </div>
        <label className="switch-line">
          <input
            type="checkbox"
            data-field="memoryEnabled"
            checked={form.memoryEnabled}
            onChange={(e) => {
              setField("memoryEnabled", e.target.checked);
              if (e.target.checked && form.workspaceMode === "DISABLED")
                setField("workspaceMode", "LOCAL_ROOTED");
            }}
          />
          {t("agents.enabled")}
        </label>
      </div>
      <div className="runtime-grid muted-when-disabled" aria-disabled={!form.memoryEnabled}>
        <label className="runtime-field">
          <span>{t("agents.memoryFlushMode")}</span>
          <select
            disabled={!form.memoryEnabled}
            value={form.memoryFlushMode}
            onChange={(e) =>
              setField("memoryFlushMode", e.target.value as AgentItem["memoryFlushMode"])
            }
          >
            <option value="ALWAYS">ALWAYS</option>
            <option value="THROTTLED">THROTTLED</option>
            <option value="NEVER">NEVER</option>
          </select>
        </label>
        <label className="runtime-field">
          <span>{t("agents.memoryFlushInterval")}</span>
          <input
            disabled={!form.memoryEnabled || form.memoryFlushMode !== "THROTTLED"}
            type="number"
            min={1}
            max={1440}
            value={form.memoryFlushIntervalMinutes}
            onChange={(e) =>
              setField("memoryFlushIntervalMinutes", Number(e.target.value))
            }
          />
        </label>
        <label className="runtime-field">
          <span>{t("agents.memoryConsolidation")}</span>
          <input
            disabled={!form.memoryEnabled}
            type="number"
            min={1}
            max={1440}
            value={form.memoryConsolidationIntervalMinutes}
            onChange={(e) =>
              setField("memoryConsolidationIntervalMinutes", Number(e.target.value))
            }
          />
        </label>
        <label className="runtime-field">
          <span>{t("agents.dailyRetention")}</span>
          <input
            disabled={!form.memoryEnabled}
            type="number"
            min={1}
            max={3650}
            value={form.memoryDailyRetentionDays}
            onChange={(e) => setField("memoryDailyRetentionDays", Number(e.target.value))}
          />
        </label>
        <label className="runtime-field">
          <span>{t("agents.sessionRetention")}</span>
          <input
            disabled={!form.memoryEnabled}
            type="number"
            min={1}
            max={3650}
            value={form.memorySessionRetentionDays}
            onChange={(e) =>
              setField("memorySessionRetentionDays", Number(e.target.value))
            }
          />
        </label>
      </div>
      {form.memoryEnabled && (
        <div className="info-strip">✓ {t("agents.memoryWorkspaceNotice")}</div>
      )}
      {errorsByField["memoryEnabled"]?.[0]?.message && (
        <div className="field-error">{errorsByField["memoryEnabled"][0].message}</div>
      )}
    </div>
  );
}
