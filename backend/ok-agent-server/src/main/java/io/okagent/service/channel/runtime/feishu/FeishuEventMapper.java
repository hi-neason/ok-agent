package io.okagent.service.channel.runtime.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.EventSender;
import com.lark.oapi.service.im.v1.model.MentionEvent;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.gateway.channel.InboundMessage;
import io.agentscope.harness.agent.gateway.channel.Peer;
import io.agentscope.harness.agent.gateway.channel.PeerKind;
import java.util.List;
import java.util.Optional;

/**
 * Maps a Feishu {@code im.message.receive_v1} long-connection event (already deserialized by the
 * official SDK) into the harness {@link InboundMessage}. Only text messages are dispatched; other
 * message types are acknowledged but ignored.
 */
public final class FeishuEventMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String channelId;

    public FeishuEventMapper(String channelId) {
        this.channelId = channelId;
    }

    /** Parsed inbound turn: the normalized message plus the chat_id to reply to. */
    public record Mapped(InboundMessage inbound, String chatId, boolean mentionedBot) {}

    /**
     * Maps the event. Returns empty when the message should not be dispatched (non-text, malformed,
     * or a group message that did not @ the bot).
     */
    public Optional<Mapped> map(P2MessageReceiveV1 event) {
        if (event == null || event.getEvent() == null) {
            return Optional.empty();
        }
        EventMessage message = event.getEvent().getMessage();
        EventSender sender = event.getEvent().getSender();
        if (message == null || sender == null || sender.getSenderId() == null) {
            return Optional.empty();
        }
        String messageType = message.getMessageType();
        if (!"text".equalsIgnoreCase(messageType)) {
            return Optional.empty();
        }
        String chatId = message.getChatId();
        if (chatId == null || chatId.isBlank()) {
            return Optional.empty();
        }
        boolean group = "group".equalsIgnoreCase(message.getChatType());
        MentionEvent[] mentions = message.getMentions();
        boolean mentionedBot = containsBotMention(mentions);

        // In group chats the bot only responds when explicitly @-mentioned.
        if (group && !mentionedBot) {
            return Optional.empty();
        }

        String openId = sender.getSenderId().getOpenId();
        String text = extractText(message.getContent());
        if (text == null) {
            return Optional.empty();
        }
        if (mentionedBot) {
            text = stripMentions(text, mentions);
        }
        text = text.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }

        PeerKind kind = group ? PeerKind.GROUP : PeerKind.DIRECT;
        Peer peer = new Peer(kind, chatId);
        String senderName = openId != null ? openId : chatId;
        Msg msg = Msg.builder()
                .role(MsgRole.USER)
                .name(senderName)
                .textContent(text)
                .build();
        String tenant = event.getTenantKey();

        InboundMessage inbound = InboundMessage.builder(channelId, peer, List.of(msg))
                .accountId(tenant)
                .senderId(openId)
                .build();
        return Optional.of(new Mapped(inbound, chatId, mentionedBot));
    }

    /** Extracts the {@code text} field from the JSON-encoded message content string. */
    private static String extractText(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(contentJson);
            return node.path("text").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A bot mention has {@code mentioned_type = "app"} (the bot is an application), with no open_id
     * on its id block.
     */
    private static boolean containsBotMention(MentionEvent[] mentions) {
        if (mentions == null) {
            return false;
        }
        for (MentionEvent m : mentions) {
            if (m != null && "app".equalsIgnoreCase(m.getMentionedType())) {
                return true;
            }
        }
        return false;
    }

    /** Removes {@code @_user_N} placeholders left in the text for each mention. */
    private static String stripMentions(String text, MentionEvent[] mentions) {
        if (mentions == null) {
            return text;
        }
        String result = text;
        for (MentionEvent m : mentions) {
            if (m != null && m.getKey() != null) {
                result = result.replace(m.getKey(), "");
            }
        }
        return result;
    }
}
