import { useState } from "react";
import { useTranslation } from "react-i18next";
import { PageHeader } from "../shared";
import { ProductsTab } from "./ProductsTab";
import { SolutionsTab } from "./SolutionsTab";
import { SourcesTab } from "./SourcesTab";
import "./product.css";

type Tab = "products" | "solutions" | "sources";

export function ProductPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>("products");

  return (
    <>
      <PageHeader
        kicker={t("product.kicker")}
        title={t("product.title")}
        description={t("product.description")}
      />
      <nav className="prod-tabs">
        {(
          [
            ["products", t("product.tabs.products")],
            ["solutions", t("product.tabs.solutions")],
            ["sources", t("product.tabs.sources")],
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
