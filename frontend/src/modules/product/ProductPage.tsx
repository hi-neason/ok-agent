import { useState } from "react";
import { PageHeader } from "../shared";
import { ProductsTab } from "./ProductsTab";
import { SolutionsTab } from "./SolutionsTab";
import { SourcesTab } from "./SourcesTab";
import "./product.css";

type Tab = "products" | "solutions" | "sources";

export function ProductPage() {
  const [tab, setTab] = useState<Tab>("products");

  return (
    <>
      <PageHeader
        kicker="PRODUCT CATALOG / SALES ENABLEMENT"
        title="产品管理"
        description="维护统一的产品/商品目录与组合方案，供智能销售、客服 Agent 按规则召回并由模型精选推荐。支持手动维护，也可接入外部 ERP/CRM/PIM 同步。"
      />
      <nav className="prod-tabs">
        {(
          [
            ["products", "产品目录"],
            ["solutions", "组合方案"],
            ["sources", "外部数据源"],
          ] as [Tab, string][]
        ).map(([id, label]) => (
          <button
            key={id}
            className={tab === id ? "active" : ""}
            onClick={() => setTab(id)}
          >
            {label}
          </button>
        ))}
      </nav>
      {tab === "products" && <ProductsTab />}
      {tab === "solutions" && <SolutionsTab />}
      {tab === "sources" && <SourcesTab />}
    </>
  );
}
