import { useTranslation } from "react-i18next";
import type { Option } from "../../types";
import { AgentTabProps } from "./AgentPromptTab";

export function AgentSkillsTab({
  form,
  setField,
  errorsByField,
  skills,
}: AgentTabProps & { skills: Option[] }) {
  const { t } = useTranslation();
  const toggle = (id: string) => {
    const next = new Set(form.boundSkills);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setField("boundSkills", next);
  };

  return (
    <div className="config-section">
      <div className="section-head">
        <b>{t("agents.skills")}</b>
        <small>{t("agents.skillsHint")}</small>
      </div>
      <div className="binding-list" data-field="skillIds">
        {skills.length === 0 && (
          <small style={{ padding: 8 }}>{t("agents.noSkills")}</small>
        )}
        {skills.map((s) => (
          <label key={s.id} className="binding-item">
            <input
              type="checkbox"
              checked={form.boundSkills.has(s.id)}
              onChange={() => toggle(s.id)}
            />
            <span className="meta">
              <b>{s.name}</b>
              {s.sub && <small>{s.sub}</small>}
            </span>
          </label>
        ))}
      </div>
      {errorsByField["skillIds"]?.[0]?.message && (
        <div className="field-error">{errorsByField["skillIds"][0].message}</div>
      )}
    </div>
  );
}
