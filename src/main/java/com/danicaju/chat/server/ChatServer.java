package com.danicaju.chat.server;

import com.danicaju.chat.db.DatabaseManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Servidor multihilo de alta concurrencia para RealTimeChat NextGen.
 * Integra Virtual Threads (Java 21+), base de datos embebida H2 SQL y servidor HTTP para Webhooks.
 */
public class ChatServer {

    public static final int DEFAULT_PORT = 5000;

    private final int port;
    private final ChatRoom chatRoom;
    private final ExecutorService threadPool;
    private ServerSocket serverSocket;
    private WebhookHttpServer webhookServer;
    private Thread shutdownHookThread;
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private volatile boolean running = false;

    public ChatServer(int port) {
        this(port, port == 0 ? new ChatRoom() : new ChatRoom(DatabaseManager.createDefaultFileDatabase()));
    }

    public ChatServer(int port, ChatRoom chatRoom) {
        this.port = port;
        this.chatRoom = chatRoom;
        this.threadPool = createOptimalThreadPool();
    }

    public boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
        return startLatch.await(timeout, unit);
    }

    public static ExecutorService createOptimalThreadPool() {
        try {
            return Executors.newVirtualThreadPerTaskExecutor();
        } catch (NoSuchMethodError | Exception e) {
            return Executors.newCachedThreadPool();
        }
    }

    public void start() throws IOException {
        int boundPort;
        synchronized (this) {
            if (running) {
                return;
            }
            this.serverSocket = new ServerSocket(port);
            boundPort = serverSocket.getLocalPort();
            startWebhookServer(boundPort);
            this.running = true;
            startLatch.countDown();
        }

        System.out.println("==================================================");
        System.out.println("  RealTimeChat NextGen Server - Puerto TCP " + boundPort);
        if (webhookServer != null) {
            System.out.println("  Webhooks HTTP REST API     - Puerto HTTP " + webhookServer.getPort());
        }
        System.out.println("  Concurrencia: Virtual Threads + H2 Embedded DB");
        System.out.println("  Rate Limiting: Token Bucket activado");
        System.out.println("==================================================");

        this.shutdownHookThread = new Thread(this::stop, "server-shutdown-hook");
        try {
            Runtime.getRuntime().addShutdownHook(this.shutdownHookThread);
        } catch (IllegalStateException ignored) {
        }

        try {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[+] Conexión entrante desde: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, chatRoom, threadPool);
                threadPool.submit(handler);
            }
        } catch (SocketException e) {
            if (!running) {
                System.out.println("[STOP] Servidor detenido de manera controlada.");
            } else {
                System.err.println("[ERROR] Error en el socket del servidor: " + e.getMessage());
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[ERROR] Error I/O en el servidor: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void startWebhookServer(int chatPort) {
        int preferred = (this.port == 0) ? 0 : (chatPort + 1);
        try {
            webhookServer = new WebhookHttpServer(preferred, chatRoom);
            webhookServer.start();
            chatRoom.setWebhookBaseUrl(webhookServer.getBaseUrl());
        } catch (IOException e) {
            try {
                webhookServer = new WebhookHttpServer(0, chatRoom);
                webhookServer.start();
                chatRoom.setWebhookBaseUrl(webhookServer.getBaseUrl());
            } catch (IOException ignored) {
                System.err.println("[WARN] No se pudo iniciar WebhookHttpServer: " + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        System.out.println("[INFO] Cerrando servidor RealTimeChat...");

        if (shutdownHookThread != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
            } catch (IllegalStateException ignored) {
            }
            shutdownHookThread = null;
        }

        if (webhookServer != null) {
            try {
                webhookServer.stop();
            } catch (Exception ignored) {
            }
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[WARN] Error al cerrar ServerSocket: " + e.getMessage());
        }

        chatRoom.closeAll();

        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("[OK] Servidor detenido correctamente.");
    }

    public boolean isRunning() {
        return running;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public int getPort() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return serverSocket.getLocalPort();
        }
        return port;
    }

    public int getWebhookPort() {
        return webhookServer != null ? webhookServer.getPort() : -1;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("[WARN] Puerto no válido, usando puerto por defecto: " + DEFAULT_PORT);
            }
        }

        ChatServer server = new ChatServer(port);
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("[ERROR] Fallo crítico al iniciar el servidor en puerto " + port + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
