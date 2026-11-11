package com.danicaju.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente asíncrono para ejecutar bloques de código de forma aislada
 * mediante la API pública de Piston (https://emkc.org/api/v2/piston).
 */
public class PistonClient {

    public static final String DEFAULT_BASE_URL = "https://emkc.org/api/v2/piston";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, RuntimeSpec> SUPPORTED_LANGUAGES = Map.ofEntries(
            Map.entry("js", new RuntimeSpec("javascript", "18.15.0", "main.js")),
            Map.entry("javascript", new RuntimeSpec("javascript", "18.15.0", "main.js")),
            Map.entry("node", new RuntimeSpec("javascript", "18.15.0", "main.js")),
            Map.entry("ts", new RuntimeSpec("typescript", "5.0.3", "main.ts")),
            Map.entry("typescript", new RuntimeSpec("typescript", "5.0.3", "main.ts")),
            Map.entry("py", new RuntimeSpec("python", "3.10.0", "main.py")),
            Map.entry("python", new RuntimeSpec("python", "3.10.0", "main.py")),
            Map.entry("python3", new RuntimeSpec("python", "3.10.0", "main.py")),
            Map.entry("c", new RuntimeSpec("c", "10.2.0", "main.c")),
            Map.entry("cpp", new RuntimeSpec("c++", "10.2.0", "main.cpp")),
            Map.entry("c++", new RuntimeSpec("c++", "10.2.0", "main.cpp")),
            Map.entry("java", new RuntimeSpec("java", "15.0.2", "Main.java")),
            Map.entry("go", new RuntimeSpec("go", "1.16.2", "main.go")),
            Map.entry("rust", new RuntimeSpec("rust", "1.68.2", "main.rs")),
            Map.entry("rs", new RuntimeSpec("rust", "1.68.2", "main.rs")),
            Map.entry("bash", new RuntimeSpec("bash", "5.2.0", "script.sh")),
            Map.entry("sh", new RuntimeSpec("bash", "5.2.0", "script.sh"))
    );

    private static final Set<String> RUNNABLE_SET = SUPPORTED_LANGUAGES.keySet();

    private final String baseUrl;
    private final HttpClient httpClient;

    public PistonClient() {
        this(DEFAULT_BASE_URL);
    }

    public PistonClient(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public static boolean isLanguageSupported(String language) {
        if (language == null || language.isBlank()) return false;
        return RUNNABLE_SET.contains(language.trim().toLowerCase(Locale.ROOT));
    }

    public CompletableFuture<ExecutionResult> executeAsync(String language, String code) {
        return CompletableFuture.supplyAsync(() -> execute(language, code));
    }

    public ExecutionResult execute(String language, String code) {
        if (code == null || code.isBlank()) {
            return new ExecutionResult(false, "", "El bloque de código está vacío.", -1, language);
        }
        String key = language != null ? language.trim().toLowerCase(Locale.ROOT) : "";
        RuntimeSpec spec = SUPPORTED_LANGUAGES.get(key);
        if (spec == null) {
            return new ExecutionResult(false, "", "Lenguaje no soportado en Sandbox: " + language, -1, language);
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("language", spec.language());
            body.put("version", spec.version());
            body.put("files", List.of(Map.of("name", spec.fileName(), "content", code)));
            body.put("compile_timeout", 10000);
            body.put("run_timeout", 5000);

            String jsonBody = MAPPER.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/execute"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "RealTimeChat-Sandbox/4.0")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return new ExecutionResult(
                        false,
                        "",
                        "Error de Piston API (HTTP " + resp.statusCode() + "): " + truncate(resp.body(), 240),
                        resp.statusCode(),
                        spec.language()
                );
            }

            JsonNode root = MAPPER.readTree(resp.body());
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            int exitCode = 0;

            JsonNode compile = root.get("compile");
            if (compile != null && !compile.isNull()) {
                appendStream(out, readOptionalText(compile, "stdout"));
                appendStream(err, readOptionalText(compile, "stderr"));
                int compileCode = compile.path("code").asInt(0);
                if (compileCode != 0) {
                    exitCode = compileCode;
                }
            }

            JsonNode run = root.get("run");
            if (run != null && !run.isNull()) {
                appendStream(out, readOptionalText(run, "stdout"));
                appendStream(err, readOptionalText(run, "stderr"));
                exitCode = run.path("code").asInt(exitCode);
            }

            boolean ok = exitCode == 0 && err.length() == 0;
            return new ExecutionResult(ok, out.toString().trim(), err.toString().trim(), exitCode, spec.language());
        } catch (Exception e) {
            return new ExecutionResult(
                    false,
                    "",
                    "No se pudo conectar con Piston Sandbox (" + e.getMessage() + ")",
                    -1,
                    spec.language()
            );
        }
    }

    private static String readOptionalText(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return (child.isMissingNode() || child.isNull()) ? "" : child.asText();
    }

    private static void appendStream(StringBuilder target, String chunk) {
        if (chunk != null && !chunk.isEmpty()) {
            if (target.length() > 0) target.append('\n');
            target.append(chunk);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private record RuntimeSpec(String language, String version, String fileName) {
    }

    public record ExecutionResult(boolean success, String stdout, String stderr, int exitCode, String language) {
    }
}
