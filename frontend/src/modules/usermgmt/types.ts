export type UserGroupItem = {
  id: string;
  groupKey: string;
  name: string;
  description: string;
  enabled: boolean;
  userCount: number;
  updatedAt: string;
};

export type UserItem = {
  id: string;
  userId: string;
  username: string;
  displayName: string;
  email: string;
  phone: string;
  groupId: string | null;
  groupName: string | null;
  enabled: boolean;
  updatedAt: string;
};
