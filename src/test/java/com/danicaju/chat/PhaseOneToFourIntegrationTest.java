package com.danicaju.chat;

import com.danicaju.chat.client.ChatClientConnection;
import com.danicaju.chat.client.ChatEventListener;
import com.danicaju.chat.model.ChannelInfo;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;
import com.danicaju.chat.model.PollData;
import com.danicaju.chat.model.PresenceStatus;
import com.danicaju.chat.model.WebhookInfo;
import com.danicaju.chat.security.Permission;
import com.danicaju.chat.server.ChatServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Integración End-to-End Fases 1-4")
class PhaseOneToFourIntegrationTest {

    private ChatServer server;
    private Thread serverThread;
    private int tcpPort;
    private int httpPort;

    static class FullListener implements ChatEventListener {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        final List<Message> messages = new CopyOnWriteArrayList<>();
        final List<String> ackedTempIds = new CopyOnWriteArrayList<>();
        final List<Long> deletedIds = new CopyOnWriteArrayList<>();
        final List<String> reactionEvents = new CopyOnWriteArrayList<>();
        final List<String> presenceEvents = new CopyOnWriteArrayList<>();
        final List<List<Message>> historyPages = new CopyOnWriteArrayList<>();
        final List<List<String>> channelLists = new CopyOnWriteArrayList<>();
        final List<PollData> pollUpdates = new CopyOnWriteArrayList<>();
        final List<WebhookInfo> webhookList = new CopyOnWriteArrayList<>();
        final List<Message> searchResults = new CopyOnWriteArrayList<>();
        final List<Message> threadMessages = new CopyOnWriteArrayList<>();
        final List<List<Message>> afterIdPages = new CopyOnWriteArrayList<>();
        final CountDownLatch disconnectLatch = new CountDownLatch(1);
        final CountDownLatch reconnectLatch = new CountDownLatch(2);

        @Override
        public void onConnected(String username) {
            connectLatch.countDown();
            reconnectLatch.countDown();
        }

        @Override
        public void onMessageReceived(Message message) {
            messages.add(message);
        }

        @Override
        public void onUserListUpdated(List<String> users) {
        }

        @Override
        public void onDisconnected(String reason) {
            disconnectLatch.countDown();
        }

        @Override
        public void onError(String errorMessage) {
        }

        @Override
        public void onMessageAck(String tempId, Long id, String channel, long timestamp) {
            if (tempId != null) ackedTempIds.add(tempId + ":" + id);
        }

        @Override
        public void onMessageDeleted(Long messageId, String channel, String deletedBy) {
            if (messageId != null) deletedIds.add(messageId);
        }

        @Override
        public void onReactionUpdated(Long messageId, String channel, String sender, String emoji, boolean added, Map<String, List<String>> reactions) {
            reactionEvents.add(messageId + ":" + emoji + ":" + added + ":" + sender);
        }

        @Override
        public void onPresenceUpdated(String username, PresenceStatus status, String customStatus, String statusSymbol) {
            presenceEvents.add(username + ":" + status + ":" + customStatus);
        }

        @Override
        public void onHistoryPageReceived(String channel, List<Message> history, boolean hasMore, Long beforeId, Long afterId, Long threadParentId) {
            if (threadParentId != null) {
                threadMessages.addAll(history);
            } else if (afterId != null) {
                afterIdPages.add(history);
            } else {
                historyPages.add(history);
            }
        }

        @Override
        public void onChannelListUpdated(List<String> channels) {
            channelLists.add(channels);
        }

        @Override
        public void onPollUpdated(Long messageId, String channel, PollData poll) {
            if (poll != null) pollUpdates.add(poll);
        }

        @Override
        public void onWebhookListReceived(String channel, List<WebhookInfo> webhooks) {
            webhookList.clear();
            if (webhooks != null) webhookList.addAll(webhooks);
        }

        @Override
        public void onSearchResultsReceived(String query, List<Message> results) {
            searchResults.clear();
            if (results != null) searchResults.addAll(results);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        server = new ChatServer(0);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception ignored) {
            }
        });
        serverThread.start();
        assertTrue(server.awaitStarted(5, TimeUnit.SECONDS));
        tcpPort = server.getPort();
        httpPort = server.getWebhookPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) server.stop();
        if (serverThread != null) serverThread.join(2000);
    }

    @Test
    @DisplayName("Fase 1: ACK optimista, Hilos, Reacciones delta y Presencia DND")
    void testAckThreadsReactionsPresence() throws Exception {
        ChatClientConnection alice = new ChatClientConnection();
        FullListener lAlice = new FullListener();
        assertTrue(alice.connect("127.0.0.1", tcpPort, "alice", lAlice));
        assertTrue(lAlice.connectLatch.await(3, TimeUnit.SECONDS));

        ChatClientConnection bob = new ChatClientConnection();
        FullListener lBob = new FullListener();
        assertTrue(bob.connect("127.0.0.1", tcpPort, "bob", lBob));
        assertTrue(lBob.connectLatch.await(3, TimeUnit.SECONDS));

        // Optimistic send -> server ACK con ID real
        String tempId = alice.sendOptimisticOrQueue(Message.chat("#general", "alice", "Mensaje raíz"));
        waitUntil(() -> !lAlice.ackedTempIds.isEmpty());
        assertTrue(lAlice.ackedTempIds.get(0).startsWith(tempId + ":"));

        waitUntil(() -> lBob.messages.stream().anyMatch(m -> "Mensaje raíz".equals(m.getContent())));
        Long rootId = lBob.messages.stream().filter(m -> "Mensaje raíz".equals(m.getContent()))
                .findFirst().orElseThrow().getId();
        assertNotNull(rootId);

        // Hilo de respuesta
        Message threadMsg = Message.chat("#general", "bob", "Respuesta en hilo");
        threadMsg.setThreadParentId(rootId);
        bob.sendOptimisticOrQueue(threadMsg);

        // Reacción delta
        bob.sendReaction(rootId, "#general", ":fire:", true);
        waitUntil(() -> lAlice.reactionEvents.stream().anyMatch(s -> s.equals(rootId + "::fire::true:bob")));

        // Consulta de mensajes del hilo
        alice.requestThreadHistory("#general", rootId);
        waitUntil(() -> lAlice.threadMessages.stream().anyMatch(m -> "Respuesta en hilo".equals(m.getContent())));

        // Presencia DND con texto custom
        bob.updatePresence(PresenceStatus.DND, "En llamada", "[MIC]");
        waitUntil(() -> lAlice.presenceEvents.stream().anyMatch(e -> e.equals("bob:DND:En llamada")));

        alice.disconnect();
        bob.disconnect();
    }

    @Test
    @DisplayName("Fase 2 y 3: Canales CRUD, Bitfields, Webhooks REST, Polls y Búsqueda")
    void testChannelsPermissionsWebhooksPollsAndSearch() throws Exception {
        // Alice es la primera usuaria -> recibe ADMIN automáticamente
        ChatClientConnection alice = new ChatClientConnection();
        FullListener lAlice = new FullListener();
        assertTrue(alice.connect("127.0.0.1", tcpPort, "alice", lAlice));

        ChatClientConnection bob = new ChatClientConnection();
        FullListener lBob = new FullListener();
        assertTrue(bob.connect("127.0.0.1", tcpPort, "bob", lBob));

        // Alice crea canal #arquitectura
        alice.createChannel(new ChannelInfo("#arquitectura", ChannelInfo.TYPE_TEXT, "Diseño sistema", "alice"));
        waitUntil(() -> lBob.channelLists.stream().anyMatch(list -> list.contains("#arquitectura")));

        // Alice asigna READ_ONLY a Bob
        alice.updateRoleOrOverride("bob", Permission.ROLE_READ_ONLY, Permission.DEFAULT_READ_ONLY, null);
        Thread.sleep(200);

        // Bob intenta escribir en #arquitectura y recibe error de permiso SEND_MESSAGES
        bob.switchChannel("#arquitectura");
        Thread.sleep(150);
        bob.sendChatMessage("#arquitectura", "Intento sin permiso");
        waitUntil(() -> lBob.messages.stream().anyMatch(m -> m.getType() == MessageType.SYSTEM && m.getContent().contains("No tienes permiso")));

        // Alice crea encuesta interactiva en #general
        Message pollMsg = Message.chat("#general", "alice", "Encuesta");
        pollMsg.setPoll(new PollData("¿BD?", List.of("H2", "SQLite")));
        alice.sendOptimisticOrQueue(pollMsg);
        waitUntil(() -> lAlice.messages.stream().anyMatch(m -> m.getPoll() != null));
        Long pollId = lAlice.messages.stream().filter(m -> m.getPoll() != null).findFirst().orElseThrow().getId();

        alice.votePoll("#general", pollId, 0);
        waitUntil(() -> !lAlice.pollUpdates.isEmpty() && lAlice.pollUpdates.get(0).getVoteCount(0) == 1);

        // Alice crea Webhook en #general y dispara POST HTTP REST
        alice.createWebhook("#general", "DeployBot");
        waitUntil(() -> !lAlice.webhookList.isEmpty());
        WebhookInfo hook = lAlice.webhookList.get(0);

        HttpClient http = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + httpPort + "/api/webhooks/" + hook.getId() + "/" + hook.getToken()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"Build v4 https://github.com/danicaju/RealTimeChat\",\"title\":\"Release OK\"}"))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, resp.statusCode());

        waitUntil(() -> lAlice.messages.stream().anyMatch(m -> m.getEmbed() != null && "Release OK".equals(m.getEmbed().getTitle())));

        // Búsqueda avanzada has:link
        alice.searchMessages("in:general has:link Release");
        waitUntil(() -> lAlice.searchResults.stream().anyMatch(m -> m.getContent().contains("Build v4")));

        // Cursor afterId pagination check
        alice.requestOlderHistory("#general", Long.MAX_VALUE, 20);
        waitUntil(() -> !lAlice.historyPages.isEmpty());

        alice.disconnect();
        bob.disconnect();
    }

    @Test
    @DisplayName("Fase 4: Mensajes Directos E2EE (ECDH + AES-256-GCM) sin texto plano en BD")
    void testEndToEndEncryptedDirectMessages() throws Exception {
        ChatClientConnection alice = new ChatClientConnection();
        FullListener lAlice = new FullListener();
        assertTrue(alice.connect("127.0.0.1", tcpPort, "alice", lAlice));

        ChatClientConnection bob = new ChatClientConnection();
        FullListener lBob = new FullListener();
        assertTrue(bob.connect("127.0.0.1", tcpPort, "bob", lBob));

        // Esperar intercambio automático de claves públicas ECDH
        waitUntil(() -> alice.hasPeerPublicKey("bob") && bob.hasPeerPublicKey("alice"));

        String secretText = "Contraseña secreta E2EE #9921";
        assertTrue(alice.sendEncryptedPrivateMessage("bob", secretText));

        waitUntil(() -> lBob.messages.stream().anyMatch(m ->
                m.getType() == MessageType.PRIVATE &&
                Boolean.TRUE.equals(m.getEncrypted()) &&
                secretText.equals(m.getContent())));

        // Verificar que la base de datos del servidor NO almacena el texto claro
        List<Message> storedSearch = server.getChatRoom().getDatabase()
                .searchMessages("from:alice", java.util.Set.of("#general"), "alice");
        assertTrue(storedSearch.stream().noneMatch(m -> secretText.equals(m.getContent())));

        alice.disconnect();
        bob.disconnect();
    }

    @Test
    @DisplayName("Fase 2: Cola offline FIFO, cursor afterId y resincronización tras caída de socket")
    void testOfflineQueueAndAfterIdResync() throws Exception {
        ChatClientConnection alice = new ChatClientConnection();
        alice.setAutoReconnect(true);
        alice.setReconnectBaseDelayMs(450L);
        FullListener lAlice = new FullListener();
        assertTrue(alice.connect("127.0.0.1", tcpPort, "alice", lAlice));
        assertTrue(lAlice.connectLatch.await(3, TimeUnit.SECONDS));

        ChatClientConnection bob = new ChatClientConnection();
        FullListener lBob = new FullListener();
        assertTrue(bob.connect("127.0.0.1", tcpPort, "bob", lBob));
        assertTrue(lBob.connectLatch.await(3, TimeUnit.SECONDS));

        // Mensaje inicial conocido por Alice
        bob.sendChatMessage("#general", "Baseline before drop");
        waitUntil(() -> lAlice.messages.stream().anyMatch(m -> "Baseline before drop".equals(m.getContent())));
        Long baselineId = lAlice.messages.stream()
                .filter(m -> "Baseline before drop".equals(m.getContent()))
                .findFirst().orElseThrow().getId();
        assertNotNull(baselineId);

        // Simulamos caída inesperada del socket de Alice
        alice.simulateUnexpectedDrop();
        assertTrue(lAlice.disconnectLatch.await(3, TimeUnit.SECONDS));

        // Mientras Alice está offline: Bob envía un mensaje nuevo y Alice encola 2 mensajes locales
        bob.sendChatMessage("#general", "Missed while offline");
        waitUntil(() -> lBob.messages.stream().anyMatch(m -> "Missed while offline".equals(m.getContent())));

        alice.sendOptimisticOrQueue(Message.chat("#general", "alice", "Offline FIFO 1"));
        alice.sendOptimisticOrQueue(Message.chat("#general", "alice", "Offline FIFO 2"));
        assertEquals(2, alice.getOfflineQueueSize());

        // Reconexión automática debe resincronizar por afterId y vaciar la cola FIFO
        assertTrue(lAlice.reconnectLatch.await(5, TimeUnit.SECONDS));
        waitUntil(() -> alice.getOfflineQueueSize() == 0);
        waitUntil(() -> lAlice.afterIdPages.stream()
                .flatMap(List::stream)
                .anyMatch(m -> "Missed while offline".equals(m.getContent())));
        waitUntil(() -> lBob.messages.stream().anyMatch(m -> "Offline FIFO 1".equals(m.getContent()))
                && lBob.messages.stream().anyMatch(m -> "Offline FIFO 2".equals(m.getContent())));

        // También verificamos consulta explícita requestMessagesAfter(channel, baselineId, 50)
        int prevPages = lAlice.afterIdPages.size();
        alice.requestMessagesAfter("#general", baselineId, 50);
        waitUntil(() -> lAlice.afterIdPages.size() > prevPages);
        List<Message> latestAfter = lAlice.afterIdPages.get(lAlice.afterIdPages.size() - 1);
        assertTrue(latestAfter.stream().anyMatch(m -> "Missed while offline".equals(m.getContent())));
        assertTrue(latestAfter.stream().anyMatch(m -> "Offline FIFO 2".equals(m.getContent())));

        alice.disconnect();
        bob.disconnect();
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(75);
        }
        assertTrue(condition.getAsBoolean(), "La condición asíncrona no se cumplió a tiempo.");
    }
}
