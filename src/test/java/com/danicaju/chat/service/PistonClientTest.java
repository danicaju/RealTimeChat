package com.danicaju.chat.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de PistonClient con Mock HTTP Server")
class PistonClientTest {

    @Test
    @DisplayName("Ejecución simulada de snippet Python devuelve stdout correctamente")
    void testExecuteMockPiston() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/piston/execute", exchange -> {
            byte[] resp = "{\"run\":{\"stdout\":\"42\\n\",\"stderr\":\"\",\"code\":0}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            PistonClient client = new PistonClient("http://127.0.0.1:" + port + "/api/v2/piston");
            assertTrue(PistonClient.isLanguageSupported("python"));
            assertFalse(PistonClient.isLanguageSupported("brainfuck"));

            PistonClient.ExecutionResult result = client.execute("python", "print(6 * 7)");
            assertTrue(result.success());
            assertEquals("42", result.stdout());
            assertEquals(0, result.exitCode());
        } finally {
            server.stop(0);
        }
    }
}
