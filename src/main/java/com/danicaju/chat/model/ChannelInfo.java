package com.danicaju.chat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Metadatos de un canal de RealTimeChat, incluyendo tipo (TEXT o VOICE)
 * y overrides bitfield por defecto y por usuario.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChannelInfo {

    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_VOICE = "VOICE";

    private String name;
    private String type = TYPE_TEXT;
    private String topic;
    private String createdBy;
    private long defaultAllowMask;
    private long defaultDenyMask;
    private Map<String, ChannelOverride> userOverrides = new LinkedHashMap<>();

    public ChannelInfo() {
    }

    public ChannelInfo(String name, String type, String topic, String createdBy) {
        this.name = name;
        this.type = TYPE_VOICE.equalsIgnoreCase(type) ? TYPE_VOICE : TYPE_TEXT;
        this.topic = topic;
        this.createdBy = createdBy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type != null ? type : TYPE_TEXT;
    }

    public void setType(String type) {
        this.type = TYPE_VOICE.equalsIgnoreCase(type) ? TYPE_VOICE : TYPE_TEXT;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getDefaultAllowMask() {
        return defaultAllowMask;
    }

    public void setDefaultAllowMask(long defaultAllowMask) {
        this.defaultAllowMask = defaultAllowMask;
    }

    public long getDefaultDenyMask() {
        return defaultDenyMask;
    }

    public void setDefaultDenyMask(long defaultDenyMask) {
        this.defaultDenyMask = defaultDenyMask;
    }

    public Map<String, ChannelOverride> getUserOverrides() {
        return userOverrides;
    }

    public void setUserOverrides(Map<String, ChannelOverride> userOverrides) {
        this.userOverrides = userOverrides != null ? new LinkedHashMap<>(userOverrides) : new LinkedHashMap<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChannelInfo that)) return false;
        return defaultAllowMask == that.defaultAllowMask &&
                defaultDenyMask == that.defaultDenyMask &&
                Objects.equals(name, that.name) &&
                Objects.equals(type, that.type) &&
                Objects.equals(topic, that.topic) &&
                Objects.equals(createdBy, that.createdBy) &&
                Objects.equals(userOverrides, that.userOverrides);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, topic, createdBy, defaultAllowMask, defaultDenyMask, userOverrides);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChannelOverride {
        private long allow;
        private long deny;

        public ChannelOverride() {
        }

        public ChannelOverride(long allow, long deny) {
            this.allow = allow;
            this.deny = deny;
        }

        public long getAllow() {
            return allow;
        }

        public void setAllow(long allow) {
            this.allow = allow;
        }

        public long getDeny() {
            return deny;
        }

        public void setDeny(long deny) {
            this.deny = deny;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChannelOverride that)) return false;
            return allow == that.allow && deny == that.deny;
        }

        @Override
        public int hashCode() {
            return Objects.hash(allow, deny);
        }
    }
}
