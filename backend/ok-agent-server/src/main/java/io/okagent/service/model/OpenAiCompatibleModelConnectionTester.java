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

      return new ModelConnectionTestResponse(
          true, 200, "Model request succeeded through AgentScope Java.");
    } catch (Exception exception) {
      var providerException = findCause(exception, OpenAIException.class);
      if (providerException != null && providerException.getStatusCode() != null) {
        return new ModelConnectionTestResponse(
            false,
            providerException.getStatusCode(),
            "Model provider rejected the AgentScope request.");
      }
      return new ModelConnectionTestResponse(
          false, 0, "Connection failed: " + exception.getClass().getSimpleName());
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
}
