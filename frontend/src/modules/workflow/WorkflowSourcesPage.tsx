import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
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

export function testLabel(status: string, t: TFunction): string {
  const key = ["SUCCESS", "FAILED", "UNSUPPORTED"].includes(status) ? status : "UNTESTED";
  return t(`integrations.testStatus.${key}`);
}

export function WorkflowSourcesPage() {
  const { t, i18n } = useTranslation();
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
    if (!d.name.trim()) return t("integrations.nameRequired");
    if (!d.sourceKey.trim()) return t("integrations.sourceKeyRequired");
    if (!/^[a-z0-9-]+$/.test(d.sourceKey))
      return t("integrations.sourceKeyInvalid");
    if (!d.baseUrl.trim()) return t("integrations.baseUrlRequired");
    if (editing === "new" && !d.apiKey.trim()) return t("integrations.apiKeyRequired");
    if (d.executeTimeoutSeconds <= 0 || d.executeTimeoutSeconds > 120)
      return t("workflow.timeoutInvalid");
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
            ? t("integrations.connectionSuccess", { message: updated.lastTestMessage ?? "" })
            : t("integrations.connectionFailed", {
                message: updated.lastTestMessage ?? t("common.unknownError"),
              }),
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
      setNotice({ ok: true, text: t("workflow.synced", { count: items.length }) });
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
        message: t("workflow.deleteConfirm", { name: source.name }),
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
        kicker={t("workflow.kicker")}
        title={t("workflow.title")}
        description={t("workflow.description")}
        action={<Button onClick={() => open()}>＋ {t("workflow.addSource")}</Button>}
      />

      <div className="mcp-toolbar">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t("workflow.search")}
        />
        <span>{t("integrations.totalSources", { count: page?.totalElements ?? 0 })}</span>
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
          <span>{t("workflow.source")}</span>
          <span>{t("integrations.type")}</span>
          <span>{t("workflow.count")}</span>
          <span>{t("integrations.lastTest")}</span>
          <span>{t("common.status")}</span>
          <span>{t("common.actions")}</span>
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
              {testLabel(source.lastTestStatus, t)}
              {source.lastTestedAt && (
                <small>{new Date(source.lastTestedAt).toLocaleString(i18n.resolvedLanguage)}</small>
              )}
            </span>
            <span>
              <Toggle on={source.enabled} setOn={() => void toggle(source)} />
            </span>
            <span className="row-actions">
              <button onClick={() => setCatalogFor(source)}>{t("integrations.catalog")}</button>
              <button onClick={() => void sync(source)} disabled={busy}>
                {t("integrations.sync")}
              </button>
              <button onClick={() => open(source)}>{t("common.edit")}</button>
              <button className="danger" onClick={() => void remove(source)}>
                {t("common.delete")}
              </button>
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="mcp-empty">
            ⌁<b>{t("workflow.empty")}</b>
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
                    {t("integrations.sourceKicker", {
                      kind: t("workflow.kind"),
                      mode: editing === "new" ? t("integrations.register") : t("integrations.editMode"),
                    })}
                  </p>
                  <h2>
                    {editing === "new"
                      ? t("workflow.addSource")
                      : (editing as WorkflowSource).name}
                  </h2>
                </div>
                <button className="link-button" onClick={() => setEditing(null)}>
                  {t("common.close")} ×
                </button>
              </header>
              <div className="mcp-form">
                <label>
                  <span>{t("integrations.name")}</span>
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
                    placeholder={t("workflow.namePlaceholder")}
                  />
                </label>
                <label>
                  <span>
                    SOURCE_KEY <small>· {t("integrations.uniqueKey")}</small>
                  </span>
                  <input
                    value={draft.sourceKey}
                    placeholder="travel-dify"
                    onChange={(e) => setDraft({ ...draft, sourceKey: e.target.value })}
                  />
                </label>
                <label>
                  <span>{t("integrations.type")}</span>
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
                    <small>· {t("integrations.cloudHint")}</small>
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
                    {editing !== "new" && <small>· {t("integrations.keepSecret")}</small>}
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
                  <span>{t("workflow.executeTimeout")}</span>
                  <input
                    type="number"
                    value={draft.executeTimeoutSeconds}
                    onChange={(e) =>
                      setDraft({ ...draft, executeTimeoutSeconds: +e.target.value })
                    }
                  />
                </label>
                <label>
                  <span>{t("integrations.connectTimeout")}</span>
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
                    {busy ? t("integrations.testing") : t("integrations.testConnection")}
                  </Button>
                )}
                <Button onClick={() => void save()} disabled={busy}>
                  {busy ? t("common.saving") : t("common.save")}
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
