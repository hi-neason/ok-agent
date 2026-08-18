import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { loadIntentTree, flatten } from "../../../intent/api";
import type { IntentNode } from "../../../intent/types";
import type { AgentForm, AgentSubagentConfig, Option } from "../../types";

export function AgentSubAgentTab({
  form,
  setField,
  models,
}: {
  form: AgentForm;
  setField: (key: "subagents", value: AgentSubagentConfig[]) => void;
  models: Option[];
}) {
  const { t } = useTranslation();
  const subagents = form.subagents ?? [];
  const [intentTree, setIntentTree] = useState<IntentNode[]>([]);

  useEffect(() => {
    let alive = true;
    loadIntentTree()
      .then((tree) => {
        if (alive) setIntentTree(tree);
      })
      .catch(() => {
        /* best effort: 意图树为空时多选区只是空列表 */
      });
    return () => {
      alive = false;
    };
  }, []);

  const allIntents = useMemo(() => flatten(intentTree), [intentTree]);

  const update = (next: AgentSubagentConfig[]) => setField("subagents", next);

  const addOne = () =>
    update([
      ...subagents,
      {
        key: "",
        name: "",
        description: "",
        modelAssetId: null,
        toolNames: [],
        workspacePath: "",
        intentKeys: [],
      },
    ]);

  const removeAt = (idx: number) => update(subagents.filter((_, i) => i !== idx));

  const patch = (idx: number, p: Partial<AgentSubagentConfig>) =>
    update(subagents.map((s, i) => (i === idx ? { ...s, ...p } : s)));

  const toggleIntent = (idx: number, intentKey: string) => {
    const s = subagents[idx];
    const current = s.intentKeys ?? [];
    patch(idx, {
      intentKeys: current.includes(intentKey)
        ? current.filter((k) => k !== intentKey)
        : [...current, intentKey],
    });
  };

  const renderIntentTree = (
    nodes: IntentNode[],
    claimed: Set<string>,
    idx: number,
    depth: number,
  ): ReactNode =>
    nodes.map((n) => (
      <div key={n.node.id}>
        <label className="intent-option" style={{ paddingLeft: 8 + depth * 16 }}>
          <input
            type="checkbox"
            checked={claimed.has(n.node.intentKey)}
            onChange={() => toggleIntent(idx, n.node.intentKey)}
          />
          <span>{n.node.name}</span>
          <code>{n.node.intentKey}</code>
        </label>
        {n.children.length > 0 && renderIntentTree(n.children, claimed, idx, depth + 1)}
      </div>
    ));

  return (
    <div className="agent-form-grid">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <p className="form-title" style={{ margin: 0 }}>
          {t("agents.tab.subagents", { defaultValue: "SubAgent 配置" })}
        </p>
        <button className="ui-button quiet" onClick={addOne}>
          ＋ {t("agents.subagents.add", { defaultValue: "新增子 Agent" })}
        </button>
      </div>
      <p className="form-hint">
        {t(
          "agents.subagents.hint",
          {
            defaultValue:
              "在此声明本路由智能体（主 Agent）下的子 Agent 清单，并在「负责意图」中勾选该子 Agent 处理哪些意图（来自全局意图树）。运行时总控 Agent 先做意图分类，再按这里的绑定反查到对应子 Agent 并委派。",
          },
        )}
      </p>

      {subagents.length === 0 && (
        <div className="empty-state">{t("agents.subagents.empty", { defaultValue: "尚未配置子 Agent" })}</div>
      )}

      {subagents.map((s, idx) => {
        const claimed = new Set(s.intentKeys ?? []);
        return (
          <div className="subagent-card" key={idx}>
            <div className="subagent-card-head">
              <strong>{s.name || s.key || `#${idx + 1}`}</strong>
              <button className="link-button danger" onClick={() => removeAt(idx)}>
                {t("common.remove", { defaultValue: "移除" })}
              </button>
            </div>
            <div className="subagent-grid">
              <label>
                <span>{t("agents.subagents.key", { defaultValue: "Key" })}</span>
                <input
                  data-field="subagent.key"
                  value={s.key}
                  placeholder="loan_apply"
                  onChange={(e) => patch(idx, { key: e.target.value })}
                />
              </label>
              <label>
                <span>{t("agents.subagents.name", { defaultValue: "名称" })}</span>
                <input value={s.name} onChange={(e) => patch(idx, { name: e.target.value })} />
              </label>
              <label className="span-2">
                <span>{t("agents.subagents.description", { defaultValue: "描述" })}</span>
                <input
                  value={s.description}
                  onChange={(e) => patch(idx, { description: e.target.value })}
                />
              </label>
              <label>
                <span>{t("agents.subagents.model", { defaultValue: "模型" })}</span>
                <select
                  value={s.modelAssetId ?? ""}
                  onChange={(e) => patch(idx, { modelAssetId: e.target.value || null })}
                >
                  <option value="">{t("agents.subagents.inherit", { defaultValue: "继承主 Agent 模型" })}</option>
                  {models.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>{t("agents.subagents.tools", { defaultValue: "工具白名单" })}</span>
                <input
                  value={s.toolNames.join(", ")}
                  placeholder="read_file, grep_files"
                  onChange={(e) =>
                    patch(idx, {
                      toolNames: e.target.value
                        .split(",")
                        .map((x) => x.trim())
                        .filter(Boolean),
                    })
                  }
                />
              </label>
              <label className="span-2">
                <span>{t("agents.subagents.workspace", { defaultValue: "工作区路径" })}</span>
                <input
                  value={s.workspacePath}
                  placeholder="/abs/path（留空则用默认子 Agent 工作区）"
                  onChange={(e) => patch(idx, { workspacePath: e.target.value })}
                />
              </label>
              <div className="span-2 subagent-intents">
                <details>
                  <summary>
                    负责意图
                    <span className="subagent-intent-count">{claimed.size}</span>
                  </summary>
                  {allIntents.length === 0 ? (
                    <div className="form-hint" style={{ margin: "8px 0 0" }}>
                      全局意图树为空，请先在「业务管理 → 意图管理」维护意图。
                    </div>
                  ) : (
                    <div className="intent-option-list">
                      {renderIntentTree(intentTree, claimed, idx, 0)}
                    </div>
                  )}
                </details>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
