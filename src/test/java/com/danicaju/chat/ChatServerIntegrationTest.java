package com.danicaju.chat;

import com.danicaju.chat.client.ChatClientConnection;
import com.danicaju.chat.client.ChatEventListener;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.server.ChatServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Integración End-to-End Cliente-Servidor")
class ChatServerIntegrationTest {

    private int testPort;
    private ChatServer server;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws Exception {
        server = new ChatServer(0);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException ignored) {
            }
        }, "test-server-thread");
        serverThread.start();

        assertTrue(server.awaitStarted(5, TimeUnit.SECONDS), "El servidor debió iniciar y enlazar su puerto");
        testPort = server.getPort();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            serverThread.join(1000);
        }
    }

    static class RecordingListener implements ChatEventListener {
        final List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        final List<List<String>> userListUpdates = Collections.synchronizedList(new ArrayList<>());
        final List<List<String>> channelListUpdates = Collections.synchronizedList(new ArrayList<>());
        final List<UserProfile> profileResponses = Collections.synchronizedList(new ArrayList<>());
        final List<String> typingEvents = Collections.synchronizedList(new ArrayList<>());
        final List<Message> historyReceived = Collections.synchronizedList(new ArrayList<>());

        final CountDownLatch connectLatch = new CountDownLatch(1);
        final CountDownLatch errorLatch = new CountDownLatch(1);
        final CountDownLatch disconnectLatch = new CountDownLatch(1);
        final AtomicBoolean connected = new AtomicBoolean(false);
        String lastError = null;
        String disconnectReason = null;

        @Override
        public void onConnected(String username) {
            connected.set(true);
            connectLatch.countDown();
        }

        @Override
        public void onMessageReceived(Message message) {
            messages.add(message);
        }

        @Override
        public void onUserListUpdated(List<String> users) {
            userListUpdates.add(new ArrayList<>(users));
        }

        @Override
        public void onChannelListUpdated(List<String> channels) {
            channelListUpdates.add(new ArrayList<>(channels));
        }

        @Override
        public void onTypingUpdated(String sender, String channel, boolean isTyping) {
            typingEvents.add(sender + ":" + channel + ":" + isTyping);
        }

        @Override
        public void onHistoryReceived(String channel, List<Message> history) {
            historyReceived.addAll(history);
        }

        @Override
        public void onProfileReceived(UserProfile profile) {
            profileResponses.add(profile);
        }

        @Override
        public void onDisconnected(String reason) {
            connected.set(false);
            disconnectReason = reason;
            disconnectLatch.countDown();
        }

        @Override
        public void onError(String errorMessage) {
            lastError = errorMessage;
            errorLatch.countDown();
        }
    }

    @Test
    @DisplayName("Flujo completo: Conexión de usuarios, duplicidad, chat público, privado y desconexión")
    void testEndToEndChatFlow() throws Exception {
        ChatClientConnection client1 = new ChatClientConnection();
        RecordingListener listener1 = new RecordingListener();

        // 1. Conexión de Alice
        boolean c1Ok = client1.connect("127.0.0.1", testPort, "alice", listener1);
        assertTrue(c1Ok);
        assertTrue(listener1.connectLatch.await(3, TimeUnit.SECONDS));

        // 2. Conexión de Bob
        ChatClientConnection client2 = new ChatClientConnection();
        RecordingListener listener2 = new RecordingListener();
        boolean c2Ok = client2.connect("127.0.0.1", testPort, "bob", listener2);
        assertTrue(c2Ok);
        assertTrue(listener2.connectLatch.await(3, TimeUnit.SECONDS));

        // 3. Intento de conectar cliente duplicado como "alice"
        ChatClientConnection clientDuplicate = new ChatClientConnection();
        RecordingListener listenerDuplicate = new RecordingListener();
        boolean dupOk = clientDuplicate.connect("127.0.0.1", testPort, "alice", listenerDuplicate);
        assertFalse(dupOk);
        assertTrue(listenerDuplicate.errorLatch.await(3, TimeUnit.SECONDS));
        assertNotNull(listenerDuplicate.lastError);
        assertTrue(listenerDuplicate.lastError.contains("ya está en uso"));

        // 4. Envío de mensaje público desde Alice
        client1.sendChatMessage("¡Hola Bob!");
        boolean messageReceived = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            boolean found = listener2.messages.stream().anyMatch(m ->
                    m.getType() == MessageType.CHAT &&
                    "alice".equals(m.getSender()) &&
                    "¡Hola Bob!".equals(m.getContent()));
            if (found) {
                messageReceived = true;
                break;
            }
        }
        assertTrue(messageReceived, "Bob debió recibir el mensaje público de Alice");

        // 5. Envío de mensaje privado de Bob a Alice
        client2.sendPrivateMessage("alice", "Mensaje secreto para Alice");
        boolean privateReceived = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            boolean found = listener1.messages.stream().anyMatch(m ->
                    m.getType() == MessageType.PRIVATE &&
                    "bob".equals(m.getSender()) &&
                    "Mensaje secreto para Alice".equals(m.getContent()));
            if (found) {
                privateReceived = true;
                break;
            }
        }
        assertTrue(privateReceived, "Alice debió recibir el mensaje privado de Bob");

        // 6. Desconexión voluntaria de Bob y actualización reactiva
        client2.disconnect();
        assertTrue(listener2.disconnectLatch.await(3, TimeUnit.SECONDS));
        assertFalse(listener2.connected.get());

        boolean listUpdated = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            boolean onlyAlice = listener1.userListUpdates.stream().anyMatch(users ->
                    users.size() == 1 && users.contains("alice"));
            if (onlyAlice) {
                listUpdated = true;
                break;
            }
        }
        assertTrue(listUpdated, "Alice debió recibir la actualización con solo su usuario tras salir Bob");

        client1.disconnect();
        assertTrue(listener1.disconnectLatch.await(3, TimeUnit.SECONDS));
        assertFalse(listener1.connected.get());
    }

    @Test
    @DisplayName("Aislamiento de canales y entrega de búfer de reproducción (historial)")
    void testChannelsAndHistoryReplay() throws Exception {
        ChatClientConnection client1 = new ChatClientConnection();
        RecordingListener listener1 = new RecordingListener();
        assertTrue(client1.connect("127.0.0.1", testPort, "alice", listener1));
        assertTrue(listener1.connectLatch.await(3, TimeUnit.SECONDS));

        // Alice envía mensajes en #general
        client1.sendChatMessage("Historial 1");
        client1.sendChatMessage("Historial 2");
        Thread.sleep(200);

        // Bob se conecta e ingresa a #general; debe recibir el historial previo
        ChatClientConnection client2 = new ChatClientConnection();
        RecordingListener listener2 = new RecordingListener();
        assertTrue(client2.connect("127.0.0.1", testPort, "bob", listener2));
        assertTrue(listener2.connectLatch.await(3, TimeUnit.SECONDS));

        boolean foundHist = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            if (listener2.historyReceived.stream().anyMatch(m -> "Historial 1".equals(m.getContent()))) {
                foundHist = true;
                break;
            }
        }
        assertTrue(foundHist, "Bob debió recibir el historial previo de #general al conectarse");

        // Alice cambia a #ia-computacion
        client1.switchChannel("#ia-computacion");
        Thread.sleep(200);

        // Alice envía mensaje en #ia-computacion
        client1.sendChatMessage("Mensaje para IA");
        Thread.sleep(200);

        // Bob (que sigue en #general) NO debe recibir el mensaje del canal #ia-computacion
        boolean bobGotIaMsg = listener2.messages.stream().anyMatch(m -> "Mensaje para IA".equals(m.getContent()));
        assertFalse(bobGotIaMsg, "Bob no debería recibir mensajes de canales donde no está presente");

        client1.disconnect();
        client2.disconnect();
    }

    @Test
    @DisplayName("Detección de eventos de escritura en tiempo real (typing indicator)")
    void testTypingIndicatorIntegration() throws Exception {
        ChatClientConnection client1 = new ChatClientConnection();
        RecordingListener listener1 = new RecordingListener();
        assertTrue(client1.connect("127.0.0.1", testPort, "alice", listener1));

        ChatClientConnection client2 = new ChatClientConnection();
        RecordingListener listener2 = new RecordingListener();
        assertTrue(client2.connect("127.0.0.1", testPort, "bob", listener2));

        // Alice comienza a escribir
        client1.sendTyping(true);

        boolean typingStarted = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            if (listener2.typingEvents.contains("alice:#general:true")) {
                typingStarted = true;
                break;
            }
        }
        assertTrue(typingStarted, "Bob debió recibir el evento TYPING_START de Alice");

        // Alice deja de escribir
        client1.sendTyping(false);
        boolean typingStopped = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            if (listener2.typingEvents.contains("alice:#general:false")) {
                typingStopped = true;
                break;
            }
        }
        assertTrue(typingStopped, "Bob debió recibir el evento TYPING_STOP de Alice");

        client1.disconnect();
        client2.disconnect();
    }

    @Test
    @DisplayName("Personalización y consulta de perfiles de usuario")
    void testUserProfileCustomization() throws Exception {
        ChatClientConnection client1 = new ChatClientConnection();
        RecordingListener listener1 = new RecordingListener();
        assertTrue(client1.connect("127.0.0.1", testPort, "alice", listener1));

        ChatClientConnection client2 = new ChatClientConnection();
        RecordingListener listener2 = new RecordingListener();
        assertTrue(client2.connect("127.0.0.1", testPort, "bob", listener2));

        // Alice actualiza su biografía, avatar y banner
        UserProfile aliceProfile = new UserProfile("alice", "Arquitecta de Software Cloud", "synthwave", "gradient-cyber");
        client1.updateProfile(aliceProfile);
        Thread.sleep(200);

        // Bob consulta el perfil de Alice
        client2.requestProfile("alice");

        boolean profileReceived = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            boolean matched = listener2.profileResponses.stream().anyMatch(p ->
                    "alice".equalsIgnoreCase(p.getUsername()) &&
                    "synthwave".equals(p.getAvatar()) &&
                    "Arquitecta de Software Cloud".equals(p.getBio()));
            if (matched) {
                profileReceived = true;
                break;
            }
        }
        assertTrue(profileReceived, "Bob debió recibir el perfil actualizado de Alice");

        client1.disconnect();
        client2.disconnect();
    }

    @Test
    @DisplayName("Interacción con el asistente inteligente AI RealTimeBot (@bot calc)")
    void testRealTimeBotInteraction() throws Exception {
        ChatClientConnection client = new ChatClientConnection();
        RecordingListener listener = new RecordingListener();
        assertTrue(client.connect("127.0.0.1", testPort, "alice", listener));

        // Alice envía una consulta de cálculo al bot
        client.sendChatMessage("@bot calc 2^8");

        boolean botReplied = false;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            boolean found = listener.messages.stream().anyMatch(m ->
                    "RealTimeBot".equalsIgnoreCase(m.getSender()) &&
                    m.getContent().contains("256"));
            if (found) {
                botReplied = true;
                break;
            }
        }
        assertTrue(botReplied, "RealTimeBot debió responder calculando 2^8 = 256");

        client.disconnect();
    }

    @Test
    @DisplayName("Mitigación de flood mediante Rate Limiting (Token Bucket)")
    void testRateLimiterMitigation() throws Exception {
        ChatClientConnection client = new ChatClientConnection();
        RecordingListener listener = new RecordingListener();
        assertTrue(client.connect("127.0.0.1", testPort, "spammer", listener));

        // Enviar ráfaga masiva de mensajes para saturar el bucket
        for (int i = 0; i < 15; i++) {
            client.sendChatMessage("Spam mensaje #" + i);
        }

        boolean rateLimitWarningReceived = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            boolean found = listener.messages.stream().anyMatch(m ->
                    m.getType() == MessageType.SYSTEM &&
                    m.getContent().contains("[RATE LIMIT]"));
            if (found) {
                rateLimitWarningReceived = true;
                break;
            }
        }
        assertTrue(rateLimitWarningReceived, "El cliente debió recibir aviso de RATE LIMIT al superar la capacidad del bucket");

        client.disconnect();
    }

    @Test
    @DisplayName("Colisión de apodos insensible a mayúsculas y protocolo de handshake por red real")
    void testCaseInsensitiveCollisionAndHandshakeValidation() throws Exception {
        ChatClientConnection clientA = new ChatClientConnection();
        RecordingListener listenerA = new RecordingListener();
        assertTrue(clientA.connect("127.0.0.1", testPort, "David", listenerA));

        ChatClientConnection clientB = new ChatClientConnection();
        RecordingListener listenerB = new RecordingListener();
        boolean rejected = clientB.connect("127.0.0.1", testPort, "david", listenerB);
        assertFalse(rejected);
        assertTrue(listenerB.errorLatch.await(3, TimeUnit.SECONDS));
        assertTrue(listenerB.lastError.contains("ya está en uso"));

        clientA.disconnect();
        Thread.sleep(150);

        ChatClientConnection clientC = new ChatClientConnection();
        RecordingListener listenerC = new RecordingListener();
        assertTrue(clientC.connect("127.0.0.1", testPort, "david", listenerC));
        clientC.disconnect();
    }

    @Test
    @DisplayName("Rechazo de mensaje inválido sin handshake previo")
    void testHandshakeProtocolEnforcement() throws Exception {
        try (java.net.Socket rawSocket = new java.net.Socket("127.0.0.1", testPort);
             java.io.PrintWriter pw = new java.io.PrintWriter(rawSocket.getOutputStream(), true);
             java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(rawSocket.getInputStream()))) {

            pw.println("{\"type\":\"CHAT\",\"content\":\"intento prematuro\"}");
            String response = br.readLine();
            assertNotNull(response);
            assertTrue(response.contains("ERROR: Debes identificarte con un mensaje JOIN antes de interactuar."));
        }
    }

    @Test
    @DisplayName("Aislamiento estricto de notificaciones de cambio de canal entre salas")
    void testChannelSwitchMessageIsolation() throws Exception {
        ChatClientConnection clientAlice = new ChatClientConnection();
        RecordingListener listenerAlice = new RecordingListener();
        assertTrue(clientAlice.connect("127.0.0.1", testPort, "alice", listenerAlice));

        ChatClientConnection clientBob = new ChatClientConnection();
        RecordingListener listenerBob = new RecordingListener();
        assertTrue(clientBob.connect("127.0.0.1", testPort, "bob", listenerBob));

        // Bob se mueve primero a #random
        clientBob.switchChannel("#random");
        Thread.sleep(200);

        // Limpiar mensajes acumulados de Alice
        listenerAlice.messages.clear();

        // Bob ahora cambia de #random a #ia-computacion
        clientBob.switchChannel("#ia-computacion");
        Thread.sleep(200);

        // Alice (en #general) NO debió recibir la notificación de que Bob entró a #ia-computacion ni que salió de #random
        boolean leakedNotification = listenerAlice.messages.stream().anyMatch(m ->
                m.getContent() != null && (m.getContent().contains("#ia-computacion") || m.getContent().contains("ha cambiado de canal")));
        assertFalse(leakedNotification, "Alice en #general no debe recibir avisos de canales donde no pertenece");

        clientAlice.disconnect();
        clientBob.disconnect();
    }

    @Test
    @DisplayName("Difusión reactiva de actualización de perfil hacia otros clientes")
    void testProfileReactiveBroadcast() throws Exception {
        ChatClientConnection clientAlice = new ChatClientConnection();
        RecordingListener listenerAlice = new RecordingListener();
        assertTrue(clientAlice.connect("127.0.0.1", testPort, "alice", listenerAlice));

        ChatClientConnection clientBob = new ChatClientConnection();
        RecordingListener listenerBob = new RecordingListener();
        assertTrue(clientBob.connect("127.0.0.1", testPort, "bob", listenerBob));

        // Alice actualiza su perfil
        UserProfile newProfile = new UserProfile("alice", "Especialista en IA & Redes", "cosmic", "gradient-cosmic");
        clientAlice.updateProfile(newProfile);

        // Bob debió recibir la actualización reactiva sin haberla solicitado explícitamente
        boolean bobGotBroadcast = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            if (listenerBob.profileResponses.stream().anyMatch(p ->
                    "alice".equalsIgnoreCase(p.getUsername()) &&
                    "cosmic".equals(p.getAvatar()) &&
                    "gradient-cosmic".equals(p.getBanner()))) {
                bobGotBroadcast = true;
                break;
            }
        }
        assertTrue(bobGotBroadcast, "Bob debió recibir la actualización reactiva del perfil de Alice");

        clientAlice.disconnect();
        clientBob.disconnect();
    }

    @Test
    @DisplayName("Re-unión al mismo canal devuelve confirmación e historial sin desconfigurar al cliente")
    void testRejoiningCurrentChannel() throws Exception {
        ChatClientConnection client = new ChatClientConnection();
        RecordingListener listener = new RecordingListener();
        assertTrue(client.connect("127.0.0.1", testPort, "alice", listener));

        client.sendChatMessage("Mensaje de prueba");
        Thread.sleep(150);

        // Limpiar listas del listener
        listener.historyReceived.clear();

        // Alice solicita unirse nuevamente a #general
        client.switchChannel("#general");
        Thread.sleep(200);

        // Debió recibir el historial de nuevo
        boolean gotHistory = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            if (listener.historyReceived.stream().anyMatch(m -> "Mensaje de prueba".equals(m.getContent()))) {
                gotHistory = true;
                break;
            }
        }
        assertTrue(gotHistory, "El cliente debe recibir el historial de re-unión al mismo canal");

        client.disconnect();
    }
}
