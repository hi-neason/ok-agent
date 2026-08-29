package io.okagent.module.model.api;

import io.okagent.module.model.application.*;
import io.okagent.module.model.application.ModelAssetService;
import io.okagent.shared.api.ApiResponse;
import io.okagent.shared.api.PageResponse;
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
    /** Returns model assets paginated by most-recently-updated. */
    public ApiResponse<PageResponse<ModelAssetResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(service.list(page, size)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    /** Creates a new reusable model asset configuration. */
    public ApiResponse<ModelAssetResponse> create(@Valid @RequestBody ModelAssetRequest r) {
        return ApiResponse.success(service.create(r));
    }

    @PutMapping("/{id}")
    /** Replaces the editable configuration of an existing model asset. */
    public ApiResponse<ModelAssetResponse> update(@PathVariable UUID id, @Valid @RequestBody ModelAssetRequest r) {
        return ApiResponse.success(service.update(id, r));
    }

    @PatchMapping("/{id}/enabled")
    /** Enables or disables a model asset for new Agent configuration references. */
    public ApiResponse<ModelAssetResponse> enabled(@PathVariable UUID id, @RequestParam boolean value) {
        return ApiResponse.success(service.enabled(id, value));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    /** Deletes a model asset that is no longer managed by the platform. */
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/test-connection")
    /** Validates the submitted connection configuration without exposing the credential value. */
    public ApiResponse<ModelConnectionTestResponse> test(@Valid @RequestBody ModelAssetRequest request) {
        return ApiResponse.success(service.testConnection(request));
    }

    /** Sends a real minimal request through the saved model configuration. */
    @PostMapping("/{id}/test-connection")
    public ApiResponse<ModelConnectionTestResponse> testSavedConnection(@PathVariable UUID id) {
        return ApiResponse.success(service.testConnection(id));
    }
}
