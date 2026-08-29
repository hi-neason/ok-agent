package io.okagent.module.product.application;

/** Decrypted connection details for an external product source, passed to {@link ProductProvider}. */
public record ProductSourceConfig(
        String sourceKey,
        String type,
        String baseUrl,
        String configJson,
        String secretsJson) {}
