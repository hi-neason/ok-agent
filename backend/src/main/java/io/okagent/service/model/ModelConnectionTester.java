package io.okagent.service.model;

import io.okagent.domain.model.ModelAsset;
import io.okagent.web.model.ModelConnectionTestResponse;

public interface ModelConnectionTester {
  /** Performs one minimal provider request using the saved model configuration. */
  ModelConnectionTestResponse test(ModelAsset asset, String apiKey);
}
