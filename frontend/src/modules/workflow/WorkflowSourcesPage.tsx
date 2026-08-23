import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Button, PageHeader, Toggle, useConfirm, Pagination } from "../shared";
import type { Page } from "../shared";
import {
  createSource,
  deleteSource,
  listCatalog,
  listSources,
  setSourceEnabled,
  syncSource,
  testSource,
  updateCatalogDescription,
  updateSource,
} from "./api";
import {
  SOURCE_TYPE_LABELS,
  emptySourceDraft,
  type WorkflowCatalogItem,
  type WorkflowSource,
  type WorkflowSourceDraft,
} from "./types";
import { CatalogDrawer } from "./CatalogDrawer";
import "./workflow.css";

const slugify = (value: string) =>
  value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export function testLabel(status: string): string {
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

export function WorkflowSourcesPage() {
  const { confirm, Dialog } = useConfirm();
  const [page, setPage] = useState<Page<WorkflowSource> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<WorkflowSource | "new" | null>(null);
  const [draft, setDraft] = useState<WorkflowSourceDraft>(emptySourceDraft());
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);
  const [catalogFor, setCatalogFor] = useState<WorkflowSource | null>(null);

  const load = async (targetPage = pageNumber) => {
    try {
      setPage(await listSources(targetPage, pageSize));
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  useEffect(() => {
    void load(pageNumber);
  }, [pageNumber, pageSize]);

  const open = (source?: WorkflowSource) => {
    if (source) {
      setEditing(source);
      setDraft({
        sourceKey: source.sourceKey,
        name: source.name,
        sourceType: source.sourceType,
        baseUrl: source.baseUrl,
        apiKey: "",
        executeTimeoutSeconds: source.executeTimeoutSeconds,
        connectTimeoutSeconds: source.connectTimeoutSeconds,
      });
    } else {
      setEditing("new");
      setDraft(emptySourceDraft());
    }
    setNotice(null);
  };

  const validate = (d: WorkflowSourceDraft): string | null => {
    if (!d.name.trim()) return "请填写名称";
    if (!d.sourceKey.trim()) return "请填写 SOURCE_KEY";
    if (!/^[a-z0-9-]+$/.test(d.sourceKey))
      return "SOURCE_KEY 只能包含小写字母、数字和连字符";
    if (!d.baseUrl.trim()) return "请填写 Base URL";
    if (editing === "new" && !d.apiKey.trim()) return "请填写 API Key";
    if (d.executeTimeoutSeconds <= 0 || d.executeTimeoutSeconds > 120)
      return "同步超时需在 1–120 秒之间（建议小于 120）";
    return null;
  };

  const save = async () => {
    const err = validate(draft);
    if (err) {
      setNotice({ ok: false, text: err });
      return;
    }
    setBusy(true);
    setNotice(null);
    try {
      if (editing === "new") {
        await createSource(draft);
      } else if (editing) {
        await updateSource(editing.id, draft);
      }
      setEditing(null);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setBusy(false);
    }
  };

  const test = async () => {
    if (!editing || editing === "new") return;
    setBusy(true);
    setNotice(null);
    try {
      const updated = await testSource(editing.id);
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

  const sync = async (source: WorkflowSource) => {
    setBusy(true);
    setNotice(null);
    try {
      const items = await syncSource(source.id);
      setNotice({ ok: true, text: `同步完成，发现 ${items.length} 个工作流` });
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setBusy(false);
    }
  };

  const toggle = async (source: WorkflowSource) => {
    try {
      await setSourceEnabled(source.id, !source.enabled);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const remove = async (source: WorkflowSource) => {
    if (
      !(await confirm({
        message: `确认删除工作流源「${source.name}」？其下发现的工作流与所有 Agent 绑定将一并移除。`,
        dangerous: true,
      }))
    )
      return;
    try {
      await deleteSource(source.id);
      if (catalogFor?.id === source.id) setCatalogFor(null);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const visible = useMemo(
    () =>
      (page?.content ?? []).filter((s) =>
        `${s.name} ${s.sourceKey} ${s.baseUrl}`
          .toLowerCase()
          .includes(search.toLowerCase()),
      ),
    [page, search],
  );

  return (
    <>
      <Dialog />
      <PageHeader
        kicker="WORKFLOW / INTEGRATION"
        title="工作流 - 集成"
        description="接入外部流水线系统（Dify 等），同步其流程为全局可复用目录，再在各 Agent 中按需绑定。流程描述与入参在目录层维护一次，所有 Agent 共享。"
        action={<Button onClick={() => open()}>＋ 添加工作流源</Button>}
      />

      <div className="mcp-toolbar">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="搜索工作流源 / KEY / URL"
        />
        <span>{page?.totalElements ?? 0} 个源</span>
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
          <span>工作流源</span>
          <span>类型</span>
          <span>流程数</span>
          <span>最近测试</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        {visible.map((source) => (
          <div className="mcp-row" key={source.id}>
            <span className="mcp-name">
              <i>⌁</i>
              <b>{source.name}</b>
              <small>{source.sourceKey}</small>
            </span>
            <span>
              <code>{SOURCE_TYPE_LABELS[source.sourceType]}</code>
              <small>{source.baseUrl}</small>
            </span>
            <span>{source.workflowCount}</span>
            <span className={`test-state ${source.lastTestStatus.toLowerCase()}`}>
              {testLabel(source.lastTestStatus)}
              {source.lastTestedAt && (
                <small>{new Date(source.lastTestedAt).toLocaleString("zh-CN")}</small>
              )}
            </span>
            <span>
              <Toggle on={source.enabled} setOn={() => void toggle(source)} />
            </span>
            <span className="row-actions">
              <button onClick={() => setCatalogFor(source)}>目录</button>
              <button onClick={() => void sync(source)} disabled={busy}>
                同步
              </button>
              <button onClick={() => open(source)}>编辑</button>
              <button className="danger" onClick={() => void remove(source)}>
                删除
              </button>
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="mcp-empty">
            ⌁<b>暂无工作流源，点击右上角添加</b>
          </div>
        )}
      </div>

      {page && (
        <Pagination
          page={page.number}
          totalPages={page.totalPages}
          totalElements={page.totalElements}
          size={page.size}
          loading={busy}
          onPageChange={setPageNumber}
          onSizeChange={(size) => {
            setPageSize(size);
            setPageNumber(0);
          }}
        />
      )}

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
                    WORKFLOW SOURCE / {editing === "new" ? "REGISTER" : "EDIT"}
                  </p>
                  <h2>
                    {editing === "new"
                      ? "添加工作流源"
                      : (editing as WorkflowSource).name}
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
                    placeholder="如：旅游 Dify"
                  />
                </label>
                <label>
                  <span>
                    SOURCE_KEY <small>· 唯一标识</small>
                  </span>
                  <input
                    value={draft.sourceKey}
                    placeholder="travel-dify"
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
                        sourceType: e.target.value as WorkflowSourceDraft["sourceType"],
                      })
                    }
                  >
                    <option value="DIFY">Dify</option>
                  </select>
                </label>
                <label className="wide">
                  <span>
                    Base URL <b className="field-required">*</b>
                    <small>· Dify Cloud 为 https://api.dify.ai/v1</small>
                  </span>
                  <input
                    value={draft.baseUrl}
                    placeholder="https://api.dify.ai/v1"
                    onChange={(e) => setDraft({ ...draft, baseUrl: e.target.value })}
                  />
                </label>
                <label className="wide">
                  <span>
                    API Key{" "}
                    {editing !== "new" && <small>· 留空表示不修改已保存的密钥</small>}
                    {editing === "new" && <b className="field-required">*</b>}
                  </span>
                  <input
                    type="password"
                    value={draft.apiKey}
                    placeholder="app-xxxxxxxx"
                    onChange={(e) => setDraft({ ...draft, apiKey: e.target.value })}
                  />
                </label>
                <label>
                  <span>同步超时（秒）</span>
                  <input
                    type="number"
                    value={draft.executeTimeoutSeconds}
                    onChange={(e) =>
                      setDraft({ ...draft, executeTimeoutSeconds: +e.target.value })
                    }
                  />
                </label>
                <label>
                  <span>连接超时（秒）</span>
                  <input
                    type="number"
                    value={draft.connectTimeoutSeconds}
                    onChange={(e) =>
                      setDraft({ ...draft, connectTimeoutSeconds: +e.target.value })
                    }
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
                {editing !== "new" && (
                  <Button quiet onClick={() => void test()} disabled={busy}>
                    {busy ? "测试中…" : "测试连接"}
                  </Button>
                )}
                <Button onClick={() => void save()} disabled={busy}>
                  {busy ? "保存中…" : "保存"}
                </Button>
              </footer>
            </div>
          </div>,
          document.body,
        )}

      {catalogFor && (
        <CatalogDrawer
          source={catalogFor}
          onClose={() => setCatalogFor(null)}
          onChanged={load}
        />
      )}
    </>
  );
}
