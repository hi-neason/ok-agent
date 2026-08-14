package io.okagent.web.model;

import io.okagent.service.model.ModelAssetService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/models")
public class ModelAssetController {
  private final ModelAssetService service;

  public ModelAssetController(ModelAssetService service) {
    this.service = service;
  }

  @GetMapping
  public List<ModelAssetResponse> list() {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ModelAssetResponse create(@Valid @RequestBody ModelAssetRequest r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  public ModelAssetResponse update(@PathVariable UUID id, @Valid @RequestBody ModelAssetRequest r) {
    return service.update(id, r);
  }

  @PatchMapping("/{id}/enabled")
  public ModelAssetResponse enabled(@PathVariable UUID id, @RequestParam boolean value) {
    return service.enabled(id, value);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  @PostMapping("/test-connection")
  public Map<String, Object> test(@Valid @RequestBody ModelAssetRequest r) {
    return Map.of(
        "success",
        true,
        "message",
        "Configuration accepted; network probing belongs to the runtime egress policy.");
  }
}
