package com.danicaju.chat.service;

import com.danicaju.chat.model.EmbedData;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio asíncrono de extracción de metadatos OpenGraph (og:title, og:description,
 * og:image, og:site_name) para generar tarjetas enriquecidas sin bloquear el envío de mensajes.
 * Incluye protección SSRF contra IPs privadas o loopback.
 */
public class OpenGraphService {

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s<>\"'\\)]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_TAG_PATTERN = Pattern.compile("<meta\\s+([^>]+)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_PATTERN = Pattern.compile("([a-zA-Z0-9_:-]+)\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern TITLE_TAG_PATTERN = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final boolean allowPrivateHostsForTest;

    public OpenGraphService() {
        this(false);
    }

    public OpenGraphService(boolean allowPrivateHostsForTest) {
        this.allowPrivateHostsForTest = allowPrivateHostsForTest;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static String extractFirstUrl(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = URL_PATTERN.matcher(text);
        if (m.find()) {
            String candidate = m.group(1).replaceAll("[.,;!?]+$", "");
            return candidate;
        }
        return null;
    }

    public CompletableFuture<EmbedData> fetchEmbedFromTextAsync(String text) {
        String url = extractFirstUrl(text);
        if (url == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> fetchEmbed(url));
    }

    public EmbedData fetchEmbed(String url) {
        try {
            URI uri = URI.create(url);
            if (!isSafePublicUri(uri)) {
                return null;
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "RealTimeChat-OpenGraphBot/1.0")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("text/html");
            if (!contentType.toLowerCase().contains("html") && !contentType.toLowerCase().contains("xml")) {
                return null;
            }
            String html = response.body();
            if (html != null && html.length() > 300_000) {
                html = html.substring(0, 300_000);
            }
            return parseOpenGraphHtml(url, html);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isSafePublicUri(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return false;
        }
        if (allowPrivateHostsForTest) {
            return true;
        }
        String host = uri.getHost().toLowerCase();
        if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isAnyLocalAddress()
                        || addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static EmbedData parseOpenGraphHtml(String url, String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String ogTitle = null;
        String ogDesc = null;
        String ogImage = null;
        String ogSite = null;

        Matcher metaMatcher = META_TAG_PATTERN.matcher(html);
        while (metaMatcher.find()) {
            String attrs = metaMatcher.group(1);
            String key = null;
            String value = null;
            Matcher attrMatcher = ATTR_PATTERN.matcher(attrs);
            while (attrMatcher.find()) {
                String attrName = attrMatcher.group(1).toLowerCase();
                String attrVal = attrMatcher.group(2);
                if (attrName.equals("property") || attrName.equals("name")) {
                    key = attrVal.toLowerCase().trim();
                } else if (attrName.equals("content")) {
                    value = decodeHtml(attrVal).trim();
                }
            }
            if (key == null || value == null || value.isEmpty()) {
                continue;
            }
            switch (key) {
                case "og:title", "twitter:title" -> {
                    if (ogTitle == null) ogTitle = value;
                }
                case "og:description", "twitter:description", "description" -> {
                    if (ogDesc == null) ogDesc = value;
                }
                case "og:image", "twitter:image" -> {
                    if (ogImage == null) ogImage = value;
                }
                case "og:site_name" -> {
                    if (ogSite == null) ogSite = value;
                }
                default -> {
                }
            }
        }

        if (ogTitle == null) {
            Matcher titleMatcher = TITLE_TAG_PATTERN.matcher(html);
            if (titleMatcher.find()) {
                ogTitle = decodeHtml(titleMatcher.group(1)).trim();
            }
        }

        if (ogTitle == null && ogDesc == null && ogImage == null) {
            return null;
        }
        if (ogSite == null) {
            try {
                ogSite = URI.create(url).getHost();
            } catch (Exception ignored) {
            }
        }
        EmbedData embed = new EmbedData(
                url,
                truncate(ogTitle, 140),
                truncate(ogDesc, 260),
                ogImage,
                ogSite
        );
        embed.setColorHex("#5865F2");
        return embed;
    }

    private static String decodeHtml(String text) {
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
