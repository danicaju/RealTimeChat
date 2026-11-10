package com.danicaju.chat.server;

import com.danicaju.chat.bot.RealTimeBot;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.util.TokenBucketRateLimiter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Manejador individual de cada cliente conectado en el servidor.
 * Incorpora limitación de tasa por Token Bucket, soporte para múltiples canales,
 * historial de reproducción, eventos de escritura (typing), personalización de perfiles
 * y delegación asíncrona hacia el bot inteligente RealTimeBot.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatRoom chatRoom;
    private final ExecutorService threadPool;
    private final TokenBucketRateLimiter rateLimiter;

    private PrintWriter writer;
    private BufferedReader reader;
    private String username;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, ChatRoom chatRoom) {
        this(socket, chatRoom, null);
    }

    public ClientHandler(Socket socket, ChatRoom chatRoom, ExecutorService threadPool) {
        this.socket = socket;
        this.chatRoom = chatRoom;
        this.threadPool = threadPool;
        // Capacidad para ráfaga de 6 mensajes con recarga de 2.5 mensajes por segundo
        this.rateLimiter = new TokenBucketRateLimiter(6.0, 2.5);
    }

    @Override
    public void run() {
        try {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);

            // Fase 1: Handshake y autenticación del nickname
            if (!performHandshake()) {
                return;
            }

            // Fase 2: Bucle principal de procesamiento de mensajes JSON
            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    Message msg = Message.fromJson(line);
                    processMessage(msg);
                } catch (Exception e) {
                    sendMessageRaw(Message.system("Error de formato en el mensaje enviado.").toJson());
                }
            }
        } catch (IOException e) {
            // Desconexión habitual por cierre de socket
        } finally {
            disconnect();
        }
    }

    /**
     * Realiza el handshake inicial esperando un mensaje JOIN válido.
     */
    private boolean performHandshake() throws IOException {
        String line;
        while (running && (line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            try {
                Message msg = Message.fromJson(line);
                if (msg.getType() == MessageType.JOIN) {
                    String candidate = msg.getSender() != null ? msg.getSender().trim() : "";
                    if (!chatRoom.isValidUsername(candidate)) {
                        sendMessageRaw(Message.system("ERROR: El nickname debe contener entre 2 y 20 caracteres alfanuméricos (a-z, 0-9, _, -, .).").toJson());
                        continue;
                    }

                    // Intento de registro atómico
                    if (!chatRoom.registerClient(candidate, this)) {
                        sendMessageRaw(Message.system("ERROR: El nombre de usuario '" + candidate + "' ya está en uso. Por favor, elige otro.").toJson());
                        continue;
                    }

                    this.username = candidate;
                    sendMessageRaw(Message.system("OK: Conectado correctamente como " + this.username).toJson());

                    // Enviar lista de canales disponibles
                    sendMessageRaw(Message.channelList(chatRoom.getAvailableChannels()).toJson());

                    // Notificar unión al canal por defecto (#general)
                    String defaultChan = ChatRoom.DEFAULT_CHANNEL;
                    chatRoom.broadcast(Message.system("🟢 " + this.username + " se ha unido a la sala (" + defaultChan + ")."), this);

                    // Entregar búfer de reproducción (historial) del canal al usuario recién conectado
                    List<Message> history = chatRoom.getChannelHistory(defaultChan);
                    if (!history.isEmpty()) {
                        sendMessageRaw(Message.history(defaultChan, history).toJson());
                    }

                    // Difundir lista actualizada de usuarios
                    chatRoom.broadcastChannelUserList(defaultChan);
                    return true;
                } else if (msg.getType() == MessageType.LEAVE) {
                    return false;
                } else {
                    sendMessageRaw(Message.system("ERROR: Debes identificarte con un mensaje JOIN antes de interactuar.").toJson());
                }
            } catch (Exception e) {
                sendMessageRaw(Message.system("ERROR: Se esperaba un mensaje de unión (JOIN) válido.").toJson());
            }
        }
        return false;
    }

    /**
     * Procesa los mensajes según su tipo de protocolo.
     */
    private void processMessage(Message msg) {
        if (msg.getType() == null) {
            return;
        }

        // Control de Rate Limiting para prevenir flood
        if (msg.getType() == MessageType.TYPING_START || msg.getType() == MessageType.TYPING_STOP) {
            if (!rateLimiter.tryConsume(0.2)) {
                // Descartar silenciosamente eventos de typing excesivos para evitar saturación de socket
                return;
            }
        } else if (msg.getType() == MessageType.CHAT || msg.getType() == MessageType.PRIVATE ||
                msg.getType() == MessageType.JOIN_CHANNEL || msg.getType() == MessageType.PROFILE_UPDATE ||
                msg.getType() == MessageType.PROFILE_REQUEST || msg.getType() == MessageType.USER_LIST ||
                msg.getType() == MessageType.CHANNEL_LIST) {
            if (!rateLimiter.tryConsume()) {
                sendMessageRaw(Message.system("⚠️ [RATE LIMIT] Has enviado demasiados mensajes o peticiones por segundo. Por favor, espera un momento.").toJson());
                return;
            }
        }

        switch (msg.getType()) {
            case CHAT -> handleChat(msg);
            case PRIVATE -> handlePrivate(msg);
            case JOIN_CHANNEL -> handleJoinChannel(msg);
            case CHANNEL_LIST -> {
                sendMessageRaw(Message.channelList(chatRoom.getAvailableChannels()).toJson());
            }
            case TYPING_START -> {
                String ch = chatRoom.getUserChannel(this.username);
                chatRoom.broadcastTyping(ch, this.username, true, this);
            }
            case TYPING_STOP -> {
                String ch = chatRoom.getUserChannel(this.username);
                chatRoom.broadcastTyping(ch, this.username, false, this);
            }
            case PROFILE_UPDATE -> handleProfileUpdate(msg);
            case PROFILE_REQUEST -> handleProfileRequest(msg);
            case USER_LIST -> {
                String ch = chatRoom.getUserChannel(this.username);
                sendMessageRaw(Message.userList(ch, chatRoom.getActiveUsersInChannel(ch)).toJson());
            }
            case LEAVE -> {
                running = false;
            }
            default -> {
            }
        }
    }

    private void handleChat(Message msg) {
        String content = msg.getContent() != null ? msg.getContent().trim() : "";
        if (content.isEmpty()) {
            return;
        }
        if (content.length() > 4000) {
            sendMessageRaw(Message.system("ERROR: El mensaje excede el límite de 4000 caracteres.").toJson());
            return;
        }

        String currentChannel = chatRoom.getUserChannel(this.username);
        Message broadcastMsg = Message.chat(currentChannel, this.username, content);
        chatRoom.broadcast(broadcastMsg, null);

        // Comprobar si el mensaje invoca al asistente inteligente (@bot o @ai)
        if (RealTimeBot.isBotQuery(content)) {
            triggerBotResponse(content, currentChannel);
        }
    }

    private void triggerBotResponse(String prompt, String channel) {
        Runnable botTask = () -> {
            try {
                // Notificar que el bot está escribiendo
                chatRoom.broadcastTyping(channel, RealTimeBot.BOT_NAME, true, null);
                Thread.sleep(400); // Simulación de pensamiento y tipeo

                List<Message> history = chatRoom.getChannelHistory(channel);
                Message reply = RealTimeBot.handleMessage(prompt, channel, history);

                chatRoom.broadcastTyping(channel, RealTimeBot.BOT_NAME, false, null);
                chatRoom.broadcast(reply, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                System.err.println("Error en respuesta de RealTimeBot: " + ex.getMessage());
            }
        };

        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(botTask);
        } else {
            Thread t = new Thread(botTask, "realtime-bot-worker");
            t.setDaemon(true);
            t.start();
        }
    }

    private void handlePrivate(Message msg) {
        String target = msg.getTarget() != null ? msg.getTarget().trim() : "";
        String content = msg.getContent() != null ? msg.getContent().trim() : "";
        if (target.isEmpty() || content.isEmpty()) {
            sendMessageRaw(Message.system("ERROR: El mensaje privado requiere destinatario y contenido.").toJson());
            return;
        }
        if (target.equalsIgnoreCase(this.username)) {
            sendMessageRaw(Message.system("No puedes enviarte un mensaje privado a ti mismo.").toJson());
            return;
        }
        if (content.length() > 4000) {
            sendMessageRaw(Message.system("ERROR: El mensaje privado excede el límite de 4000 caracteres.").toJson());
            return;
        }

        Message privateMsg = Message.privateMsg(this.username, target, content);
        boolean delivered = chatRoom.sendPrivate(target, privateMsg);
        if (delivered) {
            sendMessageRaw(privateMsg.toJson());
        } else {
            sendMessageRaw(Message.system("ERROR: El usuario '" + target + "' no existe o está desconectado.").toJson());
        }
    }

    private void handleJoinChannel(Message msg) {
        String reqChannel = msg.getChannel();
        if (reqChannel == null || !ChatRoom.isValidChannelName(reqChannel)) {
            sendMessageRaw(Message.system("ERROR: Nombre de canal inválido. Debe contener entre 2 y 30 caracteres (a-z, 0-9, _, -).").toJson());
            return;
        }

        String oldChannel = chatRoom.getUserChannel(this.username);
        String newChannel = chatRoom.switchUserChannel(this.username, reqChannel);

        if (oldChannel.equalsIgnoreCase(newChannel)) {
            sendMessageRaw(Message.system("Ya te encuentras en el canal " + newChannel).toJson());
            List<Message> history = chatRoom.getChannelHistory(newChannel);
            sendMessageRaw(Message.history(newChannel, history).toJson());
            sendMessageRaw(Message.userList(newChannel, chatRoom.getActiveUsersInChannel(newChannel)).toJson());
            return;
        }

        // Notificar en el canal anterior
        Message leftMsg = Message.system(oldChannel, "🚪 " + this.username + " ha cambiado de canal.");
        chatRoom.broadcast(leftMsg, this);
        chatRoom.broadcastChannelUserList(oldChannel);

        // Notificar en el nuevo canal
        Message joinedMsg = Message.system(newChannel, "🟢 " + this.username + " se ha unido a " + newChannel + ".");
        chatRoom.broadcast(joinedMsg, this);
        chatRoom.broadcastChannelUserList(newChannel);

        // Confirmar cambio y enviar búfer de reproducción al usuario
        sendMessageRaw(Message.system("OK: Cambiaste al canal " + newChannel).toJson());
        List<Message> history = chatRoom.getChannelHistory(newChannel);
        sendMessageRaw(Message.history(newChannel, history).toJson());
        sendMessageRaw(Message.userList(newChannel, chatRoom.getActiveUsersInChannel(newChannel)).toJson());
    }

    private void handleProfileUpdate(Message msg) {
        UserProfile profile = msg.getProfile();
        if (profile != null) {
            chatRoom.updateUserProfile(this.username, profile);
            UserProfile updated = chatRoom.getUserProfile(this.username);
            sendMessageRaw(Message.profileResponse(updated).toJson());
            // Difundir a todos los clientes para mantener sincronizadas sus cachés de perfiles y avatares
            chatRoom.broadcastToAll(Message.profileResponse(updated), this);
            sendMessageRaw(Message.system("OK: Tu perfil ha sido actualizado correctamente.").toJson());
        }
    }

    private void handleProfileRequest(Message msg) {
        String target = msg.getTarget() != null ? msg.getTarget().trim() : this.username;
        UserProfile profile = chatRoom.getUserProfile(target);
        sendMessageRaw(Message.profileResponse(profile).toJson());
    }

    /**
     * Envía una cadena JSON cruda de manera sincronizada para evitar intercalado de bytes.
     */
    public synchronized void sendMessageRaw(String json) {
        if (writer != null && socket != null && !socket.isClosed()) {
            writer.println(json);
        }
    }

    /**
     * Cierra los recursos y notifica la salida del usuario a la sala y a su canal.
     */
    public synchronized void disconnect() {
        if (!running && username == null) {
            return;
        }
        running = false;
        if (username != null) {
            String ch = chatRoom.getUserChannel(username);
            boolean removed = chatRoom.unregisterClient(username, this);
            if (removed) {
                chatRoom.broadcast(Message.system("🔴 " + username + " ha abandonado la sala."), null);
                chatRoom.broadcastChannelUserList(ch);
            }
            username = null;
        }
        close();
    }

    public void close() {
        running = false;
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

    public String getUsername() {
        return username;
    }

    public TokenBucketRateLimiter getRateLimiter() {
        return rateLimiter;
    }
}
