import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Button, PageHeader, Toggle, useConfirm } from "../shared";
import { loadAgents, type AgentOption } from "../agent/api";
import {
  createDraft,
  runtimeLabel,
  statusTone,
  typeLabel,
} from "./channel-ui";
import {
  deleteChannel,
  fetchChannels,
  saveChannel,
  setChannelEnabled,
  setChannelRuntime,
} from "./api";
import { FeishuQrScan } from "./FeishuQrScan";
import type { ChannelInput, ChannelItem } from "./types";
import "./channel.css";

export function ChannelPage() {
  const { confirm, Dialog } = useConfirm();
  const [channels, setChannels] = useState<ChannelItem[]>([]);
  const [agents, setAgents] = useState<AgentOption[]>([]);
  const [agentLoadError, setAgentLoadError] = useState<string | null>(null);
  const [editing, setEditing] = useState<ChannelInput | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = () => {
    void fetchChannels()
      .then(setChannels)
      .catch(() => undefined);
  };

  useEffect(() => {
    reload();
    void loadAgents()
      .then((list) => {
        setAgents(list);
        setAgentLoadError(list.length === 0 ? "未加载到任何 Agent，请确认后端已启动且存在启用的 Agent。" : null);
      })
      .catch(() => setAgentLoadError("加载 Agent 列表失败，请确认后端服务已启动。"));
  }, []);

  const agentName = useMemo(() => {
    const map = new Map<string, string>();
    agents.forEach((a) => map.set(a.id, a.name));
    return (id: string | null) => (id ? map.get(id) ?? id : "—");
  }, [agents]);

  const openCreate = () => {
    setError(null);
    setEditingId(null);
    setEditing(createDraft());
  };

  const openEdit = (item: ChannelItem) => {
    setError(null);
    setEditingId(item.id);
    setEditing({
      name: item.name,
      type: item.type,
      boundAgentId: item.boundAgentId,
      dmScope: item.dmScope,
      enabled: item.enabled,
      feishu: {
        appId: item.feishu?.appId ?? "",
        apiBase: item.feishu?.apiBase,
        callbackPath: item.feishu?.callbackPath,
        appSecret: "",
        encryptKey: "",
        verificationToken: "",
      },
    });
  };

  const save = async () => {
    if (!editing) return;
    setSaving(true);
    setError(null);
    try {
      const saved = await saveChannel(editingId, editing);
      setChannels((current) =>
        editingId
          ? current.map((x) => (x.id === saved.id ? saved : x))
          : [saved, ...current],
      );
      setEditing(null);
      setEditingId(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (item: ChannelItem, enabled: boolean) => {
    const saved = await setChannelEnabled(item.id, enabled);
    setChannels((current) =>
      current.map((x) => (x.id === item.id ? saved : x)),
    );
  };

  const toggleRuntime = async (item: ChannelItem, action: "start" | "stop") => {
    const saved = await setChannelRuntime(item.id, action);
    setChannels((current) =>
      current.map((x) => (x.id === item.id ? saved : x)),
    );
  };

  const remove = async (item: ChannelItem) => {
    const ok = await confirm({
      title: "删除渠道",
      message: `确认删除渠道「${item.name}」？删除后将停止运行，此操作不可恢复。`,
      confirmText: "删除",
    });
    if (!ok) return;
    await deleteChannel(item.id);
    setChannels((current) => current.filter((x) => x.id !== item.id));
  };

  return (
    <>
      <PageHeader
        kicker="CHANNEL / REGISTRY"
        title="渠道管理"
        description="将 Agent 绑定到飞书等通讯渠道，用户可通过个人或企业通讯工具与 Agent 对话。"
        action={<Button onClick={openCreate}>＋ 新增渠道</Button>}
      />
      <section className="run-table">
        <div className="table-tools">
          <div className="search-mini">◌ 共 {channels.length} 个渠道</div>
        </div>
        {channels.length === 0 ? (
          <div className="channel-empty">
            <p>还没有渠道，点击右上角「新增渠道」绑定飞书机器人。</p>
          </div>
        ) : (
          <>
            <div className="table-head channel-table-row">
              <span>渠道名称</span>
              <span>类型</span>
              <span>绑定 Agent</span>
              <span>对话用户</span>
              <span>运行状态</span>
              <span>启用</span>
              <span>操作</span>
            </div>
            {channels.map((item) => (
              <div className="table-row channel-table-row" key={item.id}>
                <span>
                  <b>{item.name}</b>
                  <small>{item.feishu?.appId ?? item.channelKey}</small>
                </span>
                <span>{typeLabel(item.type)}</span>
                <span>{agentName(item.boundAgentId)}</span>
                <span>{item.userCount ?? 0} 人</span>
                <span>
                  <span className={`channel-status channel-status--${statusTone(item.runtimeStatus)}`}>
                    {runtimeLabel(item.runtimeStatus)}
                  </span>
                  {item.lastError && (
                    <small className="channel-error" title={item.lastError}>
                      {item.lastError}
                    </small>
                  )}
                </span>
                <span>
                  <Toggle
                    on={item.enabled}
                    setOn={(next) => toggleEnabled(item, next)}
                    label={`启用 ${item.name}`}
                  />
                </span>
                <span className="model-actions">
                  {item.runtimeStatus === "RUNNING" ? (
                    <button
                      className="link-button"
                      onClick={() => toggleRuntime(item, "stop")}
                    >
                      停止
                    </button>
                  ) : (
                    <button
                      className="link-button"
                      onClick={() => toggleRuntime(item, "start")}
                    >
                      启动
                    </button>
                  )}
                  <button
                    className="link-button"
                    onClick={() => openEdit(item)}
                  >
                    编辑
                  </button>
                  <button
                    className="link-button"
                    onClick={() => remove(item)}
                  >
                    删除
                  </button>
                </span>
              </div>
            ))}
          </>
        )}
      </section>

      {editing &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => !saving && setEditing(null)}
          >
            <div
              className="form-surface channel-editor"
              role="dialog"
              aria-modal="true"
              aria-label="渠道配置"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">{editingId ? "编辑渠道" : "新增渠道"}</p>
                  <h2>{editingId ? editing.name : "新增渠道"}</h2>
                </div>
                <button className="link-button" onClick={() => setEditing(null)}>
                  关闭 ×
                </button>
              </div>

              <div className="field-grid">
                <label className="field">
                  <span>渠道名称</span>
                  <input
                    value={editing.name}
                    placeholder="例如：客服飞书机器人"
                    onChange={(e) =>
                      setEditing({ ...editing, name: e.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>渠道类型</span>
                  <select
                    value={editing.type}
                    disabled={Boolean(editingId)}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        type: e.target.value as ChannelInput["type"],
                      })
                    }
                  >
                    <option value="FEISHU">飞书</option>
                    <option value="DINGTALK" disabled>钉钉（规划中）</option>
                    <option value="WECOM" disabled>企业微信（规划中）</option>
                    <option value="WECHAT" disabled>微信（规划中）</option>
                  </select>
                </label>
                <label className="field">
                  <span>绑定 Agent</span>
                  <select
                    value={editing.boundAgentId ?? ""}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        boundAgentId: e.target.value || null,
                      })
                    }
                  >
                    <option value="">— 请选择 —</option>
                    {agents.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name}
                      </option>
                    ))}
                  </select>
                  {agentLoadError && (
                    <small className="channel-error">{agentLoadError}</small>
                  )}
                </label>
                <label className="field">
                  <span>会话作用域（DmScope）</span>
                  <select
                    value={editing.dmScope}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        dmScope: e.target.value as ChannelInput["dmScope"],
                      })
                    }
                  >
                    <option value="PER_PEER">每个对话用户独立会话</option>
                    <option value="MAIN">所有用户共享一个会话</option>
                    <option value="PER_CHANNEL_PEER">渠道 + 对话用户</option>
                    <option value="PER_ACCOUNT_CHANNEL_PEER">
                      账号 + 渠道 + 对话用户
                    </option>
                  </select>
                </label>
              </div>

              <div className="channel-hint">
                渠道绑定的是一个机器人（BOT），任何给该 BOT 发消息的人都会成为对话用户，
                Agent 的记忆与画像按各自身份自动隔离，无需在此指定归属用户。
              </div>

              <h3 className="channel-section-title">飞书自建应用配置</h3>
              <FeishuQrScan
                onSuccess={({ appId, appSecret }) => {
                  setEditing((cur) =>
                    cur
                      ? {
                          ...cur,
                          feishu: {
                            ...cur.feishu,
                            appId,
                            ...(appSecret ? { appSecret } : {}),
                          },
                        }
                      : cur,
                  );
                }}
              />
              <div className="channel-or">或手动填写 App ID / App Secret</div>
              <div className="field-grid">
                <label className="field">
                  <span>App ID（cli_xxx）</span>
                  <input
                    value={editing.feishu.appId}
                    placeholder="cli_a1b2c3d4e5"
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        feishu: { ...editing.feishu, appId: e.target.value },
                      })
                    }
                  />
                </label>
                <label className="field">
                  <span>App Secret</span>
                  <input
                    type="password"
                    autoComplete="new-password"
                    value={editing.feishu.appSecret ?? ""}
                    placeholder={
                      editingId ? "已配置，留空则保持不变" : "必填"
                    }
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        feishu: {
                          ...editing.feishu,
                          appSecret: e.target.value,
                        },
                      })
                    }
                  />
                </label>
              </div>

              <div className="channel-hint">
                保存后系统通过飞书长连接（WebSocket）自动接收消息，<b>无需公网回调地址或内网穿透</b>。
                请在飞书开放平台确认：① 开启「机器人」能力；② 事件订阅选择「使用长连接接收事件」，
                并添加 <code>im.message.receive_v1</code> 事件；③ 发布应用版本。单聊直接对话，群聊需 @ 机器人。
              </div>

              {error && <div className="channel-form-error">{error}</div>}

              <div className="sticky-actions">
                <Button quiet onClick={() => setEditing(null)} disabled={saving}>
                  取消
                </Button>
                <Button onClick={save} disabled={saving}>
                  {saving ? "保存中…" : "保存渠道"}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
      <Dialog />
    </>
  );
}
