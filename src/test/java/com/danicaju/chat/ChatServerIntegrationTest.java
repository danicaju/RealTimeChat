package com.danicaju.chat;

import com.danicaju.chat.client.ChatClientConnection;
import com.danicaju.chat.client.ChatEventListener;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;
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
    void setUp() throws IOException, InterruptedException {
        // Encontrar un puerto efímero libre
        try (ServerSocket s = new ServerSocket(0)) {
            testPort = s.getLocalPort();
        }

        server = new ChatServer(testPort);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException ignored) {
            }
        }, "test-server-thread");
        serverThread.start();

        // Esperar brevemente a que el servidor esté listo
        Thread.sleep(150);
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
        final CountDownLatch connectLatch = new CountDownLatch(1);
        final CountDownLatch errorLatch = new CountDownLatch(1);
        final AtomicBoolean connected = new AtomicBoolean(false);
        String lastError = null;

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
        public void onDisconnected(String reason) {
            connected.set(false);
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
        // Esperar a que Bob reciba el mensaje público
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

        // 6. Desconexión de Bob y actualización de lista de usuarios en Alice
        client2.disconnect();
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
    }
}
