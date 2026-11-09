package com.danicaju.chat.client;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conexión cliente reutilizable y orientada a eventos para RealTimeChat.
 * Gestiona el ciclo de vida del socket TCP, el protocolo JSON y el hilo de recepción.
 */
public class ChatClientConnection {

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread listenerThread;
    private ChatEventListener eventListener;
    private String username;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * Establece la conexión con el servidor y realiza el handshake inicial con el nickname.
     *
     * @param host              Dirección IP o hostname del servidor
     * @param port              Puerto del servidor
     * @param requestedUsername Nickname solicitado
     * @param listener          Receptor de eventos
     * @return true si la autenticación fue exitosa; false en caso contrario
     */
    public synchronized boolean connect(String host, int port, String requestedUsername, ChatEventListener listener) {
        if (connected.get()) {
            disconnect();
        }
        this.eventListener = listener;

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000); // Timeout protector durante handshake

            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // Enviar solicitud JOIN
            Message joinMessage = Message.join(requestedUsername != null ? requestedUsername.trim() : "");
            writer.println(joinMessage.toJson());

            // Esperar respuesta inicial de validación
            String responseLine = reader.readLine();
            if (responseLine == null) {
                if (eventListener != null) {
                    eventListener.onError("El servidor cerró la conexión inesperadamente.");
                }
                close();
                return false;
            }

            Message initialMsg = Message.fromJson(responseLine);
            if (initialMsg.getType() == MessageType.SYSTEM && initialMsg.getContent() != null) {
                if (initialMsg.getContent().startsWith("OK:")) {
                    this.username = requestedUsername != null ? requestedUsername.trim() : "";
                    this.connected.set(true);
                    socket.setSoTimeout(0); // Quitar timeout para escucha continua indefinida

                    startListenerThread();

                    if (eventListener != null) {
                        eventListener.onConnected(this.username);
                        eventListener.onMessageReceived(initialMsg);
                    }
                    return true;
                } else if (initialMsg.getContent().startsWith("ERROR:")) {
                    if (eventListener != null) {
                        eventListener.onError(initialMsg.getContent());
                    }
                    close();
                    return false;
                }
            }

            // Si llegamos aquí sin un OK claro, reportamos error
            if (eventListener != null) {
                eventListener.onError("Respuesta no válida del servidor durante el handshake.");
            }
            close();
            return false;

        } catch (IOException e) {
            if (eventListener != null) {
                eventListener.onError("No fue posible conectar con " + host + ":" + port + " (" + e.getMessage() + ")");
            }
            close();
            return false;
        }
    }

    /**
     * Inicia el hilo en segundo plano que escucha los mensajes provenientes del servidor.
     */
    private void startListenerThread() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected.get() && (line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    try {
                        Message msg = Message.fromJson(line);
                        if (msg.getType() == MessageType.USER_LIST) {
                            if (eventListener != null) {
                                eventListener.onUserListUpdated(msg.getUsers());
                            }
                        } else {
                            if (eventListener != null) {
                                eventListener.onMessageReceived(msg);
                            }
                        }
                    } catch (Exception parseEx) {
                        System.err.println("Advertencia: mensaje recibido no parseable: " + line);
                    }
                }
            } catch (IOException e) {
                // Caída de conexión habitual
            } finally {
                close();
                if (connected.compareAndSet(true, false)) {
                    if (eventListener != null) {
                        eventListener.onDisconnected("Conexión con el servidor finalizada.");
                    }
                }
            }
        }, "chat-client-listener");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Envía un mensaje público a la sala.
     */
    public synchronized void sendChatMessage(String content) {
        if (!isConnected() || writer == null || content == null || content.trim().isEmpty()) {
            return;
        }
        Message msg = Message.chat(this.username, content.trim());
        writer.println(msg.toJson());
    }

    /**
     * Envía un mensaje privado a un usuario específico.
     */
    public synchronized void sendPrivateMessage(String target, String content) {
        if (!isConnected() || writer == null || target == null || content == null) {
            return;
        }
        Message msg = Message.privateMsg(this.username, target.trim(), content.trim());
        writer.println(msg.toJson());
    }

    /**
     * Solicita al servidor refrescar la lista de usuarios.
     */
    public synchronized void requestUserList() {
        if (!isConnected() || writer == null) {
            return;
        }
        Message msg = new Message();
        msg.setType(MessageType.USER_LIST);
        writer.println(msg.toJson());
    }

    /**
     * Notifica salida y desconecta el cliente de forma limpia.
     */
    public synchronized void disconnect() {
        if (!connected.compareAndSet(true, false)) {
            return;
        }
        try {
            if (writer != null && this.username != null) {
                Message leaveMsg = Message.leave(this.username);
                writer.println(leaveMsg.toJson());
            }
        } catch (Exception ignored) {
        }
        close();
        if (eventListener != null) {
            eventListener.onDisconnected("Desconectado de la sala.");
        }
    }

    /**
     * Cierra sockets y flujos subyacentes.
     */
    private void close() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public String getUsername() {
        return username;
    }
}
