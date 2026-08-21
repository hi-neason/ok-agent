package io.okagent.web.product;

import io.okagent.domain.product.SolutionStatus;
import io.okagent.service.product.SolutionService;
import jakarta.validation.Valid;
import java.util.List;
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

    /** Lists all solutions/packages. */
    @GetMapping
    public List<SolutionResponse> list() {
        return service.list();
    }

    /** Returns one solution with its bundled product lines. */
    @GetMapping("/{id}")
    public SolutionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Creates a new solution. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SolutionResponse create(@Valid @RequestBody SolutionRequest request) {
        return service.create(request);
    }

    /** Updates an existing solution and its product lines. */
    @PutMapping("/{id}")
    public SolutionResponse update(@PathVariable UUID id, @Valid @RequestBody SolutionRequest request) {
        return service.update(id, request);
    }

    /** Enables or discontinues a solution. */
    @PatchMapping("/{id}/status")
    public SolutionResponse setStatus(@PathVariable UUID id, @RequestParam SolutionStatus status) {
        return service.setStatus(id, status);
    }

    /** Deletes a solution. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
