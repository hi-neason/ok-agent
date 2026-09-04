import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, PageHeader } from "../shared";
import {
  createVersion,
  getCurrentRelease,
  getVersion,
  listAgentReleases,
  listAgents,
  listChannels,
  listVersions,
  publishRelease,
  rollbackChannel,
} from "./api";
import type {
  AgentOption,
  ChannelOption,
  ReleaseItem,
  VersionDetail,
  VersionSummary,
} from "./types";
import "./release.css";

function shortHash(hash: string | null | undefined): string {
  return hash ? hash.slice(0, 8) : "—";
}

function formatTime(iso: string, locale: string): string {
  try {
    return new Date(iso).toLocaleString(locale, { hour12: false });
  } catch {
    return iso;
  }
}

export function ReleasePage() {
  const { t, i18n } = useTranslation();
  const [agents, setAgents] = useState<AgentOption[]>([]);
  const [channels, setChannels] = useState<ChannelOption[]>([]);
  const [agentId, setAgentId] = useState<string>("");
  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [releases, setReleases] = useState<ReleaseItem[]>([]);
  const [selected, setSelected] = useState<VersionDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const [label, setLabel] = useState("");
  const [changelog, setChangelog] = useState("");
  const [saving, setSaving] = useState(false);

  const [targetChannelId, setTargetChannelId] = useState<string>("");
  const [publishing, setPublishing] = useState(false);

  const [channelCurrent, setChannelCurrent] = useState<
    Record<string, ReleaseItem | null>
  >({});

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const [a, c] = await Promise.all([listAgents(), listChannels()]);
        if (cancelled) return;
        setAgents(a);
        setChannels(c);
        if (a.length > 0) setAgentId(a[0].id);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      }
    })();
    return () => { cancelled = true; };
  }, []);

  const agentChannels = useMemo(
    () => channels.filter((c) => !c.boundAgentId || c.boundAgentId === agentId),
    [channels, agentId],
  );

  async function refresh() {
    if (!agentId) return;
    setLoading(true);
    setError("");
    try {
      const [vs, rs] = await Promise.all([
        listVersions(agentId),
        listAgentReleases(agentId),
      ]);
      setVersions(vs);
      setReleases(rs);
      const currents: Record<string, ReleaseItem | null> = {};
      await Promise.all(
        channels.map(async (ch) => {
          currents[ch.id] = await getCurrentRelease(ch.id).catch(() => null);
        }),
      );
      setChannelCurrent(currents);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentId]);

  async function openVersion(id: string) {
    setError("");
    try {
      const detail = await getVersion(agentId, id);
      setSelected(detail);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function handleCreateVersion() {
    if (!agentId) return;
    setSaving(true);
    setError("");
    try {
      const created = await createVersion(agentId, label.trim(), changelog.trim());
      setLabel("");
      setChangelog("");
      await listVersions(agentId).then(setVersions);
      setSelected(created);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }

  async function handlePublish() {
    if (!agentId || !selected || !targetChannelId) return;
    setPublishing(true);
    setError("");
    try {
      await publishRelease(agentId, selected.versionNo, targetChannelId);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setPublishing(false);
    }
  }

  async function handleRollback(channelId: string) {
    setError("");
    try {
      await rollbackChannel(channelId);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  let snapshotPretty = "";
  if (selected) {
    try {
      snapshotPretty = JSON.stringify(JSON.parse(selected.snapshotJson), null, 2);
    } catch {
      snapshotPretty = selected.snapshotJson;
    }
  }

  return (
    <>
      <PageHeader
        kicker={t("release.kicker")}
        title={t("release.title")}
        description={t("release.description")}
      />
      <div className="rel-toolbar">
        <label className="rel-field">
          <span>{t("release.selectAgent")}</span>
          <select
            value={agentId}
            onChange={(e) => {
              setAgentId(e.target.value);
              setSelected(null);
              setTargetChannelId("");
            }}
          >
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}（{a.agentKey}）
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <div className="rel-error">⚠ {error}</div>}

      <div className="rel-layout">
        <section className="rel-col">
          <div className="rel-card">
            <h3>{t("release.createTitle")}</h3>
            <p className="rel-hint">{t("release.createHint")}</p>
            <label className="rel-field">
              <span>{t("release.versionLabel")}</span>
              <input
                value={label}
                onChange={(e) => setLabel(e.target.value)}
                placeholder={t("release.versionLabelPlaceholder")}
              />
            </label>
            <label className="rel-field">
              <span>{t("release.changelog")}</span>
              <textarea
                value={changelog}
                onChange={(e) => setChangelog(e.target.value)}
                rows={3}
                placeholder={t("release.changelogPlaceholder")}
              />
            </label>
            <Button onClick={handleCreateVersion} disabled={saving}>
              {saving ? t("release.freezing") : t("release.freeze")}
            </Button>
          </div>

          <div className="rel-card">
            <h3>{t("release.timeline")}</h3>
            {loading && <p className="rel-hint">{t("common.loading")}</p>}
            {!loading && versions.length === 0 && (
              <p className="rel-hint">{t("release.emptyVersions")}</p>
            )}
            <ul className="rel-timeline">
              {versions.map((v) => {
                const promoted = releases.find(
                  (r) => r.versionId === v.id && r.status === "PROMOTED",
                );
                return (
                  <li
                    key={v.id}
                    className={selected?.id === v.id ? "active" : ""}
                    onClick={() => void openVersion(v.id)}
                  >
                    <div className="rel-vhead">
                      <b>v{v.versionNo}</b>
                      <code>{shortHash(v.contentHash)}</code>
                      {promoted && <span className="rel-tag live">{t("release.live")}</span>}
                    </div>
                    {v.versionLabel && (
                      <div className="rel-vlabel">{v.versionLabel}</div>
                    )}
                    {v.changelog && (
                      <div className="rel-changelog">{v.changelog}</div>
                    )}
                    <div className="rel-vmeta">
                      {v.createdBy} · {formatTime(v.createdAt, i18n.resolvedLanguage ?? "zh-CN")}
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>
        </section>

        <section className="rel-col">
          {selected ? (
            <div className="rel-card">
              <h3>
                {t("release.detailTitle", { version: selected.versionNo })}
                {selected.versionLabel ? ` · ${selected.versionLabel}` : ""}
              </h3>
              <div className="rel-detail-grid">
                <span>{t("release.contentHash")}</span>
                <code>{selected.contentHash}</code>
                <span>{t("release.createdBy")}</span>
                <span>{selected.createdBy}</span>
                <span>{t("release.createdAt")}</span>
                <span>{formatTime(selected.createdAt, i18n.resolvedLanguage ?? "zh-CN")}</span>
                <span>{t("release.parentVersion")}</span>
                <code>{shortHash(selected.parentVersionId)}</code>
              </div>
              <details className="rel-snapshot">
                <summary>{t("release.viewSnapshot")}</summary>
                <pre>{snapshotPretty}</pre>
              </details>

              <div className="rel-publish">
                <label className="rel-field">
                  <span>{t("release.publishTo")}</span>
                  <select
                    value={targetChannelId}
                    onChange={(e) => setTargetChannelId(e.target.value)}
                  >
                    <option value="">{t("release.selectChannel")}</option>
                    {agentChannels.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}（{c.type}）
                      </option>
                    ))}
                  </select>
                </label>
                <Button
                  onClick={handlePublish}
                  disabled={publishing || !targetChannelId}
                >
                  {publishing
                    ? t("release.publishing")
                    : t("release.publishVersion", { version: selected.versionNo })}
                </Button>
              </div>
            </div>
          ) : (
            <div className="rel-card rel-hint">{t("release.selectVersion")}</div>
          )}

          <div className="rel-card">
            <h3>{t("release.channelVersions")}</h3>
            {channels.length === 0 && <p className="rel-hint">{t("release.noChannels")}</p>}
            <ul className="rel-channels">
              {channels.map((c) => {
                const current = channelCurrent[c.id];
                return (
                  <li key={c.id}>
                    <div>
                      <b>{c.name}</b>
                      <small>
                        {c.type} · {c.channelKey}
                      </small>
                    </div>
                    <div className="rel-ch-right">
                      {current ? (
                        <>
                          <span className="rel-tag live">v{current.versionNo}</span>
                          <button
                            className="rel-link"
                            onClick={() => handleRollback(c.id)}
                            title={t("release.rollbackTitle")}
                          >
                            {t("release.rollback")}
                          </button>
                        </>
                      ) : (
                        <span className="rel-tag idle">{t("release.unpublished")}</span>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>

          <div className="rel-card">
            <h3>{t("release.history")}</h3>
            {releases.length === 0 && <p className="rel-hint">{t("release.noHistory")}</p>}
            <ul className="rel-history">
              {releases.map((r) => {
                const ch = channels.find((c) => c.id === r.targetId);
                return (
                  <li key={r.id}>
                    <b>v{r.versionNo}</b>
                    <span className={`rel-status ${r.status.toLowerCase()}`}>
                      {t(`release.status.${r.status}`)}
                    </span>
                    <span className="rel-h-target">
                      {ch ? ch.name : r.targetId}
                    </span>
                    <small>{formatTime(r.publishedAt, i18n.resolvedLanguage ?? "zh-CN")}</small>
                  </li>
                );
              })}
            </ul>
          </div>
        </section>
      </div>
    </>
  );
}
