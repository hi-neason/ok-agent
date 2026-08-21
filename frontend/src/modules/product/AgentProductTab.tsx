import { useEffect, useMemo, useState } from "react";
import { Button } from "../shared";
import {
  deleteAgentProductBinding,
  getAgentProductBinding,
  listProducts,
  upsertAgentProductBinding,
} from "./api";
import {
  CAPABILITY_LABELS,
  SCOPE_LABELS,
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
          listProducts().catch(() => [] as Product[]),
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
      return setNotice({ ok: false, text: "按品类时必须填写品类名" });
    if (form.scope === "TAG" && !form.tags.trim())
      return setNotice({ ok: false, text: "按标签时至少填写一个场景标签" });
    if (form.scope === "EXPLICIT" && form.explicitIds.length === 0)
      return setNotice({ ok: false, text: "指定产品时至少选择一个产品" });
    if (form.capabilities.size === 0)
      return setNotice({ ok: false, text: "至少开启一项能力" });
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
      setNotice({ ok: true, text: "产品绑定已保存" });
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
      setNotice({ ok: true, text: "产品绑定已移除" });
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="config-section">加载中…</div>;

  const categories = [
    ...new Set(products.map((p) => p.category).filter((c): c is string => !!c)),
  ].sort();
  const allTags = [...new Set(products.flatMap((p) => p.scenarioTags ?? []))].sort();

  return (
    <div className="config-section">
      <div className="section-head">
        <b>产品与方案</b>
        <small>
          绑定后，Agent 运行时会获得产品工具：search_products 查询、recommend_products
          规则召回+模型精选、list/get_solution 方案推荐。可见范围按品类/标签/指定产品收窄，未绑定时不注册任何产品工具。
        </small>
      </div>

      <div className="prod-binding">
        <div className="prod-binding-row">
          <label className="prod-binding-field">
            <span>可见范围</span>
            <select
              value={form.scope}
              onChange={(e) => set("scope", e.target.value as ProductBindingScope)}
            >
              {SCOPES.map((s) => (
                <option key={s} value={s}>
                  {SCOPE_LABELS[s]}
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
            <span>启用该绑定</span>
          </label>
        </div>

        {form.scope === "CATEGORY" && (
          <label className="prod-binding-field">
            <span>品类</span>
            <input
              list="prod-categories"
              value={form.category}
              onChange={(e) => set("category", e.target.value)}
              placeholder="选择或输入品类名"
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
            <span>场景标签（每行一个）</span>
            <textarea
              rows={3}
              value={form.tags}
              onChange={(e) => set("tags", e.target.value)}
              placeholder={"中小企业\n电商客服"}
            />
            {allTags.length > 0 && (
              <small className="prod-tag-suggest">
                已有标签：
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
            <span>指定产品（已选 {form.explicitIds.length}）</span>
            <div className="prod-pick-list">
              {products.length === 0 && (
                <small>暂无产品，请先到「产品与方案」添加。</small>
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
            选择「不开放」会移除该 Agent 的全部产品工具，等同于不绑定。
          </small>
        )}

        <div className="prod-binding-field">
          <span>开放能力</span>
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
                    {CAPABILITY_LABELS[cap]}
                    {implied && <small>（推荐/方案依赖）</small>}
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
          {saving ? "保存中…" : "保存产品绑定"}
        </Button>
        {existing && (
          <button
            className="ui-button quiet danger"
            onClick={() => void remove()}
            disabled={saving}
          >
            移除绑定
          </button>
        )}
        {dirty && <span className="dirty-flag">未保存的改动</span>}
      </div>
    </div>
  );
}
