package com.danicaju.chat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Representa el perfil personalizable de un usuario en RealTimeChat.
 * Incluye biografía, identificador o preset de avatar y estilo de banner.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {

    public static final String DEFAULT_BIO = "¡Hola! Estoy usando RealTimeChat.";
    public static final String DEFAULT_AVATAR = "cyber-neon";
    public static final String DEFAULT_BANNER = "#5865F2";

    public static final int MAX_BIO_LENGTH = 300;

    private String username;
    private String bio;
    private String avatar;
    private String banner;

    public UserProfile() {
        this.bio = DEFAULT_BIO;
        this.avatar = DEFAULT_AVATAR;
        this.banner = DEFAULT_BANNER;
    }

    public UserProfile(String username) {
        this(username, DEFAULT_BIO, DEFAULT_AVATAR, DEFAULT_BANNER);
    }

    public UserProfile(String username, String bio, String avatar, String banner) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfile)) return false;
        UserProfile that = (UserProfile) o;
        return Objects.equals(username, that.username) &&
                Objects.equals(bio, that.bio) &&
                Objects.equals(avatar, that.avatar) &&
                Objects.equals(banner, that.banner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, bio, avatar, banner);
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "username='" + username + '\'' +
                ", bio='" + bio + '\'' +
                ", avatar='" + avatar + '\'' +
                ", banner='" + banner + '\'' +
                '}';
    }
}
