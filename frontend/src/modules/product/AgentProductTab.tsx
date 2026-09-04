import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "../shared";
import {
  deleteAgentProductBinding,
  getAgentProductBinding,
  listAllProducts,
  upsertAgentProductBinding,
} from "./api";
import {
  type AgentProductBinding,
  type Product,
  type ProductBindingScope,
  type ProductCapability,
} from "./types";

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

const SCOPES: ProductBindingScope[] = [
  "ALL",
  "CATEGORY",
  "TAG",
  "EXPLICIT",
  "NONE",
];
const CAPABILITIES: ProductCapability[] = ["QUERY", "RECOMMEND", "SOLUTION"];

type FormState = {
  enabled: boolean;
  scope: ProductBindingScope;
  category: string;
  tags: string; // newline separated
  explicitIds: string[];
  capabilities: Set<ProductCapability>;
};

function parseTags(value: string | null): string {
  if (!value) return "";
  try {
    const arr = JSON.parse(value) as unknown[];
    return arr.map((x) => String(x)).join("\n");
  } catch {
    return "";
  }
}

function parseUuidList(value: string | null): string[] {
  if (!value) return [];
  try {
    return (JSON.parse(value) as unknown[]).map((x) => String(x));
  } catch {
    return [];
  }
}

export function AgentProductTab({ agentId }: { agentId: string }) {
  const { t } = useTranslation();
  const [products, setProducts] = useState<Product[]>([]);
  const [existing, setExisting] = useState<AgentProductBinding | null>(null);
  const [form, setForm] = useState<FormState>({
    enabled: true,
    scope: "ALL",
    category: "",
    tags: "",
    explicitIds: [],
    capabilities: new Set(["QUERY"]),
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      setLoading(true);
      try {
        const [binding, prods] = await Promise.all([
          getAgentProductBinding(agentId),
          listAllProducts(),
        ]);
        if (cancelled) return;
        setExisting(binding);
        setProducts(prods);
        if (binding) {
          setForm({
            enabled: binding.enabled,
            scope: binding.scope,
            category: binding.scope === "CATEGORY" ? binding.scopeValue ?? "" : "",
            tags: binding.scope === "TAG" ? parseTags(binding.scopeValue) : "",
            explicitIds:
              binding.scope === "EXPLICIT" ? parseUuidList(binding.scopeValue) : [],
            capabilities: new Set(binding.capabilities),
          });
        }
      } catch (e) {
        if (!cancelled) setNotice({ ok: false, text: msg(e) });
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [agentId]);

  const productName = useMemo(
    () => new Map(products.map((p) => [p.id, p.name])),
    [products],
  );

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setDirty(true);
  };

  const toggleCapability = (cap: ProductCapability) => {
    setForm((f) => {
      const next = new Set(f.capabilities);
      if (next.has(cap)) next.delete(cap);
      else next.add(cap);
      // RECOMMEND/SOLUTION imply QUERY
      if ((cap === "RECOMMEND" || cap === "SOLUTION") && next.has(cap)) next.add("QUERY");
      // Removing QUERY also removes its dependents
      if (cap === "QUERY" && !next.has("QUERY")) {
        next.delete("RECOMMEND");
        next.delete("SOLUTION");
      }
      return { ...f, capabilities: next };
    });
    setDirty(true);
  };

  const toggleExplicit = (id: string) => {
    setForm((f) => ({
      ...f,
      explicitIds: f.explicitIds.includes(id)
        ? f.explicitIds.filter((x) => x !== id)
        : [...f.explicitIds, id],
    }));
    setDirty(true);
  };

  const buildScopeValue = (): string | null => {
    switch (form.scope) {
      case "CATEGORY":
        return form.category.trim() || null;
      case "TAG":
        return JSON.stringify(
          [
            ...new Set(
              form.tags
                .split(/[\n,，]/)
                .map((t) => t.trim())
                .filter(Boolean),
            ),
          ],
        );
      case "EXPLICIT":
        return JSON.stringify(form.explicitIds);
      default:
        return null;
    }
  };

  const save = async () => {
    if (form.scope === "CATEGORY" && !form.category.trim())
      return setNotice({ ok: false, text: t("product.binding.categoryRequired") });
    if (form.scope === "TAG" && !form.tags.trim())
      return setNotice({ ok: false, text: t("product.binding.tagRequired") });
    if (form.scope === "EXPLICIT" && form.explicitIds.length === 0)
      return setNotice({ ok: false, text: t("product.binding.productRequired") });
    if (form.capabilities.size === 0)
      return setNotice({ ok: false, text: t("product.binding.capabilityRequired") });
    setSaving(true);
    setNotice(null);
    try {
      const saved = await upsertAgentProductBinding(agentId, {
        scope: form.scope,
        scopeValue: buildScopeValue(),
        capabilities: [...form.capabilities],
        enabled: form.enabled,
      });
      setExisting(saved);
      setDirty(false);
      setNotice({ ok: true, text: t("product.binding.saved") });
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    setSaving(true);
    setNotice(null);
    try {
      await deleteAgentProductBinding(agentId);
      setExisting(null);
      setForm({
        enabled: true,
        scope: "ALL",
        category: "",
        tags: "",
        explicitIds: [],
        capabilities: new Set(["QUERY"]),
      });
      setDirty(false);
      setNotice({ ok: true, text: t("product.binding.removed") });
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="config-section">{t("common.loading")}</div>;

  const categories = [
    ...new Set(products.map((p) => p.category).filter((c): c is string => !!c)),
  ].sort();
  const allTags = [...new Set(products.flatMap((p) => p.scenarioTags ?? []))].sort();

  return (
    <div className="config-section">
      <div className="section-head">
        <b>{t("product.binding.title")}</b>
        <small>{t("product.binding.hint")}</small>
      </div>

      <div className="prod-binding">
        <div className="prod-binding-row">
          <label className="prod-binding-field">
            <span>{t("product.binding.scope")}</span>
            <select
              value={form.scope}
              onChange={(e) => set("scope", e.target.value as ProductBindingScope)}
            >
              {SCOPES.map((s) => (
                <option key={s} value={s}>
                  {t(`product.binding.scopes.${s}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="prod-binding-check">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => set("enabled", e.target.checked)}
            />
            <span>{t("product.binding.enable")}</span>
          </label>
        </div>

        {form.scope === "CATEGORY" && (
          <label className="prod-binding-field">
            <span>{t("product.binding.category")}</span>
            <input
              list="prod-categories"
              value={form.category}
              onChange={(e) => set("category", e.target.value)}
              placeholder={t("product.binding.categoryPlaceholder")}
            />
            <datalist id="prod-categories">
              {categories.map((c) => (
                <option key={c} value={c} />
              ))}
            </datalist>
          </label>
        )}

        {form.scope === "TAG" && (
          <label className="prod-binding-field">
            <span>{t("product.binding.tags")}</span>
            <textarea
              rows={3}
              value={form.tags}
              onChange={(e) => set("tags", e.target.value)}
              placeholder={t("product.binding.tagsPlaceholder")}
            />
            {allTags.length > 0 && (
              <small className="prod-tag-suggest">
                {t("product.binding.existingTags")}
                {allTags.slice(0, 12).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() =>
                      set(
                        "tags",
                        form.tags ? `${form.tags.trim()}\n${t}` : t,
                      )
                    }
                  >
                    {t}
                  </button>
                ))}
              </small>
            )}
          </label>
        )}

        {form.scope === "EXPLICIT" && (
          <div className="prod-binding-field">
            <span>{t("product.binding.selectedProducts", { count: form.explicitIds.length })}</span>
            <div className="prod-pick-list">
              {products.length === 0 && (
                <small>{t("product.binding.noProducts")}</small>
              )}
              {products
                .filter((p) => p.status === "ACTIVE")
                .map((p) => (
                  <label key={p.id}>
                    <input
                      type="checkbox"
                      checked={form.explicitIds.includes(p.id)}
                      onChange={() => toggleExplicit(p.id)}
                    />
                    <span>
                      <b>{p.name}</b>
                      <small>{p.productKey}</small>
                    </span>
                  </label>
                ))}
            </div>
          </div>
        )}

        {form.scope === "NONE" && (
          <small className="prod-scope-hint">
            {t("product.binding.noneHint")}
          </small>
        )}

        <div className="prod-binding-field">
          <span>{t("product.binding.capabilitiesTitle")}</span>
          <div className="prod-cap-list">
            {CAPABILITIES.map((cap) => {
              const on = form.capabilities.has(cap);
              const implied =
                cap === "QUERY" &&
                (form.capabilities.has("RECOMMEND") ||
                  form.capabilities.has("SOLUTION"));
              return (
                <label key={cap} className={on ? "on" : ""}>
                  <input
                    type="checkbox"
                    checked={on}
                    onChange={() => toggleCapability(cap)}
                  />
                  <span>
                    {t(`product.binding.capabilities.${cap}`)}
                    {implied && <small>{t("product.binding.implied")}</small>}
                  </span>
                </label>
              );
            })}
          </div>
        </div>
      </div>

      {notice && (
        <div className={`mcp-notice ${notice.ok ? "success" : "error"}`}>
          <b>
            {notice.ok ? "✓" : "×"} {notice.text}
          </b>
        </div>
      )}

      <div className="config-save-bar">
        <Button onClick={() => void save()} disabled={saving || !dirty}>
          {t(saving ? "common.saving" : "product.binding.save")}
        </Button>
        {existing && (
          <button
            className="ui-button quiet danger"
            onClick={() => void remove()}
            disabled={saving}
          >
            {t("product.binding.remove")}
          </button>
        )}
        {dirty && <span className="dirty-flag">{t("common.unsavedChanges")}</span>}
      </div>
    </div>
  );
}
