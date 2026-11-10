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
import java.util.List;
import java.util.Objects;

/**
 * Representa la unidad fundamental de datos intercambiada en el protocolo de RealTimeChat.
 * Soporta canales, historial de mensajes, estados de escritura (typing), perfiles de usuario
 * y mensajería pública/privada en formato JSON mediante Jackson.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    public static final String DEFAULT_CHANNEL = "#general";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

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

    // --- Métodos de factoría para instanciación limpia y consistente ---

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        Message message = (Message) o;
        return timestamp == message.timestamp &&
                type == message.type &&
                Objects.equals(sender, message.sender) &&
                Objects.equals(target, message.target) &&
                Objects.equals(content, message.content) &&
                Objects.equals(channel, message.channel) &&
                Objects.equals(users, message.users) &&
                Objects.equals(channels, message.channels) &&
                Objects.equals(history, message.history) &&
                Objects.equals(profile, message.profile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, sender, target, content, channel, users, channels, history, profile, timestamp);
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", sender='" + sender + '\'' +
                ", target='" + target + '\'' +
                ", content='" + content + '\'' +
                ", channel='" + channel + '\'' +
                ", users=" + users +
                ", channels=" + channels +
                ", history=" + (history != null ? history.size() : 0) +
                ", profile=" + profile +
                ", timestamp=" + timestamp +
                '}';
    }
}
