export type ModelItem = {
  id: string;
  name: string;
  type: "LLM" | "SPEECH" | "VISION" | "OCR" | "AUDIO_VIDEO";
  provider: string;
  modelId: string;
  endpoint: string;
  apiKey: string;
  apiKeyConfigured?: boolean;
  enabled: boolean;
  updated: string;
};

export type ModelApiItem = Omit<ModelItem, "updated" | "apiKey"> & {
  updatedAt: string;
};

export const llmProviders = [
  ["OpenAI", "gpt-4.1", "https://api.openai.com/v1"],
  ["Anthropic", "claude-sonnet-4-20250514", "https://api.anthropic.com/v1"],
  [
    "Google Gemini",
    "gemini-2.5-pro",
    "https://generativelanguage.googleapis.com/v1beta",
  ],
  [
    "阿里云百炼（Qwen）",
    "qwen-plus",
    "https://dashscope.aliyuncs.com/compatible-mode/v1",
  ],
  ["DeepSeek", "deepseek-chat", "https://api.deepseek.com/v1"],
  ["月之暗面（Kimi）", "moonshot-v1-8k", "https://api.moonshot.cn/v1"],
  ["智谱 AI（GLM）", "glm-4-plus", "https://open.bigmodel.cn/api/paas/v4"],
  ["MiniMax", "MiniMax-Text-01", "https://api.minimaxi.com/v1"],
  [
    "字节火山引擎",
    "doubao-1-5-pro-32k-250115",
    "https://ark.cn-beijing.volces.com/api/v3",
  ],
  ["Mistral AI", "mistral-large-latest", "https://api.mistral.ai/v1"],
  ["xAI（Grok）", "grok-3", "https://api.x.ai/v1"],
  ["Ollama（本地）", "llama3.3", "http://127.0.0.1:11434/v1"],
] as const;

export const modelSeed: ModelItem[] = [
  {
    id: "qwen-prod",
    name: "Qwen Production",
    type: "LLM",
    provider: "DashScope",
    modelId: "qwen-plus",
    endpoint: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    apiKey: "",
    enabled: true,
    updated: "2 min ago",
  },
  {
    id: "whisper",
    name: "Whisper Transcription",
    type: "SPEECH",
    provider: "OpenAI",
    modelId: "whisper-1",
    endpoint: "https://api.openai.com/v1",
    apiKey: "",
    enabled: true,
    updated: "18 min ago",
  },
  {
    id: "invoice-ocr",
    name: "Invoice OCR",
    type: "OCR",
    provider: "Alibaba Cloud",
    modelId: "ocr-invoice",
    endpoint: "https://ocr-api.internal/v1",
    apiKey: "",
    enabled: false,
    updated: "yesterday",
  },
  {
    id: "video",
    name: "Video Understanding",
    type: "AUDIO_VIDEO",
    provider: "Qwen",
    modelId: "qwen-vl-max",
    endpoint: "https://dashscope.aliyuncs.com/api/v1",
    apiKey: "",
    enabled: true,
    updated: "3 days ago",
  },
];
