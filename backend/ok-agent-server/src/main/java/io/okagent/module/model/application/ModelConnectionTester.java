package io.okagent.module.model.application;

import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.model.application.ModelConnectionTestResponse;

public interface ModelConnectionTester {
    /** Performs one minimal provider request using the saved model configuration. */
    ModelConnectionTestResponse test(ModelAsset asset, String apiKey);
}
