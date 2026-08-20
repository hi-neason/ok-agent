import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Button, PageHeader } from "../shared";
import { fetchUserDetail } from "./api";
import type { UserDetail as UserDetailType } from "./types";
import { channelFriendlyName, channelLabel, formatInstant } from "./channelUtil";
import "./usermgmt.css";

function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="ud-row">
      <span className="ud-label">{label}</span>
      <span className="ud-value">{children}</span>
    </div>
  );
}

export function UserDetailPage({ id, onBack }: { id: string; onBack: () => void }) {
  const [detail, setDetail] = useState<UserDetailType | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    setError("");
    fetchUserDetail(id)
      .then(setDetail)
      .catch(() => setError("加载用户详情失败"))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <>
        <PageHeader
          kicker="USER MANAGEMENT / 用户详情"
          title="加载中…"
          description="正在读取用户信息与关联渠道。"
          action={
            <Button quiet onClick={onBack}>
              ← 返回用户列表
            </Button>
          }
        />
      </>
    );
  }

  if (error || !detail) {
    return (
      <>
        <PageHeader
          kicker="USER MANAGEMENT / 用户详情"
          title="无法加载"
          description={error || "未找到该用户"}
          action={
            <Button quiet onClick={onBack}>
              ← 返回用户列表
            </Button>
          }
        />
      </>
    );
  }

  const u = detail.user;
  const sourceLabel = u.source === "CHANNEL" ? "渠道" : "控制台";
  const sourceClass = u.source === "CHANNEL" ? "um-source--channel" : "um-source--console";

  return (
    <>
      <PageHeader
        kicker="USER MANAGEMENT / 用户详情"
        title={u.displayName}
        description={`账号 ${u.username} · 关联 ${detail.channels.length} 个渠道`}
        action={
          <Button quiet onClick={onBack}>
            ← 返回用户列表
          </Button>
        }
      />
      {error && <div className="skill-error">× {error}</div>}

      <div className="ud-grid">
        <section className="form-surface ud-card">
          <div className="form-title">
            <div>
              <p className="kicker">PROFILE / 基本信息</p>
              <h2>{u.displayName}</h2>
            </div>
            <span className={`um-source ${sourceClass}`}>{sourceLabel}</span>
          </div>
          <div className="ud-info">
            <InfoRow label="账号 (username)">{u.username}</InfoRow>
            <InfoRow label="姓名">{u.displayName}</InfoRow>
            <InfoRow label="邮箱">{u.email || "—"}</InfoRow>
            <InfoRow label="电话">{u.phone || "—"}</InfoRow>
            <InfoRow label="所属用户组">{u.groupName || "—"}</InfoRow>
            <InfoRow label="状态">
              <span className={u.enabled ? "um-status on" : "um-status off"}>
                {u.enabled ? "启用" : "停用"}
              </span>
            </InfoRow>
            <InfoRow label="来源">{sourceLabel}</InfoRow>
            <InfoRow label="内部用户ID">
              <code className="ud-mono">{u.userId}</code>
            </InfoRow>
            <InfoRow label="更新时间">{formatInstant(u.updatedAt)}</InfoRow>
          </div>
        </section>

        <section className="form-surface ud-card">
          <div className="form-title">
            <div>
              <p className="kicker">ACTIVITY / 数据概览</p>
              <h2>生命周期统计</h2>
            </div>
          </div>
          <div className="ud-stats">
            <article>
              <span>{detail.sessionCount}</span>
              <small>对话会话</small>
            </article>
            <article>
              <span>{detail.personaCount}</span>
              <small>用户画像</small>
            </article>
            <article>
              <span>{detail.traceCount}</span>
              <small>运行 Trace</small>
            </article>
            <article>
              <span>{detail.messageCount}</span>
              <small>渠道消息</small>
            </article>
          </div>
        </section>
      </div>

      <section className="form-surface ud-card ud-channels">
        <div className="form-title">
          <div>
            <p className="kicker">CHANNELS / 关联渠道</p>
            <h2>绑定的渠道身份（{detail.channels.length}）</h2>
          </div>
        </div>
        {detail.channels.length === 0 ? (
          <div className="um-empty">该用户暂未绑定任何渠道身份。</div>
        ) : (
          <div className="um-channels-list">
            {detail.channels.map((c, i) => {
              const { name, sub } = channelFriendlyName(c);
              return (
                <div className="um-channel-card" key={i}>
                  <div className="um-chan-head">
                    <span className="um-chan-type">{channelLabel(c.channelType)}</span>
                    <span className="um-chan-name">{name}</span>
                  </div>
                  <code className="um-chan-ext">{sub}</code>
                  <div className="um-chan-meta">
                    <span>{c.messageCount} 条消息</span>
                    <span>首次 {formatInstant(c.firstSeenAt)}</span>
                    <span>最近 {formatInstant(c.lastSeenAt)}</span>
                  </div>
                  {c.tenantKey && (
                    <div className="um-chan-foot">
                      <small>租户 {c.tenantKey}</small>
                      <small>渠道实例 {c.channelKey}</small>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </section>
    </>
  );
}
