import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Markdown } from "./Markdown";

export type ChatCardAction = {
  label: string;
  value: string;
  style?: "primary" | "secondary";
  url?: string;
};

type ChoiceCard = {
  type: "choice";
  title: string;
  description?: string;
  actions: ChatCardAction[];
};

type ProductCard = {
  type: "product";
  title: string;
  description?: string;
  image?: string;
  eyebrow?: string;
  price?: string;
  originalPrice?: string;
  badge?: string;
  features?: string[];
  actions?: ChatCardAction[];
};

type ChatCard = ChoiceCard | ProductCard;
type MessagePart = { kind: "text"; value: string } | { kind: "card"; value: ChatCard };

const CARD_BLOCK = /```ok-card\s*\n([\s\S]*?)```/g;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function text(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function safeWebUrl(value: unknown): string | undefined {
  const candidate = text(value);
  if (!candidate) return undefined;
  try {
    const parsed = new URL(candidate, window.location.origin);
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.href : undefined;
  } catch {
    return undefined;
  }
}

function parseActions(value: unknown): ChatCardAction[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item) => {
    if (!isRecord(item)) return [];
    const label = text(item.label);
    const actionValue = text(item.value);
    const url = safeWebUrl(item.url);
    if (!label || (!actionValue && !url)) return [];
    return [{
      label,
      value: actionValue ?? label,
      url,
      style: item.style === "primary" ? "primary" as const : "secondary" as const,
    }];
  }).slice(0, 4);
}

function parseCard(source: string): ChatCard | null {
  try {
    const value: unknown = JSON.parse(source);
    if (!isRecord(value)) return null;
    const title = text(value.title);
    if (!title) return null;
    if (value.type === "choice") {
      const actions = parseActions(value.actions);
      return actions.length ? { type: "choice", title, description: text(value.description), actions } : null;
    }
    if (value.type === "product") {
      const features = Array.isArray(value.features)
        ? value.features.map(text).filter((item): item is string => Boolean(item)).slice(0, 4)
        : undefined;
      return {
        type: "product",
        title,
        description: text(value.description),
        image: safeWebUrl(value.image),
        eyebrow: text(value.eyebrow),
        price: text(value.price),
        originalPrice: text(value.originalPrice),
        badge: text(value.badge),
        features,
        actions: parseActions(value.actions),
      };
    }
  } catch {
    return null;
  }
  return null;
}

export function parseRichMessage(source: string): MessagePart[] {
  const parts: MessagePart[] = [];
  let cursor = 0;
  for (const match of source.matchAll(CARD_BLOCK)) {
    const index = match.index ?? 0;
    const before = source.slice(cursor, index).trim();
    if (before) parts.push({ kind: "text", value: before });
    const card = parseCard(match[1]);
    if (card) parts.push({ kind: "card", value: card });
    else parts.push({ kind: "text", value: match[0] });
    cursor = index + match[0].length;
  }
  const after = source.slice(cursor).trim();
  if (after) parts.push({ kind: "text", value: after });
  return parts.length ? parts : [{ kind: "text", value: source }];
}

function ActionButtons({ actions, onAction }: { actions: ChatCardAction[]; onAction?: (value: string) => Promise<boolean> }) {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  return (
    <div className="rich-card-actions" aria-label={t("chat.cardActions")}>
      {actions.map((action) => (
        <button
          type="button"
          className={`rich-card-action ${action.style ?? "secondary"}`}
          key={`${action.label}-${action.value}`}
          disabled={(Boolean(selected) || Boolean(pending)) && !action.url}
          onClick={async () => {
            if (action.url) {
              window.open(action.url, "_blank", "noopener,noreferrer");
              return;
            }
            if (!onAction) return;
            setPending(action.value);
            setFailed(false);
            const sent = await onAction(action.value);
            setPending(null);
            if (sent) setSelected(action.value);
            else setFailed(true);
          }}
        >
          <span>{selected === action.value ? "✓" : pending === action.value ? "···" : action.style === "primary" ? "→" : ""}</span>
          {action.label}
        </button>
      ))}
      {selected && <small className="rich-card-selected">{t("chat.cardSelected")}</small>}
      {pending && <small className="rich-card-selected">{t("chat.cardSending")}</small>}
      {failed && <small className="rich-card-failed">{t("chat.cardRetry")}</small>}
    </div>
  );
}

function Card({ card, onAction }: { card: ChatCard; onAction?: (value: string) => Promise<boolean> }) {
  if (card.type === "choice") {
    return (
      <section className="rich-card choice-card">
        <div className="choice-card-mark" aria-hidden="true">?</div>
        <div className="rich-card-content">
          <h3>{card.title}</h3>
          {card.description && <p>{card.description}</p>}
          <ActionButtons actions={card.actions} onAction={onAction} />
        </div>
      </section>
    );
  }

  return (
    <section className="rich-card product-card">
      {card.image ? <img src={card.image} alt="" loading="lazy" /> : <div className="product-card-placeholder" aria-hidden="true">◇</div>}
      <div className="rich-card-content">
        <div className="product-card-topline">
          {card.eyebrow && <span className="product-card-eyebrow">{card.eyebrow}</span>}
          {card.badge && <span className="product-card-badge">{card.badge}</span>}
        </div>
        <h3>{card.title}</h3>
        {card.description && <p>{card.description}</p>}
        {card.features && <ul>{card.features.map((feature) => <li key={feature}>{feature}</li>)}</ul>}
        {card.price && <div className="product-card-price"><strong>{card.price}</strong>{card.originalPrice && <del>{card.originalPrice}</del>}</div>}
        {card.actions && card.actions.length > 0 && <ActionButtons actions={card.actions} onAction={onAction} />}
      </div>
    </section>
  );
}

export function RichChatMessage({ source, onAction }: { source: string; onAction?: (value: string) => Promise<boolean> }) {
  const parts = useMemo(() => parseRichMessage(source), [source]);
  return <div className="rich-message">{parts.map((part, index) => part.kind === "text"
    ? <Markdown source={part.value} key={`text-${index}`} />
    : <Card card={part.value} onAction={onAction} key={`card-${index}`} />)}</div>;
}
