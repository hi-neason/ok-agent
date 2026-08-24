export type ReleaseStatus =
  | "PROMOTED"
  | "SUPERSEDED"
  | "ROLLED_BACK";

export type ReleaseTargetType = "CHANNEL";

export type VersionSummary = {
  id: string;
  agentId: string;
  versionNo: number;
  versionLabel: string | null;
  contentHash: string;
  parentVersionId: string | null;
  changelog: string | null;
  createdBy: string;
  createdAt: string;
};

export type VersionDetail = VersionSummary & {
  snapshotJson: string;
};

export type ReleaseItem = {
  id: string;
  agentId: string;
  versionId: string;
  versionNo: number;
  targetType: ReleaseTargetType;
  targetId: string;
  status: ReleaseStatus;
  rollbackOfId: string | null;
  publishedBy: string;
  publishedAt: string;
  supersededAt: string | null;
};

export type AgentOption = {
  id: string;
  agentKey: string;
  name: string;
};

export type ChannelOption = {
  id: string;
  name: string;
  channelKey: string;
  type: string;
  boundAgentId: string | null;
};
