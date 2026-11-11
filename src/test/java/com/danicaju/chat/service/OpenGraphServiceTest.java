package com.danicaju.chat.service;

import com.danicaju.chat.model.EmbedData;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de OpenGraphService y Protección SSRF")
class OpenGraphServiceTest {

    @Test
    @DisplayName("Bloqueo SSRF de localhost e IPs privadas en modo producción")
    void testSsrfGuard() {
        OpenGraphService prod = new OpenGraphService(false);
        assertFalse(prod.isSafePublicUri(URI.create("http://localhost:8080/admin")));
        assertFalse(prod.isSafePublicUri(URI.create("http://127.0.0.1/secret")));
        assertFalse(prod.isSafePublicUri(URI.create("http://192.168.1.1/")));
        assertFalse(prod.isSafePublicUri(URI.create("file:///etc/passwd")));
    }

    @Test
    @DisplayName("Extracción OpenGraph desde servidor HTML local")
    void testFetchAndParseOpenGraph() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/article", exchange -> {
            String html = """
                <html><head>
                  <meta property="og:title" content="RealTimeChat v4 &amp; Discord UI" />
                  <meta content="Arquitectura Java 21 con E2EE" property="og:description" />
                  <meta property="og:site_name" content="RealTime Docs" />
                  <meta property="og:image" content="https://example.org/banner.png" />
                </head><body>Hola</body></html>
            """;
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            OpenGraphService service = new OpenGraphService(true);
            String text = "Echa un vistazo a http://127.0.0.1:" + port + "/article ahora mismo.";
            EmbedData embed = service.fetchEmbedFromTextAsync(text).get();
            assertNotNull(embed);
            assertEquals("RealTimeChat v4 & Discord UI", embed.getTitle());
            assertEquals("Arquitectura Java 21 con E2EE", embed.getDescription());
            assertEquals("RealTime Docs", embed.getSiteName());
            assertEquals("https://example.org/banner.png", embed.getImageUrl());
        } finally {
            server.stop(0);
        }
    }
}
