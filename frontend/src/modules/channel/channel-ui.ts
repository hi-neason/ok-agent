import type {
  ChannelInput,
  ChannelRuntimeStatus,
  ChannelType,
} from "./types";
import type { TFunction } from "i18next";

export function typeLabel(type: ChannelType, t: TFunction): string {
  return t(`channels.types.${type}`);
}

export function runtimeLabel(status: ChannelRuntimeStatus, t: TFunction): string {
  return t(`channels.runtime.${status}`);
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
    wechatLoginId: null,
    dingtalkLoginId: null,
  };
}
