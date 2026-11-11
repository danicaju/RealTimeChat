package com.danicaju.chat.client;

import com.danicaju.chat.model.ChannelInfo;
import com.danicaju.chat.model.EmbedData;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.PollData;
import com.danicaju.chat.model.PresenceStatus;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.model.WebhookInfo;

import java.util.List;
import java.util.Map;

/**
 * Interfaz de eventos reactivos para clientes de RealTimeChat.
 * Desacopla el motor de red TCP y protocolo JSON de las interfaces de usuario (CLI o Swing GUI).
 */
public interface ChatEventListener {

    void onConnected(String username);

    void onMessageReceived(Message message);

    void onUserListUpdated(List<String> users);

    void onDisconnected(String reason);

    void onError(String errorMessage);

    default void onChannelListUpdated(List<String> channels) {}

    default void onChannelChanged(String newChannel) {}

    default void onTypingUpdated(String sender, String channel, boolean isTyping) {}

    default void onHistoryReceived(String channel, List<Message> history) {}

    default void onProfileReceived(UserProfile profile) {}

    // --- Eventos de Fases 1-4 ---

    default void onUserProfilesUpdated(Map<String, UserProfile> profiles) {}

    default void onMessageAck(String tempId, Long id, String channel, long timestamp) {}

    default void onMessageSendError(String tempId, Message failedMessage, String error) {}

    default void onMessageDeleted(Long messageId, String channel, String deletedBy) {}

    default void onReactionUpdated(Long messageId, String channel, String sender, String emoji, boolean added, Map<String, List<String>> reactions) {}

    default void onPresenceUpdated(String username, PresenceStatus status, String customStatus, String statusSymbol) {}

    default void onHistoryPageReceived(String channel, List<Message> history, boolean hasMore, Long beforeId, Long afterId, Long threadParentId) {}

    default void onChannelMetadataUpdated(List<ChannelInfo> channels) {}

    default void onMessageEmbedUpdated(Long messageId, String channel, EmbedData embed) {}

    default void onPollUpdated(Long messageId, String channel, PollData poll) {}

    default void onWebhookListReceived(String channel, List<WebhookInfo> webhooks) {}

    default void onSearchResultsReceived(String query, List<Message> results) {}

    default void onKeyExchangeReceived(String username, String publicKeyBase64) {}

    default void onWhiteboardEvent(String channel, String sender, String payloadJson) {}

    default void onVoiceStateUpdated(Message stateMessage) {}

    default void onVoiceAudioReceived(String channel, String sender, String base64Audio) {}

    default void onWebrtcSignalReceived(Message signalMessage) {}
}
