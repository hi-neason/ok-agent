import type { Page } from "../shared";
import type { AccountRole } from "./types";

export type Account = {
  id: string;
  username: string;
  displayName: string;
  role: AccountRole;
  enabled: boolean;
  lastLoginAt: string | null;
  updatedAt: string;
};

export type CreateAccount = {
  username: string;
  displayName: string;
  password: string;
  role: AccountRole;
  enabled: boolean;
};

async function jsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? "ACCOUNT_REQUEST_FAILED");
  }
  return (await response.json()) as T;
}

export async function fetchAccounts(page: number, size: number): Promise<Page<Account>> {
  return jsonOrThrow<Page<Account>>(
    await fetch(`/api/v1/accounts?page=${page}&size=${size}`),
  );
}

export async function createAccount(input: CreateAccount): Promise<Account> {
  return jsonOrThrow<Account>(
    await fetch("/api/v1/accounts", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  );
}

export async function updateAccount(
  account: Pick<Account, "id" | "displayName" | "role" | "enabled">,
): Promise<Account> {
  return jsonOrThrow<Account>(
    await fetch(`/api/v1/accounts/${account.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        displayName: account.displayName,
        role: account.role,
        enabled: account.enabled,
      }),
    }),
  );
}

export async function resetAccountPassword(id: string, password: string): Promise<void> {
  const response = await fetch(`/api/v1/accounts/${id}/password`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ password }),
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? "ACCOUNT_REQUEST_FAILED");
  }
}
