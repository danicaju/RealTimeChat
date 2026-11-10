package com.danicaju.chat.bot;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias del Asistente Virtual Inteligente (RealTimeBot)")
class RealTimeBotTest {

    @ParameterizedTest
    @ValueSource(strings = {"@bot ayuda", "@ai que es un thread", "/bot status", "/ai calc 2+2", "@BOT RESUMEN"})
    @DisplayName("Detección precisa de comandos dirigidos al bot")
    void testBotDetection(String query) {
        assertTrue(RealTimeBot.isBotQuery(query));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hola a todos", "bot es genial", "alguien me ayuda", "http://ai.example.com"})
    @DisplayName("Ignorado de mensajes comunes no dirigidos al bot")
    void testNonBotDetection(String query) {
        assertFalse(RealTimeBot.isBotQuery(query));
        assertFalse(RealTimeBot.isBotQuery(null));
    }

    @Test
    @DisplayName("Generación de mensaje de ayuda y comandos")
    void testHelpGeneration() {
        String answer = RealTimeBot.generateAnswer("ayuda", "#general", List.of());
        assertTrue(answer.contains("RealTimeBot"));
        assertTrue(answer.contains("@bot resume"));
        assertTrue(answer.contains("@bot calc"));
    }

    @Test
    @DisplayName("Evaluación matemática precisa (potencias, raíces, aritmética básica)")
    void testMathEvaluation() {
        String power = RealTimeBot.evaluateMath("2^10");
        assertTrue(power.contains("1024"));

        // Prueba del ejemplo del bot (@bot calc 2^10 + 50)
        String powerPlus = RealTimeBot.evaluateMath("2^10 + 50");
        assertTrue(powerPlus.contains("1074"));

        String sqrt = RealTimeBot.evaluateMath("sqrt(144)");
        assertTrue(sqrt.contains("12"));

        String sqrtPlus = RealTimeBot.evaluateMath("sqrt(144) + 6");
        assertTrue(sqrtPlus.contains("18"));

        String arithmetic = RealTimeBot.evaluateMath("10 + 5 * 2");
        assertTrue(arithmetic.contains("20"));

        String division = RealTimeBot.evaluateMath("100 / 4");
        assertTrue(division.contains("25"));

        String parentheses = RealTimeBot.evaluateMath("(2 + 3) * 4");
        assertTrue(parentheses.contains("20"));

        String divZero = RealTimeBot.evaluateMath("10 / 0");
        assertTrue(divZero.contains("División por cero no permitida"));

        String negSqrt = RealTimeBot.evaluateMath("sqrt(-9)");
        assertTrue(negSqrt.contains("Raíz cuadrada de número negativo"));
    }

    @Test
    @DisplayName("Resumen inteligente de historial de canal")
    void testChannelSummary() {
        List<Message> history = new ArrayList<>();
        history.add(Message.chat("#ia-computacion", "alice", "Estamos discutiendo concurrencia en Java"));
        history.add(Message.chat("#ia-computacion", "bob", "Sí, los Virtual Threads reducen overhead"));

        String summary = RealTimeBot.generateAnswer("resume", "#ia-computacion", history);
        assertTrue(summary.contains("#ia-computacion"));
        assertTrue(summary.contains("alice"));
        assertTrue(summary.contains("bob"));
        assertTrue(summary.contains("Mensajes analizados"));

        // Resumen con historial vacío
        String emptySummary = RealTimeBot.generateAnswer("resume", "#random", List.of());
        assertTrue(emptySummary.contains("Aún no hay mensajes"));
    }

    @Test
    @DisplayName("Respuestas conceptuales de arquitectura de sistemas y concurrencia")
    void testTechnicalKnowledge() {
        String concurrency = RealTimeBot.generateAnswer("explícame la concurrencia y virtual threads", "#general", List.of());
        assertTrue(concurrency.contains("Virtual Threads"));

        String rateLimit = RealTimeBot.generateAnswer("como funciona el rate limiting token bucket", "#general", List.of());
        assertTrue(rateLimit.contains("Token Bucket"));

        String deadlock = RealTimeBot.generateAnswer("que es un deadlock", "#general", List.of());
        assertTrue(deadlock.contains("Coffman"));

        String tcp = RealTimeBot.generateAnswer("diferencia entre tcp y udp", "#general", List.of());
        assertTrue(tcp.contains("TCP"));
        assertTrue(tcp.contains("UDP"));
    }

    @Test
    @DisplayName("Empaquetado de respuesta en objeto Message")
    void testHandleMessage() {
        Message response = RealTimeBot.handleMessage("@bot info", "#general", List.of());
        assertNotNull(response);
        assertEquals(MessageType.CHAT, response.getType());
        assertEquals(RealTimeBot.BOT_NAME, response.getSender());
        assertEquals("#general", response.getChannel());
        assertTrue(response.getContent().contains("Java Runtime"));
    }
}
