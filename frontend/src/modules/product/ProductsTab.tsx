import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Button, Pagination, Toggle, useConfirm, type Page } from "../shared";
import {
  createProduct,
  deleteProduct,
  listProducts,
  setProductStatus,
  updateProduct,
} from "./api";
import { emptyProductDraft, type Product, type ProductDraft } from "./types";

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

const slugify = (value: string) =>
  value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

const formatPrice = (p: Product) => {
  const lo = p.priceMin,
    hi = p.priceMax;
  if (lo === null && hi === null) return "—";
  const cur = p.currency || "CNY";
  if (lo !== null && hi !== null && lo !== hi) return `${cur} ${lo}~${hi}`;
  return `${cur} ${lo ?? hi}`;
};

export function ProductsTab() {
  const { t } = useTranslation();
  const { confirm, Dialog } = useConfirm();
  const [products, setProducts] = useState<Page<Product> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [editing, setEditing] = useState<Product | "new" | null>(null);
  const [draft, setDraft] = useState<ProductDraft>(emptyProductDraft());
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);

  const load = async (targetPage = pageNumber) => {
    try {
      setProducts(await listProducts(targetPage, pageSize));
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  useEffect(() => {
    void load(pageNumber);
  }, [pageNumber, pageSize]);

  const categories = useMemo(
    () =>
      [
        ...new Set(
          (products?.content ?? []).map((p) => p.category).filter((c): c is string => !!c),
        ),
      ].sort(),
    [products],
  );

  const open = (p?: Product) => {
    if (p) {
      setEditing(p);
      setDraft({
        productKey: p.productKey,
        name: p.name,
        brand: p.brand ?? "",
        category: p.category ?? "",
        priceMin: p.priceMin,
        priceMax: p.priceMax,
        currency: p.currency ?? "CNY",
        spec: p.spec && Object.keys(p.spec).length ? JSON.stringify(p.spec, null, 2) : "",
        sellingPoints: p.sellingPoints ?? "",
        scenarioTags: (p.scenarioTags ?? []).join("\n"),
        imageUrls: (p.imageUrls ?? []).join("\n"),
        description: p.description ?? "",
        status: p.status,
        weight: p.weight,
      });
    } else {
      setEditing("new");
      setDraft(emptyProductDraft());
    }
    setNotice(null);
  };

  const save = async () => {
    if (!draft.name.trim()) return setNotice({ ok: false, text: t("product.products.nameRequired") });
    if (!draft.productKey.trim()) return setNotice({ ok: false, text: t("product.products.keyRequired") });
    if (!/^[a-z0-9-]+$/.test(draft.productKey))
      return setNotice({ ok: false, text: t("product.products.keyInvalid") });
    setBusy(true);
    setNotice(null);
    try {
      if (editing === "new") await createProduct(draft);
      else if (editing) await updateProduct(editing.id, draft);
      setEditing(null);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setBusy(false);
    }
  };

  const toggleStatus = async (p: Product) => {
    try {
      await setProductStatus(p.id, p.status === "ACTIVE" ? "DISCONTINUED" : "ACTIVE");
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const remove = async (p: Product) => {
    if (!(await confirm({ message: t("product.products.deleteConfirm", { name: p.name }), dangerous: true }))) return;
    try {
      await deleteProduct(p.id);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const visible = useMemo(
    () =>
      (products?.content ?? []).filter((p) => {
        if (category && p.category !== category) return false;
        const q = search.trim().toLowerCase();
        if (!q) return true;
        return `${p.name} ${p.productKey} ${p.brand} ${p.category}`
          .toLowerCase()
          .includes(q);
      }),
    [products, search, category],
  );

  return (
    <>
      <Dialog />
      <div className="mcp-toolbar">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t("product.products.search")}
        />
        <select
          className="prod-filter-select"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
        >
          <option value="">{t("product.products.allCategories")}</option>
          {categories.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <span>{t("product.products.total", { count: products?.totalElements ?? 0 })}</span>
        <span style={{ flex: 1 }} />
        <Button onClick={() => open()}>＋ {t("product.products.add")}</Button>
      </div>

      {notice && (
        <div className={`mcp-notice ${notice.ok ? "success" : "error"}`}>
          <b>
            {notice.ok ? "✓" : "×"} {notice.text}
          </b>
        </div>
      )}

      <div className="mcp-table prod-grid">
        <div className="mcp-row head">
          <span>{t("product.products.product")}</span>
          <span>{t("product.products.categoryBrand")}</span>
          <span>{t("product.products.price")}</span>
          <span>{t("product.products.scenarioTags")}</span>
          <span>{t("product.products.weight")}</span>
          <span>{t("common.status")}</span>
          <span>{t("common.actions")}</span>
        </div>
        {visible.map((p) => (
          <div className="mcp-row" key={p.id}>
            <span className="mcp-name">
              <i>◈</i>
              <b>{p.name}</b>
              <small>{p.productKey}</small>
            </span>
            <span>
              <code>{p.category || "—"}</code>
              <small>{p.brand || "—"}</small>
            </span>
            <span>{formatPrice(p)}</span>
            <span className="prod-tags">
              {(p.scenarioTags ?? []).slice(0, 3).map((t) => (
                <em key={t}>{t}</em>
              ))}
            </span>
            <span>{p.weight}</span>
            <span>
              <Toggle on={p.status === "ACTIVE"} setOn={() => void toggleStatus(p)} />
            </span>
            <span className="row-actions">
              <button onClick={() => open(p)}>{t("common.edit")}</button>
              <button className="danger" onClick={() => void remove(p)}>
                {t("common.delete")}
              </button>
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="mcp-empty">
            ◈<b>{t("product.products.empty")}</b>
          </div>
        )}
        {products && (
          <Pagination
            page={products.number}
            totalPages={products.totalPages}
            totalElements={products.totalElements}
            size={products.size}
            loading={busy}
            onPageChange={setPageNumber}
            onSizeChange={(size) => {
              setPageSize(size);
              setPageNumber(0);
            }}
          />
        )}
      </div>

      {editing &&
        createPortal(
          <div className="model-modal-mask" onMouseDown={() => setEditing(null)}>
            <div
              className="mcp-inspector prod-editor"
              role="dialog"
              onMouseDown={(e) => e.stopPropagation()}
            >
              <header>
                <div>
                  <p className="kicker">
                    {t("kickers.productEditor", {
                      mode: t(editing === "new" ? "common.create" : "common.edit"),
                    })}
                  </p>
                  <h2>{editing === "new" ? t("product.products.add") : (editing as Product).name}</h2>
                </div>
                <button className="link-button" onClick={() => setEditing(null)}>
                  {t("common.close")} ×
                </button>
              </header>

              <div className="mcp-form">
                <label>
                  <span>{t("product.products.name")}</span>
                  <input
                    value={draft.name}
                    onChange={(e) => {
                      const name = e.target.value;
                      setDraft((d) => ({
                        ...d,
                        name,
                        productKey:
                          !d.productKey || d.productKey === slugify(draft.name)
                            ? slugify(name)
                            : d.productKey,
                      }));
                    }}
                  />
                </label>
                <label>
                  <span>
                    PRODUCT_KEY <small>· {t("integrations.uniqueKey")}</small>
                  </span>
                  <input
                    value={draft.productKey}
                    onChange={(e) => setDraft({ ...draft, productKey: e.target.value })}
                  />
                </label>
                <label>
                  <span>{t("product.products.brand")}</span>
                  <input
                    value={draft.brand}
                    onChange={(e) => setDraft({ ...draft, brand: e.target.value })}
                  />
                </label>
                <label>
                  <span>{t("product.products.category")}</span>
                  <input
                    value={draft.category}
                    onChange={(e) => setDraft({ ...draft, category: e.target.value })}
                    placeholder={t("product.products.categoryPlaceholder")}
                  />
                </label>
                <label>
                  <span>{t("product.products.priceMin")}</span>
                  <input
                    type="number"
                    value={draft.priceMin ?? ""}
                    onChange={(e) =>
                      setDraft({
                        ...draft,
                        priceMin: e.target.value === "" ? null : Number(e.target.value),
                      })
                    }
                  />
                </label>
                <label>
                  <span>{t("product.products.priceMax")}</span>
                  <input
                    type="number"
                    value={draft.priceMax ?? ""}
                    onChange={(e) =>
                      setDraft({
                        ...draft,
                        priceMax: e.target.value === "" ? null : Number(e.target.value),
                      })
                    }
                  />
                </label>
                <label>
                  <span>{t("product.products.currency")}</span>
                  <input
                    value={draft.currency}
                    onChange={(e) => setDraft({ ...draft, currency: e.target.value })}
                  />
                </label>
                <label>
                  <span>
                    {t("product.products.weight")} <small>· {t("product.products.weightHint")}</small>
                  </span>
                  <input
                    type="number"
                    value={draft.weight}
                    onChange={(e) => setDraft({ ...draft, weight: Number(e.target.value) })}
                  />
                </label>
                <label className="wide">
                  <span>{t("product.products.tags")}</span>
                  <textarea
                    rows={2}
                    value={draft.scenarioTags}
                    onChange={(e) => setDraft({ ...draft, scenarioTags: e.target.value })}
                    placeholder={t("product.products.tagsPlaceholder")}
                  />
                </label>
                <label className="wide">
                  <span>{t("product.products.images")}</span>
                  <textarea
                    rows={2}
                    value={draft.imageUrls}
                    onChange={(e) => setDraft({ ...draft, imageUrls: e.target.value })}
                  />
                </label>
                <label className="wide">
                  <span>{t("product.products.sellingPoints")}</span>
                  <textarea
                    rows={3}
                    value={draft.sellingPoints}
                    onChange={(e) => setDraft({ ...draft, sellingPoints: e.target.value })}
                  />
                </label>
                <label className="wide">
                  <span>
                    {t("product.products.spec")} <small>· {t("product.products.jsonObject")}</small>
                  </span>
                  <textarea
                    rows={3}
                    className="mono"
                    value={draft.spec}
                    onChange={(e) => setDraft({ ...draft, spec: e.target.value })}
                    placeholder={t("product.products.specPlaceholder")}
                  />
                </label>
                <label className="wide">
                  <span>{t("product.products.details")}</span>
                  <textarea
                    rows={4}
                    value={draft.description}
                    onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                  />
                </label>
                <label>
                  <span>{t("common.status")}</span>
                  <select
                    value={draft.status}
                    onChange={(e) =>
                      setDraft({
                        ...draft,
                        status: e.target.value as ProductDraft["status"],
                      })
                    }
                  >
                    <option value="ACTIVE">{t("product.products.active")}</option>
                    <option value="DISCONTINUED">{t("product.products.discontinued")}</option>
                  </select>
                </label>
              </div>

              {notice && (
                <div className={`mcp-notice ${notice.ok ? "success" : "error"}`}>
                  <b>
                    {notice.ok ? "✓" : "×"} {notice.text}
                  </b>
                </div>
              )}

              <footer>
                <Button onClick={() => void save()} disabled={busy}>
                  {busy ? t("common.saving") : t("common.save")}
                </Button>
              </footer>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
