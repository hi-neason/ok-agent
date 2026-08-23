import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
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

const ROLE_LABELS: Record<SolutionItemRole, string> = {
  PRIMARY: "主产品",
  ADDON: "附加",
  OPTIONAL: "可选",
};

export function SolutionsTab() {
  const { confirm, Dialog } = useConfirm();
  const [solutions, setSolutions] = useState<Page<Solution> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [products, setProducts] = useState<Product[]>([]);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Solution | "new" | null>(null);
  const [draft, setDraft] = useState<SolutionDraft>(emptySolutionDraft());
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);

  const load = async (targetPage: number) => {
    try {
      const [sols, prods] = await Promise.all([
        listSolutions(targetPage, 20),
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
  }, [pageNumber]);

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
    if (!draft.name.trim()) return setNotice({ ok: false, text: "请填写方案名称" });
    if (!draft.solutionKey.trim()) return setNotice({ ok: false, text: "请填写方案 KEY" });
    if (draft.items.length === 0)
      return setNotice({ ok: false, text: "至少添加一个产品" });
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
    if (!(await confirm({ message: `确认删除方案「${s.name}」？`, dangerous: true }))) return;
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
          placeholder="搜索方案名称 / KEY / 场景 / 目标客户"
        />
        <span>{solutions?.totalElements ?? 0} 个方案</span>
        <span style={{ flex: 1 }} />
        <Button onClick={() => open()}>＋ 添加方案</Button>
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
          <span>方案</span>
          <span>场景 / 目标客户</span>
          <span>包含产品</span>
          <span>价格说明</span>
          <span>状态</span>
          <span>操作</span>
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
              <button onClick={() => open(s)}>编辑</button>
              <button className="danger" onClick={() => void remove(s)}>
                删除
              </button>
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="mcp-empty">
            ▣<b>暂无方案，点击右上角添加</b>
          </div>
        )}
        {solutions && (
          <Pagination
            page={solutions.number}
            totalPages={solutions.totalPages}
            totalElements={solutions.totalElements}
            size={solutions.size}
            onPageChange={setPageNumber}
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
                    SOLUTION / {editing === "new" ? "CREATE" : "EDIT"}
                  </p>
                  <h2>{editing === "new" ? "添加方案" : (editing as Solution).name}</h2>
                </div>
                <button className="link-button" onClick={() => setEditing(null)}>
                  关闭 ×
                </button>
              </header>

              <div className="mcp-form">
                <label>
                  <span>方案名称 *</span>
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
                    SOLUTION_KEY <small>· 唯一标识</small>
                  </span>
                  <input
                    value={draft.solutionKey}
                    onChange={(e) => setDraft({ ...draft, solutionKey: e.target.value })}
                  />
                </label>
                <label className="wide">
                  <span>适用场景</span>
                  <input
                    value={draft.scenario}
                    onChange={(e) => setDraft({ ...draft, scenario: e.target.value })}
                    placeholder="如：电商客服一体化上线"
                  />
                </label>
                <label className="wide">
                  <span>目标客户</span>
                  <input
                    value={draft.targetCustomer}
                    onChange={(e) =>
                      setDraft({ ...draft, targetCustomer: e.target.value })
                    }
                    placeholder="如：年 GMV 千万级电商企业"
                  />
                </label>
                <label className="wide">
                  <span>价格说明</span>
                  <input
                    value={draft.priceNote}
                    onChange={(e) => setDraft({ ...draft, priceNote: e.target.value })}
                    placeholder="如：按年订阅，含实施服务"
                  />
                </label>
                <label className="wide">
                  <span>方案描述</span>
                  <textarea
                    rows={3}
                    value={draft.description}
                    onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                  />
                </label>

                <div className="wide prod-items">
                  <div className="prod-items-head">
                    <span>包含产品</span>
                    <button type="button" className="link-button" onClick={addItem}>
                      ＋ 添加产品
                    </button>
                  </div>
                  {draft.items.length === 0 && (
                    <small className="prod-items-empty">尚未添加产品</small>
                  )}
                  {draft.items.map((item, index) => (
                    <div className="prod-item-row" key={index}>
                      <select
                        value={item.productId}
                        onChange={(e) => setItem(index, { productId: e.target.value })}
                      >
                        <option value="">选择产品…</option>
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
                        title="数量"
                      />
                      <select
                        value={item.role}
                        onChange={(e) =>
                          setItem(index, { role: e.target.value as SolutionItemRole })
                        }
                        title="角色"
                      >
                        {(Object.keys(ROLE_LABELS) as SolutionItemRole[]).map((r) => (
                          <option key={r} value={r}>
                            {ROLE_LABELS[r]}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        className="link-button danger"
                        onClick={() => removeItem(index)}
                      >
                        移除
                      </button>
                    </div>
                  ))}
                  {editing !== "new" && (editing as Solution).items.length > 0 && (
                    <small className="prod-items-hint">
                      已保存 { (editing as Solution).items.length } 项；排序由添加顺序决定。
                    </small>
                  )}
                </div>

                <label>
                  <span>状态</span>
                  <select
                    value={draft.status}
                    onChange={(e) =>
                      setDraft({
                        ...draft,
                        status: e.target.value as SolutionDraft["status"],
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
