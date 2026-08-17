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

      <div className="config-section capability-card">
        <div className="capability-hero">
          <span>USR</span>
          <div>
            <b>用户画像注入</b>
            <small>对话时把目标用户的画像（标签/偏好/事实/长期记忆）注入 system prompt，帮助 Agent 个性化处理。由对话自动沉淀，人工可校正。</small>
          </div>
          <label className="switch-line">
            <input
              type="checkbox"
              data-field="personaMemoryEnabled"
              checked={form.personaMemoryEnabled}
              onChange={(e) => setField("personaMemoryEnabled", e.target.checked)}
            />
            {t("agents.enabled")}
          </label>
        </div>
        <div className="runtime-grid muted-when-disabled" aria-disabled={!form.personaMemoryEnabled}>
          <label className="runtime-field" style={{ gridColumn: "1 / -1" }}>
            <span>画像注入模板（可选占位符 {"{summary} {tags} {preferences} {facts} {memory}"}）</span>
            <textarea
              disabled={!form.personaMemoryEnabled}
              rows={4}
              value={form.personaPromptTemplate}
              placeholder={"以下是当前用户的画像，请在回答时参考：\n# 用户画像\n{summary}\n标签：{tags}\n偏好：{preferences}\n关键事实：{facts}\n长期记忆：{memory}"}
              onChange={(e) => setField("personaPromptTemplate", e.target.value)}
            />
          </label>
        </div>
      </div>
    </div>
  );
}
