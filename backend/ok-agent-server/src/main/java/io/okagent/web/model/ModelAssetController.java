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
    /** Returns all model assets available to the current management scope. */
    public List<ModelAssetResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    /** Creates a new reusable model asset configuration. */
    public ModelAssetResponse create(@Valid @RequestBody ModelAssetRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    /** Replaces the editable configuration of an existing model asset. */
    public ModelAssetResponse update(@PathVariable UUID id, @Valid @RequestBody ModelAssetRequest r) {
        return service.update(id, r);
    }

    @PatchMapping("/{id}/enabled")
    /** Enables or disables a model asset for new Agent configuration references. */
    public ModelAssetResponse enabled(@PathVariable UUID id, @RequestParam boolean value) {
        return service.enabled(id, value);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    /** Deletes a model asset that is no longer managed by the platform. */
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/test-connection")
    /** Validates the submitted connection configuration without exposing the credential value. */
    public ModelConnectionTestResponse test(@Valid @RequestBody ModelAssetRequest request) {
        return service.testConnection(request);
    }

    /** Sends a real minimal request through the saved model configuration. */
    @PostMapping("/{id}/test-connection")
    public ModelConnectionTestResponse testSavedConnection(@PathVariable UUID id) {
        return service.testConnection(id);
    }
}
