package io.okagent.service.product;

import java.util.List;

/**
 * Service-provider interface for external product systems (ERP / CRM / PIM). An implementation
 * knows how to authenticate against one system type and enumerate the products it exposes. New
 * systems are added by implementing this interface and registering a Spring component; the control
 * plane routes by {@link #type()}. No concrete provider is shipped until a real external system is
 * integrated; built-in products bypass this SPI entirely.
 */
public interface ProductProvider {

    /** Source type identifier, matching {@code ProductSourceType}, e.g. {@code HTTP}. */
    String type();

    /** Tests connectivity/credentials without persisting anything. */
    ConnectionTestResult test(ProductSourceConfig config);

    /** Lists the products reachable under the given source credentials for synchronization. */
    List<RemoteProductSummary> listProducts(ProductSourceConfig config);
}
