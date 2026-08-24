import { type FormEvent, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, PageHeader, Pagination, useConfirm, type Page } from "../shared";
import {
  createAccount,
  fetchAccounts,
  resetAccountPassword,
  updateAccount,
  type Account,
  type CreateAccount,
} from "./accounts";
import { useAuth } from "./AuthProvider";
import type { AccountRole } from "./types";

const EMPTY_PAGE: Page<Account> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
};

const EMPTY_FORM: CreateAccount = {
  username: "",
  displayName: "",
  password: "",
  role: "VIEWER",
  enabled: true,
};

export function AccountManagementPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const { confirm, Dialog } = useConfirm();
  const [data, setData] = useState<Page<Account>>(EMPTY_PAGE);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState<CreateAccount>(EMPTY_FORM);
  const [passwords, setPasswords] = useState<Record<string, string>>({});

  const load = async () => {
    if (user.role !== "ADMIN") return;
    setLoading(true);
    setError("");
    try {
      setData(await fetchAccounts(page, size));
    } catch {
      setError(t("accounts.loadFailed"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [page, size, user.role]);

  if (user.role !== "ADMIN") {
    return (
      <div className="account-access-denied">
        <h1>{t("accounts.accessDenied")}</h1>
        <p>{t("accounts.accessDeniedDescription")}</p>
      </div>
    );
  }

  const submitCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const approved = await confirm({
      title: t("accounts.createConfirmTitle"),
      message: t("accounts.createConfirm", { username: form.username, role: t(`accounts.roles.${form.role}`) }),
    });
    if (!approved) return;
    setLoading(true);
    setError("");
    try {
      await createAccount(form);
      setForm(EMPTY_FORM);
      setShowCreate(false);
      await load();
    } catch {
      setError(t("accounts.saveFailed"));
      setLoading(false);
    }
  };

  const changeAccess = async (account: Account, role: AccountRole, enabled: boolean) => {
    const approved = await confirm({
      title: t("accounts.accessConfirmTitle"),
      message: t("accounts.accessConfirm", {
        username: account.username,
        role: t(`accounts.roles.${role}`),
        status: t(enabled ? "accounts.enabled" : "accounts.disabled"),
      }),
      dangerous: !enabled || role !== account.role,
    });
    if (!approved) return;
    setLoading(true);
    setError("");
    try {
      await updateAccount({ ...account, role, enabled });
      await load();
    } catch {
      setError(t("accounts.saveFailed"));
      setLoading(false);
    }
  };

  const resetPassword = async (account: Account) => {
    const password = passwords[account.id] ?? "";
    if (password.length < 12) {
      setError(t("accounts.passwordLength"));
      return;
    }
    const approved = await confirm({
      title: t("accounts.passwordConfirmTitle"),
      message: t("accounts.passwordConfirm", { username: account.username }),
      dangerous: true,
    });
    if (!approved) return;
    setLoading(true);
    setError("");
    try {
      await resetAccountPassword(account.id, password);
      setPasswords((current) => ({ ...current, [account.id]: "" }));
    } catch {
      setError(t("accounts.saveFailed"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <PageHeader
        kicker="SYSTEM GOVERNANCE / RBAC"
        title={t("accounts.title")}
        description={t("accounts.description")}
        action={<Button onClick={() => setShowCreate((shown) => !shown)}>{t("accounts.create")}</Button>}
      />
      {error && <div className="account-error">{error}</div>}
      {showCreate && (
        <form className="account-create-panel" onSubmit={submitCreate}>
          <label><span>{t("accounts.username")}</span><input required pattern="[A-Za-z0-9._-]+" maxLength={128} value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} /></label>
          <label><span>{t("accounts.displayName")}</span><input required maxLength={128} value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} /></label>
          <label><span>{t("accounts.password")}</span><input required minLength={12} maxLength={256} type="password" autoComplete="new-password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} /></label>
          <label><span>{t("accounts.role")}</span><select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value as AccountRole })}>{(["ADMIN", "EDITOR", "VIEWER"] as AccountRole[]).map((role) => <option key={role} value={role}>{t(`accounts.roles.${role}`)}</option>)}</select></label>
          <Button disabled={loading}>{t("accounts.save")}</Button>
        </form>
      )}
      <section className="account-table">
        <div className="account-table-head"><span>{t("accounts.account")}</span><span>{t("accounts.role")}</span><span>{t("accounts.status")}</span><span>{t("accounts.lastLogin")}</span><span>{t("accounts.resetPassword")}</span></div>
        {!loading && data.content.length === 0 && <div className="account-empty">{t("accounts.empty")}</div>}
        {data.content.map((account) => (
          <div className="account-table-row" key={account.id}>
            <div><b>{account.displayName}</b><small>{account.username}</small></div>
            <select value={account.role} disabled={loading} onChange={(event) => void changeAccess(account, event.target.value as AccountRole, account.enabled)}>{(["ADMIN", "EDITOR", "VIEWER"] as AccountRole[]).map((role) => <option key={role} value={role}>{t(`accounts.roles.${role}`)}</option>)}</select>
            <button className={account.enabled ? "account-status enabled" : "account-status"} disabled={loading} onClick={() => void changeAccess(account, account.role, !account.enabled)}>{t(account.enabled ? "accounts.enabled" : "accounts.disabled")}</button>
            <span>{account.lastLoginAt ? new Date(account.lastLoginAt).toLocaleString() : t("accounts.never")}</span>
            <div className="account-password"><input aria-label={t("accounts.newPassword")} type="password" minLength={12} maxLength={256} autoComplete="new-password" placeholder={t("accounts.newPassword")} value={passwords[account.id] ?? ""} onChange={(event) => setPasswords((current) => ({ ...current, [account.id]: event.target.value }))} /><button disabled={loading} onClick={() => void resetPassword(account)}>{t("accounts.reset")}</button></div>
          </div>
        ))}
        <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={size} loading={loading} onPageChange={setPage} onSizeChange={(next) => { setPage(0); setSize(next); }} />
      </section>
      <Dialog />
    </>
  );
}
