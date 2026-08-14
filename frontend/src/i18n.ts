import i18n from "i18next";
import { initReactI18next } from "react-i18next";

const resources = {
  "zh-CN": {
    translation: {
      navigation: {
        models: "模型管理",
        skills: "技能仓库",
        mcp: "MCP 与工具",
        knowledge: "知识库",
        workflows: "工作流",
        agents: "智能体",
        memory: "记忆与上下文",
        workspace: "工作空间",
        teams: "子 Agent 与协作",
        release: "发布与环境",
        observe: "运行观测",
        system: "账号与权限",
      },
      common: { controlPlane: "控制面", search: "搜索", language: "语言" },
    },
  },
  "en-US": {
    translation: {
      navigation: {
        models: "Model Policies",
        skills: "Skill Library",
        mcp: "MCP & Tools",
        knowledge: "Knowledge Bases",
        workflows: "Workflows",
        agents: "Agents",
        memory: "Memory & Context",
        workspace: "Workspace",
        teams: "Subagents & Collaboration",
        release: "Releases & Environments",
        observe: "Runtime Observability",
        system: "Accounts & Permissions",
      },
      common: {
        controlPlane: "Control Plane",
        search: "Search",
        language: "Language",
      },
    },
  },
} as const;

const savedLanguage = window.localStorage.getItem("ok-agent.locale");

i18n.use(initReactI18next).init({
  resources,
  lng: savedLanguage ?? navigator.language,
  fallbackLng: "zh-CN",
  supportedLngs: ["zh-CN", "en-US"],
  interpolation: { escapeValue: false },
});

i18n.on("languageChanged", (language) =>
  window.localStorage.setItem("ok-agent.locale", language),
);

export default i18n;
