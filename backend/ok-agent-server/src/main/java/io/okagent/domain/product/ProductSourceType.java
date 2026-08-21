package io.okagent.domain.product;

/** Type of an external product data source (ERP / CRM / PIM). */
public enum ProductSourceType {
    /** Generic REST/HTTP product catalog endpoint. Concrete providers map to this. */
    HTTP,
    /** Manually maintained source (built-in); no remote synchronization. */
    MANUAL
}
