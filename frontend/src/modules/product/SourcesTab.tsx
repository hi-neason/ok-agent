import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Button, Pagination, Toggle, useConfirm, type Page } from "../shared";
import {
  createProductSource,
  deleteProductSource,
  listProductSources,
  setProductSourceEnabled,
  syncProductSource,
  testProductSource,
  updateProductSource,
} from "./api";
import {
  SOURCE_TYPE_LABELS,
  emptySourceDraft,
  type ProductSource,
  type ProductSourceDraft,
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

function testLabel(status: string | null): string {
  switch (status) {
    case "SUCCESS":
      return "连接正常";
    case "FAILED":
      return "连接失败";
    case "UNSUPPORTED":
      return "不支持";
    default:
      return "未测试";
  }
}

export function SourcesTab() {
  const { confirm, Dialog } = useConfirm();
  const [sources, setSources] = useState<Page<ProductSource> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<ProductSource | "new" | null>(null);
  const [draft, setDraft] = useState<ProductSourceDraft>(emptySourceDraft());
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);

  const load = async (targetPage = pageNumber) => {
    try {
      setSources(await listProductSources(targetPage, pageSize));
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  useEffect(() => {
    void load(pageNumber);
  }, [pageNumber, pageSize]);

  const open = (s?: ProductSource) => {
    if (s) {
      setEditing(s);
      setDraft({
        sourceKey: s.sourceKey,
        name: s.name,
        sourceType: s.sourceType,
        baseUrl: s.baseUrl ?? "",
        configJson: "{}",
        secretsJson: "{}",
      });
    } else {
      setEditing("new");
      setDraft(emptySourceDraft());
    }
    setNotice(null);
  };

  const save = async () => {
    if (!draft.name.trim()) return setNotice({ ok: false, text: "请填写名称" });
    if (!draft.sourceKey.trim()) return setNotice({ ok: false, text: "请填写 SOURCE_KEY" });
    if (!/^[a-z0-9-]+$/.test(draft.sourceKey))
      return setNotice({ ok: false, text: "SOURCE_KEY 只能包含小写字母、数字、连字符" });
    setBusy(true);
    setNotice(null);
    try {
      if (editing === "new") await createProductSource(draft);
      else if (editing) await updateProductSource(editing.id, draft);
      setEditing(null);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setBusy(false);
    }
  };

  const test = async (s: ProductSource) => {
    setBusy(true);
    setNotice(null);
    try {
      const updated = await testProductSource(s.id);
      setNotice({
        ok: updated.lastTestStatus === "SUCCESS",
        text:
          updated.lastTestStatus === "SUCCESS"
            ? `连接成功：${updated.lastTestMessage ?? ""}`
            : `连接失败：${updated.lastTestMessage ?? "未知错误"}`,
      });
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setBusy(false);
    }
  };

  const sync = async (s: ProductSource) => {
    setBusy(true);
    setNotice(null);
    try {
      const result = await syncProductSource(s.id);
      setNotice({ ok: true, text: `同步完成，新增/更新 ${result.upserted} 个产品` });
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setBusy(false);
    }
  };

  const toggle = async (s: ProductSource) => {
    try {
      await setProductSourceEnabled(s.id, !s.enabled);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const remove = async (s: ProductSource) => {
    if (
      !(await confirm({
        message: `确认删除数据源「${s.name}」？已同步的产品会保留（来源置空）。`,
        dangerous: true,
      }))
    )
      return;
    try {
      await deleteProductSource(s.id);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const visible = useMemo(
    () =>
      (sources?.content ?? []).filter((s) =>
        `${s.name} ${s.sourceKey} ${s.baseUrl}`.toLowerCase().includes(search.toLowerCase()),
      ),
    [sources, search],
  );

  return (
    <>
      <Dialog />
      <div className="mcp-toolbar">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="搜索数据源名称 / KEY / URL"
        />
        <span>{sources?.totalElements ?? 0} 个数据源</span>
        <span style={{ flex: 1 }} />
        <Button onClick={() => open()}>＋ 添加数据源</Button>
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
          <span>数据源</span>
          <span>类型</span>
          <span>产品数</span>
          <span>最近测试</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        {visible.map((s) => (
          <div className="mcp-row" key={s.id}>
            <span className="mcp-name">
              <i>⌁</i>
              <b>{s.name}</b>
              <small>{s.sourceKey}</small>
            </span>
            <span>
              <code>{SOURCE_TYPE_LABELS[s.sourceType]}</code>
              <small>{s.baseUrl || "—"}</small>
            </span>
            <span>{s.productCount}</span>
            <span className={`test-state ${(s.lastTestStatus ?? "none").toLowerCase()}`}>
              {testLabel(s.lastTestStatus)}
              {s.lastTestedAt && (
                <small>{new Date(s.lastTestedAt).toLocaleString("zh-CN")}</small>
              )}
            </span>
            <span>
              <Toggle on={s.enabled} setOn={() => void toggle(s)} />
            </span>
            <span className="row-actions">
              <button onClick={() => void sync(s)} disabled={busy}>
                同步
              </button>
              <button onClick={() => void test(s)} disabled={busy}>
                测试
              </button>
              <button onClick={() => open(s)}>编辑</button>
              <button className="danger" onClick={() => void remove(s)}>
                删除
              </button>
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="mcp-empty">
            ⌁<b>暂无数据源，可手动维护产品或接入外部 ERP/CRM</b>
          </div>
        )}
        {sources && (
          <Pagination
            page={sources.number}
            totalPages={sources.totalPages}
            totalElements={sources.totalElements}
            size={sources.size}
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
              className="mcp-inspector"
              role="dialog"
              onMouseDown={(e) => e.stopPropagation()}
            >
              <header>
                <div>
                  <p className="kicker">
                    PRODUCT SOURCE / {editing === "new" ? "REGISTER" : "EDIT"}
                  </p>
                  <h2>
                    {editing === "new" ? "添加数据源" : (editing as ProductSource).name}
                  </h2>
                </div>
                <button className="link-button" onClick={() => setEditing(null)}>
                  关闭 ×
                </button>
              </header>
              <div className="mcp-form">
                <label>
                  <span>名称</span>
                  <input
                    value={draft.name}
                    onChange={(e) => {
                      const name = e.target.value;
                      setDraft((d) => ({
                        ...d,
                        name,
                        sourceKey:
                          !d.sourceKey || d.sourceKey === slugify(draft.name)
                            ? slugify(name)
                            : d.sourceKey,
                      }));
                    }}
                  />
                </label>
                <label>
                  <span>
                    SOURCE_KEY <small>· 唯一标识</small>
                  </span>
                  <input
                    value={draft.sourceKey}
                    onChange={(e) => setDraft({ ...draft, sourceKey: e.target.value })}
                  />
                </label>
                <label>
                  <span>类型</span>
                  <select
                    value={draft.sourceType}
                    onChange={(e) =>
                      setDraft({
                        ...draft,
                        sourceType: e.target.value as ProductSourceDraft["sourceType"],
                      })
                    }
                  >
                    <option value="HTTP">HTTP / REST</option>
                    <option value="MANUAL">手动维护</option>
                  </select>
                </label>
                <label className="wide">
                  <span>Base URL</span>
                  <input
                    value={draft.baseUrl}
                    placeholder="https://erp.example.com/api"
                    onChange={(e) => setDraft({ ...draft, baseUrl: e.target.value })}
                  />
                </label>
                <label className="wide">
                  <span>
                    配置 configJson <small>· JSON 对象，由 Provider 自定义</small>
                  </span>
                  <textarea
                    rows={3}
                    className="mono"
                    value={draft.configJson}
                    onChange={(e) => setDraft({ ...draft, configJson: e.target.value })}
                    placeholder='{"productPath":"/products","pageSize":100}'
                  />
                </label>
                <label className="wide">
                  <span>
                    凭据 secrets <small>· JSON 对象，加密存储；留空字段表示不修改</small>
                  </span>
                  <textarea
                    rows={3}
                    className="mono"
                    value={draft.secretsJson}
                    onChange={(e) => setDraft({ ...draft, secretsJson: e.target.value })}
                    placeholder='{"apiKey":"xxxx","apiSecret":"yyyy"}'
                  />
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
