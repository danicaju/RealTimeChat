package com.danicaju.chat.model;

import com.danicaju.chat.security.Permission;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Representa el perfil personalizable de un usuario en RealTimeChat.
 * Incluye biografía con límite estricto, avatar preset o imagen personalizada codificada,
 * banner, estado de presencia, estado personalizado, rol bitfield y clave pública E2EE.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {

    public static final String DEFAULT_BIO = "¡Hola! Estoy usando RealTimeChat.";
    public static final String DEFAULT_AVATAR = "cyber-neon";
    public static final String DEFAULT_BANNER = "#5865F2";

    public static final int MAX_BIO_LENGTH = 240;
    public static final int MAX_CUSTOM_STATUS_LENGTH = 80;
    public static final int MAX_STATUS_SYMBOL_LENGTH = 12;
    public static final int MAX_AVATAR_BASE64_LENGTH = 400_000;

    private String username;
    private String bio;
    private String avatar;
    private String banner;
    private String avatarImageBase64;
    private PresenceStatus presence;
    private String customStatus;
    private String statusSymbol;
    private String role;
    private long permissions;
    private String publicKeyBase64;

    public UserProfile() {
        this.bio = DEFAULT_BIO;
        this.avatar = DEFAULT_AVATAR;
        this.banner = DEFAULT_BANNER;
        this.presence = PresenceStatus.ONLINE;
        this.role = Permission.ROLE_MEMBER;
        this.permissions = Permission.DEFAULT_MEMBER;
    }

    public UserProfile(String username) {
        this(username, DEFAULT_BIO, DEFAULT_AVATAR, DEFAULT_BANNER);
    }

    public UserProfile(String username, String bio, String avatar, String banner) {
        this();
        this.username = username != null ? username.trim() : null;
        this.bio = sanitizeBio(bio);
        this.avatar = avatar != null && !avatar.trim().isEmpty() ? avatar.trim() : DEFAULT_AVATAR;
        this.banner = banner != null && !banner.trim().isEmpty() ? banner.trim() : DEFAULT_BANNER;
    }

    public static String sanitizeBio(String bio) {
        if (bio == null || bio.trim().isEmpty()) {
            return DEFAULT_BIO;
        }
        String trimmed = bio.trim();
        if (trimmed.length() > MAX_BIO_LENGTH) {
            return trimmed.substring(0, MAX_BIO_LENGTH);
        }
        return trimmed;
    }

    public static String sanitizeCustomStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        String trimmed = status.trim().replaceAll("[\\r\\n]+", " ");
        if (trimmed.length() > MAX_CUSTOM_STATUS_LENGTH) {
            return trimmed.substring(0, MAX_CUSTOM_STATUS_LENGTH);
        }
        return trimmed;
    }

    public static String sanitizeStatusSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }
        String trimmed = symbol.trim().replaceAll("[\\r\\n]+", "");
        if (trimmed.length() > MAX_STATUS_SYMBOL_LENGTH) {
            return trimmed.substring(0, MAX_STATUS_SYMBOL_LENGTH);
        }
        return trimmed;
    }

    public static String sanitizeAvatarImageBase64(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return null;
        }
        String trimmed = base64.trim();
        if (trimmed.length() > MAX_AVATAR_BASE64_LENGTH) {
            return null;
        }
        return trimmed;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username != null ? username.trim() : null;
    }

    public String getBio() {
        return bio != null ? bio : DEFAULT_BIO;
    }

    public void setBio(String bio) {
        this.bio = sanitizeBio(bio);
    }

    public String getAvatar() {
        return avatar != null ? avatar : DEFAULT_AVATAR;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar != null && !avatar.trim().isEmpty() ? avatar.trim() : DEFAULT_AVATAR;
    }

    public String getBanner() {
        return banner != null ? banner : DEFAULT_BANNER;
    }

    public void setBanner(String banner) {
        this.banner = banner != null && !banner.trim().isEmpty() ? banner.trim() : DEFAULT_BANNER;
    }

    public String getAvatarImageBase64() {
        return avatarImageBase64;
    }

    public void setAvatarImageBase64(String avatarImageBase64) {
        this.avatarImageBase64 = sanitizeAvatarImageBase64(avatarImageBase64);
    }

    public PresenceStatus getPresence() {
        return presence != null ? presence : PresenceStatus.ONLINE;
    }

    public void setPresence(PresenceStatus presence) {
        this.presence = presence != null ? presence : PresenceStatus.ONLINE;
    }

    public String getCustomStatus() {
        return customStatus;
    }

    public void setCustomStatus(String customStatus) {
        this.customStatus = sanitizeCustomStatus(customStatus);
    }

    public String getStatusSymbol() {
        return statusSymbol;
    }

    public void setStatusSymbol(String statusSymbol) {
        this.statusSymbol = sanitizeStatusSymbol(statusSymbol);
    }

    public String getRole() {
        return role != null ? role : Permission.ROLE_MEMBER;
    }

    public void setRole(String role) {
        this.role = Permission.normalizeRole(role);
    }

    public long getPermissions() {
        return permissions;
    }

    public void setPermissions(long permissions) {
        this.permissions = permissions;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    public void setPublicKeyBase64(String publicKeyBase64) {
        this.publicKeyBase64 = (publicKeyBase64 != null && !publicKeyBase64.trim().isEmpty())
                ? publicKeyBase64.trim() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfile that)) return false;
        return permissions == that.permissions &&
                Objects.equals(username, that.username) &&
                Objects.equals(bio, that.bio) &&
                Objects.equals(avatar, that.avatar) &&
                Objects.equals(banner, that.banner) &&
                Objects.equals(avatarImageBase64, that.avatarImageBase64) &&
                presence == that.presence &&
                Objects.equals(customStatus, that.customStatus) &&
                Objects.equals(statusSymbol, that.statusSymbol) &&
                Objects.equals(role, that.role) &&
                Objects.equals(publicKeyBase64, that.publicKeyBase64);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, bio, avatar, banner, avatarImageBase64, presence,
                customStatus, statusSymbol, role, permissions, publicKeyBase64);
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "username='" + username + '\'' +
                ", bio='" + bio + '\'' +
                ", avatar='" + avatar + '\'' +
                ", banner='" + banner + '\'' +
                ", hasCustomAvatar=" + (avatarImageBase64 != null) +
                ", presence=" + presence +
                ", role='" + role + '\'' +
                '}';
    }
}
