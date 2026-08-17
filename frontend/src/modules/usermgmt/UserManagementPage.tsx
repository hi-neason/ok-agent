import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Button, PageHeader, Toggle, useConfirm } from "../shared";
import {
  deleteUser,
  deleteUserGroup,
  fetchUserGroups,
  fetchUsers,
  saveUser,
  saveUserGroup,
} from "./api";
import type { UserGroupItem, UserItem } from "./types";
import "./usermgmt.css";

type Tab = "groups" | "users";

export function UserManagementPage() {
  const { confirm, Dialog } = useConfirm();
  const [tab, setTab] = useState<Tab>("groups");
  const [groups, setGroups] = useState<UserGroupItem[]>([]);
  const [users, setUsers] = useState<UserItem[]>([]);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [saving, setSaving] = useState(false);
  const [groupEditing, setGroupEditing] = useState<Partial<UserGroupItem> | null>(null);
  const [userEditing, setUserEditing] = useState<Partial<UserItem> | null>(null);

  const loadGroups = () =>
    fetchUserGroups().then(setGroups).catch(() => setError("加载用户组失败"));
  const loadUsers = () =>
    fetchUsers().then(setUsers).catch(() => setError("加载用户失败"));

  useEffect(() => {
    loadGroups();
    loadUsers();
  }, []);

  const visibleGroups = groups.filter((g) =>
    `${g.name} ${g.groupKey} ${g.description}`.toLowerCase().includes(query.toLowerCase()),
  );
  const visibleUsers = users.filter((u) =>
    `${u.username} ${u.displayName} ${u.email ?? ""} ${u.groupName ?? ""}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );

  const saveGroup = async () => {
    if (!groupEditing || saving) return;
    if (!groupEditing.groupKey?.trim() || !groupEditing.name?.trim()) {
      setError("标识与名称为必填项");
      return;
    }
    setSaving(true);
    setError("");
    try {
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
    } catch {
      setError("保存用户组失败");
    } finally {
      setSaving(false);
    }
  };

  const saveUserRecord = async () => {
    if (!userEditing || saving) return;
    if (!userEditing.username?.trim() || !userEditing.displayName?.trim()) {
      setError("账号与姓名为必填项");
      return;
    }
    setSaving(true);
    setError("");
    try {
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
    } catch {
      setError("保存用户失败");
    } finally {
      setSaving(false);
    }
  };

  const removeGroup = async (g: UserGroupItem) => {
    const message =
      g.userCount > 0
        ? `用户组「${g.name}」下还有 ${g.userCount} 个用户，请先移除或转移用户后再删除。`
        : `确认删除用户组「${g.name}」？`;
    if (!(await confirm({ message, dangerous: g.userCount === 0 }))) return;
    try {
      await deleteUserGroup(g.id);
      setGroups((cur) => cur.filter((x) => x.id !== g.id));
    } catch {
      setError("删除用户组失败（可能仍有成员）");
    }
  };

  const removeUser = async (u: UserItem) => {
    if (
      !(await confirm({
        message: `确认删除用户「${u.displayName}」(${u.username})？`,
        dangerous: true,
      }))
    )
      return;
    try {
      await deleteUser(u.id);
      setUsers((cur) => cur.filter((x) => x.id !== u.id));
    } catch {
      setError("删除用户失败");
    }
  };

  return (
    <>
      <Dialog />
      <PageHeader
        kicker="USER MANAGEMENT / 用户与用户组"
        title="用户管理"
        description="维护用户的基础信息与用户组归属，支持用户组与用户的增删改查。"
        action={
          <Button
            onClick={() => {
              setError("");
              setQuery("");
              if (tab === "groups") setGroupEditing({ enabled: true });
              else setUserEditing({ enabled: true, groupId: groups[0]?.id ?? null });
            }}
          >
            ＋ {tab === "groups" ? "新建用户组" : "新建用户"}
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
          用户组
        </button>
        <button
          className={tab === "users" ? "um-tab active" : "um-tab"}
          onClick={() => {
            setTab("users");
            setQuery("");
          }}
        >
          用户
        </button>
      </div>
      <label className="search-mini" style={{ marginBottom: 10, display: "inline-flex" }}>
        ⌕
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={tab === "groups" ? "搜索用户组" : "搜索用户"}
        />
      </label>

      {tab === "groups" ? (
        <section className="run-table um-group-table">
          <div className="table-head">
            <span>用户组</span>
            <span>标识</span>
            <span>描述</span>
            <span>成员</span>
            <span>状态</span>
            <span>操作</span>
          </div>
          {visibleGroups.length === 0 ? (
            <div className="um-empty">暂无用户组</div>
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
                    }).then((saved) =>
                      setGroups((cur) => cur.map((x) => (x.id === saved.id ? saved : x))),
                    )
                  }
                  label={`${g.name} 状态`}
                />
                <span className="model-actions">
                  <button
                    className="link-button"
                    onClick={() => {
                      setError("");
                      setGroupEditing(g);
                    }}
                  >
                    编辑
                  </button>
                  <button
                    className="link-button danger-link"
                    onClick={() => void removeGroup(g)}
                  >
                    删除
                  </button>
                </span>
              </div>
            ))
          )}
        </section>
      ) : (
        <section className="run-table um-user-table">
          <div className="table-head">
            <span>账号</span>
            <span>用户标识</span>
            <span>姓名</span>
            <span>邮箱</span>
            <span>电话</span>
            <span>所属用户组</span>
            <span>状态</span>
            <span>操作</span>
          </div>
          {visibleUsers.length === 0 ? (
            <div className="um-empty">暂无用户</div>
          ) : (
            visibleUsers.map((u) => (
              <div className="table-row" key={u.id}>
                <span>
                  <b>{u.username}</b>
                </span>
                <code>{u.userKey}</code>
                <span>{u.displayName}</span>
                <span>{u.email || "—"}</span>
                <span>{u.phone || "—"}</span>
                <span>{u.groupName || "—"}</span>
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
                    }).then((saved) =>
                      setUsers((cur) => cur.map((x) => (x.id === saved.id ? saved : x))),
                    )
                  }
                  label={`${u.displayName} 状态`}
                />
                <span className="model-actions">
                  <button
                    className="link-button"
                    onClick={() => {
                      setError("");
                      setUserEditing(u);
                    }}
                  >
                    编辑
                  </button>
                  <button
                    className="link-button danger-link"
                    onClick={() => void removeUser(u)}
                  >
                    删除
                  </button>
                </span>
              </div>
            ))
          )}
        </section>
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
                  <h2>{groupEditing.id ? groupEditing.name : "新建用户组"}</h2>
                </div>
                <button className="link-button" onClick={() => setGroupEditing(null)}>
                  关闭 ×
                </button>
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>标识 (group key)</span>
                  <input
                    value={groupEditing.groupKey ?? ""}
                    onChange={(event) =>
                      setGroupEditing({ ...groupEditing, groupKey: event.target.value })
                    }
                    placeholder="如 ops-team"
                  />
                </label>
                <label className="field">
                  <span>名称</span>
                  <input
                    value={groupEditing.name ?? ""}
                    onChange={(event) =>
                      setGroupEditing({ ...groupEditing, name: event.target.value })
                    }
                    placeholder="如 运营组"
                  />
                </label>
                <label className="field wide">
                  <span>描述</span>
                  <input
                    value={groupEditing.description ?? ""}
                    onChange={(event) =>
                      setGroupEditing({ ...groupEditing, description: event.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>启用</span>
                  <Toggle
                    on={groupEditing.enabled ?? true}
                    setOn={(next) => setGroupEditing({ ...groupEditing, enabled: next })}
                    label="启用"
                  />
                </label>
              </div>
              {error && <div className="skill-error modal-error">× {error}</div>}
              <div className="sticky-actions">
                <Button quiet onClick={() => setGroupEditing(null)}>
                  取消
                </Button>
                <Button onClick={() => void saveGroup()} disabled={saving}>
                  {saving ? "保存中…" : "保存"}
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
                  <h2>{userEditing.id ? userEditing.displayName : "新建用户"}</h2>
                </div>
                <button className="link-button" onClick={() => setUserEditing(null)}>
                  关闭 ×
                </button>
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>账号 (username)</span>
                  <input
                    value={userEditing.username ?? ""}
                    onChange={(event) =>
                      setUserEditing({ ...userEditing, username: event.target.value })
                    }
                    placeholder="如 zhangsan"
                  />
                </label>
                <label className="field">
                  <span>姓名</span>
                  <input
                    value={userEditing.displayName ?? ""}
                    onChange={(event) =>
                      setUserEditing({ ...userEditing, displayName: event.target.value })
                    }
                    placeholder="如 张三"
                  />
                </label>
                <label className="field">
                  <span>邮箱</span>
                  <input
                    value={userEditing.email ?? ""}
                    onChange={(event) =>
                      setUserEditing({ ...userEditing, email: event.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>电话</span>
                  <input
                    value={userEditing.phone ?? ""}
                    onChange={(event) =>
                      setUserEditing({ ...userEditing, phone: event.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>所属用户组</span>
                  <select
                    value={userEditing.groupId ?? ""}
                    onChange={(event) =>
                      setUserEditing({
                        ...userEditing,
                        groupId: event.target.value || null,
                      })
                    }
                  >
                    <option value="">（无）</option>
                    {groups.map((g) => (
                      <option key={g.id} value={g.id}>
                        {g.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span>启用</span>
                  <Toggle
                    on={userEditing.enabled ?? true}
                    setOn={(next) => setUserEditing({ ...userEditing, enabled: next })}
                    label="启用"
                  />
                </label>
              </div>
              {error && <div className="skill-error modal-error">× {error}</div>}
              <div className="sticky-actions">
                <Button quiet onClick={() => setUserEditing(null)}>
                  取消
                </Button>
                <Button onClick={() => void saveUserRecord()} disabled={saving}>
                  {saving ? "保存中…" : "保存"}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
