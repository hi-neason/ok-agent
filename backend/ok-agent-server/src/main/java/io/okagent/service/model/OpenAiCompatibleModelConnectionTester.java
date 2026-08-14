package io.okagent.service.model;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.exception.OpenAIException;
import io.okagent.domain.model.ModelAsset;
import io.okagent.domain.model.ModelType;
import io.okagent.web.model.ModelConnectionTestResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleModelConnectionTester implements ModelConnectionTester {
  /**
   * Creates an AgentScope OpenAI-compatible model, sends a minimal prompt, and never returns the
   * provider response content.
   */
  @Override
  public ModelConnectionTestResponse test(ModelAsset asset, String apiKey) {
    if (asset.getType() != ModelType.LLM) {
      return new ModelConnectionTestResponse(
          false, 0, "Only LLM connection testing is currently supported.");
    }

    try {
      var model =
          OpenAIChatModel.builder()
              .apiKey(apiKey)
              .baseUrl(asset.getEndpoint())
              .modelName(asset.getModelId())
              .stream(false)
              .build();
      var prompt =
          Msg.builder()
              .role(MsgRole.USER)
              .content(List.of(TextBlock.builder().text("Reply with OK.").build()))
              .build();

      var response = model.stream(List.of(prompt), null, null).blockLast(Duration.ofSeconds(30));
      if (response == null) {
        return new ModelConnectionTestResponse(false, 0, "Model provider returned no response.");
      }

      return new ModelConnectionTestResponse(true, 200, "Connection succeeded.");
    } catch (Exception exception) {
      var providerException = findCause(exception, OpenAIException.class);
      if (providerException != null && providerException.getStatusCode() != null) {
        var statusCode = providerException.getStatusCode();
        return new ModelConnectionTestResponse(
            false, statusCode, providerFailureMessage(statusCode));
      }
      if (findCause(exception, java.util.concurrent.TimeoutException.class) != null) {
        return new ModelConnectionTestResponse(
            false, 0, "Connection timed out. Check BASE_URL and the provider status, then retry.");
      }
      return new ModelConnectionTestResponse(false, 0, "Unable to reach the model provider.");
    }
  }

  private <T extends Throwable> T findCause(Throwable throwable, Class<T> causeType) {
    var current = throwable;
    while (current != null) {
      if (causeType.isInstance(current)) {
        return causeType.cast(current);
      }
      current = current.getCause();
    }
    return null;
  }

  private String providerFailureMessage(int statusCode) {
    return switch (statusCode) {
      case 400 -> "The provider rejected MODEL_ID or request parameters.";
      case 401 -> "API_KEY authentication failed.";
      case 403 -> "API_KEY does not have permission to access this model.";
      case 404 -> "BASE_URL or MODEL_ID was not found.";
      case 429 -> "The provider rate limit or quota was exceeded.";
      default ->
          statusCode >= 500
              ? "The model provider is temporarily unavailable."
              : "The model provider rejected the connection request.";
    };
  }
}
