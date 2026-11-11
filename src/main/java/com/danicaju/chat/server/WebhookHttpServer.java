package com.danicaju.chat.server;

import com.danicaju.chat.model.EmbedData;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.WebhookInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP embebido para recibir invocaciones REST de Webhooks de canales
 * en la ruta POST /api/webhooks/{id}/{token} y verificar salud en GET /api/health.
 */
public class WebhookHttpServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_BODY_BYTES = 128 * 1024;

    private final ChatRoom chatRoom;
    private final HttpServer server;

    public WebhookHttpServer(int port, ChatRoom chatRoom) throws IOException {
        this.chatRoom = chatRoom;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.createContext("/api/health", this::handleHealth);
        this.server.createContext("/api/webhooks/", this::handleWebhookPost);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + getPort();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"status\":\"ok\",\"service\":\"RealTimeChat-Webhooks\"}");
    }

    private void handleWebhookPost(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        // Esperado: /api/webhooks/{id}/{token}
        String suffix = path.substring("/api/webhooks/".length());
        String[] parts = suffix.split("/");
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Ruta inválida. Usa /api/webhooks/{id}/{token}\"}");
            return;
        }
        String webhookId = parts[0];
        String token = parts[1];

        WebhookInfo webhook = chatRoom.validateWebhook(webhookId, token);
        if (webhook == null) {
            sendJson(exchange, 401, "{\"error\":\"Webhook no autorizado o token inválido\"}");
            return;
        }

        byte[] bodyBytes;
        try (InputStream is = exchange.getRequestBody()) {
            bodyBytes = is.readNBytes(MAX_BODY_BYTES);
        }
        String rawBody = new String(bodyBytes, StandardCharsets.UTF_8).trim();
        if (rawBody.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"El cuerpo JSON del webhook está vacío\"}");
            return;
        }

        try {
            JsonNode root = MAPPER.readTree(rawBody);
            String senderName = textOrNull(root, "username");
            if (senderName == null) {
                senderName = webhook.getName() + " [Webhook]";
            }
            String content = textOrNull(root, "content");

            EmbedData embed = null;
            JsonNode embedNode = root.has("embed") ? root.get("embed")
                    : (root.has("embeds") && root.get("embeds").isArray() && root.get("embeds").size() > 0
                    ? root.get("embeds").get(0) : null);
            if (embedNode != null && embedNode.isObject()) {
                embed = new EmbedData();
                embed.setTitle(textOrNull(embedNode, "title"));
                embed.setDescription(textOrNull(embedNode, "description"));
                embed.setUrl(textOrNull(embedNode, "url"));
                embed.setImageUrl(textOrNull(embedNode, "imageUrl"));
                embed.setSiteName(textOrNull(embedNode, "siteName") != null
                        ? textOrNull(embedNode, "siteName") : webhook.getName());
                embed.setColorHex(textOrNull(embedNode, "colorHex") != null
                        ? textOrNull(embedNode, "colorHex") : "#57F287");
                embed.setAuthorName(senderName);
            } else if (root.has("title") || root.has("description")) {
                embed = new EmbedData();
                embed.setTitle(textOrNull(root, "title"));
                embed.setDescription(textOrNull(root, "description"));
                embed.setUrl(textOrNull(root, "url"));
                embed.setImageUrl(textOrNull(root, "imageUrl"));
                embed.setSiteName(webhook.getName());
                embed.setColorHex("#57F287");
                embed.setAuthorName(senderName);
            }

            if ((content == null || content.isBlank()) && embed == null) {
                sendJson(exchange, 400, "{\"error\":\"Se requiere 'content' o un bloque 'embed'\"}");
                return;
            }

            Message msg = Message.chat(webhook.getChannel(), senderName, content != null ? content : "");
            msg.setEmbed(embed);
            Message saved = chatRoom.dispatchExternalChannelMessage(msg);

            String okJson = String.format("{\"ok\":true,\"messageId\":%d,\"channel\":\"%s\"}",
                    saved.getId() != null ? saved.getId() : 0L,
                    escapeJson(webhook.getChannel()));
            sendJson(exchange, 201, okJson);
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"JSON inválido: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return null;
        String val = node.get(field).asText().trim();
        return val.isEmpty() ? null : val;
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
