import { useTranslation } from "react-i18next";
import { AGENT_TABS, type AgentTab } from "../types";

const TAB_INDEX: Record<AgentTab, string> = {
  core: "01",
  skills: "02",
  mcp: "03",
  memory: "04",
  workspace: "05",
  runtime: "06",
};

export function AgentConfigTabs({
  active,
  onSelect,
  errorCounts,
}: {
  active: AgentTab;
  onSelect: (tab: AgentTab) => void;
  errorCounts: Record<AgentTab, number>;
}) {
  const { t } = useTranslation();
  const label = (tab: AgentTab) => t(`agents.tab.${tab}`);

  return (
    <nav className="agent-config-tabs" aria-label={t("agents.configTabs")}>
      {AGENT_TABS.map((tab) => (
        <button
          key={tab}
          className={active === tab ? "active" : ""}
          aria-current={active === tab ? "page" : undefined}
          onClick={() => onSelect(tab)}
        >
          <span>{TAB_INDEX[tab]}</span>
          <strong>{label(tab)}</strong>
          {errorCounts[tab] > 0 && (
            <em className="tab-error-badge" title={`${errorCounts[tab]} error(s)`}>
              {errorCounts[tab]}
            </em>
          )}
        </button>
      ))}
    </nav>
  );
}
