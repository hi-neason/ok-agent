import type { ReactNode } from "react";

export function Button({
  children,
  quiet = false,
  onClick,
  disabled = false,
}: {
  children: ReactNode;
  quiet?: boolean;
  onClick?: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={quiet ? "ui-button quiet" : "ui-button"}
    >
      {children}
    </button>
  );
}

export function Toggle({
  on,
  setOn,
  label,
}: {
  on: boolean;
  setOn: (next: boolean) => void;
  label?: string;
}) {
  return (
    <button
      aria-label={label}
      onClick={() => setOn(!on)}
      className={`toggle ${on ? "on" : ""}`}
    >
      <i />
    </button>
  );
}

export function Field({
  label,
  value,
  hint,
  wide = false,
}: {
  label: string;
  value: string;
  hint?: string;
  wide?: boolean;
}) {
  return (
    <label className={`field ${wide ? "wide" : ""}`}>
      <span>{label}</span>
      <input defaultValue={value} />
      {hint && <small>{hint}</small>}
    </label>
  );
}

export function PageHeader({
  kicker,
  title,
  description,
  action,
}: {
  kicker: string;
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        <p className="kicker">{kicker}</p>
        <h1>{title}</h1>
        <p className="page-description">{description}</p>
      </div>
      {action}
    </header>
  );
}
