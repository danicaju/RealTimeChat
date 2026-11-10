package com.danicaju.chat.server;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de ChatRoom, Canales, Historial y Perfiles")
class ChatRoomTest {

    private ChatRoom chatRoom;

    static class FakeClientHandler extends ClientHandler {
        final List<String> receivedMessages = new ArrayList<>();
        boolean wasClosed = false;

        public FakeClientHandler(ChatRoom room) {
            super(null, room);
        }

        @Override
        public synchronized void sendMessageRaw(String json) {
            receivedMessages.add(json);
        }

        @Override
        public void close() {
            wasClosed = true;
        }
    }

    @BeforeEach
    void setUp() {
        chatRoom = new ChatRoom();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dani", "usuario_123", "User-Name", "Admin.01", "a1", "12345678901234567890"})
    @DisplayName("Validación exitosa de nombres de usuario válidos")
    void testValidUsernames(String username) {
        assertTrue(chatRoom.isValidUsername(username));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a", "usuario con espacios", "nombre#invalido", "user@domain", "este_nombre_es_demasiado_largo_para_el_limite"})
    @DisplayName("Rechazo de nombres de usuario inválidos")
    void testInvalidUsernames(String username) {
        assertFalse(chatRoom.isValidUsername(username));
        assertFalse(chatRoom.isValidUsername(null));
    }

    @Test
    @DisplayName("Registro exitoso de usuario en la sala")
    void testRegisterClientSuccess() {
        FakeClientHandler handler = new FakeClientHandler(chatRoom);
        boolean registered = chatRoom.registerClient("alice", handler);

        assertTrue(registered);
        assertTrue(chatRoom.isUsernameTaken("alice"));
        assertEquals(1, chatRoom.getUserCount());
        assertEquals(List.of("alice"), chatRoom.getActiveUsers());
        assertEquals(ChatRoom.DEFAULT_CHANNEL, chatRoom.getUserChannel("alice"));
    }

    @Test
    @DisplayName("Prevención de nombres de usuario duplicados")
    void testDuplicateUsernameRejection() {
        FakeClientHandler handler1 = new FakeClientHandler(chatRoom);
        FakeClientHandler handler2 = new FakeClientHandler(chatRoom);

        assertTrue(chatRoom.registerClient("alice", handler1));
        assertFalse(chatRoom.registerClient("alice", handler2));
        assertEquals(1, chatRoom.getUserCount());
    }

    @Test
    @DisplayName("Rechazo de registro con handler nulo o nombre inválido")
    void testRegisterInvalidInputs() {
        FakeClientHandler handler = new FakeClientHandler(chatRoom);
        assertFalse(chatRoom.registerClient(null, handler));
        assertFalse(chatRoom.registerClient("inv@lid", handler));
        assertFalse(chatRoom.registerClient("validName", null));
        assertEquals(0, chatRoom.getUserCount());
    }

    @Test
    @DisplayName("Desregistro correcto de usuario")
    void testUnregisterClient() {
        FakeClientHandler handler = new FakeClientHandler(chatRoom);
        chatRoom.registerClient("alice", handler);
        assertEquals(1, chatRoom.getUserCount());

        boolean removed = chatRoom.unregisterClient("alice", handler);
        assertTrue(removed);
        assertFalse(chatRoom.isUsernameTaken("alice"));
        assertEquals(0, chatRoom.getUserCount());

        assertFalse(chatRoom.unregisterClient("alice", handler));
    }

    @Test
    @DisplayName("Obtención de usuarios ordenados alfabéticamente (insensible a mayúsculas)")
    void testSortedActiveUsers() {
        FakeClientHandler h1 = new FakeClientHandler(chatRoom);
        FakeClientHandler h2 = new FakeClientHandler(chatRoom);
        FakeClientHandler h3 = new FakeClientHandler(chatRoom);

        chatRoom.registerClient("Charlie", h1);
        chatRoom.registerClient("alice", h2);
        chatRoom.registerClient("Bob", h3);

        List<String> users = chatRoom.getActiveUsers();
        assertEquals(List.of("alice", "Bob", "Charlie"), users);
    }

    @Test
    @DisplayName("Envío de mensaje privado a un usuario existente y a uno inexistente")
    void testSendPrivateMessage() {
        FakeClientHandler alice = new FakeClientHandler(chatRoom);
        FakeClientHandler bob = new FakeClientHandler(chatRoom);

        chatRoom.registerClient("alice", alice);
        chatRoom.registerClient("bob", bob);

        Message msg = Message.privateMsg("alice", "bob", "Hola secreto");
        boolean delivered = chatRoom.sendPrivate("bob", msg);

        assertTrue(delivered);
        assertEquals(1, bob.receivedMessages.size());
        assertTrue(bob.receivedMessages.get(0).contains("Hola secreto"));
        assertEquals(0, alice.receivedMessages.size());

        boolean failed = chatRoom.sendPrivate("carlos_fantasma", msg);
        assertFalse(failed);
    }

    @Test
    @DisplayName("Broadcast a todos los usuarios con y sin exclusión de remitente")
    void testBroadcast() {
        FakeClientHandler alice = new FakeClientHandler(chatRoom);
        FakeClientHandler bob = new FakeClientHandler(chatRoom);
        FakeClientHandler charlie = new FakeClientHandler(chatRoom);

        chatRoom.registerClient("alice", alice);
        chatRoom.registerClient("bob", bob);
        chatRoom.registerClient("charlie", charlie);

        Message msg1 = Message.chat("alice", "Mensaje público");
        chatRoom.broadcast(msg1, alice);

        assertEquals(0, alice.receivedMessages.size());
        assertEquals(1, bob.receivedMessages.size());
        assertEquals(1, charlie.receivedMessages.size());

        Message msg2 = Message.system("Aviso general");
        chatRoom.broadcast(msg2, null);

        assertEquals(1, alice.receivedMessages.size());
        assertEquals(2, bob.receivedMessages.size());
        assertEquals(2, charlie.receivedMessages.size());
    }

    @Test
    @DisplayName("Gestión de múltiples canales y cambio de canal de usuarios")
    void testChannelsAndSwitching() {
        FakeClientHandler alice = new FakeClientHandler(chatRoom);
        FakeClientHandler bob = new FakeClientHandler(chatRoom);

        chatRoom.registerClient("alice", alice);
        chatRoom.registerClient("bob", bob);

        // Por defecto ambos están en #general
        assertEquals("#general", chatRoom.getUserChannel("alice"));
        assertEquals(List.of("alice", "bob"), chatRoom.getActiveUsersInChannel("#general"));

        // Bob cambia a #ia-computacion
        String switched = chatRoom.switchUserChannel("bob", "#ia-computacion");
        assertEquals("#ia-computacion", switched);
        assertEquals("#ia-computacion", chatRoom.getUserChannel("bob"));
        assertEquals(List.of("alice"), chatRoom.getActiveUsersInChannel("#general"));
        assertEquals(List.of("bob"), chatRoom.getActiveUsersInChannel("#ia-computacion"));

        // Mensaje enviado a #ia-computacion solo llega a Bob
        Message iaMsg = Message.chat("#ia-computacion", "bob", "Hola IA");
        chatRoom.broadcast(iaMsg, null);

        assertTrue(bob.receivedMessages.stream().anyMatch(m -> m.contains("Hola IA")));
        assertFalse(alice.receivedMessages.stream().anyMatch(m -> m.contains("Hola IA")));
    }

    @Test
    @DisplayName("Búfer de reproducción (historial) acotado a MAX_HISTORY_PER_CHANNEL")
    void testChannelHistoryBuffer() {
        String channel = "#general";
        for (int i = 0; i < 60; i++) {
            chatRoom.recordChannelMessage(channel, Message.chat(channel, "user", "Mensaje " + i));
        }

        List<Message> history = chatRoom.getChannelHistory(channel);
        assertEquals(ChatRoom.MAX_HISTORY_PER_CHANNEL, history.size());
        assertEquals("Mensaje 10", history.get(0).getContent());
        assertEquals("Mensaje 59", history.get(history.size() - 1).getContent());
    }

    @Test
    @DisplayName("Almacenamiento y actualización de perfiles de usuario")
    void testUserProfiles() {
        FakeClientHandler alice = new FakeClientHandler(chatRoom);
        chatRoom.registerClient("alice", alice);

        UserProfile defaultProf = chatRoom.getUserProfile("alice");
        assertNotNull(defaultProf);
        assertEquals("alice", defaultProf.getUsername());

        UserProfile updatedProf = new UserProfile("alice", "Especialista en IA y Redes", "synthwave", "gradient-sunset");
        chatRoom.updateUserProfile("alice", updatedProf);

        UserProfile retrieved = chatRoom.getUserProfile("alice");
        assertEquals("Especialista en IA y Redes", retrieved.getBio());
        assertEquals("synthwave", retrieved.getAvatar());
        assertEquals("gradient-sunset", retrieved.getBanner());
    }

    @Test
    @DisplayName("Difusión de eventos de escritura (typing indicator)")
    void testBroadcastTyping() {
        FakeClientHandler alice = new FakeClientHandler(chatRoom);
        FakeClientHandler bob = new FakeClientHandler(chatRoom);

        chatRoom.registerClient("alice", alice);
        chatRoom.registerClient("bob", bob);

        chatRoom.broadcastTyping("#general", "alice", true, alice);

        assertEquals(0, alice.receivedMessages.size());
        assertEquals(1, bob.receivedMessages.size());
        assertTrue(bob.receivedMessages.get(0).contains("\"type\":\"TYPING_START\""));
        assertTrue(bob.receivedMessages.get(0).contains("\"sender\":\"alice\""));
    }

    @Test
    @DisplayName("Prevención de apodos duplicados con diferente uso de mayúsculas/minúsculas")
    void testCaseInsensitiveDuplicateRejection() {
        FakeClientHandler h1 = new FakeClientHandler(chatRoom);
        FakeClientHandler h2 = new FakeClientHandler(chatRoom);
        FakeClientHandler h3 = new FakeClientHandler(chatRoom);

        assertTrue(chatRoom.registerClient("Alice", h1));
        assertFalse(chatRoom.registerClient("alice", h2), "Debe rechazar 'alice' si 'Alice' ya existe");
        assertFalse(chatRoom.registerClient("ALICE", h3), "Debe rechazar 'ALICE' si 'Alice' ya existe");

        assertTrue(chatRoom.isUsernameTaken("alice"));
        assertTrue(chatRoom.isUsernameTaken("ALICE"));
        assertTrue(chatRoom.isUsernameTaken("Alice"));
        assertEquals(1, chatRoom.getUserCount());
        assertEquals(List.of("Alice"), chatRoom.getActiveUsers());
    }

    @Test
    @DisplayName("Enrutamiento privado insensible a mayúsculas/minúsculas")
    void testCaseInsensitivePrivateMessageDelivery() {
        FakeClientHandler alice = new FakeClientHandler(chatRoom);
        FakeClientHandler bob = new FakeClientHandler(chatRoom);

        chatRoom.registerClient("Alice", alice);
        chatRoom.registerClient("Bob", bob);

        Message msg = Message.privateMsg("Bob", "alice", "Hola confidencial");
        boolean delivered = chatRoom.sendPrivate("alice", msg);

        assertTrue(delivered);
        assertEquals(1, alice.receivedMessages.size());
        assertTrue(alice.receivedMessages.get(0).contains("Hola confidencial"));
    }

    @Test
    @DisplayName("Desregistro de usuario insensible a mayúsculas/minúsculas")
    void testCaseInsensitiveUnregister() {
        FakeClientHandler h1 = new FakeClientHandler(chatRoom);
        chatRoom.registerClient("Alice", h1);
        assertEquals(1, chatRoom.getUserCount());

        boolean removed = chatRoom.unregisterClient("alice", h1);
        assertTrue(removed);
        assertFalse(chatRoom.isUsernameTaken("Alice"));
        assertFalse(chatRoom.isUsernameTaken("alice"));
        assertEquals(0, chatRoom.getUserCount());
    }

    @Test
    @DisplayName("Estrés de alta concurrencia: múltiples hilos intentando registrar el mismo nickname a la vez")
    void testConcurrentRegistrationContention() throws InterruptedException {
        int threadsCount = 10;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threadsCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch endLatch = new java.util.concurrent.CountDownLatch(threadsCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threadsCount; i++) {
            FakeClientHandler handler = new FakeClientHandler(chatRoom);
            pool.submit(() -> {
                try {
                    startLatch.await();
                    if (chatRoom.registerClient("contender_user", handler)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(1, chatRoom.getUserCount());
    }

    @Test
    @DisplayName("Cierre de todos los clientes en apagado de la sala (closeAll)")
    void testCloseAll() {
        FakeClientHandler h1 = new FakeClientHandler(chatRoom);
        FakeClientHandler h2 = new FakeClientHandler(chatRoom);

        chatRoom.registerClient("user1", h1);
        chatRoom.registerClient("user2", h2);

        chatRoom.closeAll();

        assertTrue(h1.wasClosed);
        assertTrue(h2.wasClosed);
        assertEquals(0, chatRoom.getUserCount());
    }

    @Test
    @DisplayName("Tolerancia y robustez ante nulos en operaciones de sala")
    void testNullTolerances() {
        assertDoesNotThrow(() -> chatRoom.broadcast(null, null));
        assertFalse(chatRoom.sendPrivate(null, Message.chat("a", "b")));
        assertFalse(chatRoom.sendPrivate("alice", null));
        assertFalse(chatRoom.isUsernameTaken(null));
        assertFalse(chatRoom.unregisterClient(null, null));
    }

    @Test
    @DisplayName("Validación y saneamiento de nombres de canal")
    void testChannelNameValidation() {
        assertTrue(ChatRoom.isValidChannelName("#general"));
        assertTrue(ChatRoom.isValidChannelName("ia-computacion"));
        assertTrue(ChatRoom.isValidChannelName("#gaming_room"));
        assertTrue(ChatRoom.isValidChannelName("c1"));

        assertFalse(ChatRoom.isValidChannelName("#"));
        assertFalse(ChatRoom.isValidChannelName(""));
        assertFalse(ChatRoom.isValidChannelName("   "));
        assertFalse(ChatRoom.isValidChannelName(null));
        assertFalse(ChatRoom.isValidChannelName("canal con espacios"));
        assertFalse(ChatRoom.isValidChannelName("#canal@invalido!"));
        assertFalse(ChatRoom.isValidChannelName("#un_nombre_de_canal_que_supera_los_treinta_caracteres_permitidos"));

        assertEquals("#general", ChatRoom.normalizeChannel(null));
        assertEquals("#general", ChatRoom.normalizeChannel("   "));
        assertEquals("#general", ChatRoom.normalizeChannel("#"));
        assertEquals("#general", ChatRoom.normalizeChannel("invalid space"));
        assertEquals("#gaming", ChatRoom.normalizeChannel("gaming"));
        assertEquals("#gaming", ChatRoom.normalizeChannel("#gaming"));
    }

    @Test
    @DisplayName("Rechazo de registro de canales con nombres inválidos")
    void testInvalidChannelRegistration() {
        int initialCount = chatRoom.getAvailableChannels().size();
        chatRoom.registerChannel("#");
        chatRoom.registerChannel("invalid with spaces");
        chatRoom.registerChannel(null);
        assertEquals(initialCount, chatRoom.getAvailableChannels().size());

        chatRoom.registerChannel("#nuevo-canal");
        assertTrue(chatRoom.getAvailableChannels().contains("#nuevo-canal"));
    }
}
