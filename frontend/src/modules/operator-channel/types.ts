export type OperatorPresenceStatus = "ONLINE" | "BUSY" | "OFFLINE";

export type OperatorPresence = {
  status: OperatorPresenceStatus;
  updatedAt: string | null;
};

export type MyChannel = {
  id: string;
  name: string;
  type: "FEISHU" | "DINGTALK" | "WECOM" | "WECHAT";
  runtimeStatus: "STOPPED" | "STARTING" | "RUNNING" | "ERROR";
  enabled: boolean;
  boundAgentId: string | null;
  boundAgentName: string | null;
  customerCount: number;
  operatorCount: number;
  assignedAt: string;
};
