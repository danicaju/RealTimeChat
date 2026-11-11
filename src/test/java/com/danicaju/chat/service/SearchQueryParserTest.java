package com.danicaju.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas del Parser de Búsqueda Avanzada")
class SearchQueryParserTest {

    @Test
    @DisplayName("Parseo completo de modificadores from:, in:, has:link, has:file y texto")
    void testParseModifiers() {
        SearchQueryParser p = SearchQueryParser.parse("from:alice in:ia-computacion has:link has:file \"arquitectura limpia\" java");
        assertEquals("alice", p.fromUser());
        assertEquals("#ia-computacion", p.inChannel());
        assertTrue(p.hasLink());
        assertTrue(p.hasFile());
        assertEquals(List.of("arquitectura limpia", "java"), p.textTerms());
    }
}
