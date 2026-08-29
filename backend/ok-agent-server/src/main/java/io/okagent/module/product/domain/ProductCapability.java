package io.okagent.module.product.domain;

/** Runtime product capability an agent can be granted via its product binding. */
public enum ProductCapability {
    /** Search/list/get products (always needed; customer-service agents usually stop here). */
    QUERY,
    /** Run the weighted product recommendation tool. */
    RECOMMEND,
    /** List and retrieve solutions/packages. */
    SOLUTION
}
