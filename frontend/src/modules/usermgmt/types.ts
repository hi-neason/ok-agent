export type UserGroupItem = {
  id: string;
  groupKey: string;
  name: string;
  description: string;
  enabled: boolean;
  userCount: number;
  updatedAt: string;
};

export type UserSource = "CONSOLE" | "CHANNEL";

export type ChannelIdentity = {
  channelType: string;
  channelKey: string;
  externalId: string;
  unionId: string | null;
  tenantKey: string | null;
  displayName: string | null;
  avatarUrl: string | null;
  messageCount: number;
  firstSeenAt: string;
  lastSeenAt: string;
};

export type UserItem = {
  id: string;
  userId: string;
  username: string;
  displayName: string;
  source: UserSource;
  avatarUrl: string | null;
  email: string;
  phone: string;
  groupId: string | null;
  groupName: string | null;
  enabled: boolean;
  channelCount: number;
  updatedAt: string;
};
