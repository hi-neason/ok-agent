import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Button, PageHeader, Toggle, useConfirm, Pagination } from "../shared";
import type { Page } from "../shared";
import {
  deleteUser,
  deleteUserGroup,
  fetchUserChannels,
  fetchUserGroups,
  fetchUserGroupsPage,
  fetchUsers,
  fetchUsersPage,
  mergeUsers,
  saveUser,
  saveUserGroup,
} from "./api";
import type { ChannelIdentity, UserGroupItem, UserItem } from "./types";
import { channelFriendlyName, channelLabel, formatInstant } from "./channelUtil";
import "./usermgmt.css";

type Tab = "groups" | "users";

export function UserManagementPage({ onOpenUser }: { onOpenUser?: (id: string) => void }) {
  const { t, i18n } = useTranslation();
  const { confirm, Dialog } = useConfirm();
  const [tab, setTab] = useState<Tab>("groups");
  const [groups, setGroups] = useState<UserGroupItem[]>([]);
  const [groupPage, setGroupPage] = useState<Page<UserGroupItem> | null>(null);
  const [groupPageNumber, setGroupPageNumber] = useState(0);
  const [groupPageSize, setGroupPageSize] = useState(20);
  const [users, setUsers] = useState<UserItem[]>([]);
  const [usersPage, setUsersPage] = useState<Page<UserItem> | null>(null);
  const [userPageNumber, setUserPageNumber] = useState(0);
  const [userPageSize, setUserPageSize] = useState(20);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [saving, setSaving] = useState(false);
  const [groupEditing, setGroupEditing] = useState<Partial<UserGroupItem> | null>(null);
  const [userEditing, setUserEditing] = useState<Partial<UserItem> | null>(null);
  const [channelDrawer, setChannelDrawer] = useState<UserItem | null>(null);
  const [channels, setChannels] = useState<ChannelIdentity[]>([]);
  const [mergeTarget, setMergeTarget] = useState<UserItem | null>(null);
  const [mergeCandidateId, setMergeCandidateId] = useState("");

  // Full list (dropdowns) + paged list (tabs)
  const loadGroups = () =>
    fetchUserGroups().then(setGroups).catch(() => setError(t("users.loadGroupsFailed")));
  const loadGroupPage = (targetPage = groupPageNumber) =>
    fetchUserGroupsPage(targetPage, groupPageSize).then(setGroupPage).catch(() => setError(t("users.loadGroupsFailed")));
  const loadUsers = () =>
    fetchUsers().then(setUsers).catch(() => setError(t("users.loadUsersFailed")));
  const loadUsersPage = (targetPage = userPageNumber) =>
    fetchUsersPage(targetPage, userPageSize).then(setUsersPage).catch(() => setError(t("users.loadUsersFailed")));

  useEffect(() => {
    loadGroups();
    loadGroupPage(0);
    loadUsers();
    loadUsersPage(0);
  }, []);

  useEffect(() => {
    void loadGroupPage(groupPageNumber);
  }, [groupPageNumber, groupPageSize]);

  useEffect(() => {
    void loadUsersPage(userPageNumber);
  }, [userPageNumber, userPageSize]);

  const visibleGroups = (groupPage?.content ?? []).filter((g) =>
    `${g.name} ${g.groupKey} ${g.description}`.toLowerCase().includes(query.toLowerCase()),
  );
  const visibleUsers = (usersPage?.content ?? []).filter((u) =>
    `${u.username} ${u.displayName} ${u.email ?? ""} ${u.groupName ?? ""}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );

  const openChannels = async (u: UserItem) => {
    setChannelDrawer(u);
    setChannels([]);
    try {
      setChannels(await fetchUserChannels(u.id));
    } catch {
      setError(t("users.loadChannelsFailed"));
    }
  };

  const runMerge = async () => {
    if (!mergeTarget || !mergeCandidateId || mergeCandidateId === mergeTarget.id) return;
    const candidate = users.find((u) => u.id === mergeCandidateId);
    const ok = await confirm({
      title: t("users.mergeTitle"),
      message: t("users.mergeConfirm", {
        candidate: candidate?.displayName ?? mergeCandidateId,
        target: mergeTarget.displayName,
      }),
      confirmText: t("users.confirmMerge"),
      dangerous: true,
    });
    if (!ok) return;
    setSaving(true);
    setError("");
    try {
      await mergeUsers(mergeTarget.id, mergeCandidateId);
      setMergeTarget(null);
      setMergeCandidateId("");
      loadUsers();
      loadUsersPage(userPageNumber);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("users.mergeFailed"));
    } finally {
      setSaving(false);
    }
  };

  const saveGroup = async () => {
    if (!groupEditing || saving) return;
    if (!groupEditing.groupKey?.trim() || !groupEditing.name?.trim()) {
      setError(t("users.groupRequired"));
      return;
    }
    setSaving(true);
    setError("");
    try {
      const isNew = !groupEditing.id;
      const saved = await saveUserGroup({
        id: groupEditing.id,
        groupKey: groupEditing.groupKey.trim(),
        name: groupEditing.name.trim(),
        description: groupEditing.description ?? "",
        enabled: groupEditing.enabled ?? true,
      });
      setGroups((cur) =>
        cur.some((g) => g.id === saved.id)
          ? cur.map((g) => (g.id === saved.id ? saved : g))
          : [saved, ...cur],
      );
      setGroupEditing(null);
      if (isNew) setGroupPageNumber(0);
      else await loadGroupPage(groupPageNumber);
    } catch {
      setError(t("users.saveGroupFailed"));
    } finally {
      setSaving(false);
    }
  };

  const saveUserRecord = async () => {
    if (!userEditing || saving) return;
    if (!userEditing.username?.trim() || !userEditing.displayName?.trim()) {
      setError(t("users.userRequired"));
      return;
    }
    setSaving(true);
    setError("");
    try {
      const isNew = !userEditing.id;
      const saved = await saveUser({
        id: userEditing.id,
        username: userEditing.username.trim(),
        displayName: userEditing.displayName.trim(),
        email: userEditing.email ?? "",
        phone: userEditing.phone ?? "",
        groupId: userEditing.groupId ?? null,
        enabled: userEditing.enabled ?? true,
      });
      setUsers((cur) =>
        cur.some((u) => u.id === saved.id)
          ? cur.map((u) => (u.id === saved.id ? saved : u))
          : [saved, ...cur],
      );
      setUserEditing(null);
      if (isNew) setUserPageNumber(0);
      else await loadUsersPage(userPageNumber);
    } catch {
      setError(t("users.saveUserFailed"));
    } finally {
      setSaving(false);
    }
  };

  const removeGroup = async (g: UserGroupItem) => {
    const message =
      g.userCount > 0
        ? t("users.groupHasUsers", { name: g.name, count: g.userCount })
        : t("users.deleteGroupConfirm", { name: g.name });
    if (!(await confirm({ message, dangerous: g.userCount === 0 }))) return;
    try {
      await deleteUserGroup(g.id);
      setGroups((cur) => cur.filter((x) => x.id !== g.id));
      await loadGroupPage(groupPageNumber);
    } catch {
      setError(t("users.deleteGroupFailed"));
    }
  };

  const removeUser = async (u: UserItem) => {
    if (
      !(await confirm({
        message: t("users.deleteUserConfirm", { name: u.displayName, username: u.username }),
        dangerous: true,
      }))
    )
      return;
    try {
      await deleteUser(u.id);
      setUsers((cur) => cur.filter((x) => x.id !== u.id));
      await loadUsersPage(userPageNumber);
    } catch {
      setError(t("users.deleteUserFailed"));
    }
  };

  return (
    <>
      <Dialog />
      <PageHeader
        kicker={t("users.kicker")}
        title={t("users.title")}
        description={t("users.description")}
        action={
          <Button
            onClick={() => {
              setError("");
              setQuery("");
              if (tab === "groups") setGroupEditing({ enabled: true });
              else setUserEditing({ enabled: true, groupId: groups[0]?.id ?? null });
            }}
          >
            ＋ {tab === "groups" ? t("users.newGroup") : t("users.newUser")}
          </Button>
        }
      />
      {error && <div className="skill-error">× {error}</div>}
      <div className="um-tabstrip">
        <button
          className={tab === "groups" ? "um-tab active" : "um-tab"}
          onClick={() => {
            setTab("groups");
            setQuery("");
          }}
        >
          {t("users.groups")}
        </button>
        <button
          className={tab === "users" ? "um-tab active" : "um-tab"}
          onClick={() => {
            setTab("users");
            setQuery("");
          }}
        >
          {t("users.users")}
        </button>
      </div>
      <label className="search-mini" style={{ marginBottom: 10, display: "inline-flex" }}>
        ⌕
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={tab === "groups" ? t("users.searchGroups") : t("users.searchUsers")}
        />
      </label>

      {tab === "groups" ? (
        <section className="run-table um-group-table">
          <div className="table-head">
            <span>{t("users.groups")}</span>
            <span>{t("users.identifier")}</span>
            <span>{t("users.descriptionLabel")}</span>
            <span>{t("users.members")}</span>
            <span>{t("common.status")}</span>
            <span>{t("common.actions")}</span>
          </div>
          {visibleGroups.length === 0 ? (
            <div className="um-empty">{t("users.noGroups")}</div>
          ) : (
            visibleGroups.map((g) => (
              <div className="table-row" key={g.id}>
                <span>
                  <b>{g.name}</b>
                </span>
                <code>{g.groupKey}</code>
                <small>{g.description || "—"}</small>
                <span>{g.userCount}</span>
                <Toggle
                  on={g.enabled}
                  setOn={(next) =>
                    void saveUserGroup({
                      id: g.id,
                      groupKey: g.groupKey,
                      name: g.name,
                      description: g.description,
                      enabled: next,
                    }).then((saved) => {
                      setGroups((cur) => cur.map((x) => (x.id === saved.id ? saved : x)));
                      setGroupPage((p) =>
                        p ? { ...p, content: p.content.map((x) => (x.id === saved.id ? saved : x)) } : p,
                      );
                    })
                  }
                  label={t("users.statusLabel", { name: g.name })}
                />
                <span className="model-actions">
                  <button
                    className="link-button"
                    onClick={() => {
                      setError("");
                      setGroupEditing(g);
                    }}
                  >
                    {t("common.edit")}
                  </button>
                  <button
                    className="link-button danger-link"
                    onClick={() => void removeGroup(g)}
                  >
                    {t("common.delete")}
                  </button>
                </span>
              </div>
            ))
          )}
        </section>
      ) : (
        <section className="run-table um-user-table">
          <div className="table-head">
            <span>{t("users.account")}</span>
            <span>{t("users.source")}</span>
            <span>{t("users.name")}</span>
            <span>{t("users.email")}</span>
            <span>{t("users.phone")}</span>
            <span>{t("users.group")}</span>
            <span>{t("users.channel")}</span>
            <span>{t("common.status")}</span>
            <span>{t("common.actions")}</span>
          </div>
          {visibleUsers.length === 0 ? (
            <div className="um-empty">{t("users.noUsers")}</div>
          ) : (
            visibleUsers.map((u) => (
              <div className="table-row" key={u.id}>
                <span>
                  <button
                    className="link-button um-user-link"
                    onClick={() => onOpenUser?.(u.id)}
                    title={t("users.viewDetail")}
                  >
                    <b>{u.username}</b>
                  </button>
                </span>
                <span>
                  <span className={`um-source um-source--${u.source.toLowerCase()}`}>
                    {u.source === "CHANNEL" ? t("users.channel") : t("users.console")}
                  </span>
                </span>
                <span>
                  <button
                    className="link-button um-user-link"
                    onClick={() => onOpenUser?.(u.id)}
                    title={t("users.viewDetail")}
                  >
                    {u.displayName}
                  </button>
                </span>
                <span>{u.email || "—"}</span>
                <span>{u.phone || "—"}</span>
                <span>{u.groupName || "—"}</span>
                <span>
                  {u.channelCount > 0 ? (
                    <button
                      className="link-button um-channel-count"
                      onClick={() => void openChannels(u)}
                      title={t("users.viewChannels")}
                    >
                      <b>{u.channelCount}</b>
                      <em>{t("users.countUnit")}</em>
                    </button>
                  ) : (
                    <span className="um-channel-count">
                      <b>0</b>
                      <em>{t("users.countUnit")}</em>
                    </span>
                  )}
                </span>
                <Toggle
                  on={u.enabled}
                  setOn={(next) =>
                    void saveUser({
                      id: u.id,
                      username: u.username,
                      displayName: u.displayName,
                      email: u.email,
                      phone: u.phone,
                      groupId: u.groupId,
                      enabled: next,
                    }).then((saved) => {
                      setUsers((cur) => cur.map((x) => (x.id === saved.id ? saved : x)));
                      setUsersPage((p) =>
                        p ? { ...p, content: p.content.map((x) => (x.id === saved.id ? saved : x)) } : p,
                      );
                    })
                  }
                  label={t("users.statusLabel", { name: u.displayName })}
                />
                <span className="model-actions">
                  <button
                    className="link-button"
                    onClick={() => {
                      setError("");
                      setUserEditing(u);
                    }}
                  >
                    {t("common.edit")}
                  </button>
                  <button
                    className="link-button"
                    onClick={() => {
                      setMergeTarget(u);
                      setMergeCandidateId("");
                      setError("");
                    }}
                  >
                    {t("users.merge")}
                  </button>
                  <button
                    className="link-button danger-link"
                    onClick={() => void removeUser(u)}
                  >
                    {t("common.delete")}
                  </button>
                </span>
              </div>
            ))
          )}
        </section>
      )}

      {tab === "groups" && groupPage && (
        <Pagination
          page={groupPage.number}
          totalPages={groupPage.totalPages}
          totalElements={groupPage.totalElements}
          size={groupPage.size}
          loading={saving}
          onPageChange={setGroupPageNumber}
          onSizeChange={(size) => {
            setGroupPageSize(size);
            setGroupPageNumber(0);
          }}
        />
      )}
      {tab === "users" && usersPage && (
        <Pagination
          page={usersPage.number}
          totalPages={usersPage.totalPages}
          totalElements={usersPage.totalElements}
          size={usersPage.size}
          loading={saving}
          onPageChange={setUserPageNumber}
          onSizeChange={(size) => {
            setUserPageSize(size);
            setUserPageNumber(0);
          }}
        />
      )}

      {groupEditing &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setGroupEditing(null)}
          >
            <div
              className="form-surface model-editor"
              role="dialog"
              aria-modal="true"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">USER GROUP / {groupEditing.id ? "EDIT" : "CREATE"}</p>
                  <h2>{groupEditing.id ? groupEditing.name : t("users.newGroup")}</h2>
                </div>
                <button className="link-button" onClick={() => setGroupEditing(null)}>
                  {t("common.close")} ×
                </button>
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>{t("users.groupKey")}</span>
                  <input
                    value={groupEditing.groupKey ?? ""}
                    onChange={(event) =>
                      setGroupEditing({ ...groupEditing, groupKey: event.target.value })
                    }
                    placeholder={t("users.groupKeyPlaceholder")}
                  />
                </label>
                <label className="field">
                  <span>{t("integrations.name")}</span>
                  <input
                    value={groupEditing.name ?? ""}
                    onChange={(event) =>
                      setGroupEditing({ ...groupEditing, name: event.target.value })
                    }
                    placeholder={t("users.groupNamePlaceholder")}
                  />
                </label>
                <label className="field wide">
                  <span>{t("users.descriptionLabel")}</span>
                  <input
                    value={groupEditing.description ?? ""}
                    onChange={(event) =>
                      setGroupEditing({ ...groupEditing, description: event.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>{t("common.enabled")}</span>
                  <Toggle
                    on={groupEditing.enabled ?? true}
                    setOn={(next) => setGroupEditing({ ...groupEditing, enabled: next })}
                    label={t("common.enabled")}
                  />
                </label>
              </div>
              {error && <div className="skill-error modal-error">× {error}</div>}
              <div className="sticky-actions">
                <Button quiet onClick={() => setGroupEditing(null)}>
                  {t("common.cancel")}
                </Button>
                <Button onClick={() => void saveGroup()} disabled={saving}>
                  {saving ? t("common.saving") : t("common.save")}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}

      {userEditing &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setUserEditing(null)}
          >
            <div
              className="form-surface model-editor"
              role="dialog"
              aria-modal="true"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">USER / {userEditing.id ? "EDIT" : "CREATE"}</p>
                  <h2>{userEditing.id ? userEditing.displayName : t("users.newUser")}</h2>
                </div>
                <button className="link-button" onClick={() => setUserEditing(null)}>
                  {t("common.close")} ×
                </button>
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>{t("users.username")}</span>
                  <input
                    value={userEditing.username ?? ""}
                    onChange={(event) =>
                      setUserEditing({ ...userEditing, username: event.target.value })
                    }
                    placeholder={t("users.usernamePlaceholder")}
                  />
                </label>
                <label className="field">
                  <span>{t("users.name")}</span>
                  <input
                    value={userEditing.displayName ?? ""}
                    onChange={(event) =>
                      setUserEditing({ ...userEditing, displayName: event.target.value })
                    }
                    placeholder={t("users.displayNamePlaceholder")}
                  />
                </label>
                <label className="field">
                  <span>{t("users.email")}</span>
                  <input
                    value={userEditing.email ?? ""}
                    onChange={(event) =>
                      setUserEditing({ ...userEditing, email: event.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>{t("users.phone")}</span>
                  <input
                    value={userEditing.phone ?? ""}
                    onChange={(event) =>
                      setUserEditing({ ...userEditing, phone: event.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>{t("users.group")}</span>
                  <select
                    value={userEditing.groupId ?? ""}
                    onChange={(event) =>
                      setUserEditing({
                        ...userEditing,
                        groupId: event.target.value || null,
                      })
                    }
                  >
                    <option value="">{t("users.none")}</option>
                    {groups.map((g) => (
                      <option key={g.id} value={g.id}>
                        {g.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span>{t("common.enabled")}</span>
                  <Toggle
                    on={userEditing.enabled ?? true}
                    setOn={(next) => setUserEditing({ ...userEditing, enabled: next })}
                    label={t("common.enabled")}
                  />
                </label>
              </div>
              {error && <div className="skill-error modal-error">× {error}</div>}
              <div className="sticky-actions">
                <Button quiet onClick={() => setUserEditing(null)}>
                  {t("common.cancel")}
                </Button>
                <Button onClick={() => void saveUserRecord()} disabled={saving}>
                  {saving ? t("common.saving") : t("common.save")}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}

      {channelDrawer &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setChannelDrawer(null)}
          >
            <div
              className="form-surface model-editor"
              role="dialog"
              aria-modal="true"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">USER CHANNELS</p>
                  <h2>{t("users.channelsTitle", { name: channelDrawer.displayName })}</h2>
                </div>
                <button className="link-button" onClick={() => setChannelDrawer(null)}>
                  {t("common.close")} ×
                </button>
              </div>
              {channels.length === 0 ? (
                <div className="um-empty">{t("users.channelsEmpty")}</div>
              ) : (
                <div className="um-channels-row">
                  {channels.map((c, i) => {
                    const { name, sub } = channelFriendlyName(c, t);
                    return (
                      <div className="um-channel-chip" key={i}>
                        <span className="um-chan-type">
                          {channelLabel(c.channelType, t)}
                        </span>
                        <span className="um-chan-name">{name}</span>
                        <code>{sub}</code>
                        <span className="um-chan-meta">
                          {t("users.channelMeta", {
                            count: c.messageCount,
                            time: formatInstant(c.lastSeenAt, i18n.resolvedLanguage ?? "zh-CN"),
                          })}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
              <div className="sticky-actions">
                <Button onClick={() => setChannelDrawer(null)}>{t("common.close")}</Button>
              </div>
            </div>
          </div>,
          document.body,
        )}

      {mergeTarget &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => !saving && setMergeTarget(null)}
          >
            <div
              className="form-surface model-editor"
              role="dialog"
              aria-modal="true"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">MERGE USER</p>
                  <h2>{t("users.mergeInto", { name: mergeTarget.displayName })}</h2>
                </div>
                <button className="link-button" onClick={() => setMergeTarget(null)}>
                  {t("common.close")} ×
                </button>
              </div>
              <p style={{ fontSize: 13, color: "var(--color-text-secondary)", lineHeight: 1.7 }}>
                {t("users.mergeDescription", { name: mergeTarget.displayName })}
              </p>
              <div className="field-grid">
                <label className="field wide">
                  <span>{t("users.selectMergeCandidate")}</span>
                  <select
                    value={mergeCandidateId}
                    onChange={(e) => setMergeCandidateId(e.target.value)}
                  >
                    <option value="">{t("channels.select")}</option>
                    {users
                      .filter((u) => u.id !== mergeTarget.id)
                      .map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.displayName}（{u.username}
                          {u.channelCount
                            ? ` · ${t("users.channelOption", { count: u.channelCount })}`
                            : ""}）
                        </option>
                      ))}
                  </select>
                </label>
              </div>
              {(() => {
                const candidate = users.find((u) => u.id === mergeCandidateId);
                if (!candidate) return null;
                const reversed =
                  candidate.source === "CONSOLE" && mergeTarget.source === "CHANNEL";
                return (
                  <div className={reversed ? "um-merge-warn" : "um-merge-confirm"}>
                    <span className="um-merge-arrow">
                      {candidate.displayName} → {mergeTarget.displayName}
                    </span>
                    {reversed && (
                      <small>
                        {t("users.reverseWarning")}
                      </small>
                    )}
                  </div>
                );
              })()}
              {error && <div className="skill-error modal-error">× {error}</div>}
              <div className="sticky-actions">
                <Button quiet onClick={() => setMergeTarget(null)} disabled={saving}>
                  {t("common.cancel")}
                </Button>
                <Button onClick={() => void runMerge()} disabled={saving || !mergeCandidateId}>
                  {saving ? t("users.merging") : t("users.confirmMerge")}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
