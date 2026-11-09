package com.danicaju.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Servidor multihilo de RealTimeChat.
 * Utiliza un Thread Pool (ExecutorService) para administrar las conexiones concurrentes
 * y un Shutdown Hook para la liberación adecuada de sockets y recursos del sistema.
 */
public class ChatServer {

    public static final int DEFAULT_PORT = 5000;

    private final int port;
    private final ChatRoom chatRoom;
    private final ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public ChatServer(int port) {
        this.port = port;
        this.chatRoom = new ChatRoom();
        this.threadPool = Executors.newCachedThreadPool();
    }

    /**
     * Inicia el bucle de escucha del servidor.
     */
    public void start() throws IOException {
        synchronized (this) {
            if (running) {
                return;
            }
            this.serverSocket = new ServerSocket(port);
            this.running = true;
        }

        System.out.println("==================================================");
        System.out.println("  RealTimeChat Server - Iniciado en el puerto " + port);
        System.out.println("==================================================");

        // Registro de gancho de apagado seguro (Shutdown Hook)
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "server-shutdown-hook"));

        try {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("📥 Conexión entrante desde: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, chatRoom);
                threadPool.submit(handler);
            }
        } catch (SocketException e) {
            if (!running) {
                System.out.println("🛑 Servidor detenido de manera controlada.");
            } else {
                System.err.println("❌ Error en el socket del servidor: " + e.getMessage());
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("❌ Error I/O en el servidor: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    /**
     * Detiene el servidor y libera todos los recursos de red y ejecución.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        System.out.println("🔄 Cerrando servidor RealTimeChat...");

        // Cerrar socket servidor principal
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("⚠️ Error al cerrar ServerSocket: " + e.getMessage());
        }

        // Desconectar clientes activos en la sala
        chatRoom.closeAll();

        // Apagar el grupo de hilos
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("✅ Servidor detenido correctamente.");
    }

    public boolean isRunning() {
        return running;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public int getPort() {
        return port;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("⚠️ Puerto no válido, usando puerto por defecto: " + DEFAULT_PORT);
            }
        }

        ChatServer server = new ChatServer(port);
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("❌ Fallo crítico al iniciar el servidor en puerto " + port + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
