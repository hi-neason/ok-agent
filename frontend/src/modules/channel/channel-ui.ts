import type {
  ChannelInput,
  ChannelRuntimeStatus,
  ChannelType,
} from "./types";

export function typeLabel(type: ChannelType): string {
  switch (type) {
    case "FEISHU":
      return "飞书";
    case "DINGTALK":
      return "钉钉";
    case "WECOM":
      return "企业微信";
    case "WECHAT":
      return "微信";
  }
}

export function runtimeLabel(status: ChannelRuntimeStatus): string {
  switch (status) {
    case "RUNNING":
      return "运行中";
    case "STARTING":
      return "启动中";
    case "STOPPED":
      return "已停止";
    case "ERROR":
      return "异常";
  }
}

export function statusTone(
  status: ChannelRuntimeStatus,
): "success" | "warning" | "muted" | "danger" {
  switch (status) {
    case "RUNNING":
      return "success";
    case "STARTING":
      return "warning";
    case "ERROR":
      return "danger";
    case "STOPPED":
      return "muted";
  }
}

export function createDraft(): ChannelInput {
  return {
    name: "",
    type: "FEISHU",
    boundAgentId: null,
    dmScope: "PER_PEER",
    enabled: true,
    feishu: {
      appId: "",
      appSecret: "",
      encryptKey: "",
      verificationToken: "",
    },
    dingtalk: {
      appKey: "",
      appSecret: "",
      robotCode: "",
    },
  };
}
