package io.okagent.web.chat;

import io.okagent.service.chat.IntentRouterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ProductionChatController {
    private final IntentRouterService service;

    public ProductionChatController(IntentRouterService service) {
        this.service = service;
    }

    /** Production customer-service chat: intent-routed, channel+session scoped, no manual agent pick. */
    @PostMapping
    public ProductionChatResponse chat(@Valid @RequestBody ProductionChatRequest request) {
        return service.chat(request);
    }
}
