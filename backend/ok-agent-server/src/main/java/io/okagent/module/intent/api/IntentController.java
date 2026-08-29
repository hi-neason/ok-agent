package io.okagent.module.intent.api;

import io.okagent.module.intent.application.CreateIntentRequest;
import io.okagent.module.intent.application.IntentDto;
import io.okagent.module.intent.application.IntentNode;
import io.okagent.module.intent.application.IntentService;
import io.okagent.module.intent.application.UpdateIntentRequest;
import io.okagent.shared.api.Response;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intents")
public class IntentController {
    private final IntentService service;

    public IntentController(IntentService service) {
        this.service = service;
    }

    /** Returns the intent tree (roots with nested children). */
    @GetMapping("/tree")
    public Response<List<IntentNode>> tree() {
        return Response.success(service.getTree());
    }

    /** Returns a single intent by id. */
    @GetMapping("/{id}")
    public Response<IntentDto> get(@PathVariable UUID id) {
        return Response.success(service.get(id));
    }

    /** Creates a new intent node. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<IntentDto> create(@Valid @RequestBody CreateIntentRequest request) {
        return Response.success(service.create(request));
    }

    /** Updates an intent's definition. */
    @PutMapping("/{id}")
    public Response<IntentDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateIntentRequest request) {
        return Response.success(service.update(id, request));
    }

    /** Deletes an intent. Rejected if it still has children. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }
}
