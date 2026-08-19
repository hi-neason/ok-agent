import { useEffect, useMemo, useState } from "react";
import { Button, PageHeader } from "../shared";
import { channelTypeLabel, fetchChannelUsers, formatTime } from "./api";
import type { ChannelUser } from "./types";
import "./channel-users.css";

export function ChannelUsersPage() {
  const [users, setUsers] = useState<ChannelUser[]>([]);
  const [typeFilter, setTypeFilter] = useState<string>("ALL");
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = () => {
    setLoading(true);
    fetchChannelUsers()
      .then((list) => {
        setUsers(list);
        setError(null);
      })
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload();
  }, []);

  const types = useMemo(() => {
    const set = new Set(users.map((u) => u.channelType));
    return ["ALL", ...Array.from(set)];
  }, [users]);

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return users.filter((u) => {
      if (typeFilter !== "ALL" && u.channelType !== typeFilter) return false;
      if (!kw) return true;
      return (
        (u.displayName ?? "").toLowerCase().includes(kw) ||
        u.externalId.toLowerCase().includes(kw) ||
        (u.tenantKey ?? "").toLowerCase().includes(kw) ||
        u.channelKey.toLowerCase().includes(kw)
      );
    });
  }, [users, typeFilter, keyword]);

  return (
    <>
      <PageHeader
        kicker="CHANNEL / USERS"
        title="渠道用户"
        description="自动沉淀所有通过飞书等渠道与机器人对话的用户，按渠道身份独立记录。"
        action={
          <Button onClick={reload} quiet>
            ↻ 刷新
          </Button>
        }
      />

      <section className="run-table">
        <div className="table-tools channel-user-tools">
          <div className="search-mini">
            ◌ 共 {filtered.length} 位对话用户
            {loading ? "（加载中…）" : ""}
          </div>
          <div className="channel-user-filters">
            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
            >
              {types.map((t) => (
                <option key={t} value={t}>
                  {t === "ALL" ? "全部渠道" : channelTypeLabel(t)}
                </option>
              ))}
            </select>
            <input
              value={keyword}
              placeholder="搜索昵称 / 外部 ID / BOT"
              onChange={(e) => setKeyword(e.target.value)}
            />
          </div>
        </div>

        {error && <div className="channel-user-error">{error}</div>}

        {filtered.length === 0 ? (
          <div className="channel-empty">
            <p>
              {loading
                ? "正在加载渠道用户…"
                : "还没有渠道对话用户。当有人通过飞书机器人给绑定的 Agent 发消息后，这里会自动出现。"}
            </p>
          </div>
        ) : (
          <>
            <div className="table-head channel-user-row">
              <span>用户</span>
              <span>渠道</span>
              <span>所属 BOT</span>
              <span>消息数</span>
              <span>首次对话</span>
              <span>最近对话</span>
            </div>
            {filtered.map((u) => {
              const key = `${u.channelType}:${u.channelKey}:${u.externalId}`;
              return (
                <div className="table-row channel-user-row" key={key}>
                  <span>
                    <b>{u.displayName || "未命名用户"}</b>
                    <small>{u.externalId}</small>
                  </span>
                  <span>{channelTypeLabel(u.channelType)}</span>
                  <span>
                    <code>{u.channelKey.slice(0, 8)}…</code>
                  </span>
                  <span>{u.messageCount}</span>
                  <span>{formatTime(u.firstSeenAt)}</span>
                  <span>{formatTime(u.lastSeenAt)}</span>
                </div>
              );
            })}
          </>
        )}
      </section>
    </>
  );
}
