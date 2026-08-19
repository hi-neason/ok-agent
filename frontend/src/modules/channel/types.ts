export type ChannelType = "FEISHU" | "DINGTALK" | "WECOM" | "WECHAT";
export type ChannelDmScope =
  | "MAIN"
  | "PER_PEER"
  | "PER_CHANNEL_PEER"
  | "PER_ACCOUNT_CHANNEL_PEER";
export type ChannelRuntimeStatus =
  | "STOPPED"
  | "STARTING"
  | "RUNNING"
  | "ERROR";

export type FeishuChannelView = {
  appId: string;
  apiBase: string;
  callbackPath: string;
  appSecretConfigured: boolean;
  encryptKeyConfigured: boolean;
  verificationTokenConfigured: boolean;
};

export type ChannelItem = {
  id: string;
  channelKey: string;
  name: string;
  type: ChannelType;
  boundAgentId: string | null;
  dmScope: ChannelDmScope;
  feishu: FeishuChannelView | null;
  enabled: boolean;
  runtimeStatus: ChannelRuntimeStatus;
  lastError: string | null;
  callbackUrl: string | null;
  userCount: number;
  createdAt: string;
  updatedAt: string;
};

export type FeishuChannelInput = {
  appId: string;
  appSecret?: string;
  encryptKey?: string;
  verificationToken?: string;
  apiBase?: string;
  callbackPath?: string;
};

export type ChannelInput = {
  name: string;
  type: ChannelType;
  boundAgentId: string | null;
  dmScope: ChannelDmScope;
  feishu: FeishuChannelInput;
  enabled: boolean;
};
