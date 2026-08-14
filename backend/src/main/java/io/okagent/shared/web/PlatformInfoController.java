package io.okagent.shared.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PlatformInfoController {
  @GetMapping("/platform")
  /** Returns the public identity and readiness summary of the ok-agent platform. */
  public Map<String, String> platform() {
    return Map.of(
        "name", "ok-agent", "managementPlane", "ready", "runtimePlane", "agentscope-java-2");
  }
}
