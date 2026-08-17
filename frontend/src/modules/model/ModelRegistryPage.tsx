import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Button, PageHeader, Toggle } from "../shared";
import { fetchModels, requestConnectionTest, saveModel } from "./api";
import { llmProviders, modelSeed, type ModelItem } from "./types";

export function ModelRegistryPage() {
  const { t } = useTranslation();
  const [models, setModels] = useState<ModelItem[]>(modelSeed);
  const [type, setType] = useState<"ALL" | ModelItem["type"]>("ALL");
  const [editing, setEditing] = useState<ModelItem | null>(null);
  const [testResult, setTestResult] = useState<{
    state: "testing" | "success" | "error";
    message: string;
  } | null>(null);
  useEffect(() => {
    void fetchModels()
      .then(setModels)
      .catch(() => undefined);
  }, []);
  const visible = models.filter(
    (model) => type === "ALL" || model.type === type,
  );
  const save = async () => {
    if (!editing || testResult?.state === "testing") return;
    const existing = Boolean(editing.id);
    try {
      const saved = await saveModel(editing);
      setModels((current) =>
        existing
          ? current.map((x) => (x.id === saved.id ? { ...saved, updated: "now" } : x))
          : [{ ...saved, updated: "now" }, ...current],
      );
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
        `请求失败（HTTP ${status}）`;
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
        kicker="MODEL ASSETS / REGISTRY"
        title="模型管理"
        description="统一管理文本、语音、视觉、OCR 和音视频模型。Agent 仅引用已启用的模型资产与其版本。"
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
            ＋ 新增模型
          </Button>
        }
      />
      <section className="run-table">
        <div className="table-tools">
          <div className="search-mini">◌ 共 {models.length} 个模型</div>
          <label className="model-type-filter">
            类型
            <select
              value={type}
              onChange={(event) => setType(event.target.value as typeof type)}
            >
              <option value="ALL">全部类型</option>
              <option value="LLM">大语言模型</option>
              <option value="SPEECH">语音模型</option>
              <option value="VISION">视觉模型</option>
              <option value="OCR">OCR 模型</option>
              <option value="AUDIO_VIDEO">音视频模型</option>
            </select>
          </label>
        </div>
        <div className="table-head model-table-row">
          <span>模型名称</span>
          <span>类型</span>
          <span>提供商</span>
          <span>模型 ID</span>
          <span>密钥引用</span>
          <span>启用状态</span>
          <span>操作</span>
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
            <code>{model.apiKeyConfigured ? "已配置" : "未配置"}</code>
            <Toggle
              on={model.enabled}
              setOn={(next) =>
                setModels((current) =>
                  current.map((x) =>
                    x.id === model.id ? { ...x, enabled: next } : x,
                  ),
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
                编辑
              </button>
              <button
                className="link-button"
                onClick={() =>
                  setModels((current) =>
                    current.filter((x) => x.id !== model.id),
                  )
                }
              >
                删除
              </button>
            </span>
          </div>
        ))}
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
              aria-label="模型配置"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="form-title">
                <div>
                  <p className="kicker">
                    {editing.id ? "编辑模型" : "新增模型"}
                  </p>
                  <h2>{editing.id ? editing.name : "新增模型"}</h2>
                </div>
                <button
                  className="link-button"
                  onClick={() => setEditing(null)}
                >
                  关闭 ×
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
                  <span>名称</span>
                  <input
                    value={editing.name}
                    onChange={(e) =>
                      setEditing({ ...editing, name: e.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>模型提供商</span>
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
                        <option value="CUSTOM">自定义</option>
                      </select>
                      {!llmProviders.some(
                        ([name]) => name === editing.provider,
                      ) && (
                        <input
                          placeholder="请输入自定义模型厂商"
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
                  <span>模型（MODEL_ID）</span>
                  <input
                    value={editing.modelId}
                    onChange={(e) =>
                      setEditing({ ...editing, modelId: e.target.value })
                    }
                  />
                </label>
                <label className="field">
                  <span>API 密钥（API_KEY）</span>
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
                  <span>服务地址（BASE_URL）</span>
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
                <Button onClick={save}>保存模型</Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
