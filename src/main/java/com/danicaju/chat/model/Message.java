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
 * Se serializa y deserializa en formato JSON mediante Jackson.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private MessageType type;
    private String sender;
    private String target;
    private String content;
    private List<String> users;
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
        Message msg = new Message();
        msg.setType(MessageType.CHAT);
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

    public List<String> getUsers() {
        return users != null ? Collections.unmodifiableList(users) : Collections.emptyList();
    }

    public void setUsers(List<String> users) {
        this.users = users != null ? new ArrayList<>(users) : null;
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
                Objects.equals(users, message.users);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, sender, target, content, users, timestamp);
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", sender='" + sender + '\'' +
                ", target='" + target + '\'' +
                ", content='" + content + '\'' +
                ", users=" + users +
                ", timestamp=" + timestamp +
                '}';
    }
}
