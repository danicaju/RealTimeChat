package com.danicaju.chat.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias del Perfil de Usuario (UserProfile)")
class UserProfileTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Inicialización con valores por defecto")
    void testDefaults() {
        UserProfile profile = new UserProfile("alice");
        assertEquals("alice", profile.getUsername());
        assertEquals(UserProfile.DEFAULT_BIO, profile.getBio());
        assertEquals(UserProfile.DEFAULT_AVATAR, profile.getAvatar());
        assertEquals(UserProfile.DEFAULT_BANNER, profile.getBanner());
    }

    @Test
    @DisplayName("Saneamiento de biografía y recorte al límite máximo")
    void testSanitizeBio() {
        assertEquals(UserProfile.DEFAULT_BIO, UserProfile.sanitizeBio(null));
        assertEquals(UserProfile.DEFAULT_BIO, UserProfile.sanitizeBio("   "));

        String longBio = "A".repeat(400);
        String sanitized = UserProfile.sanitizeBio(longBio);
        assertEquals(UserProfile.MAX_BIO_LENGTH, sanitized.length());
    }

    @Test
    @DisplayName("Serialización y deserialización JSON limpia")
    void testJsonSerialization() throws IOException {
        UserProfile original = new UserProfile("bob", "Desarrollador Java & Concurrencia", "cosmic", "#57F287");
        String json = mapper.writeValueAsString(original);

        assertNotNull(json);
        assertTrue(json.contains("\"username\":\"bob\""));
        assertTrue(json.contains("\"avatar\":\"cosmic\""));
        assertTrue(json.contains("\"banner\":\"#57F287\""));

        UserProfile parsed = mapper.readValue(json, UserProfile.class);
        assertEquals(original, parsed);
        assertEquals(original.hashCode(), parsed.hashCode());
    }

    @Test
    @DisplayName("Verificación de equals y toString")
    void testEqualsAndToString() {
        UserProfile p1 = new UserProfile("charlie", "Bio 1", "cyber-neon", "#5865F2");
        UserProfile p2 = new UserProfile("charlie", "Bio 1", "cyber-neon", "#5865F2");
        UserProfile p3 = new UserProfile("dave", "Bio 1", "cyber-neon", "#5865F2");

        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
        assertTrue(p1.toString().contains("charlie"));
    }
}
