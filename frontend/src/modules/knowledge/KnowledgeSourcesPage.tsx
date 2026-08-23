import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Button, PageHeader, Pagination, Toggle, useConfirm, type Page } from "../shared";
import {
  createSource,
  deleteSource,
  listSources,
  setSourceEnabled,
  syncSource,
  testSource,
  updateSource,
} from "./api";
import {
  SOURCE_TYPE_LABELS,
  emptySourceDraft,
  type KnowledgeSource,
  type KnowledgeSourceDraft,
} from "./types";
import { CatalogDrawer } from "./CatalogDrawer";
import "./knowledge.css";

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

export function KnowledgeSourcesPage() {
  const { confirm, Dialog } = useConfirm();
  const [page, setPage] = useState<Page<KnowledgeSource> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<KnowledgeSource | "new" | null>(null);
  const [draft, setDraft] = useState<KnowledgeSourceDraft>(emptySourceDraft());
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);
  const [catalogFor, setCatalogFor] = useState<KnowledgeSource | null>(null);

  const load = async (targetPage: number) => {
    try {
      setPage(await listSources(targetPage, 20));
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  useEffect(() => {
    void load(pageNumber);
  }, [pageNumber]);

  const open = (source?: KnowledgeSource) => {
    if (source) {
      setEditing(source);
      setDraft({
        sourceKey: source.sourceKey,
        name: source.name,
        sourceType: source.sourceType,
        baseUrl: source.baseUrl,
        apiKey: "",
        retrieveTimeoutSeconds: source.retrieveTimeoutSeconds,
        connectTimeoutSeconds: source.connectTimeoutSeconds,
      });
    } else {
      setEditing("new");
      setDraft(emptySourceDraft());
    }
    setNotice(null);
  };

  const validate = (d: KnowledgeSourceDraft): string | null => {
    if (!d.name.trim()) return "请填写名称";
    if (!d.sourceKey.trim()) return "请填写 SOURCE_KEY";
    if (!/^[a-z0-9-]+$/.test(d.sourceKey))
      return "SOURCE_KEY 只能包含小写字母、数字和连字符";
    if (!d.baseUrl.trim()) return "请填写 Base URL";
    if (editing === "new" && !d.apiKey.trim()) return "请填写 API Key";
    if (d.retrieveTimeoutSeconds <= 0 || d.retrieveTimeoutSeconds > 120)
      return "检索超时需在 1–120 秒之间";
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

  const sync = async (source: KnowledgeSource) => {
    setBusy(true);
    setNotice(null);
    try {
      const items = await syncSource(source.id);
      setNotice({ ok: true, text: `同步完成，发现 ${items.length} 个知识库` });
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    } finally {
      setBusy(false);
    }
  };

  const toggle = async (source: KnowledgeSource) => {
    try {
      await setSourceEnabled(source.id, !source.enabled);
      await load(pageNumber);
    } catch (e) {
      setNotice({ ok: false, text: msg(e) });
    }
  };

  const remove = async (source: KnowledgeSource) => {
    if (
      !(await confirm({
        message: `确认删除知识库源「${source.name}」？其下发现的知识库与所有 Agent 绑定将一并移除。`,
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
        kicker="KNOWLEDGE / INTEGRATION"
        title="知识库 - 集成"
        description="接入外部知识库系统（Dify 等），同步其知识库为全局可复用目录，再在各 Agent 中按需绑定。模型通过检索工具按需查阅，作为回答的事实依据。"
        action={<Button onClick={() => open()}>＋ 添加知识库源</Button>}
      />

      <div className="mcp-toolbar">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="搜索知识库源 / KEY / URL"
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
          <span>知识库源</span>
          <span>类型</span>
          <span>知识库数</span>
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
            <span>{source.knowledgeCount}</span>
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
            ⌁<b>暂无知识库源，点击右上角添加</b>
          </div>
        )}
        {page && (
          <Pagination
            page={page.number}
            totalPages={page.totalPages}
            totalElements={page.totalElements}
            size={page.size}
            onPageChange={setPageNumber}
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
                    KNOWLEDGE SOURCE / {editing === "new" ? "REGISTER" : "EDIT"}
                  </p>
                  <h2>
                    {editing === "new"
                      ? "添加知识库源"
                      : (editing as KnowledgeSource).name}
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
                    placeholder="如：产品知识库"
                  />
                </label>
                <label>
                  <span>
                    SOURCE_KEY <small>· 唯一标识</small>
                  </span>
                  <input
                    value={draft.sourceKey}
                    placeholder="product-knowledge"
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
                        sourceType: e.target.value as KnowledgeSourceDraft["sourceType"],
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
                    Dataset API Key{" "}
                    {editing !== "new" && <small>· 留空表示不修改已保存的密钥</small>}
                    {editing === "new" && <b className="field-required">*</b>}
                  </span>
                  <input
                    type="password"
                    value={draft.apiKey}
                    placeholder="dataset-xxxxxxxx"
                    onChange={(e) => setDraft({ ...draft, apiKey: e.target.value })}
                  />
                </label>
                <label>
                  <span>检索超时（秒）</span>
                  <input
                    type="number"
                    value={draft.retrieveTimeoutSeconds}
                    onChange={(e) =>
                      setDraft({ ...draft, retrieveTimeoutSeconds: +e.target.value })
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
          onChanged={() => void load(pageNumber)}
        />
      )}
    </>
  );
}
