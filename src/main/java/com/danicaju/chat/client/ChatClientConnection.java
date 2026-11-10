package com.danicaju.chat.client;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;
import com.danicaju.chat.model.UserProfile;

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
 * Gestiona el ciclo de vida del socket TCP, el protocolo JSON y el hilo de recepción en segundo plano.
 * Soporta canales, indicadores de escritura (typing), personalización de perfiles y solicitudes de historial.
 */
public class ChatClientConnection {

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread listenerThread;
    private ChatEventListener eventListener;
    private String username;
    private String currentChannel = Message.DEFAULT_CHANNEL;
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

            // Esperar respuesta inicial de validación (procesar hasta SYSTEM con OK: o ERROR:)
            String responseLine;
            while ((responseLine = reader.readLine()) != null) {
                responseLine = responseLine.trim();
                if (responseLine.isEmpty()) {
                    continue;
                }

                Message initialMsg = Message.fromJson(responseLine);
                if (initialMsg.getType() == MessageType.SYSTEM && initialMsg.getContent() != null) {
                    if (initialMsg.getContent().startsWith("OK:")) {
                        this.username = requestedUsername != null ? requestedUsername.trim() : "";
                        this.currentChannel = Message.DEFAULT_CHANNEL;
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
            }

            if (eventListener != null) {
                eventListener.onError("El servidor cerró la conexión inesperadamente.");
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
                        handleIncomingMessage(msg);
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

    private void handleIncomingMessage(Message msg) {
        if (eventListener == null || msg == null || msg.getType() == null) {
            return;
        }

        switch (msg.getType()) {
            case USER_LIST -> {
                eventListener.onUserListUpdated(msg.getUsers());
            }
            case CHANNEL_LIST -> {
                eventListener.onChannelListUpdated(msg.getChannels());
            }
            case HISTORY -> {
                eventListener.onHistoryReceived(msg.getChannel(), msg.getHistory());
            }
            case TYPING_START -> {
                eventListener.onTypingUpdated(msg.getSender(), msg.getChannel(), true);
            }
            case TYPING_STOP -> {
                eventListener.onTypingUpdated(msg.getSender(), msg.getChannel(), false);
            }
            case PROFILE_RESPONSE -> {
                eventListener.onProfileReceived(msg.getProfile());
            }
            default -> {
                eventListener.onMessageReceived(msg);
            }
        }
    }

    /**
     * Envía un mensaje público al canal activo.
     */
    public synchronized void sendChatMessage(String content) {
        sendChatMessage(this.currentChannel, content);
    }

    /**
     * Envía un mensaje público a un canal específico.
     */
    public synchronized void sendChatMessage(String channel, String content) {
        if (!isConnected() || writer == null || content == null || content.trim().isEmpty()) {
            return;
        }
        String targetChannel = channel != null && !channel.trim().isEmpty() ? channel.trim() : this.currentChannel;
        Message msg = Message.chat(targetChannel, this.username, content.trim());
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
     * Solicita cambio de canal.
     */
    public synchronized void switchChannel(String newChannel) {
        if (!isConnected() || writer == null || newChannel == null || newChannel.trim().isEmpty()) {
            return;
        }
        String target = newChannel.trim();
        if (!target.startsWith("#")) {
            target = "#" + target;
        }
        this.currentChannel = target;
        Message msg = Message.joinChannel(this.username, target);
        writer.println(msg.toJson());
        if (eventListener != null) {
            eventListener.onChannelChanged(target);
        }
    }

    /**
     * Envía una notificación de inicio o parada de escritura (typing indicator).
     */
    public synchronized void sendTyping(boolean isTyping) {
        if (!isConnected() || writer == null) {
            return;
        }
        Message msg = isTyping ? Message.typingStart(this.username, this.currentChannel)
                : Message.typingStop(this.username, this.currentChannel);
        writer.println(msg.toJson());
    }

    /**
     * Actualiza el perfil del usuario.
     */
    public synchronized void updateProfile(UserProfile profile) {
        if (!isConnected() || writer == null || profile == null) {
            return;
        }
        Message msg = Message.profileUpdate(this.username, profile);
        writer.println(msg.toJson());
    }

    /**
     * Solicita el perfil de un usuario para inspección.
     */
    public synchronized void requestProfile(String targetUsername) {
        if (!isConnected() || writer == null || targetUsername == null) {
            return;
        }
        Message msg = Message.profileRequest(this.username, targetUsername.trim());
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
     * Solicita la lista de canales disponibles.
     */
    public synchronized void requestChannelList() {
        if (!isConnected() || writer == null) {
            return;
        }
        Message msg = new Message();
        msg.setType(MessageType.CHANNEL_LIST);
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

    public String getCurrentChannel() {
        return currentChannel;
    }
}
