import { useEffect, useMemo, useState } from "react";
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

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleString("zh-CN", { hour12: false });
  } catch {
    return iso;
  }
}

const statusLabel: Record<ReleaseItem["status"], string> = {
  PROMOTED: "线上",
  SUPERSEDED: "已取代",
  ROLLED_BACK: "已回滚",
};

export function ReleasePage() {
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
    void (async () => {
      const [a, c] = await Promise.all([listAgents(), listChannels()]);
      setAgents(a);
      setChannels(c);
      if (a.length > 0) setAgentId(a[0].id);
    })();
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
        kicker="RELEASE MANAGEMENT / VERSIONING"
        title="发布管理"
        description="从 Agent 草稿冻结出不可变版本（v1、v2…），将版本发布到渠道；运行态只读取已发布快照，不读取草稿。"
      />
      <div className="rel-toolbar">
        <label className="rel-field">
          <span>选择 Agent</span>
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
            <h3>保存新版本</h3>
            <p className="rel-hint">
              将当前草稿冻结为不可变版本。引用的子 Agent 会被固定到它的最新版本。
            </p>
            <label className="rel-field">
              <span>版本标签（可选）</span>
              <input
                value={label}
                onChange={(e) => setLabel(e.target.value)}
                placeholder="如 v1.0.0 / 大促版本"
              />
            </label>
            <label className="rel-field">
              <span>变更说明</span>
              <textarea
                value={changelog}
                onChange={(e) => setChangelog(e.target.value)}
                rows={3}
                placeholder="本次版本改了什么"
              />
            </label>
            <Button onClick={handleCreateVersion} disabled={saving}>
              {saving ? "冻结中…" : "冻结为新版本"}
            </Button>
          </div>

          <div className="rel-card">
            <h3>版本时间线</h3>
            {loading && <p className="rel-hint">加载中…</p>}
            {!loading && versions.length === 0 && (
              <p className="rel-hint">
                还没有版本。点击上方「冻结为新版本」从当前草稿创建第一个版本。
              </p>
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
                      {promoted && <span className="rel-tag live">线上</span>}
                    </div>
                    {v.versionLabel && (
                      <div className="rel-vlabel">{v.versionLabel}</div>
                    )}
                    {v.changelog && (
                      <div className="rel-changelog">{v.changelog}</div>
                    )}
                    <div className="rel-vmeta">
                      {v.createdBy} · {formatTime(v.createdAt)}
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
                版本 v{selected.versionNo} 详情
                {selected.versionLabel ? ` · ${selected.versionLabel}` : ""}
              </h3>
              <div className="rel-detail-grid">
                <span>内容指纹</span>
                <code>{selected.contentHash}</code>
                <span>创建人</span>
                <span>{selected.createdBy}</span>
                <span>创建时间</span>
                <span>{formatTime(selected.createdAt)}</span>
                <span>父版本</span>
                <code>{shortHash(selected.parentVersionId)}</code>
              </div>
              <details className="rel-snapshot">
                <summary>查看冻结快照（snapshot_json）</summary>
                <pre>{snapshotPretty}</pre>
              </details>

              <div className="rel-publish">
                <label className="rel-field">
                  <span>发布到渠道</span>
                  <select
                    value={targetChannelId}
                    onChange={(e) => setTargetChannelId(e.target.value)}
                  >
                    <option value="">请选择渠道…</option>
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
                  {publishing ? "发布中…" : `发布 v${selected.versionNo} →`}
                </Button>
              </div>
            </div>
          ) : (
            <div className="rel-card rel-hint">选择左侧版本查看详情。</div>
          )}

          <div className="rel-card">
            <h3>渠道当前版本</h3>
            {channels.length === 0 && <p className="rel-hint">暂无渠道。</p>}
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
                            title="回滚到上一个版本"
                          >
                            回滚
                          </button>
                        </>
                      ) : (
                        <span className="rel-tag idle">未发布</span>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>

          <div className="rel-card">
            <h3>发布历史</h3>
            {releases.length === 0 && <p className="rel-hint">暂无发布记录。</p>}
            <ul className="rel-history">
              {releases.map((r) => {
                const ch = channels.find((c) => c.id === r.targetId);
                return (
                  <li key={r.id}>
                    <b>v{r.versionNo}</b>
                    <span className={`rel-status ${r.status.toLowerCase()}`}>
                      {statusLabel[r.status]}
                    </span>
                    <span className="rel-h-target">
                      {ch ? ch.name : r.targetId}
                    </span>
                    <small>{formatTime(r.publishedAt)}</small>
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
