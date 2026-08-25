import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Button, Pagination, Toggle, useConfirm, type Page } from "../shared";
import {
  createSolution,
  deleteSolution,
  listProducts,
  listSolutions,
  setSolutionStatus,
  updateSolution,
} from "./api";
import {
  emptySolutionDraft,
  type Product,
  type Solution,
  type SolutionDraft,
  type SolutionItemRole,
} from "./types";

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

const slugify = (value: string) =>
  value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

const ROLES: SolutionItemRole[] = ["PRIMARY", "ADDON", "OPTIONAL"];

export function SolutionsTab() {
  const { t } = useTranslation();
  const { confirm, Dialog } = useConfirm();
  const [solutions, setSolutions] = useState<Page<Solution> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [products, setProducts] = useState<Product[]>([]);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Solution | "new" | null>(null);
  const [draft, setDraft] = useState<SolutionDraft>(emptySolutionDraft());
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);

  const load = async (targetPage = pageNumber) => {
    try {
      const [sols, prods] = await Promise.all([
        listSolutions(targetPage, pageSize),
        listProducts(0, 1000),
      ]);
      setSolutions(sols);
      setProducts(prods.content);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  useEffect(() => {
    void load(pageNumber);
  }, [pageNumber, pageSize]);

  const open = (s?: Solution) => {
    if (s) {
      setEditing(s);
      setDraft({
        solutionKey: s.solutionKey,
        name: s.name,
        description: s.description ?? "",
        targetCustomer: s.targetCustomer ?? "",
        scenario: s.scenario ?? "",
        priceNote: s.priceNote ?? "",
        status: s.status,
        items: s.items.map((it) => ({
          productId: it.productId,
          quantity: it.quantity,
          role: it.role,
        })),
      });
    } else {
      setEditing("new");
      setDraft(emptySolutionDraft());
    }
    setNotice(null);
  };

  const save = async () => {
    if (!draft.name.trim()) return setNotice({ ok: false, text: t("product.solutions.nameRequired") });
    if (!draft.solutionKey.trim()) return setNotice({ ok: false, text: t("product.solutions.keyRequired") });
    if (draft.items.length === 0)
      return setNotice({ ok: false, text: t("product.solutions.productRequired") });
    setBusy(true);
    setNotice(null);
    try {
      if (editing === "new") await createSolution(draft);
      else if (editing) await updateSolution(editing.id, draft);
      setEditing(null);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setBusy(false);
    }
  };

  const toggleStatus = async (s: Solution) => {
    try {
      await setSolutionStatus(s.id, s.status === "ACTIVE" ? "DISCONTINUED" : "ACTIVE");
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const remove = async (s: Solution) => {
    if (!(await confirm({ message: t("product.solutions.deleteConfirm", { name: s.name }), dangerous: true }))) return;
    try {
      await deleteSolution(s.id);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const setItem = (
    index: number,
    patch: Partial<SolutionDraft["items"][number]>,
  ) => {
    setDraft((d) => ({
      ...d,
      items: d.items.map((it, i) => (i === index ? { ...it, ...patch } : it)),
    }));
  };
  const removeItem = (index: number) =>
    setDraft((d) => ({ ...d, items: d.items.filter((_, i) => i !== index) }));
  const addItem = () =>
    setDraft((d) => ({
      ...d,
      items: [...d.items, { productId: "", quantity: 1, role: "PRIMARY" }],
    }));

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    const all = solutions?.content ?? [];
    if (!q) return all;
    return all.filter((s) =>
      `${s.name} ${s.solutionKey} ${s.scenario} ${s.targetCustomer}`.toLowerCase().includes(q),
    );
  }, [solutions, search]);

  return (
    <>
      <Dialog />
      <div className="mcp-toolbar">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t("product.solutions.search")}
        />
        <span>{t("product.solutions.total", { count: solutions?.totalElements ?? 0 })}</span>
        <span style={{ flex: 1 }} />
        <Button onClick={() => open()}>＋ {t("product.solutions.add")}</Button>
      </div>

      {notice && (
        <div className={`mcp-notice ${notice.ok ? "success" : "error"}`}>
          <b>
            {notice.ok ? "✓" : "×"} {notice.text}
          </b>
        </div>
      )}

      <div className="mcp-table">
        <div className="mcp-row head">
          <span>{t("product.solutions.solution")}</span>
          <span>{t("product.solutions.scenarioCustomer")}</span>
          <span>{t("product.solutions.includedProducts")}</span>
          <span>{t("product.solutions.priceNote")}</span>
          <span>{t("common.status")}</span>
          <span>{t("common.actions")}</span>
        </div>
        {visible.map((s) => (
          <div className="mcp-row" key={s.id}>
            <span className="mcp-name">
              <i>▣</i>
              <b>{s.name}</b>
              <small>{s.solutionKey}</small>
            </span>
            <span>
              <code>{s.scenario || "—"}</code>
              <small>{s.targetCustomer || "—"}</small>
            </span>
            <span className="prod-tags">
              {s.items.slice(0, 3).map((it) => (
                <em key={it.id}>
                  {it.productName} ×{it.quantity}
                </em>
              ))}
              {s.items.length > 3 && <em>+{s.items.length - 3}</em>}
            </span>
            <span>{s.priceNote || "—"}</span>
            <span>
              <Toggle on={s.status === "ACTIVE"} setOn={() => void toggleStatus(s)} />
            </span>
            <span className="row-actions">
              <button onClick={() => open(s)}>{t("common.edit")}</button>
              <button className="danger" onClick={() => void remove(s)}>
                {t("common.delete")}
              </button>
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="mcp-empty">
            ▣<b>{t("product.solutions.empty")}</b>
          </div>
        )}
        {solutions && (
          <Pagination
            page={solutions.number}
            totalPages={solutions.totalPages}
            totalElements={solutions.totalElements}
            size={solutions.size}
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
                    {t("kickers.solutionEditor", {
                      mode: t(editing === "new" ? "common.create" : "common.edit"),
                    })}
                  </p>
                  <h2>{editing === "new" ? t("product.solutions.add") : (editing as Solution).name}</h2>
                </div>
                <button className="link-button" onClick={() => setEditing(null)}>
                  {t("common.close")} ×
                </button>
              </header>

              <div className="mcp-form">
                <label>
                  <span>{t("product.solutions.name")}</span>
                  <input
                    value={draft.name}
                    onChange={(e) => {
                      const name = e.target.value;
                      setDraft((d) => ({
                        ...d,
                        name,
                        solutionKey:
                          !d.solutionKey || d.solutionKey === slugify(draft.name)
                            ? slugify(name)
                            : d.solutionKey,
                      }));
                    }}
                  />
                </label>
                <label>
                  <span>
                    SOLUTION_KEY <small>· {t("integrations.uniqueKey")}</small>
                  </span>
                  <input
                    value={draft.solutionKey}
                    onChange={(e) => setDraft({ ...draft, solutionKey: e.target.value })}
                  />
                </label>
                <label className="wide">
                  <span>{t("product.solutions.scenario")}</span>
                  <input
                    value={draft.scenario}
                    onChange={(e) => setDraft({ ...draft, scenario: e.target.value })}
                    placeholder={t("product.solutions.scenarioPlaceholder")}
                  />
                </label>
                <label className="wide">
                  <span>{t("product.solutions.targetCustomer")}</span>
                  <input
                    value={draft.targetCustomer}
                    onChange={(e) =>
                      setDraft({ ...draft, targetCustomer: e.target.value })
                    }
                    placeholder={t("product.solutions.targetPlaceholder")}
                  />
                </label>
                <label className="wide">
                  <span>{t("product.solutions.priceNote")}</span>
                  <input
                    value={draft.priceNote}
                    onChange={(e) => setDraft({ ...draft, priceNote: e.target.value })}
                    placeholder={t("product.solutions.pricePlaceholder")}
                  />
                </label>
                <label className="wide">
                  <span>{t("product.solutions.description")}</span>
                  <textarea
                    rows={3}
                    value={draft.description}
                    onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                  />
                </label>

                <div className="wide prod-items">
                  <div className="prod-items-head">
                    <span>{t("product.solutions.includedProducts")}</span>
                    <button type="button" className="link-button" onClick={addItem}>
                      ＋ {t("product.solutions.addProduct")}
                    </button>
                  </div>
                  {draft.items.length === 0 && (
                    <small className="prod-items-empty">{t("product.solutions.noProducts")}</small>
                  )}
                  {draft.items.map((item, index) => (
                    <div className="prod-item-row" key={index}>
                      <select
                        value={item.productId}
                        onChange={(e) => setItem(index, { productId: e.target.value })}
                      >
                        <option value="">{t("product.solutions.selectProduct")}</option>
                        {products.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name}（{p.productKey}）
                          </option>
                        ))}
                      </select>
                      <input
                        type="number"
                        min={1}
                        value={item.quantity}
                        onChange={(e) =>
                          setItem(index, { quantity: Number(e.target.value) || 1 })
                        }
                        title={t("product.solutions.quantity")}
                      />
                      <select
                        value={item.role}
                        onChange={(e) =>
                          setItem(index, { role: e.target.value as SolutionItemRole })
                        }
                        title={t("product.solutions.role")}
                      >
                        {ROLES.map((r) => (
                          <option key={r} value={r}>
                            {t(`product.solutions.roles.${r}`)}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        className="link-button danger"
                        onClick={() => removeItem(index)}
                      >
                        {t("product.solutions.remove")}
                      </button>
                    </div>
                  ))}
                  {editing !== "new" && (editing as Solution).items.length > 0 && (
                    <small className="prod-items-hint">
                      {t("product.solutions.savedItems", {
                        count: (editing as Solution).items.length,
                      })}
                    </small>
                  )}
                </div>

                <label>
                  <span>{t("common.status")}</span>
                  <select
                    value={draft.status}
                    onChange={(e) =>
                      setDraft({
                        ...draft,
                        status: e.target.value as SolutionDraft["status"],
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
