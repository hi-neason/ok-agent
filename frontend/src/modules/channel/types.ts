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

export type WechatChannelView = {
  apiBase: string;
  channelVersion: string;
};

export type DingTalkChannelView = {
  appKey: string;
  robotCode: string;
  apiBase: string;
  oapiBase: string;
  streamRegisterUrl: string;
  appSecretConfigured: boolean;
};

export type IlinkLoginStatus =
  | "LOGGED_OUT"
  | "WAITING_QR"
  | "SCANNED"
  | "LOGGED_IN"
  | "EXPIRED"
  | "ERROR";

export type WechatIlinkStatus = {
  channelId: string;
  loginStatus: IlinkLoginStatus;
  /** Polling identifier (the iLink `qrcode` field); not the scannable payload. */
  qrcodeToken: string | null;
  /** The `qrcode_img_content` field — either an image URL/data-URI or the raw QR payload to render. */
  qrcodeUrl: string | null;
  botId: string | null;
  ilinkUserId: string | null;
  lastError: string | null;
  loggedInAt: string | null;
  updatedAt: string;
};

export type ChannelItem = {
  id: string;
  channelKey: string;
  name: string;
  type: ChannelType;
  boundAgentId: string | null;
  dmScope: ChannelDmScope;
  feishu: FeishuChannelView | null;
  wechat: WechatChannelView | null;
  dingtalk: DingTalkChannelView | null;
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

export type WechatChannelInput = {
  apiBase?: string;
  channelVersion?: string;
};

export type DingTalkChannelInput = {
  appKey: string;
  appSecret?: string;
  robotCode: string;
  apiBase?: string;
  oapiBase?: string;
  streamRegisterUrl?: string;
};

export type ChannelInput = {
  name: string;
  type: ChannelType;
  boundAgentId: string | null;
  dmScope: ChannelDmScope;
  feishu: FeishuChannelInput;
  wechat?: WechatChannelInput;
  dingtalk?: DingTalkChannelInput;
  wechatLoginId?: string | null;
  dingtalkLoginId?: string | null;
  enabled: boolean;
};
