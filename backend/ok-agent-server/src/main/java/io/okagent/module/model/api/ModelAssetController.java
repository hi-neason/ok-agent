package io.okagent.module.model.api;

import io.okagent.module.model.application.*;
import io.okagent.module.model.application.ModelAssetService;
import io.okagent.shared.api.Response;
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
    public Response<PageResponse<ModelAssetResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return Response.success(PageResponse.of(service.list(page, size)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    /** Creates a new reusable model asset configuration. */
    public Response<ModelAssetResponse> create(@Valid @RequestBody ModelAssetRequest r) {
        return Response.success(service.create(r));
    }

    @PutMapping("/{id}")
    /** Replaces the editable configuration of an existing model asset. */
    public Response<ModelAssetResponse> update(@PathVariable UUID id, @Valid @RequestBody ModelAssetRequest r) {
        return Response.success(service.update(id, r));
    }

    @PatchMapping("/{id}/enabled")
    /** Enables or disables a model asset for new Agent configuration references. */
    public Response<ModelAssetResponse> enabled(@PathVariable UUID id, @RequestParam boolean value) {
        return Response.success(service.enabled(id, value));
    }

    @DeleteMapping("/{id}")
    /** Deletes a model asset that is no longer managed by the platform. */
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }

    @PostMapping("/test-connection")
    /** Validates the submitted connection configuration without exposing the credential value. */
    public Response<ModelConnectionTestResponse> test(@Valid @RequestBody ModelAssetRequest request) {
        return Response.success(service.testConnection(request));
    }

    /** Sends a real minimal request through the saved model configuration. */
    @PostMapping("/{id}/test-connection")
    public Response<ModelConnectionTestResponse> testSavedConnection(@PathVariable UUID id) {
        return Response.success(service.testConnection(id));
    }
}
