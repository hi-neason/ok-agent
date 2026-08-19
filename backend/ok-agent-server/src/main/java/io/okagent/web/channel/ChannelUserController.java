package io.okagent.web.channel;

import io.okagent.service.channel.ChannelUserService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Read-only listing of auto-discovered people who converse with channel-bound bots. */
@RestController
@RequestMapping("/api/v1/channel-users")
public class ChannelUserController {

    private final ChannelUserService service;

    public ChannelUserController(ChannelUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<ChannelUserView> list(
            @RequestParam(required = false) String channelType,
            @RequestParam(required = false) String channelKey,
            @RequestParam(defaultValue = "200") int limit) {
        return service.list(channelType, channelKey, limit).stream()
                .map(ChannelUserView::from)
                .toList();
    }
}
