package io.okagent.module.model;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models")
public class ModelAssetController {
    private final ModelAssetService service;

    public ModelAssetController(ModelAssetService service) { this.service = service; }

    @GetMapping public List<ModelAssetResponse> list() { return service.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ModelAssetResponse create(@Valid @RequestBody ModelAssetRequest request) { return service.create(request); }
    @PutMapping("/{id}") public ModelAssetResponse update(@PathVariable String id, @Valid @RequestBody ModelAssetRequest request) { return service.update(id, request); }
    @PatchMapping("/{id}/enabled") public ModelAssetResponse setEnabled(@PathVariable String id, @RequestParam boolean value) { return service.setEnabled(id, value); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id) { service.delete(id); }
    @PostMapping("/test-connection") public Map<String, Object> testConnection(@Valid @RequestBody ModelAssetRequest request) { return service.test(request); }
}
