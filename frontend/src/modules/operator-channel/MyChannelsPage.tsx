import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, PageHeader } from "../shared";
import { fetchMyChannels, fetchPresence, updatePresence } from "./api";
import type { MyChannel, OperatorPresenceStatus } from "./types";
import "./my-channels.css";

const presenceOptions: OperatorPresenceStatus[] = ["ONLINE", "BUSY", "OFFLINE"];

export function MyChannelsPage() {
  const { t, i18n } = useTranslation();
  const [channels, setChannels] = useState<MyChannel[]>([]);
  const [presence, setPresence] = useState<OperatorPresenceStatus>("OFFLINE");
  const [loading, setLoading] = useState(true);
  const [savingPresence, setSavingPresence] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextChannels, nextPresence] = await Promise.all([fetchMyChannels(), fetchPresence()]);
      setChannels(nextChannels);
      setPresence(nextPresence.status);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : String(caught));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const summary = useMemo(() => ({
    running: channels.filter((channel) => channel.enabled && channel.runtimeStatus === "RUNNING").length,
    customers: channels.reduce((total, channel) => total + channel.customerCount, 0),
  }), [channels]);

  const setOperatorPresence = async (next: OperatorPresenceStatus) => {
    if (next === presence || savingPresence) return;
    const previous = presence;
    setPresence(next);
    setSavingPresence(true);
    setError(null);
    try {
      setPresence((await updatePresence(next)).status);
    } catch (caught) {
      setPresence(previous);
      setError(caught instanceof Error ? caught.message : String(caught));
    } finally {
      setSavingPresence(false);
    }
  };

  return (
    <>
      <PageHeader
        kicker={t("operatorChannels.kicker")}
        title={t("operatorChannels.title")}
        description={t("operatorChannels.description")}
        action={<Button quiet onClick={() => void load()}>↻ {t("operatorChannels.refresh")}</Button>}
      />

      <section className="operator-channel-commandbar">
        <div>
          <small>{t("operatorChannels.presence.label")}</small>
          <div className="operator-presence-options" aria-label={t("operatorChannels.presence.label")}>
            {presenceOptions.map((option) => (
              <button
                key={option}
                className={`presence-${option.toLowerCase()} ${presence === option ? "active" : ""}`}
                disabled={savingPresence}
                onClick={() => void setOperatorPresence(option)}
              >
                <i /> {t(`operatorChannels.presence.${option}`)}
              </button>
            ))}
          </div>
        </div>
        <div className="operator-channel-summary">
          <span><b>{channels.length}</b><small>{t("operatorChannels.summary.assigned")}</small></span>
          <span><b>{summary.running}</b><small>{t("operatorChannels.summary.running")}</small></span>
          <span><b>{summary.customers}</b><small>{t("operatorChannels.summary.customers")}</small></span>
        </div>
      </section>

      {error && <div className="operator-channel-error">{error}</div>}
      {loading ? (
        <div className="operator-channel-state">{t("common.loading")}</div>
      ) : channels.length === 0 ? (
        <div className="operator-channel-empty">
          <span>⇄</span>
          <h2>{t("operatorChannels.empty.title")}</h2>
          <p>{t("operatorChannels.empty.description")}</p>
        </div>
      ) : (
        <section className="operator-channel-grid">
          {channels.map((channel) => (
            <article className="operator-channel-card" key={channel.id}>
              <header>
                <span className={`operator-channel-provider provider-${channel.type.toLowerCase()}`}>
                  {t(`channels.types.${channel.type}`)}
                </span>
                <span className={`operator-channel-runtime runtime-${channel.runtimeStatus.toLowerCase()}`}>
                  <i /> {t(`channels.runtime.${channel.runtimeStatus}`)}
                </span>
              </header>
              <h2>{channel.name}</h2>
              <p>{channel.boundAgentName || t("operatorChannels.unboundAgent")}</p>
              <dl>
                <div><dt>{t("operatorChannels.metrics.customers")}</dt><dd>{channel.customerCount}</dd></div>
                <div><dt>{t("operatorChannels.metrics.team")}</dt><dd>{channel.operatorCount}</dd></div>
                <div><dt>{t("operatorChannels.metrics.assignedAt")}</dt><dd>{new Intl.DateTimeFormat(i18n.language, { month: "short", day: "numeric" }).format(new Date(channel.assignedAt))}</dd></div>
              </dl>
              {!channel.enabled && <footer>{t("operatorChannels.disabledHint")}</footer>}
            </article>
          ))}
        </section>
      )}
    </>
  );
}
