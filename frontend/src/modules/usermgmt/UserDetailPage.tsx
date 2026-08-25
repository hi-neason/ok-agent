import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
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
  const { t, i18n } = useTranslation();
  const [detail, setDetail] = useState<UserDetailType | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    setError("");
    fetchUserDetail(id)
      .then(setDetail)
      .catch(() => setError(t("users.detail.loadFailed")))
      .finally(() => setLoading(false));
  }, [id, t]);

  if (loading) {
    return (
      <>
        <PageHeader
          kicker={t("users.detail.kicker")}
          title={t("common.loading")}
          description={t("users.detail.loadingDescription")}
          action={
            <Button quiet onClick={onBack}>
              ← {t("users.detail.back")}
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
          kicker={t("users.detail.kicker")}
          title={t("users.detail.unableToLoad")}
          description={error || t("users.detail.notFound")}
          action={
            <Button quiet onClick={onBack}>
              ← {t("users.detail.back")}
            </Button>
          }
        />
      </>
    );
  }

  const u = detail.user;
  const sourceLabel = u.source === "CHANNEL" ? t("users.channel") : t("users.console");
  const sourceClass = u.source === "CHANNEL" ? "um-source--channel" : "um-source--console";

  return (
    <>
      <PageHeader
        kicker={t("users.detail.kicker")}
        title={u.displayName}
        description={t("users.detail.summary", {
          username: u.username,
          count: detail.channels.length,
        })}
        action={
          <Button quiet onClick={onBack}>
            ← {t("users.detail.back")}
          </Button>
        }
      />
      {error && <div className="skill-error">× {error}</div>}

      <div className="ud-grid">
        <section className="form-surface ud-card">
          <div className="form-title">
            <div>
              <p className="kicker">
                {t("kickers.profileSection", { section: t("users.detail.profile") })}
              </p>
              <h2>{u.displayName}</h2>
            </div>
            <span className={`um-source ${sourceClass}`}>{sourceLabel}</span>
          </div>
          <div className="ud-info">
            <InfoRow label={t("users.username")}>{u.username}</InfoRow>
            <InfoRow label={t("users.name")}>{u.displayName}</InfoRow>
            <InfoRow label={t("users.email")}>{u.email || "—"}</InfoRow>
            <InfoRow label={t("users.phone")}>{u.phone || "—"}</InfoRow>
            <InfoRow label={t("users.group")}>{u.groupName || "—"}</InfoRow>
            <InfoRow label={t("common.status")}>
              <span className={u.enabled ? "um-status on" : "um-status off"}>
                {u.enabled ? t("common.enabled") : t("common.disabled")}
              </span>
            </InfoRow>
            <InfoRow label={t("users.source")}>{sourceLabel}</InfoRow>
            <InfoRow label={t("users.detail.internalId")}>
              <code className="ud-mono">{u.userId}</code>
            </InfoRow>
            <InfoRow label={t("users.detail.updatedAt")}>
              {formatInstant(u.updatedAt, i18n.resolvedLanguage ?? "zh-CN")}
            </InfoRow>
          </div>
        </section>

        <section className="form-surface ud-card">
          <div className="form-title">
            <div>
              <p className="kicker">
                {t("kickers.activitySection", { section: t("users.detail.activity") })}
              </p>
              <h2>{t("users.detail.lifecycle")}</h2>
            </div>
          </div>
          <div className="ud-stats">
            <article>
              <span>{detail.sessionCount}</span>
              <small>{t("users.detail.sessions")}</small>
            </article>
            <article>
              <span>{detail.personaCount}</span>
              <small>{t("users.detail.personas")}</small>
            </article>
            <article>
              <span>{detail.traceCount}</span>
              <small>{t("users.detail.traces")}</small>
            </article>
            <article>
              <span>{detail.messageCount}</span>
              <small>{t("users.detail.messages")}</small>
            </article>
          </div>
        </section>
      </div>

      <section className="form-surface ud-card ud-channels">
        <div className="form-title">
          <div>
            <p className="kicker">
              {t("kickers.channelsSection", { section: t("users.detail.channels") })}
            </p>
            <h2>{t("users.detail.boundChannels", { count: detail.channels.length })}</h2>
          </div>
        </div>
        {detail.channels.length === 0 ? (
          <div className="um-empty">{t("users.detail.channelsEmpty")}</div>
        ) : (
          <div className="um-channels-list">
            {detail.channels.map((c, i) => {
              const { name, sub } = channelFriendlyName(c, t);
              return (
                <div className="um-channel-card" key={i}>
                  <div className="um-chan-head">
                    <span className="um-chan-type">{channelLabel(c.channelType, t)}</span>
                    <span className="um-chan-name">{name}</span>
                  </div>
                  <code className="um-chan-ext">{sub}</code>
                  <div className="um-chan-meta">
                    <span>{t("users.detail.messageCount", { count: c.messageCount })}</span>
                    <span>
                      {t("users.detail.firstSeen", {
                        time: formatInstant(c.firstSeenAt, i18n.resolvedLanguage ?? "zh-CN"),
                      })}
                    </span>
                    <span>
                      {t("users.detail.lastSeen", {
                        time: formatInstant(c.lastSeenAt, i18n.resolvedLanguage ?? "zh-CN"),
                      })}
                    </span>
                  </div>
                  {c.tenantKey && (
                    <div className="um-chan-foot">
                      <small>{t("users.detail.tenant", { key: c.tenantKey })}</small>
                      <small>{t("users.detail.channelInstance", { key: c.channelKey })}</small>
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
