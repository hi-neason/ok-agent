# ok-agent

面向智能销售与智能客服场景的 Agent 控制台和运行平台。当前 MVP 已形成从机器人接待、人工接管到业务转化与服务复盘的最小闭环。

## MVP 业务闭环

1. 渠道或调试会话进入统一的「会话工作台」。
2. 机器人可把会话转入人工队列，运营人员可认领、分配负责人、调整优先级并推进处理状态。
3. 处理人员沉淀会话摘要、客户需求、意向标签、关注产品、预算、购买周期、客户情绪、解决结果和后续动作。
4. 会话可幂等转化为销售线索或客服工单，业务记录保留来源会话、客户、负责人和优先级。
5. 工作台记录 1–5 分客户满意度，并展示会话、人工队列、解决量、线索、工单和平均满意度指标。

工作台入口：`/inbox`。

## 核心能力

- Agent 配置、调试、版本冻结和渠道发布
- 模型、技能、MCP、知识库、工作流和产品能力管理
- 钉钉、飞书、微信等渠道接入
- 用户、画像、意图和权限管理
- 统一会话队列、人工接管和全量对话上下文
- 结构化会话结果、销售线索、客服工单和满意度指标
- 中英文界面

## 本地启动

后端要求 Java 17+、Maven 和 MySQL；前端要求 Node.js。后端安全密钥必须保持稳定，否则已保存的加密凭据将无法解密。

```bash
cd backend
export OK_AGENT_API_KEY_ENCRYPTION_KEY="replace-with-a-stable-private-key"
export OK_AGENT_JWT_SECRET="replace-with-at-least-32-random-characters"
mvn -pl ok-agent-server spring-boot:run
```

首次初始化管理员时，另行设置 `OK_AGENT_BOOTSTRAP_ADMIN_PASSWORD`，长度至少 12 位。账号初始化完成后不再传入该变量。

```bash
cd frontend
npm install
npm run dev
```

- 控制台：http://127.0.0.1:4173
- 后端健康检查：http://127.0.0.1:8080/actuator/health

## 验证

```bash
cd backend && mvn test
cd frontend && npm run build
```

## MVP 边界

当前线索与工单聚焦“从会话创建并可追溯”，尚未包含独立的 CRM 销售漏斗、工单 SLA/升级规则、自动跟进任务、外部 CRM 双向同步和按时间区间的高级报表。这些能力适合作为下一阶段扩展。
