package io.okagent.module.product.domain;

/** Determines which products an agent can see at runtime. */
public enum ProductBindingScope {
    /** Every active product is visible to the agent. */
    ALL,
    /** Visible products restricted by category; scope_value holds the category. */
    CATEGORY,
    /** Visible products restricted by scenario tag; scope_value holds a JSON array of tags. */
    TAG,
    /** Only explicitly listed products are visible; scope_value holds a JSON array of product ids. */
    EXPLICIT,
    /** Product tools are not exposed to this agent (binding disabled by capability). */
    NONE
}
