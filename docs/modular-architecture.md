# 模块化单体架构

当前阶段保持一个 React 前端应用和一个 Spring Boot 后端应用，通过产品模块边界降低开发与调试成本。模块是代码所有权和依赖约束，不是独立部署单元。

## 前端

顶部产品区切换负责选择大模块，左侧菜单只展示当前模块的功能：

| 产品模块 | 入口 | 用户 | 职责 |
| --- | --- | --- | --- |
| Agent 管理 | `/agent/*` | 企业 Agent 管理员 | Agent、模型、Skill、MCP、知识库、工作流、版本发布、渠道和权限 |
| 销售客服工作台 | `/workbench/*` | 一线销售与客服 | 多渠道会话、人工接管、客户、线索、工单、跟进与绩效 |
| 客户对话 | `/chat` | 外部客户 | 与已发布的客服 Agent 对话 |

`frontend/src/modules/agent-management`、`operator-workbench` 和 `customer-chat` 是三个产品模块的公开入口。跨模块导航通过 `app/navigation.ts`，页面加载只能经各模块的 `index.ts` 门面。原有路径暂时作为兼容别名，新增链接统一使用新路径。

## 后端

| 模块 | Java 包 | 职责 |
| --- | --- | --- |
| Agent Manager | `module.agentmanager` | 编辑态配置和不可变版本/发布生命周期 |
| Agent Runtime | `module.agentruntime` | 解析发布快照、执行 Agent 和记录运行结果 |
| Operator Workbench | `module.workbench` | B 端会话队列、接管、业务结果、线索/工单和运营指标 |
| Customer Chat | `module.customerchat` | C 端客户消息契约和入站用例端口 |

包内采用 `api`（HTTP 适配层）和 `application`（用例层）。稳定的技术契约放在 `shared`，不能把某个业务模块的 DTO 当作全局共享类型。

依赖方向：

```text
customerchat.api -> customerchat.application <- agentruntime.application
workbench.api -> workbench.application
agentmanager.api -> agentmanager.application
```

关键规则：

- Runtime 不依赖 Agent Manager 的编辑态应用服务，只消费不可变发布快照。
- B 端不能调用旧 `web` Controller 包或其他产品模块的内部实现。
- C 端应用端口不依赖 Spring、HTTP、数据库或运行时实现。
- 旧 `domain/service/repository/web` 包按业务迭代逐步迁入新边界；禁止新增横向技术分层包。
- `ModuleBoundaryTests` 在 `mvn test` 中阻止已建立的边界发生反向依赖。

## 尚未开放的公网边界

`/api/v1/customer-chat/messages` 当前仍要求控制台登录，仅用于内部预览。正式对外开放前必须增加访客会话身份、渠道签名、租户隔离、频率限制、内容安全和滥用审计，不能简单地在 Spring Security 中配置匿名放行。
