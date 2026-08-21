import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Button, Toggle, useConfirm } from "../shared";
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
  const { confirm, Dialog } = useConfirm();
  const [products, setProducts] = useState<Product[]>([]);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [editing, setEditing] = useState<Product | "new" | null>(null);
  const [draft, setDraft] = useState<ProductDraft>(emptyProductDraft());
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);

  const load = async () => {
    try {
      setProducts(await listProducts());
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const categories = useMemo(
    () =>
      [...new Set(products.map((p) => p.category).filter((c): c is string => !!c))].sort(),
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
    if (!draft.name.trim()) return setNotice({ ok: false, text: "请填写产品名称" });
    if (!draft.productKey.trim()) return setNotice({ ok: false, text: "请填写产品 KEY" });
    if (!/^[a-z0-9-]+$/.test(draft.productKey))
      return setNotice({ ok: false, text: "产品 KEY 只能包含小写字母、数字、连字符" });
    setBusy(true);
    setNotice(null);
    try {
      if (editing === "new") await createProduct(draft);
      else if (editing) await updateProduct(editing.id, draft);
      setEditing(null);
      await load();
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setBusy(false);
    }
  };

  const toggleStatus = async (p: Product) => {
    try {
      await setProductStatus(p.id, p.status === "ACTIVE" ? "DISCONTINUED" : "ACTIVE");
      await load();
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const remove = async (p: Product) => {
    if (!(await confirm({ message: `确认删除产品「${p.name}」？`, dangerous: true }))) return;
    try {
      await deleteProduct(p.id);
      await load();
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const visible = useMemo(
    () =>
      products.filter((p) => {
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
          placeholder="搜索产品名称 / KEY / 品牌 / 品类"
        />
        <select
          className="prod-filter-select"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
        >
          <option value="">全部品类</option>
          {categories.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <span>{visible.length} 个产品</span>
        <span style={{ flex: 1 }} />
        <Button onClick={() => open()}>＋ 添加产品</Button>
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
          <span>产品</span>
          <span>品类 / 品牌</span>
          <span>价格区间</span>
          <span>场景标签</span>
          <span>权重</span>
          <span>状态</span>
          <span>操作</span>
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
              <button onClick={() => open(p)}>编辑</button>
              <button className="danger" onClick={() => void remove(p)}>
                删除
              </button>
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="mcp-empty">
            ◈<b>暂无产品，点击右上角添加</b>
          </div>
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
                    PRODUCT / {editing === "new" ? "CREATE" : "EDIT"}
                  </p>
                  <h2>{editing === "new" ? "添加产品" : (editing as Product).name}</h2>
                </div>
                <button className="link-button" onClick={() => setEditing(null)}>
                  关闭 ×
                </button>
              </header>

              <div className="mcp-form">
                <label>
                  <span>产品名称 *</span>
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
                    PRODUCT_KEY <small>· 唯一标识</small>
                  </span>
                  <input
                    value={draft.productKey}
                    onChange={(e) => setDraft({ ...draft, productKey: e.target.value })}
                  />
                </label>
                <label>
                  <span>品牌</span>
                  <input
                    value={draft.brand}
                    onChange={(e) => setDraft({ ...draft, brand: e.target.value })}
                  />
                </label>
                <label>
                  <span>品类</span>
                  <input
                    value={draft.category}
                    onChange={(e) => setDraft({ ...draft, category: e.target.value })}
                    placeholder="如：智能客服 / 硬件 / 增值服务"
                  />
                </label>
                <label>
                  <span>最低价</span>
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
                  <span>最高价</span>
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
                  <span>币种</span>
                  <input
                    value={draft.currency}
                    onChange={(e) => setDraft({ ...draft, currency: e.target.value })}
                  />
                </label>
                <label>
                  <span>
                    权重 <small>· 越高越优先推荐</small>
                  </span>
                  <input
                    type="number"
                    value={draft.weight}
                    onChange={(e) => setDraft({ ...draft, weight: Number(e.target.value) })}
                  />
                </label>
                <label className="wide">
                  <span>场景标签（每行一个或逗号分隔）</span>
                  <textarea
                    rows={2}
                    value={draft.scenarioTags}
                    onChange={(e) => setDraft({ ...draft, scenarioTags: e.target.value })}
                    placeholder={"中小企业\n电商客服"}
                  />
                </label>
                <label className="wide">
                  <span>图片 URL（每行一个）</span>
                  <textarea
                    rows={2}
                    value={draft.imageUrls}
                    onChange={(e) => setDraft({ ...draft, imageUrls: e.target.value })}
                  />
                </label>
                <label className="wide">
                  <span>卖点（selling points）</span>
                  <textarea
                    rows={3}
                    value={draft.sellingPoints}
                    onChange={(e) => setDraft({ ...draft, sellingPoints: e.target.value })}
                  />
                </label>
                <label className="wide">
                  <span>
                    规格 spec <small>· JSON 对象</small>
                  </span>
                  <textarea
                    rows={3}
                    className="mono"
                    value={draft.spec}
                    onChange={(e) => setDraft({ ...draft, spec: e.target.value })}
                    placeholder='{"并发路数":"100","部署方式":"SaaS"}'
                  />
                </label>
                <label className="wide">
                  <span>详细描述</span>
                  <textarea
                    rows={4}
                    value={draft.description}
                    onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                  />
                </label>
                <label>
                  <span>状态</span>
                  <select
                    value={draft.status}
                    onChange={(e) =>
                      setDraft({
                        ...draft,
                        status: e.target.value as ProductDraft["status"],
                      })
                    }
                  >
                    <option value="ACTIVE">在售（ACTIVE）</option>
                    <option value="DISCONTINUED">停售（DISCONTINUED）</option>
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
                  {busy ? "保存中…" : "保存"}
                </Button>
              </footer>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
