export type AccountRole = "ADMIN" | "EDITOR" | "VIEWER";

export type AuthUser = {
  id: string;
  userId: string;
  username: string;
  displayName: string;
  role: AccountRole;
};

export type LoginResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: AuthUser;
};
