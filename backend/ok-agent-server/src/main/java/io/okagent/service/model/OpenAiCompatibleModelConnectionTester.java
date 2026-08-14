package io.okagent.service.model;

import io.okagent.domain.model.ModelAsset;
import io.okagent.domain.model.ModelType;
import io.okagent.web.model.ModelConnectionTestResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleModelConnectionTester implements ModelConnectionTester {
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  /** Sends a minimal chat-completion request and reports only its status, never its response body. */
  @Override public ModelConnectionTestResponse test(ModelAsset asset, String apiKey) {
    if (asset.getType() != ModelType.LLM) return new ModelConnectionTestResponse(false, 0, "Only LLM connection testing is currently supported.");
    try { var baseUrl = asset.getEndpoint().replaceAll("/+$", ""); var body = "{\"model\":\"" + asset.getModelId().replace("\"", "") + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1}"; var request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions")).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofString(body)).build(); var response = client.send(request, HttpResponse.BodyHandlers.discarding()); return new ModelConnectionTestResponse(response.statusCode() >= 200 && response.statusCode() < 300, response.statusCode(), response.statusCode() < 300 ? "Model request succeeded." : "Model provider rejected the request."); }
    catch (Exception exception) { return new ModelConnectionTestResponse(false, 0, "Connection failed: " + exception.getClass().getSimpleName()); }
  }
}
