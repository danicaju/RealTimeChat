package com.danicaju.chat.service;

import com.danicaju.chat.model.PollData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de SlashCommandHelper (/poll, /roll, /shrug, autocompletado)")
class SlashCommandHelperTest {

    @Test
    @DisplayName("Autocompletado de comandos slash")
    void testAutocomplete() {
        List<SlashCommandHelper.CommandSpec> po = SlashCommandHelper.autocomplete("/po");
        assertEquals(1, po.size());
        assertEquals("/poll", po.get(0).name());

        assertFalse(SlashCommandHelper.autocomplete("/").isEmpty());
        assertTrue(SlashCommandHelper.autocomplete("hola").isEmpty());
    }

    @Test
    @DisplayName("Parseo de /poll con separador pipe y comillas")
    void testParsePoll() {
        PollData p1 = SlashCommandHelper.parsePoll("/poll ¿Lenguaje favorito? | Java | Python | Rust");
        assertNotNull(p1);
        assertEquals("¿Lenguaje favorito?", p1.getQuestion());
        assertEquals(List.of("Java", "Python", "Rust"), p1.getOptions());

        PollData p2 = SlashCommandHelper.parsePoll("/poll \"Hora de reunión\" \"10:00\" \"16:00\"");
        assertNotNull(p2);
        assertEquals("Hora de reunión", p2.getQuestion());
        assertEquals(List.of("10:00", "16:00"), p2.getOptions());

        assertNull(SlashCommandHelper.parsePoll("/poll Solo pregunta | Una opcion"));
    }

    @Test
    @DisplayName("Evaluación de /roll y /shrug")
    void testRollAndShrug() {
        String roll = SlashCommandHelper.evaluateRoll("/roll 3d6", "alice");
        assertTrue(roll.contains("alice"));
        assertTrue(roll.contains("3d6"));

        assertEquals("Todo bien ¯\\_(ツ)_/¯", SlashCommandHelper.evaluateShrug("/shrug Todo bien"));
    }
}
