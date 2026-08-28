package io.okagent.service.model;

/** Indicates that a stored API credential cannot be encrypted or decrypted with the configured key. */
public class ApiKeyProcessingException extends IllegalStateException {
    public ApiKeyProcessingException(Throwable cause) {
        super("Unable to process API key", cause);
    }
}
