package io.okagent.web.intent;

import io.okagent.service.intent.CreateIntentRequest;
import io.okagent.service.intent.IntentDto;
import io.okagent.service.intent.IntentNode;
import io.okagent.service.intent.IntentService;
import io.okagent.service.intent.UpdateIntentRequest;
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
    public List<IntentNode> tree() {
        return service.getTree();
    }

    /** Returns a single intent by id. */
    @GetMapping("/{id}")
    public IntentDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Creates a new intent node. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IntentDto create(@Valid @RequestBody CreateIntentRequest request) {
        return service.create(request);
    }

    /** Updates an intent's definition. */
    @PutMapping("/{id}")
    public IntentDto update(@PathVariable UUID id, @Valid @RequestBody UpdateIntentRequest request) {
        return service.update(id, request);
    }

    /** Deletes an intent. Rejected if it still has children. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
