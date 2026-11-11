package com.danicaju.chat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Unidad fundamental de datos intercambiada en el protocolo JSON de RealTimeChat.
 * Soporta canales, cursor pagination, optimistic UI ACK, threads, reacciones,
 * presencia, roles bitfield, polls, webhooks, embeds OpenGraph, E2EE, pizarra y voz.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    public static final String DEFAULT_CHANNEL = "#general";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private Long id;
    private String tempId;
    private MessageType type;
    private String sender;
    private String target;
    private String content;
    private String channel;
    private List<String> users;
    private List<String> channels;
    private List<Message> history;
    private UserProfile profile;
    private long timestamp;

    // --- FASE 1: Threads, Reacciones y Presencia ---
    private Long replyToId;
    private String replySender;
    private String replyPreview;
    private Long threadParentId;
    private Integer threadReplyCount;
    private Map<String, List<String>> reactions;
    private PresenceStatus presenceStatus;
    private String customStatus;
    private String statusSymbol;
    private Map<String, UserProfile> userProfiles;

    // --- FASE 2: Cursores, Canales y Roles ---
    private Long beforeId;
    private Long afterId;
    private Integer limit;
    private Boolean hasMore;
    private List<ChannelInfo> channelInfos;
    private ChannelInfo channelInfo;
    private String role;
    private Long permissions;

    // --- FASE 3: Polls, Webhooks y Búsqueda ---
    private PollData poll;
    private Integer pollOptionIndex;
    private WebhookInfo webhookInfo;
    private List<WebhookInfo> webhooks;

    // --- FASE 4: Multimedia, OpenGraph, Voz/WebRTC y E2EE ---
    private String attachmentName;
    private String attachmentBase64;
    private EmbedData embed;
    private Boolean encrypted;
    private String iv;
    private String senderPublicKey;
    private Double voiceLevel;
    private Boolean voiceMuted;
    private Boolean voiceDeafened;
    private String signalType;
    private String signalPayload;

    public Message() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public Message(MessageType type, String sender, String target, String content, List<String> users, long timestamp) {
        this.type = type;
        this.sender = sender;
        this.target = target;
        this.content = content;
        this.users = users != null ? new ArrayList<>(users) : null;
        this.timestamp = timestamp > 0 ? timestamp : Instant.now().toEpochMilli();
    }

    // --- Métodos de factoría base ---

    public static Message join(String username) {
        Message msg = new Message();
        msg.setType(MessageType.JOIN);
        msg.setSender(username);
        return msg;
    }

    public static Message chat(String sender, String content) {
        return chat(DEFAULT_CHANNEL, sender, content);
    }

    public static Message chat(String channel, String sender, String content) {
        Message msg = new Message();
        msg.setType(MessageType.CHAT);
        msg.setChannel(channel != null && !channel.trim().isEmpty() ? channel.trim() : DEFAULT_CHANNEL);
        msg.setSender(sender);
        msg.setContent(content);
        return msg;
    }

    public static Message privateMsg(String sender, String target, String content) {
        Message msg = new Message();
        msg.setType(MessageType.PRIVATE);
        msg.setSender(sender);
        msg.setTarget(target);
        msg.setContent(content);
        return msg;
    }

    public static Message userList(List<String> users) {
        Message msg = new Message();
        msg.setType(MessageType.USER_LIST);
        msg.setUsers(users);
        return msg;
    }

    public static Message userList(String channel, List<String> users) {
        Message msg = new Message();
        msg.setType(MessageType.USER_LIST);
        msg.setChannel(channel);
        msg.setUsers(users);
        return msg;
    }

    public static Message leave(String sender) {
        Message msg = new Message();
        msg.setType(MessageType.LEAVE);
        msg.setSender(sender);
        return msg;
    }

    public static Message system(String content) {
        Message msg = new Message();
        msg.setType(MessageType.SYSTEM);
        msg.setContent(content);
        return msg;
    }

    public static Message system(String channel, String content) {
        Message msg = new Message();
        msg.setType(MessageType.SYSTEM);
        msg.setChannel(channel);
        msg.setContent(content);
        return msg;
    }

    public static Message joinChannel(String sender, String channel) {
        Message msg = new Message();
        msg.setType(MessageType.JOIN_CHANNEL);
        msg.setSender(sender);
        msg.setChannel(channel);
        return msg;
    }

    public static Message channelList(List<String> channels) {
        Message msg = new Message();
        msg.setType(MessageType.CHANNEL_LIST);
        msg.setChannels(channels);
        return msg;
    }

    public static Message history(String channel, List<Message> history) {
        Message msg = new Message();
        msg.setType(MessageType.HISTORY);
        msg.setChannel(channel);
        msg.setHistory(history);
        return msg;
    }

    public static Message typingStart(String sender, String channel) {
        Message msg = new Message();
        msg.setType(MessageType.TYPING_START);
        msg.setSender(sender);
        msg.setChannel(channel);
        return msg;
    }

    public static Message typingStop(String sender, String channel) {
        Message msg = new Message();
        msg.setType(MessageType.TYPING_STOP);
        msg.setSender(sender);
        msg.setChannel(channel);
        return msg;
    }

    public static Message profileUpdate(String sender, UserProfile profile) {
        Message msg = new Message();
        msg.setType(MessageType.PROFILE_UPDATE);
        msg.setSender(sender);
        msg.setProfile(profile);
        return msg;
    }

    public static Message profileRequest(String sender, String target) {
        Message msg = new Message();
        msg.setType(MessageType.PROFILE_REQUEST);
        msg.setSender(sender);
        msg.setTarget(target);
        return msg;
    }

    public static Message profileResponse(UserProfile profile) {
        Message msg = new Message();
        msg.setType(MessageType.PROFILE_RESPONSE);
        msg.setProfile(profile);
        return msg;
    }

    // --- Nuevos métodos de factoría (Fases 1-4) ---

    public static Message ack(String tempId, Long id, String channel, long timestamp) {
        Message msg = new Message();
        msg.setType(MessageType.MESSAGE_ACK);
        msg.setTempId(tempId);
        msg.setId(id);
        msg.setChannel(channel);
        msg.setTimestamp(timestamp);
        return msg;
    }

    public static Message deleteMsg(Long id, String channel, String sender) {
        Message msg = new Message();
        msg.setType(MessageType.MESSAGE_DELETE);
        msg.setId(id);
        msg.setChannel(channel);
        msg.setSender(sender);
        return msg;
    }

    public static Message reactionAdd(Long messageId, String channel, String sender, String emoji) {
        Message msg = new Message();
        msg.setType(MessageType.REACTION_ADD);
        msg.setId(messageId);
        msg.setChannel(channel);
        msg.setSender(sender);
        msg.setContent(emoji);
        return msg;
    }

    public static Message reactionRemove(Long messageId, String channel, String sender, String emoji) {
        Message msg = new Message();
        msg.setType(MessageType.REACTION_REMOVE);
        msg.setId(messageId);
        msg.setChannel(channel);
        msg.setSender(sender);
        msg.setContent(emoji);
        return msg;
    }

    public static Message presenceUpdate(String sender, PresenceStatus status, String customStatus, String statusSymbol) {
        Message msg = new Message();
        msg.setType(MessageType.PRESENCE_UPDATE);
        msg.setSender(sender);
        msg.setPresenceStatus(status);
        msg.setCustomStatus(customStatus);
        msg.setStatusSymbol(statusSymbol);
        return msg;
    }

    public static Message ping(String sender) {
        Message msg = new Message();
        msg.setType(MessageType.PING);
        msg.setSender(sender);
        return msg;
    }

    public static Message pong() {
        Message msg = new Message();
        msg.setType(MessageType.PONG);
        return msg;
    }

    public static Message historyPageRequest(String sender, String channel, Long beforeId, Long afterId, int limit) {
        Message msg = new Message();
        msg.setType(MessageType.HISTORY_PAGE);
        msg.setSender(sender);
        msg.setChannel(channel);
        msg.setBeforeId(beforeId);
        msg.setAfterId(afterId);
        msg.setLimit(limit);
        return msg;
    }

    public static Message historyPageResponse(String channel, List<Message> history, boolean hasMore, Long beforeId, Long afterId) {
        Message msg = new Message();
        msg.setType(MessageType.HISTORY_PAGE);
        msg.setChannel(channel);
        msg.setHistory(history);
        msg.setHasMore(hasMore);
        msg.setBeforeId(beforeId);
        msg.setAfterId(afterId);
        return msg;
    }

    public static Message channelCreate(String sender, ChannelInfo info) {
        Message msg = new Message();
        msg.setType(MessageType.CHANNEL_CREATE);
        msg.setSender(sender);
        msg.setChannelInfo(info);
        if (info != null) {
            msg.setChannel(info.getName());
        }
        return msg;
    }

    public static Message channelDelete(String sender, String channel) {
        Message msg = new Message();
        msg.setType(MessageType.CHANNEL_DELETE);
        msg.setSender(sender);
        msg.setChannel(channel);
        return msg;
    }

    public static Message channelMetadata(List<ChannelInfo> infos) {
        Message msg = new Message();
        msg.setType(MessageType.CHANNEL_METADATA);
        msg.setChannelInfos(infos);
        return msg;
    }

    public static Message roleUpdate(String sender, String targetUser, String role, Long permissions, ChannelInfo channelOverrideUpdate) {
        Message msg = new Message();
        msg.setType(MessageType.ROLE_UPDATE);
        msg.setSender(sender);
        msg.setTarget(targetUser);
        msg.setRole(role);
        msg.setPermissions(permissions);
        msg.setChannelInfo(channelOverrideUpdate);
        return msg;
    }

    public static Message pollVote(String channel, String sender, Long messageId, int optionIndex) {
        Message msg = new Message();
        msg.setType(MessageType.POLL_VOTE);
        msg.setChannel(channel);
        msg.setSender(sender);
        msg.setId(messageId);
        msg.setPollOptionIndex(optionIndex);
        return msg;
    }

    public static Message webhookCreate(String sender, String channel, String name) {
        Message msg = new Message();
        msg.setType(MessageType.WEBHOOK_CREATE);
        msg.setSender(sender);
        msg.setChannel(channel);
        msg.setContent(name);
        return msg;
    }

    public static Message webhookDelete(String sender, String channel, String webhookId) {
        Message msg = new Message();
        msg.setType(MessageType.WEBHOOK_DELETE);
        msg.setSender(sender);
        msg.setChannel(channel);
        msg.setContent(webhookId);
        return msg;
    }

    public static Message webhookList(String channel, List<WebhookInfo> webhooks) {
        Message msg = new Message();
        msg.setType(MessageType.WEBHOOK_LIST);
        msg.setChannel(channel);
        msg.setWebhooks(webhooks);
        return msg;
    }

    public static Message searchRequest(String sender, String query) {
        Message msg = new Message();
        msg.setType(MessageType.SEARCH_REQUEST);
        msg.setSender(sender);
        msg.setContent(query);
        return msg;
    }

    public static Message searchResponse(String query, List<Message> results) {
        Message msg = new Message();
        msg.setType(MessageType.SEARCH_RESPONSE);
        msg.setContent(query);
        msg.setHistory(results);
        return msg;
    }

    public static Message embedUpdate(Long messageId, String channel, EmbedData embed) {
        Message msg = new Message();
        msg.setType(MessageType.MESSAGE_EMBED_UPDATE);
        msg.setId(messageId);
        msg.setChannel(channel);
        msg.setEmbed(embed);
        return msg;
    }

    public static Message keyExchange(String sender, String target, String publicKeyBase64) {
        Message msg = new Message();
        msg.setType(MessageType.KEY_EXCHANGE);
        msg.setSender(sender);
        msg.setTarget(target);
        msg.setSenderPublicKey(publicKeyBase64);
        return msg;
    }

    public static Message whiteboardEvent(String channel, String sender, String payloadJson) {
        Message msg = new Message();
        msg.setType(MessageType.WHITEBOARD_EVENT);
        msg.setChannel(channel);
        msg.setSender(sender);
        msg.setContent(payloadJson);
        return msg;
    }

    public static Message voiceState(String channel, String sender, boolean muted, boolean deafened, double level, List<String> participants) {
        Message msg = new Message();
        msg.setType(MessageType.VOICE_STATE);
        msg.setChannel(channel);
        msg.setSender(sender);
        msg.setVoiceMuted(muted);
        msg.setVoiceDeafened(deafened);
        msg.setVoiceLevel(level);
        msg.setUsers(participants);
        return msg;
    }

    public static Message voiceAudio(String channel, String sender, String base64Audio) {
        Message msg = new Message();
        msg.setType(MessageType.VOICE_AUDIO);
        msg.setChannel(channel);
        msg.setSender(sender);
        msg.setContent(base64Audio);
        return msg;
    }

    public static Message webrtcSignal(String sender, String target, String channel, String signalType, String signalPayload) {
        Message msg = new Message();
        msg.setType(MessageType.WEBRTC_SIGNAL);
        msg.setSender(sender);
        msg.setTarget(target);
        msg.setChannel(channel);
        msg.setSignalType(signalType);
        msg.setSignalPayload(signalPayload);
        return msg;
    }

    // --- Métodos de serialización y deserialización JSON ---

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error al serializar el mensaje a JSON: " + e.getMessage(), e);
        }
    }

    public static Message fromJson(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("El JSON de entrada no puede ser nulo ni vacío");
        }
        return MAPPER.readValue(json, Message.class);
    }

    // --- Getters y Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTempId() {
        return tempId;
    }

    public void setTempId(String tempId) {
        this.tempId = tempId;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public List<String> getUsers() {
        return users != null ? Collections.unmodifiableList(users) : Collections.emptyList();
    }

    public void setUsers(List<String> users) {
        this.users = users != null ? new ArrayList<>(users) : null;
    }

    public List<String> getChannels() {
        return channels != null ? Collections.unmodifiableList(channels) : Collections.emptyList();
    }

    public void setChannels(List<String> channels) {
        this.channels = channels != null ? new ArrayList<>(channels) : null;
    }

    public List<Message> getHistory() {
        return history != null ? Collections.unmodifiableList(history) : Collections.emptyList();
    }

    public void setHistory(List<Message> history) {
        this.history = history != null ? new ArrayList<>(history) : null;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public void setProfile(UserProfile profile) {
        this.profile = profile;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Long getReplyToId() {
        return replyToId;
    }

    public void setReplyToId(Long replyToId) {
        this.replyToId = replyToId;
    }

    public String getReplySender() {
        return replySender;
    }

    public void setReplySender(String replySender) {
        this.replySender = replySender;
    }

    public String getReplyPreview() {
        return replyPreview;
    }

    public void setReplyPreview(String replyPreview) {
        this.replyPreview = replyPreview;
    }

    public Long getThreadParentId() {
        return threadParentId;
    }

    public void setThreadParentId(Long threadParentId) {
        this.threadParentId = threadParentId;
    }

    public Integer getThreadReplyCount() {
        return threadReplyCount;
    }

    public void setThreadReplyCount(Integer threadReplyCount) {
        this.threadReplyCount = threadReplyCount;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, List<String>> getReactions() {
        return reactions;
    }

    public void setReactions(Map<String, List<String>> reactions) {
        this.reactions = reactions != null ? new LinkedHashMap<>(reactions) : null;
    }

    public PresenceStatus getPresenceStatus() {
        return presenceStatus;
    }

    public void setPresenceStatus(PresenceStatus presenceStatus) {
        this.presenceStatus = presenceStatus;
    }

    public String getCustomStatus() {
        return customStatus;
    }

    public void setCustomStatus(String customStatus) {
        this.customStatus = customStatus;
    }

    public String getStatusSymbol() {
        return statusSymbol;
    }

    public void setStatusSymbol(String statusSymbol) {
        this.statusSymbol = statusSymbol;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, UserProfile> getUserProfiles() {
        return userProfiles;
    }

    public void setUserProfiles(Map<String, UserProfile> userProfiles) {
        this.userProfiles = userProfiles != null ? new LinkedHashMap<>(userProfiles) : null;
    }

    public Long getBeforeId() {
        return beforeId;
    }

    public void setBeforeId(Long beforeId) {
        this.beforeId = beforeId;
    }

    public Long getAfterId() {
        return afterId;
    }

    public void setAfterId(Long afterId) {
        this.afterId = afterId;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ChannelInfo> getChannelInfos() {
        return channelInfos;
    }

    public void setChannelInfos(List<ChannelInfo> channelInfos) {
        this.channelInfos = channelInfos != null ? new ArrayList<>(channelInfos) : null;
    }

    public ChannelInfo getChannelInfo() {
        return channelInfo;
    }

    public void setChannelInfo(ChannelInfo channelInfo) {
        this.channelInfo = channelInfo;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getPermissions() {
        return permissions;
    }

    public void setPermissions(Long permissions) {
        this.permissions = permissions;
    }

    public PollData getPoll() {
        return poll;
    }

    public void setPoll(PollData poll) {
        this.poll = poll;
    }

    public Integer getPollOptionIndex() {
        return pollOptionIndex;
    }

    public void setPollOptionIndex(Integer pollOptionIndex) {
        this.pollOptionIndex = pollOptionIndex;
    }

    public WebhookInfo getWebhookInfo() {
        return webhookInfo;
    }

    public void setWebhookInfo(WebhookInfo webhookInfo) {
        this.webhookInfo = webhookInfo;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<WebhookInfo> getWebhooks() {
        return webhooks;
    }

    public void setWebhooks(List<WebhookInfo> webhooks) {
        this.webhooks = webhooks != null ? new ArrayList<>(webhooks) : null;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public void setAttachmentName(String attachmentName) {
        this.attachmentName = attachmentName;
    }

    public String getAttachmentBase64() {
        return attachmentBase64;
    }

    public void setAttachmentBase64(String attachmentBase64) {
        this.attachmentBase64 = attachmentBase64;
    }

    public EmbedData getEmbed() {
        return embed;
    }

    public void setEmbed(EmbedData embed) {
        this.embed = embed;
    }

    public Boolean getEncrypted() {
        return encrypted;
    }

    public void setEncrypted(Boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getSenderPublicKey() {
        return senderPublicKey;
    }

    public void setSenderPublicKey(String senderPublicKey) {
        this.senderPublicKey = senderPublicKey;
    }

    public Double getVoiceLevel() {
        return voiceLevel;
    }

    public void setVoiceLevel(Double voiceLevel) {
        this.voiceLevel = voiceLevel;
    }

    public Boolean getVoiceMuted() {
        return voiceMuted;
    }

    public void setVoiceMuted(Boolean voiceMuted) {
        this.voiceMuted = voiceMuted;
    }

    public Boolean getVoiceDeafened() {
        return voiceDeafened;
    }

    public void setVoiceDeafened(Boolean voiceDeafened) {
        this.voiceDeafened = voiceDeafened;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public String getSignalPayload() {
        return signalPayload;
    }

    public void setSignalPayload(String signalPayload) {
        this.signalPayload = signalPayload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message message)) return false;
        return timestamp == message.timestamp &&
                Objects.equals(id, message.id) &&
                Objects.equals(tempId, message.tempId) &&
                type == message.type &&
                Objects.equals(sender, message.sender) &&
                Objects.equals(target, message.target) &&
                Objects.equals(content, message.content) &&
                Objects.equals(channel, message.channel) &&
                Objects.equals(users, message.users) &&
                Objects.equals(channels, message.channels) &&
                Objects.equals(history, message.history) &&
                Objects.equals(profile, message.profile) &&
                Objects.equals(replyToId, message.replyToId) &&
                Objects.equals(threadParentId, message.threadParentId) &&
                Objects.equals(reactions, message.reactions) &&
                Objects.equals(embed, message.embed) &&
                Objects.equals(poll, message.poll) &&
                Objects.equals(encrypted, message.encrypted) &&
                Objects.equals(iv, message.iv);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tempId, type, sender, target, content, channel,
                users, channels, history, profile, timestamp, replyToId, threadParentId,
                reactions, embed, poll, encrypted, iv);
    }

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", tempId='" + tempId + '\'' +
                ", type=" + type +
                ", sender='" + sender + '\'' +
                ", target='" + target + '\'' +
                ", content='" + content + '\'' +
                ", channel='" + channel + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
