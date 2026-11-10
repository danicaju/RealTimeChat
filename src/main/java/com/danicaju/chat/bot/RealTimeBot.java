package com.danicaju.chat.bot;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor del asistente virtual y bot inteligente de RealTimeChat.
 * Responde a consultas sobre computación, algoritmos, concurrencia, resúmenes del canal
 * y operaciones matemáticas interactivas al ser invocado con '@bot' o '@ai'.
 */
public class RealTimeBot {

    public static final String BOT_NAME = "RealTimeBot";
    private static final Pattern BOT_PREFIX_PATTERN = Pattern.compile("^@(?:bot|ai)\\b|^/(?:bot|ai)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Determina si un mensaje va dirigido al asistente inteligente.
     */
    public static boolean isBotQuery(String content) {
        if (content == null) {
            return false;
        }
        return BOT_PREFIX_PATTERN.matcher(content.trim()).find();
    }

    /**
     * Extrae el contenido de la consulta eliminando el prefijo del bot.
     */
    public static String extractPrompt(String content) {
        if (content == null) {
            return "";
        }
        return BOT_PREFIX_PATTERN.matcher(content.trim()).replaceFirst("").trim();
    }

    /**
     * Procesa la consulta y devuelve la respuesta correspondiente.
     *
     * @param rawPrompt     Texto completo del mensaje
     * @param channel        Canal desde donde se realiza la consulta
     * @param channelHistory Historial reciente del canal para resúmenes
     * @return Mensaje de respuesta listo para ser transmitido
     */
    public static Message handleMessage(String rawPrompt, String channel, List<Message> channelHistory) {
        String prompt = extractPrompt(rawPrompt);
        String responseText = generateAnswer(prompt, channel, channelHistory);
        return Message.chat(channel, BOT_NAME, responseText);
    }

    /**
     * Genera la respuesta lógica para una consulta dada.
     */
    public static String generateAnswer(String prompt, String channel, List<Message> history) {
        if (prompt == null || prompt.trim().isEmpty() || prompt.matches("^(?i)(hola|hi|hey|ayuda|help)$")) {
            return getHelpMessage();
        }

        String lower = prompt.toLowerCase(Locale.ROOT).trim();

        // 1. Resumen de la sala o canal
        if (lower.startsWith("resume") || lower.startsWith("resumen") || lower.startsWith("summarize")) {
            return summarizeChannel(channel, history);
        }

        // 2. Información del sistema
        if (lower.startsWith("info") || lower.startsWith("status") || lower.startsWith("sistema")) {
            return getSystemInfo();
        }

        // 3. Calculadora matemática
        if (lower.startsWith("calc ") || lower.startsWith("calcular ") || lower.matches("^[0-9\\s+\\-*/^().%]+$")) {
            String expr = prompt.trim().replaceFirst("^(?i)(calc|calcular)\\s+", "");
            return evaluateMath(expr);
        }

        // 4. Preguntas técnicas y algoritmos
        if (lower.contains("concurrencia") || lower.contains("virtual thread") || lower.contains("hilo")) {
            return "⚡ **Concurrencia en RealTimeChat**: Emplea Virtual Threads (Project Loom en Java 21+) y Thread Pools optimizados. " +
                    "Los hilos virtuales son ultra ligeros (~1 KB vs ~1 MB de los hilos de SO), permitiendo miles de conexiones concurrentes " +
                    "sin bloquear hilos nativos del sistema operativo.";
        }

        if (lower.contains("rate limit") || lower.contains("token bucket") || lower.contains("spam")) {
            return "🛡️ **Token Bucket Rate Limiting**: Cada usuario dispone de un cubo de capacidad C con reposición continua a tasa R tokens/s. " +
                    "Permite ráfagas cortas mientras mitiga de forma determinista la saturación por spam o ataques DoS.";
        }

        if (lower.contains("deadlock") || lower.contains("bloqueo mutuo")) {
            return "🔒 **Deadlock (Interbloqueo)**: Situación donde dos o más hilos quedan bloqueados indefinidamente, esperando mutuamente " +
                    "recursos adquiridos por los otros. Las 4 condiciones necesarias de Coffman son: exclusión mutua, retención y espera, " +
                    "sin desalojo y espera circular. Se previene ordenando globalmente la adquisición de cerraduras.";
        }

        if (lower.contains("tcp") || lower.contains("udp") || lower.contains("protocolo")) {
            return "🌐 **TCP vs UDP**: TCP (Transmission Control Protocol) es orientado a conexión, confiable, con entrega ordenada " +
                    "y control de congestión (utilizado en RealTimeChat con sockets). UDP es no orientado a conexión y sin garantías, " +
                    "ideal para streaming o juegos multijugador.";
        }

        if (lower.contains("big o") || lower.contains("complejidad") || lower.contains("algoritmo")) {
            return "📊 **Complejidad Algorítmica**: O(1) tiempo constante (ej: HashMap.get), O(log n) logarítmico (búsqueda binaria), " +
                    "O(n) lineal, O(n log n) óptimo para ordenamiento comparativo (MergeSort/TimSort), O(n²) cuadrático. " +
                    "¡Siempre optimiza el cuello de botella más caliente!";
        }

        if (lower.contains("json") || lower.contains("jackson") || lower.contains("serializ")) {
            return "📦 **Protocolo JSON**: RealTimeChat estructura su comunicación en mensajes JSON serializados con Jackson Databind " +
                    "y módulo JSR310 para timestamps de precisión temporal ISO Instant.";
        }

        // 5. Respuesta conversacional estándar
        return "🤖 Comprendo tu consulta: \"" + prompt + "\". " +
                "Puedo ayudarte con temas de concurrencia, algoritmos, resumen del canal (@bot resume), " +
                "evaluación matemática (@bot calc 2^8) o detalles de arquitectura del sistema. ¡Escribe `@bot ayuda` para más opciones!";
    }

    private static String getHelpMessage() {
        return "🤖 **Comandos del Asistente RealTimeBot**:\n" +
                "• `@bot resume` : Genera un resumen inteligente de la conversación reciente en el canal.\n" +
                "• `@bot calc <expresion>` : Resuelve expresiones matemáticas (ej: `@bot calc 2^10 + 50`).\n" +
                "• `@bot info` : Métricas y estado del servidor y entorno de ejecución.\n" +
                "• Preguntas sobre computación: Concurrencia, virtual threads, token bucket, sockets TCP, Big-O, etc.";
    }

    private static String getSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMem = runtime.maxMemory() / (1024 * 1024);
        long totalMem = runtime.totalMemory() / (1024 * 1024);
        long freeMem = runtime.freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;
        int cpus = runtime.availableProcessors();
        String javaVer = System.getProperty("java.version");

        return String.format(Locale.ROOT,
                "🖥️ **Estado del Sistema RealTimeChat**:\n" +
                "• Java Runtime: %s | CPUs: %d núcleos\n" +
                "• Memoria JVM: %d MB en uso / %d MB reservados (Máx: %d MB)\n" +
                "• Arquitectura: Multi-canal reactivo con Token Bucket y Virtual Threads.",
                javaVer, cpus, usedMem, totalMem, maxMem);
    }

    private static String summarizeChannel(String channel, List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "📝 **Resumen de " + channel + "**: Aún no hay mensajes recientes registrados en el historial de este canal.";
        }

        List<Message> chatMessages = new ArrayList<>();
        for (Message m : history) {
            if (m.getType() == MessageType.CHAT && !BOT_NAME.equalsIgnoreCase(m.getSender())
                    && m.getContent() != null && !m.getContent().trim().isEmpty()) {
                chatMessages.add(m);
            }
        }

        if (chatMessages.isEmpty()) {
            return "📝 **Resumen de " + channel + "**: El canal solo contiene notificaciones del sistema o mensajes de bot.";
        }

        List<String> senders = new ArrayList<>();
        for (Message m : chatMessages) {
            if (m.getSender() != null && !senders.contains(m.getSender())) {
                senders.add(m.getSender());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📝 **Resumen de conversación en ").append(channel).append("**:\n");
        sb.append("• Mensajes analizados: ").append(chatMessages.size()).append(" de ").append(history.size()).append(" totales.\n");
        sb.append("• Participantes activos: ").append(String.join(", ", senders)).append(".\n");
        sb.append("• Puntos clave recientes:\n");

        int sampleCount = Math.min(3, chatMessages.size());
        for (int i = chatMessages.size() - sampleCount; i < chatMessages.size(); i++) {
            Message m = chatMessages.get(i);
            String rawContent = m.getContent() != null ? m.getContent().trim() : "";
            String snippet = rawContent.length() > 60 ? rawContent.substring(0, 57) + "..." : rawContent;
            sb.append("  - ").append(m.getSender()).append(": ").append(snippet).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Evaluador matemático seguro para expresiones aritméticas (+, -, *, /, %, ^, sqrt, abs, paréntesis).
     */
    public static String evaluateMath(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return "⚠️ Expresión matemática vacía.";
        }
        try {
            double result = parseMathExpression(expression.trim());
            return "🔢 " + expression.trim() + " = " + formatNumber(result);
        } catch (ArithmeticException ae) {
            return "⚠️ Error matemático: " + ae.getMessage() + ".";
        } catch (Exception e) {
            return "⚠️ No pude evaluar la expresión: '" + expression + "'. Formato sugerido: `@bot calc 2^10 + 50` o `@bot calc sqrt(144) + 8`";
        }
    }

    private static String formatNumber(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return String.valueOf(val);
        }
        if (val == (long) val) {
            return String.format(Locale.ROOT, "%d", (long) val);
        } else {
            return String.format(Locale.ROOT, "%.4f", val);
        }
    }

    public static double parseMathExpression(String str) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ' || ch == '\t') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                while (ch == ' ' || ch == '\t') nextChar();
                if (pos < str.length()) throw new IllegalArgumentException("Carácter inesperado: " + (char) ch);
                return x;
            }

            // expression = term (('+' | '-') term)*
            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            // term = factor (('*' | '/' | '%') factor)*
            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) {
                        double divisor = parseFactor();
                        if (divisor == 0.0) throw new ArithmeticException("División por cero no permitida");
                        x /= divisor;
                    } else if (eat('%')) {
                        double mod = parseFactor();
                        if (mod == 0.0) throw new ArithmeticException("Módulo por cero no permitido");
                        x %= mod;
                    } else return x;
                }
            }

            // factor = primary ('^' factor)? (asociatividad por la derecha)
            double parseFactor() {
                double x = parsePrimary();
                if (eat('^')) {
                    double exp = parseFactor();
                    return Math.pow(x, exp);
                }
                return x;
            }

            // primary = ('+' | '-') primary | '(' expression ')' | func '(' expression ')' | number
            double parsePrimary() {
                if (eat('+')) return +parsePrimary();
                if (eat('-')) return -parsePrimary();

                while (ch == ' ' || ch == '\t') nextChar();
                int startPos = this.pos;

                if (eat('(')) {
                    double x = parseExpression();
                    if (!eat(')')) throw new IllegalArgumentException("Paréntesis de cierre faltante");
                    return x;
                }

                if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    return Double.parseDouble(str.substring(startPos, this.pos).trim());
                }

                if (Character.isLetter(ch)) {
                    while (Character.isLetter(ch)) nextChar();
                    String func = str.substring(startPos, this.pos).toLowerCase(Locale.ROOT);
                    if (!eat('(')) throw new IllegalArgumentException("Se esperaba '(' tras la función " + func);
                    double arg = parseExpression();
                    if (!eat(')')) throw new IllegalArgumentException("Se esperaba ')' tras el argumento de " + func);

                    if ("sqrt".equals(func)) {
                        if (arg < 0) throw new ArithmeticException("Raíz cuadrada de número negativo");
                        return Math.sqrt(arg);
                    } else if ("abs".equals(func)) {
                        return Math.abs(arg);
                    } else {
                        throw new IllegalArgumentException("Función no reconocida: " + func);
                    }
                }

                throw new IllegalArgumentException("Factor no reconocido: " + (char) ch);
            }
        }.parse();
    }
}
