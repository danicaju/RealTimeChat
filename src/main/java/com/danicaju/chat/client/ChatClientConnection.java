package com.danicaju.chat.client;

import com.danicaju.chat.model.ChannelInfo;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;
import com.danicaju.chat.model.PollData;
import com.danicaju.chat.model.PresenceStatus;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.security.E2EECrypto;

import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conexión cliente reutilizable de RealTimeChat NextGen.
 * Incluye Optimistic UI ACK + timeout, cola offline con resincronización por cursor (afterId),
 * heartbeat PING/PONG, throttling de escritura (2.5s), gestión de presencia y cifrado E2EE (ECDH + AES-GCM).
 */
public class ChatClientConnection {

    public static final long TYPING_THROTTLE_MS = 2500L;
    public static final long ACK_TIMEOUT_MS = 8000L;
    public static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread listenerThread;
    private ScheduledExecutorService maintenanceScheduler;
    private ChatEventListener eventListener;

    private String host;
    private int port;
    private String username;
    private String currentChannel = Message.DEFAULT_CHANNEL;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile boolean manualDisconnect = false;
    private volatile boolean autoReconnect = false;
    private volatile long reconnectBaseDelayMs = 2000L;
    private volatile long lastTypingSentMs = 0L;

    private final Map<String, PendingAck> pendingAcks = new ConcurrentHashMap<>();
    private final Queue<Message> offlineQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, Long> lastKnownMessageIdPerChannel = new ConcurrentHashMap<>();

    // E2EE Keyring
    private KeyPair e2eeKeyPair;
    private final Map<String, String> peerPublicKeys = new ConcurrentHashMap<>();
    private final Map<String, SecretKey> sharedSecretCache = new ConcurrentHashMap<>();

    public ChatClientConnection() {
        try {
            this.e2eeKeyPair = E2EECrypto.generateKeyPair();
        } catch (Exception e) {
            System.err.println("Advertencia: no se pudo inicializar par ECDH: " + e.getMessage());
        }
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public void setReconnectBaseDelayMs(long reconnectBaseDelayMs) {
        this.reconnectBaseDelayMs = Math.max(50L, reconnectBaseDelayMs);
    }

    public synchronized boolean connect(String host, int port, String requestedUsername, ChatEventListener listener) {
        if (connected.get()) {
            disconnect();
        }
        this.manualDisconnect = false;
        this.host = host;
        this.port = port;
        this.eventListener = listener;
        return establishConnection(requestedUsername, false);
    }

    private synchronized boolean establishConnection(String requestedUsername, boolean isReconnect) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);

            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String cleanNick = requestedUsername != null ? requestedUsername.trim() : "";
            writer.println(Message.join(cleanNick).toJson());

            String responseLine;
            while ((responseLine = reader.readLine()) != null) {
                responseLine = responseLine.trim();
                if (responseLine.isEmpty()) continue;

                Message initialMsg = Message.fromJson(responseLine);
                if (initialMsg.getType() == MessageType.SYSTEM && initialMsg.getContent() != null) {
                    if (initialMsg.getContent().startsWith("OK:")) {
                        this.username = cleanNick;
                        if (!isReconnect) {
                            this.currentChannel = Message.DEFAULT_CHANNEL;
                        }
                        this.connected.set(true);
                        socket.setSoTimeout(0);

                        startListenerThread();
                        startMaintenanceTasks();

                        // Publicar clave pública E2EE inmediatamente
                        if (e2eeKeyPair != null) {
                            sendRaw(Message.keyExchange(this.username, null,
                                    E2EECrypto.encodePublicKey(e2eeKeyPair.getPublic())));
                        }

                        if (eventListener != null) {
                            eventListener.onConnected(this.username);
                            eventListener.onMessageReceived(initialMsg);
                        }

                        if (isReconnect) {
                            resyncAndFlushAfterReconnect();
                        }
                        return true;
                    } else if (initialMsg.getContent().startsWith("ERROR:")) {
                        if (eventListener != null) {
                            eventListener.onError(initialMsg.getContent());
                        }
                        closeResources();
                        return false;
                    }
                }
            }

            if (eventListener != null) {
                eventListener.onError("El servidor cerró la conexión inesperadamente.");
            }
            closeResources();
            return false;
        } catch (IOException e) {
            if (eventListener != null) {
                eventListener.onError("No fue posible conectar con " + host + ":" + port + " (" + e.getMessage() + ")");
            }
            closeResources();
            return false;
        }
    }

    private void resyncAndFlushAfterReconnect() {
        // Volver al canal en el que estaba el usuario si no era #general
        if (currentChannel != null && !Message.DEFAULT_CHANNEL.equalsIgnoreCase(currentChannel)) {
            sendRaw(Message.joinChannel(username, currentChannel));
        }
        // Resincronizar mensajes perdidos desde el último ID conocido
        Long lastId = lastKnownMessageIdPerChannel.get(currentChannel);
        if (lastId != null && lastId > 0) {
            sendRaw(Message.historyPageRequest(username, currentChannel, null, lastId, 100));
        }
        // Vaciar cola offline en orden FIFO
        flushOfflineQueue();
    }

    public synchronized void flushOfflineQueue() {
        if (!isConnected() || writer == null) return;
        Message queued;
        while ((queued = offlineQueue.poll()) != null) {
            sendTracked(queued);
        }
    }

    public int getOfflineQueueSize() {
        return offlineQueue.size();
    }

    private void startMaintenanceTasks() {
        stopMaintenanceTasks();
        maintenanceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chat-client-maintenance");
            t.setDaemon(true);
            return t;
        });
        maintenanceScheduler.scheduleAtFixedRate(() -> {
            if (isConnected() && username != null) {
                sendRaw(Message.ping(username));
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        maintenanceScheduler.scheduleAtFixedRate(this::checkPendingAckTimeouts, 1, 1, TimeUnit.SECONDS);
    }

    private void checkPendingAckTimeouts() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingAck>> it = pendingAcks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PendingAck> entry = it.next();
            if (now - entry.getValue().sentAtMs() > ACK_TIMEOUT_MS) {
                it.remove();
                if (eventListener != null) {
                    eventListener.onMessageSendError(
                            entry.getKey(),
                            entry.getValue().message(),
                            "Sin confirmación del servidor (timeout)."
                    );
                }
            }
        }
    }

    private void stopMaintenanceTasks() {
        if (maintenanceScheduler != null) {
            maintenanceScheduler.shutdownNow();
            maintenanceScheduler = null;
        }
    }

    private void startListenerThread() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected.get() && (line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    try {
                        Message msg = Message.fromJson(line);
                        handleIncomingMessage(msg);
                    } catch (Exception parseEx) {
                        System.err.println("Advertencia: mensaje no parseable: " + parseEx.getMessage());
                    }
                }
            } catch (IOException e) {
                // Caída de socket
            } finally {
                closeResources();
                stopMaintenanceTasks();
                if (connected.compareAndSet(true, false)) {
                    if (eventListener != null) {
                        eventListener.onDisconnected("Conexión con el servidor finalizada.");
                    }
                    if (autoReconnect && !manualDisconnect && host != null && username != null) {
                        triggerReconnectLoop();
                    }
                }
            }
        }, "chat-client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void triggerReconnectLoop() {
        Thread reconnectThread = new Thread(() -> {
            for (int attempt = 1; attempt <= 5 && autoReconnect && !manualDisconnect && !connected.get(); attempt++) {
                try {
                    Thread.sleep(reconnectBaseDelayMs * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (manualDisconnect || connected.get()) {
                    return;
                }
                if (establishConnection(username, true)) {
                    return;
                }
            }
        }, "chat-client-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private void handleIncomingMessage(Message msg) {
        if (eventListener == null || msg == null || msg.getType() == null) {
            return;
        }

        // Registrar último ID conocido para resync
        if (msg.getId() != null && msg.getChannel() != null && msg.getThreadParentId() == null) {
            lastKnownMessageIdPerChannel.merge(msg.getChannel(), msg.getId(), Math::max);
        }

        switch (msg.getType()) {
            case PONG -> {
                // Heartbeat OK
            }
            case MESSAGE_ACK -> {
                if (msg.getTempId() != null) {
                    pendingAcks.remove(msg.getTempId());
                }
                if (msg.getId() != null && msg.getChannel() != null) {
                    lastKnownMessageIdPerChannel.merge(msg.getChannel(), msg.getId(), Math::max);
                }
                eventListener.onMessageAck(msg.getTempId(), msg.getId(), msg.getChannel(), msg.getTimestamp());
            }
            case MESSAGE_DELETE -> eventListener.onMessageDeleted(msg.getId(), msg.getChannel(), msg.getSender());
            case REACTION_ADD -> eventListener.onReactionUpdated(msg.getId(), msg.getChannel(), msg.getSender(), msg.getContent(), true, msg.getReactions());
            case REACTION_REMOVE -> eventListener.onReactionUpdated(msg.getId(), msg.getChannel(), msg.getSender(), msg.getContent(), false, msg.getReactions());
            case PRESENCE_UPDATE -> eventListener.onPresenceUpdated(msg.getSender(), msg.getPresenceStatus(), msg.getCustomStatus(), msg.getStatusSymbol());
            case USER_LIST -> {
                if (msg.getUserProfiles() != null) {
                    for (Map.Entry<String, UserProfile> e : msg.getUserProfiles().entrySet()) {
                        if (e.getValue() != null && e.getValue().getPublicKeyBase64() != null) {
                            registerPeerPublicKey(e.getKey(), e.getValue().getPublicKeyBase64());
                        }
                    }
                    eventListener.onUserProfilesUpdated(msg.getUserProfiles());
                }
                eventListener.onUserListUpdated(msg.getUsers());
            }
            case CHANNEL_LIST -> eventListener.onChannelListUpdated(msg.getChannels());
            case CHANNEL_METADATA -> {
                if (msg.getChannelInfos() != null) {
                    eventListener.onChannelMetadataUpdated(msg.getChannelInfos());
                }
            }
            case HISTORY -> {
                trackHistoryMaxId(msg.getChannel(), msg.getHistory());
                eventListener.onHistoryReceived(msg.getChannel(), msg.getHistory());
            }
            case HISTORY_PAGE -> {
                if (msg.getThreadParentId() == null) {
                    trackHistoryMaxId(msg.getChannel(), msg.getHistory());
                }
                eventListener.onHistoryPageReceived(
                        msg.getChannel(),
                        msg.getHistory(),
                        Boolean.TRUE.equals(msg.getHasMore()),
                        msg.getBeforeId(),
                        msg.getAfterId(),
                        msg.getThreadParentId()
                );
            }
            case TYPING_START -> eventListener.onTypingUpdated(msg.getSender(), msg.getChannel(), true);
            case TYPING_STOP -> eventListener.onTypingUpdated(msg.getSender(), msg.getChannel(), false);
            case PROFILE_RESPONSE -> {
                if (msg.getProfile() != null && msg.getProfile().getPublicKeyBase64() != null) {
                    registerPeerPublicKey(msg.getProfile().getUsername(), msg.getProfile().getPublicKeyBase64());
                }
                eventListener.onProfileReceived(msg.getProfile());
            }
            case MESSAGE_EMBED_UPDATE -> eventListener.onMessageEmbedUpdated(msg.getId(), msg.getChannel(), msg.getEmbed());
            case POLL_VOTE -> eventListener.onPollUpdated(msg.getId(), msg.getChannel(), msg.getPoll());
            case WEBHOOK_LIST -> eventListener.onWebhookListReceived(msg.getChannel(), msg.getWebhooks());
            case SEARCH_RESPONSE -> eventListener.onSearchResultsReceived(msg.getContent(), msg.getHistory());
            case KEY_EXCHANGE -> {
                if (msg.getSender() != null && msg.getSenderPublicKey() != null) {
                    registerPeerPublicKey(msg.getSender(), msg.getSenderPublicKey());
                    eventListener.onKeyExchangeReceived(msg.getSender(), msg.getSenderPublicKey());
                }
            }
            case WHITEBOARD_EVENT -> eventListener.onWhiteboardEvent(msg.getChannel(), msg.getSender(), msg.getContent());
            case VOICE_STATE -> eventListener.onVoiceStateUpdated(msg);
            case VOICE_AUDIO -> eventListener.onVoiceAudioReceived(msg.getChannel(), msg.getSender(), msg.getContent());
            case WEBRTC_SIGNAL -> eventListener.onWebrtcSignalReceived(msg);
            case PRIVATE -> {
                if (Boolean.TRUE.equals(msg.getEncrypted())) {
                    decryptIncomingPrivate(msg);
                }
                eventListener.onMessageReceived(msg);
            }
            default -> eventListener.onMessageReceived(msg);
        }
    }

    private void trackHistoryMaxId(String channel, List<Message> history) {
        if (channel == null || history == null) return;
        for (Message m : history) {
            if (m.getId() != null) {
                lastKnownMessageIdPerChannel.merge(channel, m.getId(), Math::max);
            }
        }
    }

    private void registerPeerPublicKey(String peer, String pubKeyBase64) {
        if (peer == null || pubKeyBase64 == null || e2eeKeyPair == null) return;
        String key = peer.trim().toLowerCase();
        String prev = peerPublicKeys.put(key, pubKeyBase64);
        if (!pubKeyBase64.equals(prev)) {
            try {
                PublicKey peerPub = E2EECrypto.decodePublicKey(pubKeyBase64);
                SecretKey shared = E2EECrypto.deriveSharedKey(e2eeKeyPair.getPrivate(), peerPub);
                sharedSecretCache.put(key, shared);
            } catch (Exception ignored) {
            }
        }
    }

    private void decryptIncomingPrivate(Message msg) {
        try {
            if (msg.getSenderPublicKey() != null && msg.getSender() != null
                    && !msg.getSender().equalsIgnoreCase(this.username)) {
                registerPeerPublicKey(msg.getSender(), msg.getSenderPublicKey());
            }
            String peerName = (msg.getSender() != null && msg.getSender().equalsIgnoreCase(this.username))
                    ? msg.getTarget()
                    : msg.getSender();
            if (peerName == null) return;
            SecretKey key = sharedSecretCache.get(peerName.trim().toLowerCase());
            if (key != null && msg.getIv() != null && msg.getContent() != null) {
                String plain = E2EECrypto.decrypt(msg.getContent(), msg.getIv(), key);
                msg.setContent(plain);
            } else {
                msg.setContent("[Mensaje E2EE cifrado - clave pendiente]");
            }
        } catch (Exception e) {
            msg.setContent("[No se pudo descifrar el mensaje E2EE: " + e.getMessage() + "]");
        }
    }

    // =========================================================================
    // ENVÍO OPTIMISTA, COLA OFFLINE Y CIFRADO E2EE
    // =========================================================================

    public synchronized void sendChatMessage(String content) {
        sendChatMessage(this.currentChannel, content);
    }

    public synchronized void sendChatMessage(String channel, String content) {
        if (content == null || content.trim().isEmpty()) return;
        Message msg = Message.chat(
                channel != null && !channel.trim().isEmpty() ? channel.trim() : this.currentChannel,
                this.username,
                content.trim()
        );
        sendOptimisticOrQueue(msg);
    }

    public synchronized String sendRichMessage(Message msg) {
        return sendOptimisticOrQueue(msg);
    }

    public synchronized String sendOptimisticOrQueue(Message msg) {
        if (msg == null) return null;
        if (msg.getTempId() == null || msg.getTempId().isBlank()) {
            msg.setTempId(UUID.randomUUID().toString());
        }
        if (msg.getSender() == null) {
            msg.setSender(this.username);
        }
        if (msg.getChannel() == null && msg.getType() == MessageType.CHAT) {
            msg.setChannel(this.currentChannel);
        }

        if (!isConnected() || writer == null) {
            if (this.username != null && !manualDisconnect) {
                offlineQueue.offer(msg);
                return msg.getTempId();
            }
            if (eventListener != null) {
                eventListener.onMessageSendError(msg.getTempId(), msg, "Desconectado del servidor.");
            }
            return msg.getTempId();
        }

        sendTracked(msg);
        return msg.getTempId();
    }

    private void sendTracked(Message msg) {
        if (msg.getType() == MessageType.CHAT || msg.getType() == MessageType.PRIVATE) {
            pendingAcks.put(msg.getTempId(), new PendingAck(msg, System.currentTimeMillis()));
        }
        writer.println(msg.toJson());
    }

    public synchronized void retryFailedMessage(Message failedMsg) {
        if (failedMsg == null) return;
        sendOptimisticOrQueue(failedMsg);
    }

    public void cancelPendingMessage(String tempId) {
        if (tempId != null) {
            pendingAcks.remove(tempId);
        }
    }

    public synchronized void sendPrivateMessage(String target, String content) {
        if (target == null || content == null) return;
        Message msg = Message.privateMsg(this.username, target.trim(), content.trim());
        sendOptimisticOrQueue(msg);
    }

    public synchronized boolean sendEncryptedPrivateMessage(String target, String plaintext) {
        if (target == null || plaintext == null || e2eeKeyPair == null) return false;
        SecretKey shared = sharedSecretCache.get(target.trim().toLowerCase());
        if (shared == null) {
            return false;
        }
        try {
            E2EECrypto.EncryptedPayload enc = E2EECrypto.encrypt(plaintext, shared);
            Message msg = Message.privateMsg(this.username, target.trim(), enc.ciphertextBase64());
            msg.setEncrypted(true);
            msg.setIv(enc.ivBase64());
            msg.setSenderPublicKey(E2EECrypto.encodePublicKey(e2eeKeyPair.getPublic()));
            sendOptimisticOrQueue(msg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasPeerPublicKey(String peer) {
        return peer != null && sharedSecretCache.containsKey(peer.trim().toLowerCase());
    }

    public String getMyPublicKeyBase64() {
        return e2eeKeyPair != null ? E2EECrypto.encodePublicKey(e2eeKeyPair.getPublic()) : null;
    }

    public String getPeerPublicKeyBase64(String peer) {
        return peer != null ? peerPublicKeys.get(peer.trim().toLowerCase()) : null;
    }

    // =========================================================================
    // CANALES, TYPING (CON THROTTLING 2.5s), REACCIONES, PRESENCIA Y MAS
    // =========================================================================

    public synchronized void switchChannel(String newChannel) {
        if (!isConnected() || writer == null || newChannel == null || newChannel.trim().isEmpty()) {
            return;
        }
        String target = newChannel.trim();
        if (!target.startsWith("#")) {
            target = "#" + target;
        }
        this.currentChannel = target;
        sendRaw(Message.joinChannel(this.username, target));
        if (eventListener != null) {
            eventListener.onChannelChanged(target);
        }
    }

    public synchronized void notifyUserTypingThrottled() {
        if (!isConnected()) return;
        long now = System.currentTimeMillis();
        if (now - lastTypingSentMs >= TYPING_THROTTLE_MS) {
            lastTypingSentMs = now;
            sendRaw(Message.typingStart(this.username, this.currentChannel));
        }
    }

    public synchronized void sendTyping(boolean isTyping) {
        if (!isConnected() || writer == null) return;
        if (isTyping) {
            lastTypingSentMs = System.currentTimeMillis();
            sendRaw(Message.typingStart(this.username, this.currentChannel));
        } else {
            lastTypingSentMs = 0L;
            sendRaw(Message.typingStop(this.username, this.currentChannel));
        }
    }

    public synchronized void updateProfile(UserProfile profile) {
        if (profile == null) return;
        if (e2eeKeyPair != null && profile.getPublicKeyBase64() == null) {
            profile.setPublicKeyBase64(E2EECrypto.encodePublicKey(e2eeKeyPair.getPublic()));
        }
        sendRaw(Message.profileUpdate(this.username, profile));
    }

    public synchronized void requestProfile(String targetUsername) {
        if (targetUsername != null) {
            sendRaw(Message.profileRequest(this.username, targetUsername.trim()));
        }
    }

    public synchronized void requestUserList() {
        Message msg = new Message();
        msg.setType(MessageType.USER_LIST);
        sendRaw(msg);
    }

    public synchronized void requestChannelList() {
        Message msg = new Message();
        msg.setType(MessageType.CHANNEL_LIST);
        sendRaw(msg);
    }

    public synchronized void deleteMessage(Long messageId, String channel) {
        if (messageId != null) {
            sendRaw(Message.deleteMsg(messageId, channel != null ? channel : currentChannel, username));
        }
    }

    public synchronized void sendReaction(Long messageId, String channel, String emoji, boolean add) {
        if (messageId == null || emoji == null || emoji.isBlank()) return;
        Message msg = add
                ? Message.reactionAdd(messageId, channel, username, emoji)
                : Message.reactionRemove(messageId, channel, username, emoji);
        sendRaw(msg);
    }

    public synchronized void updatePresence(PresenceStatus status, String customStatus, String statusSymbol) {
        sendRaw(Message.presenceUpdate(username, status, customStatus, statusSymbol));
    }

    public synchronized void requestOlderHistory(String channel, long beforeId, int limit) {
        sendRaw(Message.historyPageRequest(username, channel, beforeId, null, limit));
    }

    public synchronized void requestMessagesAfter(String channel, long afterId, int limit) {
        sendRaw(Message.historyPageRequest(username, channel, null, afterId, limit));
    }

    /**
     * Cierra abruptamente el socket subyacente sin marcar manualDisconnect
     * para simular una caída de red real (reconexión y resync automáticos).
     */
    public void simulateUnexpectedDrop() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized void requestThreadHistory(String channel, long threadParentId) {
        Message req = Message.historyPageRequest(username, channel, null, null, 200);
        req.setThreadParentId(threadParentId);
        sendRaw(req);
    }

    public synchronized void createChannel(ChannelInfo info) {
        if (info != null) {
            sendRaw(Message.channelCreate(username, info));
        }
    }

    public synchronized void deleteChannel(String channelName) {
        if (channelName != null && !channelName.isBlank()) {
            sendRaw(Message.channelDelete(username, channelName));
        }
    }

    public synchronized void updateRoleOrOverride(String targetUser, String role, Long permissions, ChannelInfo channelOverrideUpdate) {
        sendRaw(Message.roleUpdate(username, targetUser, role, permissions, channelOverrideUpdate));
    }

    public synchronized void votePoll(String channel, Long messageId, int optionIndex) {
        sendRaw(Message.pollVote(channel, username, messageId, optionIndex));
    }

    public synchronized void createWebhook(String channel, String name) {
        sendRaw(Message.webhookCreate(username, channel, name));
    }

    public synchronized void deleteWebhook(String channel, String webhookId) {
        sendRaw(Message.webhookDelete(username, channel, webhookId));
    }

    public synchronized void listWebhooks(String channel) {
        Message req = new Message();
        req.setType(MessageType.WEBHOOK_LIST);
        req.setSender(username);
        req.setChannel(channel);
        sendRaw(req);
    }

    public synchronized void searchMessages(String query) {
        sendRaw(Message.searchRequest(username, query));
    }

    public synchronized void sendWhiteboardEvent(String channel, String payloadJson) {
        sendRaw(Message.whiteboardEvent(channel != null ? channel : currentChannel, username, payloadJson));
    }

    public synchronized void sendVoiceState(String channel, boolean muted, boolean deafened, double level, boolean leave) {
        Message msg = Message.voiceState(channel, username, muted, deafened, level, null);
        if (leave) {
            msg.setContent("LEAVE");
        }
        sendRaw(msg);
    }

    public synchronized void sendVoiceAudio(String channel, String base64Pcm) {
        sendRaw(Message.voiceAudio(channel, username, base64Pcm));
    }

    public synchronized void sendWebrtcSignal(String target, String channel, String signalType, String payload) {
        sendRaw(Message.webrtcSignal(username, target, channel, signalType, payload));
    }

    private synchronized void sendRaw(Message msg) {
        if (isConnected() && writer != null && msg != null) {
            writer.println(msg.toJson());
        }
    }

    public synchronized void disconnect() {
        this.manualDisconnect = true;
        this.autoReconnect = false;
        stopMaintenanceTasks();
        pendingAcks.clear();
        if (!connected.compareAndSet(true, false)) {
            return;
        }
        try {
            if (writer != null && this.username != null) {
                writer.println(Message.leave(this.username).toJson());
            }
        } catch (Exception ignored) {
        }
        closeResources();
        if (eventListener != null) {
            eventListener.onDisconnected("Desconectado de la sala.");
        }
    }

    private void closeResources() {
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

    private record PendingAck(Message message, long sentAtMs) {
    }
}
