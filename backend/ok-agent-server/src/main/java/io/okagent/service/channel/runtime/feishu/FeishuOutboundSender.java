package io.okagent.service.channel.runtime.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sends text messages back to Feishu through the official Open API SDK, addressed by chat_id. */
public class FeishuOutboundSender {

    private static final Logger log = LoggerFactory.getLogger(FeishuOutboundSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Client client;

    public FeishuOutboundSender(Client client) {
        this.client = client;
    }

    /** Sends a text message to the given chat (works for both p2p and group chats). */
    public void sendText(String chatId, String text) {
        if (chatId == null || chatId.isBlank() || text == null || text.isBlank()) {
            return;
        }
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("text", text);
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType("chat_id")
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(chatId)
                            .msgType("text")
                            .content(JSON.writeValueAsString(content))
                            .build())
                    .build();
            var resp = client.im().message().create(req);
            if (!resp.success()) {
                log.warn("Feishu send to chat {} failed: code={}, msg={}", chatId, resp.getCode(), resp.getMsg());
            }
        } catch (Exception e) {
            log.warn("Feishu send to chat {} failed: {}", chatId, e.getMessage());
        }
    }
}
