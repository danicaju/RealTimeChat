package com.danicaju.chat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Tarjeta enriquecida (OpenGraph Embed o Webhook Embed) adjunta a un mensaje.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmbedData {

    private String url;
    private String title;
    private String description;
    private String imageUrl;
    private String siteName;
    private String colorHex;
    private String authorName;
    private String footerText;

    public EmbedData() {
    }

    public EmbedData(String url, String title, String description, String imageUrl, String siteName) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.siteName = siteName;
        this.colorHex = "#5865F2";
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getFooterText() {
        return footerText;
    }

    public void setFooterText(String footerText) {
        this.footerText = footerText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbedData that)) return false;
        return Objects.equals(url, that.url) &&
                Objects.equals(title, that.title) &&
                Objects.equals(description, that.description) &&
                Objects.equals(imageUrl, that.imageUrl) &&
                Objects.equals(siteName, that.siteName) &&
                Objects.equals(colorHex, that.colorHex) &&
                Objects.equals(authorName, that.authorName) &&
                Objects.equals(footerText, that.footerText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, title, description, imageUrl, siteName, colorHex, authorName, footerText);
    }
}
