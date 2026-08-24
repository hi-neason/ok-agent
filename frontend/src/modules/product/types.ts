export type ProductStatus = "ACTIVE" | "DISCONTINUED";
export type SolutionStatus = "ACTIVE" | "DISCONTINUED";
export type SolutionItemRole = "PRIMARY" | "ADDON" | "OPTIONAL";
export type ProductSourceType = "HTTP" | "MANUAL";
export type ProductBindingScope =
  | "ALL"
  | "CATEGORY"
  | "TAG"
  | "EXPLICIT"
  | "NONE";
export type ProductCapability = "QUERY" | "RECOMMEND" | "SOLUTION";

export type Product = {
  id: string;
  productKey: string;
  sourceId: string | null;
  externalId: string | null;
  name: string;
  brand: string | null;
  category: string | null;
  priceMin: number | null;
  priceMax: number | null;
  currency: string | null;
  spec: Record<string, unknown> | null;
  sellingPoints: string | null;
  scenarioTags: string[] | null;
  imageUrls: string[] | null;
  description: string | null;
  status: ProductStatus;
  weight: number;
  version: number;
  createdAt: string;
  updatedAt: string;
};

export type ProductDraft = {
  productKey: string;
  name: string;
  brand: string;
  category: string;
  priceMin: number | null;
  priceMax: number | null;
  currency: string;
  spec: string;
  sellingPoints: string;
  scenarioTags: string;
  imageUrls: string;
  description: string;
  status: ProductStatus;
  weight: number;
};

export type SolutionItem = {
  id: string;
  productId: string;
  productKey: string;
  productName: string;
  quantity: number;
  role: SolutionItemRole;
  sortOrder: number;
};

export type Solution = {
  id: string;
  solutionKey: string;
  name: string;
  description: string | null;
  targetCustomer: string;
  scenario: string;
  priceNote: string;
  status: SolutionStatus;
  version: number;
  items: SolutionItem[];
  createdAt: string;
  updatedAt: string;
};

export type SolutionDraft = {
  solutionKey: string;
  name: string;
  description: string;
  targetCustomer: string;
  scenario: string;
  priceNote: string;
  status: SolutionStatus;
  items: { productId: string; quantity: number; role: SolutionItemRole }[];
};

export type ProductSource = {
  id: string;
  sourceKey: string;
  name: string;
  sourceType: ProductSourceType;
  baseUrl: string;
  hasSecrets: boolean;
  lastTestStatus: string;
  lastTestMessage: string | null;
  lastTestedAt: string | null;
  lastSyncedAt: string | null;
  productCount: number;
  enabled: boolean;
  updatedAt: string;
};

export type ProductSourceDraft = {
  sourceKey: string;
  name: string;
  sourceType: ProductSourceType;
  baseUrl: string;
  configJson: string;
  /** JSON object string, parsed into Map<String,String> at the API boundary. */
  secretsJson: string;
};

export type AgentProductBinding = {
  id: string;
  agentId: string;
  scope: ProductBindingScope;
  scopeValue: string | null;
  capabilities: ProductCapability[];
  enabled: boolean;
};

export const emptyProductDraft = (): ProductDraft => ({
  productKey: "",
  name: "",
  brand: "",
  category: "",
  priceMin: null,
  priceMax: null,
  currency: "CNY",
  spec: "",
  sellingPoints: "",
  scenarioTags: "",
  imageUrls: "",
  description: "",
  status: "ACTIVE",
  weight: 100,
});

export const emptySourceDraft = (): ProductSourceDraft => ({
  sourceKey: "",
  name: "",
  sourceType: "HTTP",
  baseUrl: "",
  configJson: "{}",
  secretsJson: "{}",
});

export const emptySolutionDraft = (): SolutionDraft => ({
  solutionKey: "",
  name: "",
  description: "",
  targetCustomer: "",
  scenario: "",
  priceNote: "",
  status: "ACTIVE",
  items: [],
});
