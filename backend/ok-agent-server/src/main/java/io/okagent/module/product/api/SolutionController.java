package io.okagent.module.product.api;

import io.okagent.module.product.application.*;
import io.okagent.module.product.application.SolutionService;
import io.okagent.module.product.domain.SolutionStatus;
import io.okagent.shared.api.Response;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/solutions")
public class SolutionController {
    private final SolutionService service;

    public SolutionController(SolutionService service) {
        this.service = service;
    }

    /** Lists solutions/packages, paginated by most-recently-updated. */
    @GetMapping
    public Response<PageResponse<SolutionResponse>> list(
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page, @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {
        return Response.success(PageResponse.of(service.list(page, size)));
    }

    /** Returns one solution with its bundled product lines. */
    @GetMapping("/{id}")
    public Response<SolutionResponse> get(@PathVariable UUID id) {
        return Response.success(service.get(id));
    }

    /** Creates a new solution. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<SolutionResponse> create(@Valid @RequestBody SolutionRequest request) {
        return Response.success(service.create(request));
    }

    /** Updates an existing solution and its product lines. */
    @PutMapping("/{id}")
    public Response<SolutionResponse> update(@PathVariable UUID id, @Valid @RequestBody SolutionRequest request) {
        return Response.success(service.update(id, request));
    }

    /** Enables or discontinues a solution. */
    @PatchMapping("/{id}/status")
    public Response<SolutionResponse> setStatus(@PathVariable UUID id, @RequestParam SolutionStatus status) {
        return Response.success(service.setStatus(id, status));
    }

    /** Deletes a solution. */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }
}
