import { type FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";

type LoginPageProps = {
  onLogin: (username: string, password: string) => Promise<void>;
};

export function LoginPage({ onLogin }: LoginPageProps) {
  const { t, i18n } = useTranslation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [failed, setFailed] = useState(false);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setFailed(false);
    try {
      await onLogin(username, password);
    } catch {
      setFailed(true);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <div className="auth-brand">
          <span className="brand-mark">ok</span>
          <span>AGENT</span>
        </div>
        <p className="auth-kicker">HARNESS CONTROL PLANE</p>
        <h1>{t("auth.title")}</h1>
        <p className="auth-description">{t("auth.description")}</p>
        <form onSubmit={submit}>
          <label>
            <span>{t("auth.username")}</span>
            <input
              autoComplete="username"
              autoFocus
              maxLength={128}
              required
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </label>
          <label>
            <span>{t("auth.password")}</span>
            <input
              autoComplete="current-password"
              maxLength={256}
              required
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {failed && <p className="auth-error">{t("auth.invalidCredentials")}</p>}
          <button disabled={submitting} type="submit">
            {submitting ? t("auth.signingIn") : t("auth.signIn")}
          </button>
        </form>
        <button
          aria-label={t("common.language")}
          className="auth-language"
          onClick={() =>
            i18n.changeLanguage(i18n.resolvedLanguage === "zh-CN" ? "en-US" : "zh-CN")
          }
          type="button"
        >
          {i18n.resolvedLanguage === "zh-CN" ? t("common.switchToEnglish") : t("common.switchToChinese")}
        </button>
      </section>
    </main>
  );
}
