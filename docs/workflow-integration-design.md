# 外部流水线（Workflow）集成设计

> 状态：设计讨论稿（未实现）
> MVP：Dify 工作流应用（https://cloud.dify.ai ）。SPI 不绑定 Dify，后续可接 n8n / Airflow 等。
> 目标：把外部流水线系统的流程作为一项能力集成进 Agent——可动态新增流水线源、动态选择流程、运行时执行并等待结果。

## 1. 设计原则

1. **不绑定具体系统**：通过 `WorkflowSource` SPI 抽象，Dify 是第一个实现；未来加 n8n / Airflow / 自建平台只需新增实现，上层不动。
2. **三层职责分离**：
   - 平台层（源 + 流程全局元数据）：定义"有哪些流水线、每条收什么参数、干什么用"。
   - Agent 层（绑定）：决定"这个 Agent 能用哪几条"，默认继承全局元数据，仅可选微调。
   - 运行时（模型）：决定"这次具体选哪条、怎么填参"。
3. **工具固定、流程动态**：对模型只暴露固定的 3 个工具，不为每条流程热注册工具；流程清单运行时从 Catalog 读取。
4. **凭证与契约唯一来源**：密钥走现有 `ApiKeyCipher`（AES）；输入 schema / 描述由平台层维护一份，全局复用，Agent 不可改 schema 结构。

## 2. 与现有代码的关系

- 对标现有 `mcp` 域结构（domain/repository/service/web），新增 `workflow` 域。
- 运行时接缝在 `HarnessAgentFactory.build(draft, userId)`：读取 Agent 绑定 → 构造 `WorkflowTools` → 注册到 `Toolkit` → `builder.toolkit(toolkit)`。已验证 `toolkit()` 与现有 `toolsConfig(MCP)` 可共存。
- 工具方法可声明 `RuntimeContext` 参数，框架自动注入，从中取 userId/sessionId（ToolMethodInvoker 已支持）。
- 前端：左侧现有菜单项 `workflows`（当前 `wip` 占位，"工作流"）改名为 **"工作流 - 集成"** 并去掉 wip，承载流水线源管理；Agent 配置页新增"工作流"Tab。

## 3. SPI 抽象（核心）

```java
public interface WorkflowSource {

    /** SPI 类型标识，如 "dify" / "n8n"，对应 source_type 列。 */
    String type();

    /** 测试连通性，返回是否成功 + 识别到的应用/流程信息。 */
    ConnectionTestResult test(WorkflowSourceConfig config);

    /**
     * 拉取该源可暴露的流程清单（轻量，不含完整 schema）。
     * 注意不同系统语义不同：
     *  - Dify：一个 app key 只对应一个 app，返回单条；
     *  - n8n：一个 instance key 可列出全部 workflow，返回多条。
     * 这个差异由各实现吸收，上层统一按列表处理。
     */
    List<WorkflowCatalogItem> listWorkflows(WorkflowSourceConfig config);

    /** 拉取某条流程的远程元数据与输入 schema（用于同步/补全本地元数据）。 */
    RemoteWorkflowDetail describeRemote(WorkflowSourceConfig config, String remoteWorkflowId);

    /** 触发执行，同步返回结果（P1 只支持 blocking 同步）。 */
    WorkflowExecutionResult execute(WorkflowSourceConfig config, String remoteWorkflowId,
                                    Map<String, Object> inputs, String endUserId);
}
```

- `WorkflowSourceConfig`：从 `workflow_source` 记录构造，含 baseUrl、解密后的认证信息、超时等；实现类不碰数据库。
- `WorkflowCatalogItem`：remoteWorkflowId、name、active、tags、远程侧描述。
- `RemoteWorkflowDetail`：active 状态、输入变量 schema、远程描述、原始响应（调试用）。
- `WorkflowExecutionResult`：status（SUCCESS/ERROR）、裁剪后的 outputs 摘要、远程 runId、错误信息、耗时/token。
- 注册：Spring 注入 `List<WorkflowSource>`，按 `type()` 路由；新增系统 = 新增一个 `@Component`。

### 3.1 Dify 实现（MVP）

关键事实（已查官方文档核实）：

- **API key 是 app 级别**：一个 key 只能访问一个应用，用 `Authorization: Bearer {appApiKey}`；cloud baseUrl 为 `https://api.dify.ai/v1`。
- **能自动发现元数据和输入 schema**（比 n8n 友好）：
  - `GET /v1/info` → `{ name, description, mode, tags }`。
  - `GET /v1/parameters?user={id}` → `user_input_form`（变量名、类型、必填、label、默认值）。
- **只支持 `mode=workflow` 的应用**（工作流应用走 `/v1/workflows/run`）；chatflow / agent / completion 走不同接口，MVP 不支持，test 时校验 mode 并给出明确提示。
- 各接口对应：
  - `test`：调 `/v1/info`，回显 app name + mode，mode≠workflow 直接标记不支持。
  - `listWorkflows`：用 `/v1/info` 构造**单条** catalog item（remoteWorkflowId 固定如 `self` 或 Dify app id）。
  - `describeRemote`：调 `/v1/parameters`，把 `user_input_form` 转成标准 JSON Schema。
  - `execute`：`POST /v1/workflows/run`，body `{ "inputs": {...}, "response_mode": "blocking", "user": "{endUserId}" }`。
- 同步执行响应：
  ```json
  { "workflow_run_id": "...", "task_id": "...",
    "data": { "id": "...", "workflow_id": "...", "status": "succeeded",
              "outputs": { ... }, "error": null, "elapsed_time": 2.3, "total_tokens": 500 } }
  ```
  - `user` 透传 `RuntimeContext` 的 userId（Dify 用它区分终端用户）。
  - `status=succeeded/failed`；失败读 `data.error`。
  - outputs 可能很大，source 层裁剪成摘要再返回模型。
- 将来异步：Dify 支持 `response_mode=streaming` 和 `GET /v1/workflows/run/{workflow_run_id}`，P3 再做。

## 4. 数据模型（Flyway 迁移，接续 V22）

### V23 `workflow_source`（流水线源，平台级）

对标 `mcp_server`：

| 列 | 说明 |
|---|---|
| id BINARY(16) PK | |
| source_key VARCHAR(128) UNIQUE | 稳定标识，如 `dify-report` |
| name VARCHAR(128) | |
| source_type VARCHAR(32) | SPI 类型：MVP 为 `dify` |
| base_url VARCHAR(2048) | Dify cloud 填 `https://api.dify.ai/v1`，自建填实例地址 |
| config_json TEXT | 类型相关非密配置（**execute_timeout_seconds 等**） |
| secrets_ciphertext TEXT | AES 加密的认证信息（Dify app API Key），不回显 |
| enabled BOOLEAN | |
| last_test_status VARCHAR(32) | UNTESTED/SUCCESS/UNSUPPORTED/FAILED |
| last_test_message VARCHAR(1024) | 测试/识别结果（如 app name、mode 不支持原因） |
| last_tested_at TIMESTAMP(6) | |
| last_synced_at TIMESTAMP(6) | 最近一次目录同步（MVP 手动按钮触发） |
| workflow_count INT | |
| created_at / updated_at TIMESTAMP(6) | |

`config_json` MVP 至少含：`executeTimeoutSeconds`（默认 90，**同步执行超时，配置项**）、`connectTimeoutSeconds`（默认 10）。

### V24 `workflow_catalog_item`（源里的流程 + 全局元数据，平台级）

流程契约的唯一事实来源。Dify 下每个 source 通常只有一条：

| 列 | 说明 |
|---|---|
| id BINARY(16) PK | |
| source_id BINARY(16) FK→workflow_source | |
| remote_workflow_id VARCHAR(255) | 源系统内流程 id（Dify 为 app 自身标识） |
| name VARCHAR(255) | 源侧名称（同步覆盖） |
| remote_mode VARCHAR(32) | 源侧应用类型（Dify: workflow 等） |
| active BOOLEAN | 源侧是否可用 |
| tags_json TEXT | 源侧标签 |
| remote_description TEXT | 源侧原始描述（同步覆盖，参考） |
| **description TEXT** | **owner 维护：给模型看的"适用场景/何时使用"** |
| **input_schema_json MEDIUMTEXT** | **输入契约（Dify 可从 /parameters 自动同步，owner 可微调）** |
| remote_raw_json MEDIUMTEXT | 最近一次同步的原始元数据（调试用） |
| metadata_status VARCHAR(32) | READY（已可用）/ NEEDS_REVIEW（建议 owner 补描述） |
| discovered_at / updated_at TIMESTAMP(6) | |
| UNIQUE(source_id, remote_workflow_id) | |

> 与 n8n 不同：Dify 的 input_schema 可自动生成，不强制 owner 手填；但 description（选型用）仍建议 owner 用业务语言补一句，Dify 自带 description 是面向人的，未必适合模型选型。

### V25 `agent_workflow_binding`（Agent 绑定，Agent 级）

只存"挑选 + 可选微调"，不存 schema：

| 列 | 说明 |
|---|---|
| id BINARY(16) PK | |
| agent_id BINARY(16) FK→agent_asset | |
| catalog_item_id BINARY(16) FK→workflow_catalog_item | |
| description_override TEXT NULL | 非必填，Agent 视角描述覆盖 |
| parameter_defaults_json TEXT NULL | 非必填，给入参设默认/固定值（不改 schema 结构） |
| enabled BOOLEAN | |
| created_at / updated_at TIMESTAMP(6) | |
| UNIQUE(agent_id, catalog_item_id) | |

### V26 `workflow_execution_audit`（执行审计）

| 列 | 说明 |
|---|---|
| id BINARY(16) PK | |
| agent_id / user_id / session_id VARCHAR | 执行上下文 |
| source_id / catalog_item_id | 被触发的流程 |
| inputs_hash VARCHAR(64) | 幂等键（session+workflow+inputsHash） |
| remote_run_id VARCHAR(255) NULL | 源系统 run id（Dify workflow_run_id） |
| status VARCHAR(32) | SUCCESS/ERROR/TIMEOUT |
| result_summary TEXT NULL | 裁剪后的 outputs 摘要 |
| error_message TEXT NULL | |
| elapsed_seconds DOUBLE NULL | 源侧耗时 |
| total_tokens INT NULL | 源侧 token（如有） |
| latency_ms INT | 我方端到端耗时 |
| created_at TIMESTAMP(6) | |

> 不再给 `agent_asset` 加 `workflow_ids_json`——绑定要带 override/defaults，必须独立成行。

## 5. 运行时设计

### 5.1 固定 3 个工具（`WorkflowTools`，每个 agent build 一个实例，持有 agentId）

- `list_workflows()`：返回该 Agent 绑定的流程清单（id、name、描述）。绑定时通常只有 1~几条。
- `describe_workflow(workflowId)`：返回全局 `inputSchema`（叠加绑定的 parameterDefaults 提示）。
- `start_workflow(workflowId, inputsJson)`：授权校验 → 幂等检查 → 调 SPI `execute`（透传 userId 为 Dify endUser）→ 写审计 → 返回裁剪后的 outputs。
- P1 同步，不需要 `get_workflow_result`；留到 P3 异步模式。

### 5.2 授权与安全

- 所有工具先校验 `agentId → catalogItemId ∈ agent_workflow_binding`，防止模型串号。
- 幂等：`sessionId + catalogItemId + inputsHash` 短期去重，防止模型重试逻辑 bug 重复触发真实流程。
- 结果裁剪：SPI 返回层把 outputs 裁成简短摘要（必要时让 workflow 自身输出结构化 JSON），不把原始大 JSON 喂回模型。
- 副作用审批：复用现有 `AgentPermissionMode`，NEEDS_APPROVAL 时 start_workflow 自动走 HITL。
- **超时为配置项**：execute 超时取 source 的 `executeTimeoutSeconds`（默认 90s），并受同步 debug chat 的 120s `CALL_TIMEOUT` 上限约束；两者都可配，前者须小于后者。

### 5.3 Catalog 缓存

- `WorkflowCatalog`（平台级单例）持有各源元数据缓存。
- MVP 同步为**手动按钮触发**（源列表页"同步"按钮 → 调 listWorkflows + describeRemote 刷新本地 catalog），不做定时任务。
- execute 不走缓存；list/describe 读本地表（已同步的元数据），不每次打 Dify。

## 6. 用户旅程（三角色）

### ① 平台管理员 —「工作流 - 集成」菜单
1. 新增源：名称、类型(Dify)、baseUrl（默认 `https://api.dify.ai/v1`）、app API Key。
2. 点"测试连接"：后端调 `/v1/info`，回显 app name / mode；mode≠workflow 给出不支持提示。
3. 点"同步"：调 `/v1/info` + `/v1/parameters`，写入/更新 `workflow_catalog_item`（name/schema 自动填）。
4. owner 补一句业务描述（适用场景），确认后 metadata_status=READY。
5. 一个源对应一个 Dify app；要接多个 app 就建多个源。源可被多个 Agent 复用。

### ② Agent 设计者 — Agent 配置「工作流」Tab
1. 从已就绪的 catalog 流程里勾选要用的（MVP 每条来自一个 Dify 源）。
2. 可选：填 descriptionOverride / parameterDefaults（多数绑定不需要）。
3. 保存 → 写 `agent_workflow_binding`，build 时注册工具。
4. 若源被停用或同步后 app 不可用，标红提示，不自动删绑定。

### ③ 终端用户 — 调试聊天（无感）
1. 用户提需求（如"把这段客户反馈分一下类"）。
2. 模型按 Skill playbook：list_workflows → 读描述选流程 → describe_workflow 取 schema → 填参 → start_workflow。
3. 可能触发 HITL 审批。
4. 同步等待 outputs → 模型基于结果续答。
5. 运行观测页可见本次 workflow 调用、状态、耗时、Dify workflow_run_id。

## 7. Skill 教学（workflow-playbook）

通过 Skill 教模型选择与调用规则（可热更新，不写死在 Java）：
1. 用户要"执行/触发/跑"某个流程时先 list_workflows 确认。
2. start 前必须 describe_workflow，严格按 schema 填参，不臆造字段。
3. 失败时把错误原文转达用户，不自行换流程重试。

## 8. 分期

- **P1（MVP，Dify 跑通）**：workflow_source CRUD + Dify SPI（info/parameters/workflows.run blocking）+ 手动同步自动拉 schema + Agent 绑定 + 3 个工具 + 审计表 + Skill + 「工作流 - 集成」页 + Agent 工作流 Tab。超时可配。
- **P2**：descriptionOverride/defaults 完善 UI、绑定流程预注入、outputs 结构化裁剪与摘要、连通性与同步状态的更细提示。
- **P3**：Dify streaming / run 状态查询、长流程异步与会话恢复；第二个 SPI 实现（n8n 等）；embedding 粗筛；按 tag 自动纳管；定时同步。

## 9. 已定结论

- 同步执行超时作为**源配置项** `executeTimeoutSeconds`（默认 90s，受 chat 120s 上限约束）。
- MVP 不做分级权限，全局元数据由平台管理员维护。
- 目录同步 MVP 为**手动按钮**，不做定时任务。
- MVP 第一个 SPI 实现为 **Dify workflow 应用**。
