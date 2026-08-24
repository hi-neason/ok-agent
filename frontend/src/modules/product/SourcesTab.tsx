import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
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

export function SourcesTab() {
  const { t, i18n } = useTranslation();
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
    if (!draft.name.trim()) return setNotice({ ok: false, text: t("integrations.nameRequired") });
    if (!draft.sourceKey.trim()) return setNotice({ ok: false, text: t("integrations.sourceKeyRequired") });
    if (!/^[a-z0-9-]+$/.test(draft.sourceKey))
      return setNotice({ ok: false, text: t("integrations.sourceKeyInvalid") });
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

  const sync = async (s: ProductSource) => {
    setBusy(true);
    setNotice(null);
    try {
      const result = await syncProductSource(s.id);
      setNotice({ ok: true, text: t("product.sources.synced", { count: result.upserted }) });
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
        message: t("product.sources.deleteConfirm", { name: s.name }),
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
          placeholder={t("product.sources.search")}
        />
        <span>{t("product.sources.total", { count: sources?.totalElements ?? 0 })}</span>
        <span style={{ flex: 1 }} />
        <Button onClick={() => open()}>＋ {t("product.sources.add")}</Button>
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
          <span>{t("product.sources.source")}</span>
          <span>{t("integrations.type")}</span>
          <span>{t("product.sources.productCount")}</span>
          <span>{t("integrations.lastTest")}</span>
          <span>{t("common.status")}</span>
          <span>{t("common.actions")}</span>
        </div>
        {visible.map((s) => (
          <div className="mcp-row" key={s.id}>
            <span className="mcp-name">
              <i>⌁</i>
              <b>{s.name}</b>
              <small>{s.sourceKey}</small>
            </span>
            <span>
              <code>{t(`product.sources.sourceTypes.${s.sourceType}`)}</code>
              <small>{s.baseUrl || "—"}</small>
            </span>
            <span>{s.productCount}</span>
            <span className={`test-state ${(s.lastTestStatus ?? "none").toLowerCase()}`}>
              {t(`integrations.testStatus.${["SUCCESS", "FAILED", "UNSUPPORTED"].includes(s.lastTestStatus ?? "") ? s.lastTestStatus : "UNTESTED"}`)}
              {s.lastTestedAt && (
                <small>{new Date(s.lastTestedAt).toLocaleString(i18n.resolvedLanguage)}</small>
              )}
            </span>
            <span>
              <Toggle on={s.enabled} setOn={() => void toggle(s)} />
            </span>
            <span className="row-actions">
              <button onClick={() => void sync(s)} disabled={busy}>
                {t("integrations.sync")}
              </button>
              <button onClick={() => void test(s)} disabled={busy}>
                {t("product.sources.test")}
              </button>
              <button onClick={() => open(s)}>{t("common.edit")}</button>
              <button className="danger" onClick={() => void remove(s)}>
                {t("common.delete")}
              </button>
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="mcp-empty">
            ⌁<b>{t("product.sources.empty")}</b>
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
                    {editing === "new" ? t("product.sources.add") : (editing as ProductSource).name}
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
                  />
                </label>
                <label>
                  <span>
                    SOURCE_KEY <small>· {t("integrations.uniqueKey")}</small>
                  </span>
                  <input
                    value={draft.sourceKey}
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
                        sourceType: e.target.value as ProductSourceDraft["sourceType"],
                      })
                    }
                  >
                    <option value="HTTP">HTTP / REST</option>
                    <option value="MANUAL">{t("product.sources.manual")}</option>
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
                    {t("product.sources.config")} <small>· {t("product.sources.providerJson")}</small>
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
                    {t("product.sources.secrets")} <small>· {t("product.sources.secretsHint")}</small>
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
