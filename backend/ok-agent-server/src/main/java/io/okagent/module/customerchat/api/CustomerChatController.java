package io.okagent.module.customerchat.api;

import io.okagent.module.customerchat.application.CustomerChatService;
import io.okagent.shared.api.Response;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/customer-chat/messages", "/api/v1/chat"})
public class CustomerChatController {
    private final CustomerChatService service;

    public CustomerChatController(CustomerChatService service) {
        this.service = service;
    }

    /** Production customer-service chat: intent-routed, channel+session scoped, no manual agent pick. */
    @PostMapping
    public Response<CustomerChatResponse> chat(@Valid @RequestBody CustomerChatRequest request) {
        return Response.success(CustomerChatResponse.from(service.chat(request.toCommand())));
    }
}
