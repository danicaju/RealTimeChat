package com.danicaju.chat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Objects;

/**
 * Metadatos de un Webhook HTTP asociado a un canal.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookInfo {

    private String id;
    private String channel;
    private String name;
    private String token;
    private String createdBy;
    private String url;
    private long createdAt;

    public WebhookInfo() {
        this.createdAt = Instant.now().toEpochMilli();
    }

    public WebhookInfo(String id, String channel, String name, String token, String createdBy) {
        this.id = id;
        this.channel = channel;
        this.name = name;
        this.token = token;
        this.createdBy = createdBy;
        this.createdAt = Instant.now().toEpochMilli();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebhookInfo that)) return false;
        return createdAt == that.createdAt &&
                Objects.equals(id, that.id) &&
                Objects.equals(channel, that.channel) &&
                Objects.equals(name, that.name) &&
                Objects.equals(token, that.token) &&
                Objects.equals(createdBy, that.createdBy) &&
                Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, channel, name, token, createdBy, url, createdAt);
    }
}
