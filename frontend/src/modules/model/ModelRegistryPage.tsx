import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Button, PageHeader, Pagination, Toggle, type Page } from "../shared";
import { fetchModels, requestConnectionTest, saveModel } from "./api";
import { llmProviders, type ModelItem } from "./types";

export function ModelRegistryPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState<Page<ModelItem> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [type, setType] = useState<"ALL" | ModelItem["type"]>("ALL");
  const [editing, setEditing] = useState<ModelItem | null>(null);
  const [testResult, setTestResult] = useState<{
    state: "testing" | "success" | "error";
    message: string;
  } | null>(null);
  const load = async (targetPage = pageNumber) => {
    try {
      setPage(await fetchModels(targetPage, pageSize));
    } catch {
      setPage(null);
    }
  };
  useEffect(() => {
    void load(pageNumber);
  }, [pageNumber, pageSize]);
  const visible = (page?.content ?? []).filter(
    (model) => type === "ALL" || model.type === type,
  );
  const save = async () => {
    if (!editing || testResult?.state === "testing") return;
    const existing = Boolean(editing.id);
    try {
      await saveModel(editing);
      await load(pageNumber);
      setEditing(null);
    } catch {
      setTestResult({
        state: "error",
        message: t("models.saveFailed"),
      });
    }
  };
  const applyLlmProvider = (provider: string) => {
    const preset = llmProviders.find(([name]) => name === provider);
    if (editing && preset) {
      setEditing({
        ...editing,
        provider: preset[0],
        modelId: preset[1],
        endpoint: preset[2],
      });
    }
  };
  const testConnection = async () => {
    if (!editing) return;
    setTestResult({ state: "testing", message: t("models.connectionTesting") });
    try {
      const { ok, status, result } = await requestConnectionTest(editing);
      const success = ok && result.success === true;
      const message =
        result.message ||
        result.detail ||
        result.title ||
        t("common.requestFailed", { status });
      const statusSuffix =
        result.statusCode && !message.includes(`HTTP ${result.statusCode}`)
          ? `（HTTP ${result.statusCode}）`
          : "";
      setTestResult(
        success
          ? { state: "success", message: t("models.connectionSucceeded") }
          : {
              state: "error",
              message: `${message || t("models.connectionFailed")}${statusSuffix}`,
            },
      );
    } catch {
      setTestResult({
        state: "error",
        message: t("models.connectionFailed"),
      });
    }
  };
  return (
    <>
      <PageHeader
        kicker={t("models.kicker")}
        title={t("models.title")}
        description={t("models.description")}
        action={
          <Button
            onClick={() => {
              setTestResult(null);
              setEditing({
                id: "",
                name: "",
                type: "LLM",
                provider: "OpenAI",
                modelId: "",
                endpoint: "",
                apiKey: "",
                enabled: true,
                updated: "now",
              });
            }}
          >
            ＋ {t("models.add")}
          </Button>
        }
      />
      <section className="run-table">
        <div className="table-tools">
          <div className="search-mini">◌ {t("models.total", { count: page?.totalElements ?? 0 })}</div>
          <label className="model-type-filter">
            {t("models.type")}
            <select
              value={type}
              onChange={(event) => setType(event.target.value as typeof type)}
            >
              <option value="ALL">{t("models.allTypes")}</option>
              {(["LLM", "SPEECH", "VISION", "OCR", "AUDIO_VIDEO"] as const).map((modelType) => (
                <option key={modelType} value={modelType}>{t(`models.types.${modelType}`)}</option>
              ))}
            </select>
          </label>
        </div>
        <div className="table-head model-table-row">
          <span>{t("models.name")}</span><span>{t("models.type")}</span>
          <span>{t("models.provider")}</span><span>{t("models.modelId")}</span>
          <span>{t("models.apiKeyReference")}</span><span>{t("models.enabledStatus")}</span>
          <span>{t("common.actions")}</span>
        </div>
        {visible.map((model) => (
          <div className="table-row model-table-row" key={model.id}>
            <span>
              <b>{model.name}</b>
              <small>{model.endpoint}</small>
            </span>
            <span>{model.type.replace("_", " / ")}</span>
            <span>{model.provider}</span>
            <code>{model.modelId}</code>
            <code>{t(model.apiKeyConfigured ? "common.configured" : "common.notConfigured")}</code>
            <Toggle
              on={model.enabled}
              setOn={(next) =>
                setPage((p) =>
                  p
                    ? {
                        ...p,
                        content: p.content.map((x) =>
                          x.id === model.id ? { ...x, enabled: next } : x,
                        ),
                      }
                    : p,
                )
              }
              label={`Enable ${model.name}`}
            />
            <span className="model-actions">
              <button
                className="link-button"
                onClick={() => {
                  setTestResult(null);
                  setEditing(model);
                }}
              >
                {t("common.edit")}
              </button>
              <button
                className="link-button"
                onClick={() =>
                  setPage((p) =>
                    p ? { ...p, content: p.content.filter((x) => x.id !== model.id) } : p,
                  )
                }
              >
                {t("common.delete")}
              </button>
            </span>
          </div>
        ))}
        {page && (
          <Pagination
            page={page.number}
            totalPages={page.totalPages}
            totalElements={page.totalElements}
            size={page.size}
            loading={testResult?.state === "testing"}
            onPageChange={setPageNumber}
            onSizeChange={(size) => {
              setPageSize(size);
              setPageNumber(0);
            }}
          />
        )}
      </section>
      {editing &&
        createPortal(
          <div
            className="model-modal-mask"
            role="presentation"
            onMouseDown={() => setEditing(null)}
          >
            <div
              className="form-surface model-editor"
              role="dialog"
              aria-modal="true"
              aria-label={t("models.configAria")}
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">
                    {t(editing.id ? "models.edit" : "models.add")}
                  </p>
                  <h2>{editing.id ? editing.name : t("models.add")}</h2>
                </div>
                <button
                  className="link-button"
                  onClick={() => setEditing(null)}
                >
                  {t("models.close")}
                </button>
              </div>
              <div className="provider-pills">
                {(
                  ["LLM", "SPEECH", "VISION", "OCR", "AUDIO_VIDEO"] as const
                ).map((x) => (
                  <button
                    key={x}
                    onClick={() => setEditing({ ...editing, type: x })}
                    className={
                      editing.type === x ? "provider active" : "provider"
                    }
                  >
                    {x.replace("_", " / ")}
                  </button>
                ))}
              </div>
              <div className="field-grid">
                <label className="field">
                  <span>{t("models.nameField")}</span>
                  <input
                    value={editing.name}
                    onChange={(e) =>
                      setEditing({ ...editing, name: e.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>{t("models.providerField")}</span>
                  {editing.type === "LLM" ? (
                    <>
                      <select
                        value={
                          llmProviders.some(
                            ([name]) => name === editing.provider,
                          )
                            ? editing.provider
                            : "CUSTOM"
                        }
                        onChange={(event) =>
                          event.target.value === "CUSTOM"
                            ? setEditing({ ...editing, provider: "" })
                            : applyLlmProvider(event.target.value)
                        }
                      >
                        {llmProviders.map(([name]) => (
                          <option key={name} value={name}>
                            {name}
                          </option>
                        ))}
                        <option value="CUSTOM">{t("models.custom")}</option>
                      </select>
                      {!llmProviders.some(
                        ([name]) => name === editing.provider,
                      ) && (
                        <input
                          placeholder={t("models.customProviderPlaceholder")}
                          value={editing.provider}
                          onChange={(event) =>
                            setEditing({
                              ...editing,
                              provider: event.target.value,
                            })
                          }
                        />
                      )}
                    </>
                  ) : (
                    <input
                      value={editing.provider}
                      onChange={(e) =>
                        setEditing({ ...editing, provider: e.target.value })
                      }
                    />
                  )}
                </label>
                <label className="field">
                  <span>{t("models.modelIdField")}</span>
                  <input
                    value={editing.modelId}
                    onChange={(e) =>
                      setEditing({ ...editing, modelId: e.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>{t("models.apiKeyField")}</span>
                  <input
                    type="password"
                    autoComplete="new-password"
                    value={editing.apiKey}
                    placeholder={
                      editing.apiKeyConfigured
                        ? t("models.apiKeyConfigured")
                        : undefined
                    }
                    onChange={(e) =>
                      setEditing({ ...editing, apiKey: e.target.value })
                    }
                  />
                </label>
                <label className="field wide">
                  <span>{t("models.baseUrlField")}</span>
                  <input
                    value={editing.endpoint}
                    onChange={(e) =>
                      setEditing({ ...editing, endpoint: e.target.value })
                    }
                  />
                </label>
              </div>
              {testResult && (
                <div
                  className={`connection-result connection-result--${testResult.state}`}
                  role="status"
                  aria-live="polite"
                >
                  <span className="connection-result__icon" aria-hidden="true">
                    {testResult.state === "success"
                      ? "✓"
                      : testResult.state === "error"
                        ? "×"
                        : "···"}
                  </span>
                  <div>
                    <b>{testResult.message}</b>
                    <p>{t("models.connectionHint")}</p>
                  </div>
                </div>
              )}
              <div className="sticky-actions">
                <Button
                  quiet
                  onClick={testConnection}
                  disabled={testResult?.state === "testing"}
                >
                  {testResult?.state === "testing"
                    ? t("models.connectionTesting")
                    : testResult?.state === "error"
                      ? t("models.connectionRetry")
                      : t("models.connectionTest")}
                </Button>
                <Button onClick={save}>{t("models.save")}</Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
