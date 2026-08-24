import {
  createContext,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  AUTH_UNAUTHORIZED_EVENT,
  clearAccessToken,
  getAccessToken,
  storeAccessToken,
} from "./authFetch";
import { LoginPage } from "./LoginPage";
import type { AuthUser, LoginResponse } from "./types";

type AuthContextValue = {
  user: AuthUser;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

type AuthState =
  | { status: "loading"; user: null }
  | { status: "anonymous"; user: null }
  | { status: "authenticated"; user: AuthUser };

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() =>
    getAccessToken()
      ? { status: "loading", user: null }
      : { status: "anonymous", user: null },
  );

  const logout = () => {
    clearAccessToken();
    setState({ status: "anonymous", user: null });
  };

  useEffect(() => {
    const handleUnauthorized = () => logout();
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
  }, []);

  useEffect(() => {
    if (state.status !== "loading") return;
    fetch("/api/v1/auth/me")
      .then(async (response) => {
        if (!response.ok) throw new Error("AUTH_SESSION_INVALID");
        return (await response.json()) as AuthUser;
      })
      .then((user) => setState({ status: "authenticated", user }))
      .catch(() => logout());
  }, [state.status]);

  const login = async (username: string, password: string): Promise<void> => {
    const response = await fetch("/api/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    if (!response.ok) throw new Error("INVALID_CREDENTIALS");
    const result = (await response.json()) as LoginResponse;
    storeAccessToken(result.accessToken);
    setState({ status: "authenticated", user: result.user });
  };

  const value = useMemo<AuthContextValue | null>(
    () =>
      state.status === "authenticated"
        ? { user: state.user, logout }
        : null,
    [state],
  );

  if (state.status === "loading") {
    return <div className="auth-loading">OK AGENT</div>;
  }
  if (state.status === "anonymous") {
    return <LoginPage onLogin={login} />;
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
