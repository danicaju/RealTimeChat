package com.danicaju.chat.db;

import com.danicaju.chat.model.ChannelInfo;
import com.danicaju.chat.model.EmbedData;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.PollData;
import com.danicaju.chat.model.PresenceStatus;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.model.WebhookInfo;
import com.danicaju.chat.security.Permission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Persistencia H2, Cursores, Hilos, Polls, Webhooks y Búsqueda")
class DatabaseManagerTest {

    private DatabaseManager db;

    @BeforeEach
    void setUp() {
        db = DatabaseManager.createInMemory("test_" + UUID.randomUUID().toString().replace("-", ""));
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    @DisplayName("Paginación por cursores beforeId / afterId sin saltos ni OFFSET")
    void testCursorPagination() {
        for (int i = 1; i <= 8; i++) {
            db.insertMessage(Message.chat("#general", "alice", "Mensaje #" + i));
        }

        // Últimos 3 mensajes
        DatabaseManager.CursorPage latest = db.getMessagesCursor("#general", null, null, 3);
        assertEquals(3, latest.messages().size());
        assertTrue(latest.hasMore());
        assertEquals("Mensaje #6", latest.messages().get(0).getContent());
        assertEquals("Mensaje #8", latest.messages().get(2).getContent());

        // Cargar anteriores (beforeId = 6)
        long cursorBefore = latest.messages().get(0).getId();
        DatabaseManager.CursorPage older = db.getMessagesCursor("#general", cursorBefore, null, 3);
        assertEquals(3, older.messages().size());
        assertTrue(older.hasMore());
        assertEquals("Mensaje #3", older.messages().get(0).getContent());
        assertEquals("Mensaje #5", older.messages().get(2).getContent());

        // Cargar restantes (beforeId = 3)
        long cursorOldest = older.messages().get(0).getId();
        DatabaseManager.CursorPage earliest = db.getMessagesCursor("#general", cursorOldest, null, 3);
        assertEquals(2, earliest.messages().size());
        assertFalse(earliest.hasMore());
        assertEquals("Mensaje #1", earliest.messages().get(0).getContent());

        // Resincronización afterId = 5
        DatabaseManager.CursorPage missed = db.getMessagesCursor("#general", null, 5L, 10);
        assertEquals(3, missed.messages().size());
        assertEquals("Mensaje #6", missed.messages().get(0).getContent());
        assertEquals("Mensaje #8", missed.messages().get(2).getContent());
    }

    @Test
    @DisplayName("Hilos (Threads), conteo de respuestas y eliminación en cascada")
    void testThreadsAndDelete() {
        Message root = db.insertMessage(Message.chat("#general", "alice", "Hilo principal"));
        Message reply1 = Message.chat("#general", "bob", "Respuesta 1");
        reply1.setThreadParentId(root.getId());
        db.insertMessage(reply1);

        Message reply2 = Message.chat("#general", "carol", "Respuesta 2");
        reply2.setThreadParentId(root.getId());
        db.insertMessage(reply2);

        // El feed principal no debe mezclar respuestas de hilo pero sí incluir threadReplyCount
        DatabaseManager.CursorPage mainFeed = db.getMessagesCursor("#general", null, null, 20);
        assertEquals(1, mainFeed.messages().size());
        assertEquals(2, mainFeed.messages().get(0).getThreadReplyCount());

        List<Message> threadMsgs = db.getThreadMessages(root.getId());
        assertEquals(2, threadMsgs.size());

        assertTrue(db.deleteMessage(root.getId()));
        assertTrue(db.getThreadMessages(root.getId()).isEmpty());
    }

    @Test
    @DisplayName("Reacciones delta, Encuestas en tiempo real y Embeds")
    void testReactionsPollsAndEmbeds() {
        Message msg = Message.chat("#general", "alice", "Mira https://example.com");
        msg.setPoll(new PollData("¿Mejor lenguaje?", List.of("Java", "Rust", "Python")));
        db.insertMessage(msg);

        Map<String, List<String>> added = db.updateReaction(msg.getId(), ":rocket:", "bob", true);
        assertNotNull(added);
        assertEquals(List.of("bob"), added.get(":rocket:"));

        Map<String, List<String>> removed = db.updateReaction(msg.getId(), ":rocket:", "bob", false);
        assertTrue(removed.isEmpty());

        PollData voted = db.votePoll(msg.getId(), 0, "bob");
        assertEquals(1, voted.getVoteCount(0));
        // Cambio de voto en encuesta single choice
        PollData switched = db.votePoll(msg.getId(), 2, "bob");
        assertEquals(0, switched.getVoteCount(0));
        assertEquals(1, switched.getVoteCount(2));

        EmbedData embed = new EmbedData("https://example.com", "Example Domain", "Desc", null, "example.com");
        assertTrue(db.updateEmbed(msg.getId(), embed));
        Message reloaded = db.getMessageById(msg.getId());
        assertEquals("Example Domain", reloaded.getEmbed().getTitle());
    }

    @Test
    @DisplayName("Búsqueda avanzada con from:, in:, has:link, has:file")
    void testAdvancedSearch() {
        Message m1 = Message.chat("#general", "alice", "Revisa https://github.com/danicaju");
        Message m2 = Message.chat("#random", "bob", "Foto adjunta");
        m2.setAttachmentName("captura.png");
        m2.setAttachmentBase64("AAAA");
        Message m3 = Message.chat("#ia-computacion", "alice", "Redes neuronales");

        db.insertMessage(m1);
        db.insertMessage(m2);
        db.insertMessage(m3);

        Set<String> access = Set.of("#general", "#random", "#ia-computacion");

        List<Message> linkMatches = db.searchMessages("from:alice has:link", access, "alice");
        assertEquals(1, linkMatches.size());
        assertEquals(m1.getId(), linkMatches.get(0).getId());

        List<Message> fileMatches = db.searchMessages("in:random has:file", access, "alice");
        assertEquals(1, fileMatches.size());
        assertEquals(m2.getId(), fileMatches.get(0).getId());

        List<Message> textMatches = db.searchMessages("in:ia-computacion neuronales", access, "alice");
        assertEquals(1, textMatches.size());
        assertEquals(m3.getId(), textMatches.get(0).getId());
    }

    @Test
    @DisplayName("Perfiles persistentes, Canales con overrides y Webhooks")
    void testProfilesChannelsWebhooks() {
        UserProfile admin = new UserProfile("admin", "Admin del servidor", "cosmic", "#EB459E");
        admin.setRole(Permission.ROLE_ADMIN);
        admin.setPermissions(Permission.DEFAULT_ADMIN);
        admin.setPresence(PresenceStatus.DND);
        admin.setCustomStatus("Desplegando v4");
        db.upsertUserProfile(admin);

        UserProfile loaded = db.getUserProfile("ADMIN");
        assertNotNull(loaded);
        assertEquals(Permission.ROLE_ADMIN, loaded.getRole());
        assertEquals(PresenceStatus.DND, loaded.getPresence());
        assertEquals(1, db.countAdminUsers());

        ChannelInfo vip = new ChannelInfo("#vip", ChannelInfo.TYPE_TEXT, "Solo VIP", "admin");
        vip.setDefaultDenyMask(Permission.SEND_MESSAGES);
        db.saveChannel(vip);
        assertTrue(db.getAllChannels().stream().anyMatch(c -> "#vip".equals(c.getName())));

        WebhookInfo wh = db.createWebhook("#vip", "CI Bot", "admin");
        assertNotNull(db.validateWebhook(wh.getId(), wh.getToken()));
        assertNull(db.validateWebhook(wh.getId(), "wrong-token"));
        assertEquals(1, db.getWebhooksForChannel("#vip").size());
        assertTrue(db.deleteWebhook(wh.getId()));

        assertFalse(db.deleteChannel("#general"));
        assertTrue(db.deleteChannel("#vip"));
    }
}
