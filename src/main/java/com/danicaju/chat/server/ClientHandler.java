package com.danicaju.chat.server;

import com.danicaju.chat.bot.RealTimeBot;
import com.danicaju.chat.db.DatabaseManager;
import com.danicaju.chat.model.ChannelInfo;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;
import com.danicaju.chat.model.PresenceStatus;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.model.WebhookInfo;
import com.danicaju.chat.security.Permission;
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
 * Gestiona handshake, rate limiting, optimistic ACK, hilos, reacciones, presencia,
 * paginación por cursores, permisos bitfield, búsqueda, webhooks, E2EE, pizarra y voz.
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
    private volatile boolean readyForBroadcasts;

    public ClientHandler(Socket socket, ChatRoom chatRoom) {
        this(socket, chatRoom, null);
    }

    public ClientHandler(Socket socket, ChatRoom chatRoom, ExecutorService threadPool) {
        this.socket = socket;
        this.chatRoom = chatRoom;
        this.threadPool = threadPool;
        this.readyForBroadcasts = (socket == null);
        this.rateLimiter = new TokenBucketRateLimiter(6.0, 3.0);
    }

    @Override
    public void run() {
        try {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);

            if (!performHandshake()) {
                return;
            }

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
            // Desconexión habitual
        } finally {
            disconnect();
        }
    }

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

                    if (!chatRoom.registerClient(candidate, this)) {
                        sendMessageRaw(Message.system("ERROR: El nombre de usuario '" + candidate + "' ya está en uso. Por favor, elige otro.").toJson());
                        continue;
                    }

                    this.username = candidate;
                    sendMessageRaw(Message.system("OK: Conectado correctamente como " + this.username).toJson());

                    // Enviar canales disponibles, metadatos con permisos y perfil propio
                    sendMessageRaw(Message.channelList(chatRoom.getAvailableChannels()).toJson());
                    sendMessageRaw(Message.channelMetadata(chatRoom.getChannelInfos()).toJson());
                    sendMessageRaw(Message.profileResponse(chatRoom.getUserProfile(this.username)).toJson());

                    this.readyForBroadcasts = true;

                    String defaultChan = ChatRoom.DEFAULT_CHANNEL;
                    chatRoom.broadcast(Message.system(defaultChan, "[+] " + this.username + " se ha unido a la sala (" + defaultChan + ")."), this);

                    List<Message> history = chatRoom.getChannelHistory(defaultChan);
                    if (!history.isEmpty()) {
                        sendMessageRaw(Message.history(defaultChan, history).toJson());
                    }

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

    private void processMessage(Message msg) {
        if (msg.getType() == null) {
            return;
        }

        MessageType t = msg.getType();
        if (t == MessageType.PING) {
            sendMessageRaw(Message.pong().toJson());
            return;
        }
        if (t == MessageType.VOICE_AUDIO || t == MessageType.WHITEBOARD_EVENT
                || t == MessageType.WEBRTC_SIGNAL || t == MessageType.KEY_EXCHANGE) {
            // Señalización y streaming en tiempo real sin throttling estricto de chat
        } else if (t == MessageType.TYPING_START || t == MessageType.TYPING_STOP) {
            if (!rateLimiter.tryConsume(0.15)) {
                return;
            }
        } else if (t == MessageType.CHAT || t == MessageType.PRIVATE || t == MessageType.JOIN_CHANNEL) {
            if (!rateLimiter.tryConsume()) {
                sendMessageRaw(Message.system("[RATE LIMIT] Has enviado demasiados mensajes o peticiones por segundo. Por favor, espera un momento.").toJson());
                return;
            }
        }

        switch (t) {
            case CHAT -> handleChat(msg);
            case PRIVATE -> handlePrivate(msg);
            case JOIN_CHANNEL -> handleJoinChannel(msg);
            case CHANNEL_LIST -> {
                sendMessageRaw(Message.channelList(chatRoom.getAvailableChannels()).toJson());
                sendMessageRaw(Message.channelMetadata(chatRoom.getChannelInfos()).toJson());
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
                Message ul = Message.userList(ch, chatRoom.getActiveUsersInChannel(ch));
                ul.setUserProfiles(chatRoom.buildOnlineProfilesSnapshot());
                sendMessageRaw(ul.toJson());
            }
            case MESSAGE_DELETE -> handleDeleteMessage(msg);
            case REACTION_ADD -> chatRoom.processReactionDelta(this.username, msg.getId(), msg.getChannel(), msg.getContent(), true);
            case REACTION_REMOVE -> chatRoom.processReactionDelta(this.username, msg.getId(), msg.getChannel(), msg.getContent(), false);
            case PRESENCE_UPDATE -> chatRoom.updatePresence(this.username, msg.getPresenceStatus(), msg.getCustomStatus(), msg.getStatusSymbol());
            case HISTORY_PAGE -> handleHistoryPage(msg);
            case CHANNEL_CREATE -> handleCreateChannel(msg);
            case CHANNEL_DELETE -> handleDeleteChannel(msg);
            case ROLE_UPDATE -> handleRoleUpdate(msg);
            case POLL_VOTE -> {
                if (msg.getPollOptionIndex() != null) {
                    chatRoom.processPollVote(this.username, msg.getId(), msg.getPollOptionIndex());
                }
            }
            case WEBHOOK_CREATE -> handleWebhookCreate(msg);
            case WEBHOOK_DELETE -> handleWebhookDelete(msg);
            case WEBHOOK_LIST -> handleWebhookList(msg);
            case SEARCH_REQUEST -> handleSearch(msg);
            case KEY_EXCHANGE -> handleKeyExchange(msg);
            case WHITEBOARD_EVENT -> handleWhiteboardEvent(msg);
            case VOICE_STATE -> chatRoom.handleVoiceState(this.username, msg);
            case VOICE_AUDIO -> handleVoiceAudio(msg);
            case WEBRTC_SIGNAL -> handleWebrtcSignal(msg);
            case LEAVE -> running = false;
            default -> {
            }
        }
    }

    private void handleChat(Message msg) {
        String content = msg.getContent() != null ? msg.getContent().trim() : "";
        boolean hasAttachment = msg.getAttachmentBase64() != null && !msg.getAttachmentBase64().isBlank();
        boolean hasPoll = msg.getPoll() != null;
        if (content.isEmpty() && !hasAttachment && !hasPoll) {
            return;
        }
        if (content.length() > 4000) {
            sendMessageRaw(Message.system("ERROR: El mensaje excede el límite de 4000 caracteres.").toJson());
            return;
        }

        String currentChannel = msg.getChannel() != null && !msg.getChannel().isBlank()
                ? ChatRoom.normalizeChannel(msg.getChannel())
                : chatRoom.getUserChannel(this.username);

        Message outgoing = Message.chat(currentChannel, this.username, content);
        outgoing.setTempId(msg.getTempId());
        outgoing.setReplyToId(msg.getReplyToId());
        outgoing.setReplySender(msg.getReplySender());
        outgoing.setReplyPreview(msg.getReplyPreview());
        outgoing.setThreadParentId(msg.getThreadParentId());
        outgoing.setPoll(msg.getPoll());
        outgoing.setEmbed(msg.getEmbed());
        if (hasAttachment) {
            if (msg.getAttachmentBase64().length() > 1_200_000) {
                sendMessageRaw(Message.system("ERROR: La imagen adjunta supera el tamaño máximo permitido (~850 KB).").toJson());
                return;
            }
            outgoing.setAttachmentName(msg.getAttachmentName());
            outgoing.setAttachmentBase64(msg.getAttachmentBase64());
        }

        Message saved = chatRoom.dispatchClientChannelMessage(this, outgoing);

        if (saved != null && outgoing.getThreadParentId() == null && RealTimeBot.isBotQuery(content)) {
            triggerBotResponse(content, currentChannel);
        }
    }

    private void triggerBotResponse(String prompt, String channel) {
        Runnable botTask = () -> {
            try {
                chatRoom.broadcastTyping(channel, RealTimeBot.BOT_NAME, true, null);
                Thread.sleep(350);

                List<Message> history = chatRoom.getChannelHistory(channel);
                Message reply = RealTimeBot.handleMessage(prompt, channel, history);

                chatRoom.broadcastTyping(channel, RealTimeBot.BOT_NAME, false, null);
                chatRoom.dispatchExternalChannelMessage(reply);
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
        boolean hasAttachment = msg.getAttachmentBase64() != null && !msg.getAttachmentBase64().isBlank();
        if (target.isEmpty() || (content.isEmpty() && !hasAttachment)) {
            sendMessageRaw(Message.system("ERROR: El mensaje privado requiere destinatario y contenido.").toJson());
            return;
        }
        if (target.equalsIgnoreCase(this.username)) {
            sendMessageRaw(Message.system("No puedes enviarte un mensaje privado a ti mismo.").toJson());
            return;
        }
        if (content.length() > 8000) {
            sendMessageRaw(Message.system("ERROR: El mensaje privado excede el límite permitido.").toJson());
            return;
        }

        Message privateMsg = Message.privateMsg(this.username, target, content);
        privateMsg.setTempId(msg.getTempId());
        privateMsg.setEncrypted(msg.getEncrypted());
        privateMsg.setIv(msg.getIv());
        privateMsg.setSenderPublicKey(msg.getSenderPublicKey());
        privateMsg.setAttachmentName(msg.getAttachmentName());
        privateMsg.setAttachmentBase64(msg.getAttachmentBase64());

        boolean delivered = chatRoom.sendPrivate(target, this, privateMsg);
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

        String normalizedTarget = ChatRoom.normalizeChannel(reqChannel);
        if (!chatRoom.hasChannelPermission(this.username, normalizedTarget, Permission.VIEW_CHANNEL)) {
            sendMessageRaw(Message.system("ERROR: No tienes permiso VIEW_CHANNEL para entrar en " + normalizedTarget + ".").toJson());
            return;
        }

        String oldChannel = chatRoom.getUserChannel(this.username);
        String newChannel = chatRoom.switchUserChannel(this.username, normalizedTarget);

        if (oldChannel.equalsIgnoreCase(newChannel)) {
            sendMessageRaw(Message.system("Ya te encuentras en el canal " + newChannel).toJson());
            sendHistoryAndUsers(newChannel);
            return;
        }

        Message leftMsg = Message.system(oldChannel, "[-] " + this.username + " ha cambiado de canal.");
        chatRoom.broadcast(leftMsg, this);
        chatRoom.broadcastChannelUserList(oldChannel);

        Message joinedMsg = Message.system(newChannel, "[+] " + this.username + " se ha unido a " + newChannel + ".");
        chatRoom.broadcast(joinedMsg, this);
        chatRoom.broadcastChannelUserList(newChannel);

        sendMessageRaw(Message.system("OK: Cambiaste al canal " + newChannel).toJson());
        sendHistoryAndUsers(newChannel);
    }

    private void sendHistoryAndUsers(String channel) {
        List<Message> history = chatRoom.getChannelHistory(channel);
        sendMessageRaw(Message.history(channel, history).toJson());
        Message ul = Message.userList(channel, chatRoom.getActiveUsersInChannel(channel));
        ul.setUserProfiles(chatRoom.buildOnlineProfilesSnapshot());
        sendMessageRaw(ul.toJson());
    }

    private void handleProfileUpdate(Message msg) {
        UserProfile profile = msg.getProfile();
        if (profile != null) {
            chatRoom.updateUserProfile(this.username, profile);
            UserProfile updated = chatRoom.getUserProfile(this.username);
            sendMessageRaw(Message.profileResponse(updated).toJson());
            chatRoom.broadcastToAll(Message.profileResponse(updated), this);
            sendMessageRaw(Message.system("OK: Tu perfil ha sido actualizado correctamente.").toJson());
        }
    }

    private void handleProfileRequest(Message msg) {
        String target = msg.getTarget() != null ? msg.getTarget().trim() : this.username;
        UserProfile profile = chatRoom.getUserProfile(target);
        sendMessageRaw(Message.profileResponse(profile).toJson());
    }

    private void handleDeleteMessage(Message msg) {
        if (!chatRoom.deleteMessage(this.username, msg.getId(), msg.getChannel())) {
            sendMessageRaw(Message.system("ERROR: No tienes permiso para eliminar este mensaje o no existe.").toJson());
        }
    }

    private void handleHistoryPage(Message msg) {
        if (msg.getThreadParentId() != null) {
            List<Message> threadMsgs = chatRoom.getThreadHistory(msg.getThreadParentId());
            Message resp = Message.historyPageResponse(
                    ChatRoom.normalizeChannel(msg.getChannel()),
                    threadMsgs,
                    false,
                    null,
                    null
            );
            resp.setThreadParentId(msg.getThreadParentId());
            sendMessageRaw(resp.toJson());
            return;
        }
        String ch = ChatRoom.normalizeChannel(msg.getChannel() != null ? msg.getChannel() : chatRoom.getUserChannel(this.username));
        if (!chatRoom.hasChannelPermission(this.username, ch, Permission.VIEW_CHANNEL)) {
            sendMessageRaw(Message.system("ERROR: Sin permiso VIEW_CHANNEL en " + ch).toJson());
            return;
        }
        int limit = msg.getLimit() != null ? msg.getLimit() : 30;
        DatabaseManager.CursorPage page = chatRoom.getChannelHistoryCursor(ch, msg.getBeforeId(), msg.getAfterId(), limit);
        sendMessageRaw(Message.historyPageResponse(ch, page.messages(), page.hasMore(), msg.getBeforeId(), msg.getAfterId()).toJson());
    }

    private void handleCreateChannel(Message msg) {
        ChannelInfo info = msg.getChannelInfo();
        if (info == null && msg.getChannel() != null) {
            info = new ChannelInfo(msg.getChannel(), ChannelInfo.TYPE_TEXT, "Nuevo canal", this.username);
        }
        if (chatRoom.createChannel(this.username, info)) {
            sendMessageRaw(Message.system("OK: Canal '" + ChatRoom.normalizeChannel(info.getName()) + "' creado correctamente.").toJson());
        } else {
            sendMessageRaw(Message.system("ERROR: No se pudo crear el canal (requiere permiso MANAGE_CHANNELS o nombre válido único).").toJson());
        }
    }

    private void handleDeleteChannel(Message msg) {
        if (chatRoom.deleteChannel(this.username, msg.getChannel())) {
            sendMessageRaw(Message.system("OK: Canal '" + ChatRoom.normalizeChannel(msg.getChannel()) + "' eliminado.").toJson());
        } else {
            sendMessageRaw(Message.system("ERROR: No tienes permiso MANAGE_CHANNELS o no se puede eliminar #general.").toJson());
        }
    }

    private void handleRoleUpdate(Message msg) {
        if (chatRoom.applyRoleOrOverrideUpdate(this.username, msg)) {
            sendMessageRaw(Message.system("OK: Permisos actualizados correctamente.").toJson());
        } else {
            sendMessageRaw(Message.system("ERROR: No tienes permisos suficientes para modificar roles/overrides.").toJson());
        }
    }

    private void handleWebhookCreate(Message msg) {
        String ch = ChatRoom.normalizeChannel(msg.getChannel() != null ? msg.getChannel() : chatRoom.getUserChannel(this.username));
        WebhookInfo info = chatRoom.createWebhook(this.username, ch, msg.getContent());
        if (info != null) {
            sendMessageRaw(Message.webhookList(ch, chatRoom.listWebhooks(ch)).toJson());
            sendMessageRaw(Message.system("OK: Webhook creado: " + info.getUrl()).toJson());
        } else {
            sendMessageRaw(Message.system("ERROR: No tienes permiso MANAGE_WEBHOOKS en " + ch).toJson());
        }
    }

    private void handleWebhookDelete(Message msg) {
        String ch = ChatRoom.normalizeChannel(msg.getChannel() != null ? msg.getChannel() : chatRoom.getUserChannel(this.username));
        if (chatRoom.deleteWebhook(this.username, ch, msg.getContent())) {
            sendMessageRaw(Message.webhookList(ch, chatRoom.listWebhooks(ch)).toJson());
            sendMessageRaw(Message.system("OK: Webhook eliminado.").toJson());
        } else {
            sendMessageRaw(Message.system("ERROR: No se pudo eliminar el webhook (requiere permiso MANAGE_WEBHOOKS).").toJson());
        }
    }

    private void handleWebhookList(Message msg) {
        String ch = ChatRoom.normalizeChannel(msg.getChannel() != null ? msg.getChannel() : chatRoom.getUserChannel(this.username));
        if (!chatRoom.hasChannelPermission(this.username, ch, Permission.MANAGE_WEBHOOKS)) {
            sendMessageRaw(Message.system("ERROR: No tienes permiso MANAGE_WEBHOOKS.").toJson());
            return;
        }
        sendMessageRaw(Message.webhookList(ch, chatRoom.listWebhooks(ch)).toJson());
    }

    private void handleSearch(Message msg) {
        String query = msg.getContent() != null ? msg.getContent() : "";
        List<Message> results = chatRoom.getDatabase().searchMessages(
                query,
                chatRoom.getAccessibleChannels(this.username),
                this.username
        );
        sendMessageRaw(Message.searchResponse(query, results).toJson());
    }

    private void handleKeyExchange(Message msg) {
        if (msg.getSenderPublicKey() != null && !msg.getSenderPublicKey().isBlank()) {
            chatRoom.updateUserPublicKey(this.username, msg.getSenderPublicKey());
        }
        if (msg.getTarget() != null && !msg.getTarget().isBlank()) {
            Message relay = Message.keyExchange(this.username, msg.getTarget(), msg.getSenderPublicKey());
            chatRoom.relayDirectFrame(msg.getTarget(), relay);
        } else {
            chatRoom.broadcastToAll(Message.keyExchange(this.username, null, msg.getSenderPublicKey()), this);
        }
    }

    private void handleWhiteboardEvent(Message msg) {
        String ch = ChatRoom.normalizeChannel(msg.getChannel() != null ? msg.getChannel() : chatRoom.getUserChannel(this.username));
        if (!chatRoom.hasChannelPermission(this.username, ch, Permission.SEND_MESSAGES)) {
            return;
        }
        Message relay = Message.whiteboardEvent(ch, this.username, msg.getContent());
        chatRoom.broadcast(relay, this);
    }

    private void handleVoiceAudio(Message msg) {
        String ch = ChatRoom.normalizeChannel(msg.getChannel() != null ? msg.getChannel() : chatRoom.getUserChannel(this.username));
        if (!chatRoom.hasChannelPermission(this.username, ch, Permission.VOICE_CONNECT)) {
            return;
        }
        Message relay = Message.voiceAudio(ch, this.username, msg.getContent());
        chatRoom.relayVoiceAudio(this.username, relay, this);
    }

    private void handleWebrtcSignal(Message msg) {
        Message relay = Message.webrtcSignal(
                this.username,
                msg.getTarget(),
                ChatRoom.normalizeChannel(msg.getChannel()),
                msg.getSignalType(),
                msg.getSignalPayload()
        );
        if (msg.getTarget() != null && !msg.getTarget().isBlank()) {
            chatRoom.relayDirectFrame(msg.getTarget(), relay);
        } else {
            chatRoom.broadcast(relay, this);
        }
    }

    public void deliverBroadcast(Message message) {
        if (readyForBroadcasts && message != null) {
            deliverBroadcast(message.toJson());
        }
    }

    public void deliverBroadcast(String json) {
        if (readyForBroadcasts) {
            sendMessageRaw(json);
        }
    }

    public synchronized void sendMessageRaw(String json) {
        if (writer != null && socket != null && !socket.isClosed()) {
            writer.println(json);
        }
    }

    public synchronized void disconnect() {
        if (!running && username == null) {
            return;
        }
        running = false;
        readyForBroadcasts = false;
        if (username != null) {
            String ch = chatRoom.getUserChannel(username);
            boolean removed = chatRoom.unregisterClient(username, this);
            if (removed) {
                chatRoom.broadcast(Message.system(ch, "[-] " + username + " ha abandonado la sala."), null);
                chatRoom.broadcastChannelUserList(ch);
            }
            username = null;
        }
        close();
    }

    public void close() {
        running = false;
        try {
            if (writer != null) writer.close();
        } catch (Exception ignored) {
        }
        try {
            if (reader != null) reader.close();
        } catch (Exception ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }
}
