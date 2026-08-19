export type ChannelUser = {
  channelType: string;
  channelKey: string;
  externalId: string;
  displayName: string | null;
  tenantKey: string | null;
  firstSeenAt: string;
  lastSeenAt: string;
  messageCount: number;
};
