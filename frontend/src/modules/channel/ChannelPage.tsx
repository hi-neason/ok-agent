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
import { WechatQrLogin } from "./WechatQrLogin";
import type { ChannelInput, ChannelItem } from "./types";
import "./channel.css";

export function ChannelPage() {
  const { confirm, Dialog } = useConfirm();
  const [channels, setChannels] = useState<ChannelItem[]>([]);
  const [agents, setAgents] = useState<AgentOption[]>([]);
  const [agentLoadError, setAgentLoadError] = useState<string | null>(null);
  const [editing, setEditing] = useState<ChannelInput | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [tempCreate, setTempCreate] = useState(false);
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
    setTempCreate(false);
    setEditing(createDraft());
  };

  const openEdit = (item: ChannelItem) => {
    setError(null);
    setEditingId(item.id);
    setTempCreate(false);
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
      wechat: {
        apiBase: item.wechat?.apiBase ?? "",
        channelVersion: item.wechat?.channelVersion ?? "",
      },
      dingtalk: {
        appKey: item.dingtalk?.appKey ?? "",
        appSecret: "",
        robotCode: item.dingtalk?.robotCode ?? "",
        apiBase: item.dingtalk?.apiBase ?? "",
        oapiBase: item.dingtalk?.oapiBase ?? "",
        streamRegisterUrl: item.dingtalk?.streamRegisterUrl ?? "",
      },
    });
  };

  const switchType = (type: ChannelInput["type"]) => {
    setEditing((cur) => {
      if (!cur) return cur;
      const next: ChannelInput = { ...cur, type };
      if (type === "FEISHU") {
        next.feishu = cur.feishu ?? {
          appId: "",
          appSecret: "",
          encryptKey: "",
          verificationToken: "",
        };
        next.wechat = undefined;
      } else if (type === "WECHAT") {
        next.wechat = cur.wechat ?? { apiBase: "", channelVersion: "" };
        if (!next.feishu) {
          next.feishu = {
            appId: "",
            appSecret: "",
            encryptKey: "",
            verificationToken: "",
          };
        }
      } else if (type === "DINGTALK") {
        next.dingtalk = cur.dingtalk ?? { appKey: "", appSecret: "", robotCode: "" };
        if (!next.feishu) {
          next.feishu = {
            appId: "",
            appSecret: "",
            encryptKey: "",
            verificationToken: "",
          };
        }
      }
      return next;
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
      setTempCreate(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  // WECHAT create flow: persist a minimal channel up front so the QR-login
  // panel can call the per-channel login endpoints. The user can still fill
  // name / agent afterwards and hit 保存渠道 (update). If they cancel we
  // delete the temporary channel so it doesn't linger.
  const startWechatBind = async () => {
    if (!editing) return;
    setSaving(true);
    setError(null);
    try {
      const payload: ChannelInput = {
        ...editing,
        name: editing.name?.trim() || "微信渠道（待完善）",
      };
      const saved = await saveChannel(null, payload);
      setChannels((current) => [saved, ...current]);
      setEditingId(saved.id);
      setTempCreate(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const closeEditor = async () => {
    // Clean up a half-created channel if the user bails out.
    if (tempCreate && editingId) {
      try {
        await deleteChannel(editingId);
        setChannels((current) => current.filter((x) => x.id !== editingId));
      } catch {
        /* best-effort; the orphaned channel can be deleted from the list */
      }
    }
    setEditing(null);
    setEditingId(null);
    setTempCreate(false);
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
                  <small>
                    {item.type === "FEISHU"
                      ? item.feishu?.appId ?? item.channelKey
                      : item.type === "WECHAT"
                        ? item.wechat?.apiBase || item.channelKey
                        : item.type === "DINGTALK"
                          ? item.dingtalk?.appKey ?? item.channelKey
                          : item.channelKey}
                  </small>
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
            onMouseDown={() => !saving && void closeEditor()}
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
                <button className="link-button" onClick={() => void closeEditor()}>
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
                      switchType(e.target.value as ChannelInput["type"])
                    }
                  >
                    <option value="FEISHU">飞书</option>
                    <option value="WECHAT">微信（个人号 · ClawBot）</option>
                    <option value="DINGTALK">钉钉（Stream 长连接）</option>
                    <option value="WECOM" disabled>企业微信（规划中）</option>
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

              {editing.type === "FEISHU" && (
                <>
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
                </>
              )}

              {editing.type === "WECHAT" && (
                <>
                  <h3 className="channel-section-title">微信个人号（ClawBot / iLink）</h3>
                  <div className="field-grid">
                    <label className="field">
                      <span>API 地址（apiBase）</span>
                      <input
                        value={editing.wechat?.apiBase ?? ""}
                        placeholder="https://ilinkai.weixin.qq.com"
                        onChange={(e) =>
                          setEditing({
                            ...editing,
                            wechat: {
                              ...(editing.wechat ?? { apiBase: "", channelVersion: "" }),
                              apiBase: e.target.value,
                            },
                          })
                        }
                      />
                    </label>
                    <label className="field">
                      <span>协议版本（channelVersion）</span>
                      <input
                        value={editing.wechat?.channelVersion ?? ""}
                        placeholder="0.1.0"
                        onChange={(e) =>
                          setEditing({
                            ...editing,
                            wechat: {
                              ...(editing.wechat ?? { apiBase: "", channelVersion: "" }),
                              channelVersion: e.target.value,
                            },
                          })
                        }
                      />
                    </label>
                  </div>

                  {editingId ? (
                    <WechatQrLogin
                      channelId={editingId}
                      onCancel={
                        tempCreate ? () => void closeEditor() : undefined
                      }
                    />
                  ) : (
                    <div className="feishu-qr-panel">
                      <div className="feishu-qr-head">
                        <b>微信扫码登录（个人号 · ClawBot）</b>
                      </div>
                      <div className="feishu-qr-tip">
                        点击下方按钮，系统将创建渠道并立即调出微信扫码二维码。
                        扫码成功后再补全渠道名称与绑定 Agent，最后点「保存渠道」即可。
                        <div style={{ marginTop: 10 }}>
                          <Button
                            onClick={startWechatBind}
                            disabled={saving || !!editingId}
                          >
                            {saving ? "正在准备…" : "▣ 开始扫码绑定"}
                          </Button>
                        </div>
                      </div>
                    </div>
                  )}

                  <div className="channel-hint">
                    微信个人号走 iLink 长轮询接收消息，<b>仅支持私聊</b>，不支持群聊。
                    扫码登录后，bot_token 加密保存在服务端；context_token 用于回复消息，有效期约 24 小时。
                    {editingId
                      ? "请将上方渠道开关打开并启动渠道，登录态生效后会自动拉取消息。"
                      : "保存渠道后，请在列表中打开开关并启动渠道。"}
                  </div>
                </>
              )}

              {editing.type === "DINGTALK" && (
                <>
                  <h3 className="channel-section-title">钉钉企业内部应用（Stream 长连接）</h3>
                  <div className="field-grid">
                    <label className="field">
                      <span>AppKey（clientId）</span>
                      <input
                        value={editing.dingtalk?.appKey ?? ""}
                        placeholder="dingxxxxxxxxxx"
                        onChange={(e) =>
                          setEditing({
                            ...editing,
                            dingtalk: {
                              ...(editing.dingtalk ?? { appKey: "", appSecret: "", robotCode: "" }),
                              appKey: e.target.value,
                            },
                          })
                        }
                      />
                    </label>
                    <label className="field">
                      <span>AppSecret（clientSecret）</span>
                      <input
                        type="password"
                        autoComplete="new-password"
                        value={editing.dingtalk?.appSecret ?? ""}
                        placeholder={editingId ? "已配置，留空则保持不变" : "必填"}
                        onChange={(e) =>
                          setEditing({
                            ...editing,
                            dingtalk: {
                              ...(editing.dingtalk ?? { appKey: "", appSecret: "", robotCode: "" }),
                              appSecret: e.target.value,
                            },
                          })
                        }
                      />
                    </label>
                    <label className="field">
                      <span>RobotCode（机器人编码）</span>
                      <input
                        value={editing.dingtalk?.robotCode ?? ""}
                        placeholder="dingxxxxxxxxxx"
                        onChange={(e) =>
                          setEditing({
                            ...editing,
                            dingtalk: {
                              ...(editing.dingtalk ?? { appKey: "", appSecret: "", robotCode: "" }),
                              robotCode: e.target.value,
                            },
                          })
                        }
                      />
                    </label>
                  </div>

                  <details className="channel-advanced">
                    <summary>高级：接口地址覆盖（一般无需修改）</summary>
                    <div className="field-grid">
                      <label className="field">
                        <span>API Base</span>
                        <input
                          value={editing.dingtalk?.apiBase ?? ""}
                          placeholder="https://api.dingtalk.com"
                          onChange={(e) =>
                            setEditing({
                              ...editing,
                              dingtalk: {
                                ...(editing.dingtalk ?? { appKey: "", appSecret: "", robotCode: "" }),
                                apiBase: e.target.value,
                              },
                            })
                          }
                        />
                      </label>
                      <label className="field">
                        <span>OAPI Base（旧版 gettoken）</span>
                        <input
                          value={editing.dingtalk?.oapiBase ?? ""}
                          placeholder="https://oapi.dingtalk.com"
                          onChange={(e) =>
                            setEditing({
                              ...editing,
                              dingtalk: {
                                ...(editing.dingtalk ?? { appKey: "", appSecret: "", robotCode: "" }),
                                oapiBase: e.target.value,
                              },
                            })
                          }
                        />
                      </label>
                      <label className="field">
                        <span>Stream 注册地址</span>
                        <input
                          value={editing.dingtalk?.streamRegisterUrl ?? ""}
                          placeholder="https://api.dingtalk.com/v1.0/gateway/connections/open"
                          onChange={(e) =>
                            setEditing({
                              ...editing,
                              dingtalk: {
                                ...(editing.dingtalk ?? { appKey: "", appSecret: "", robotCode: "" }),
                                streamRegisterUrl: e.target.value,
                              },
                            })
                          }
                        />
                      </label>
                    </div>
                  </details>

                  <div className="channel-hint">
                    钉钉走 Stream 模式（持久 WebSocket）接收消息，<b>无需公网回调地址</b>。
                    请在钉钉开放平台创建企业内部应用并添加「机器人」能力，发布后获取 AppKey / AppSecret / RobotCode。
                    单聊直接对话，群聊需 @ 机器人。保存后在渠道列表点击「启动」即可连接。
                  </div>
                </>
              )}

              {error && <div className="channel-form-error">{error}</div>}

              <div className="sticky-actions">
                <Button quiet onClick={() => void closeEditor()} disabled={saving}>
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
