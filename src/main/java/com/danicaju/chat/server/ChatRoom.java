package com.danicaju.chat.server;

import com.danicaju.chat.db.DatabaseManager;
import com.danicaju.chat.model.ChannelInfo;
import com.danicaju.chat.model.EmbedData;
import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.PollData;
import com.danicaju.chat.model.PresenceStatus;
import com.danicaju.chat.model.UserProfile;
import com.danicaju.chat.model.WebhookInfo;
import com.danicaju.chat.security.Permission;
import com.danicaju.chat.service.OpenGraphService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

/**
 * Gestor central thread-safe de la sala, canales, persistencia H2,
 * permisos bitfield, presencia, hilos, reacciones, polls, webhooks, pizarra y voz.
 */
public class ChatRoom {

    public static final String DEFAULT_CHANNEL = Message.DEFAULT_CHANNEL;
    public static final int MAX_HISTORY_PER_CHANNEL = 50;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{2,20}$");
    private static final Pattern CHANNEL_PATTERN = Pattern.compile("^#[a-zA-Z0-9_-]{1,30}$");

    private final DatabaseManager db;
    private final OpenGraphService openGraphService;
    private String webhookBaseUrl = "http://127.0.0.1:5001";

    private final ConcurrentSkipListMap<String, ClientHandler> clients =
            new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    private final ConcurrentHashMap<String, String> userChannels = new ConcurrentHashMap<>();
    private final ConcurrentSkipListMap<String, UserProfile> userProfiles =
            new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    private final ConcurrentSkipListMap<String, ChannelInfo> channelsMap =
            new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    private final ConcurrentHashMap<String, Set<String>> voiceParticipants = new ConcurrentHashMap<>();

    public ChatRoom() {
        this(DatabaseManager.createInMemory("chatroom_" + UUID.randomUUID().toString().replace("-", "")));
    }

    public ChatRoom(DatabaseManager db) {
        this(db, new OpenGraphService(false));
    }

    public ChatRoom(DatabaseManager db, OpenGraphService openGraphService) {
        this.db = db;
        this.openGraphService = openGraphService;
        loadFromDatabase();
    }

    private void loadFromDatabase() {
        for (ChannelInfo info : db.getAllChannels()) {
            channelsMap.put(info.getName(), info);
        }
        for (UserProfile p : db.getAllUserProfiles()) {
            p.setPresence(PresenceStatus.OFFLINE);
            userProfiles.put(p.getUsername(), p);
        }
    }

    public DatabaseManager getDatabase() {
        return db;
    }

    public void setWebhookBaseUrl(String webhookBaseUrl) {
        if (webhookBaseUrl != null && !webhookBaseUrl.isBlank()) {
            this.webhookBaseUrl = webhookBaseUrl.replaceAll("/+$", "");
        }
    }

    public String getWebhookBaseUrl() {
        return webhookBaseUrl;
    }

    // =========================================================================
    // VALIDACIONES Y REGISTRO DE CLIENTES
    // =========================================================================

    public boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    public static boolean isValidChannelName(String channel) {
        if (channel == null || channel.trim().isEmpty()) {
            return false;
        }
        String c = channel.trim().toLowerCase(Locale.ROOT);
        if (!c.startsWith("#")) {
            c = "#" + c;
        }
        return CHANNEL_PATTERN.matcher(c).matches();
    }

    public static String normalizeChannel(String channel) {
        if (channel == null || channel.trim().isEmpty()) {
            return DEFAULT_CHANNEL;
        }
        String c = channel.trim().toLowerCase(Locale.ROOT);
        if (!c.startsWith("#")) {
            c = "#" + c;
        }
        return isValidChannelName(c) ? c : DEFAULT_CHANNEL;
    }

    public synchronized boolean registerClient(String username, ClientHandler handler) {
        if (!isValidUsername(username) || handler == null) {
            return false;
        }
        String cleanUsername = username.trim();
        if (clients.putIfAbsent(cleanUsername, handler) == null) {
            userChannels.put(cleanUsername, DEFAULT_CHANNEL);
            UserProfile profile = userProfiles.get(cleanUsername);
            if (profile == null) {
                profile = db.getUserProfile(cleanUsername);
            }
            if (profile == null) {
                profile = new UserProfile(cleanUsername);
                if (db.countAdminUsers() == 0) {
                    profile.setRole(Permission.ROLE_ADMIN);
                    profile.setPermissions(Permission.DEFAULT_ADMIN);
                }
            } else {
                profile.setUsername(cleanUsername);
                if (db.countAdminUsers() == 0) {
                    profile.setRole(Permission.ROLE_ADMIN);
                    profile.setPermissions(Permission.DEFAULT_ADMIN);
                }
            }
            profile.setPresence(PresenceStatus.ONLINE);
            userProfiles.put(cleanUsername, profile);
            db.upsertUserProfile(profile);
            return true;
        }
        return false;
    }

    public boolean unregisterClient(String username, ClientHandler handler) {
        if (username == null || handler == null) {
            return false;
        }
        String cleanUsername = username.trim();
        boolean removed = clients.remove(cleanUsername, handler);
        if (removed) {
            userChannels.remove(cleanUsername);
            leaveAllVoiceChannels(cleanUsername);
            UserProfile profile = userProfiles.get(cleanUsername);
            if (profile != null) {
                profile.setPresence(PresenceStatus.OFFLINE);
                db.upsertUserProfile(profile);
                broadcastToAll(Message.presenceUpdate(cleanUsername, PresenceStatus.OFFLINE,
                        profile.getCustomStatus(), profile.getStatusSymbol()), null);
            }
        }
        return removed;
    }

    public boolean isUsernameTaken(String username) {
        if (username == null) {
            return false;
        }
        return clients.containsKey(username.trim());
    }

    // =========================================================================
    // PERMISOS BITFIELD
    // =========================================================================

    public long getEffectivePermissions(String username, String channel) {
        UserProfile profile = getUserProfile(username);
        long base = profile != null ? profile.getPermissions() : Permission.DEFAULT_MEMBER;
        ChannelInfo ch = channelsMap.get(normalizeChannel(channel));
        if (ch == null) {
            return base;
        }
        long allow = ch.getDefaultAllowMask();
        long deny = ch.getDefaultDenyMask();
        if (username != null && ch.getUserOverrides() != null) {
            for (Map.Entry<String, ChannelInfo.ChannelOverride> entry : ch.getUserOverrides().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(username.trim()) && entry.getValue() != null) {
                    allow |= entry.getValue().getAllow();
                    deny |= entry.getValue().getDeny();
                    break;
                }
            }
        }
        return Permission.computeEffectivePermissions(base, allow, deny);
    }

    public boolean hasChannelPermission(String username, String channel, long permission) {
        return Permission.hasPermission(getEffectivePermissions(username, channel), permission);
    }

    public Set<String> getAccessibleChannels(String username) {
        Set<String> set = new LinkedHashSet<>();
        for (ChannelInfo ch : channelsMap.values()) {
            if (hasChannelPermission(username, ch.getName(), Permission.VIEW_CHANNEL)) {
                set.add(ch.getName().toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    // =========================================================================
    // CANALES
    // =========================================================================

    public String getUserChannel(String username) {
        if (username == null) {
            return DEFAULT_CHANNEL;
        }
        return userChannels.getOrDefault(username.trim(), DEFAULT_CHANNEL);
    }

    public String switchUserChannel(String username, String channel) {
        if (username == null) {
            return DEFAULT_CHANNEL;
        }
        String normalized = normalizeChannel(channel);
        registerChannel(normalized);
        userChannels.put(username.trim(), normalized);
        return normalized;
    }

    public void registerChannel(String channel) {
        if (isValidChannelName(channel)) {
            String normalized = normalizeChannel(channel);
            channelsMap.computeIfAbsent(normalized, name -> {
                ChannelInfo info = new ChannelInfo(name, ChannelInfo.TYPE_TEXT, "Canal de texto", "system");
                db.saveChannel(info);
                return info;
            });
        }
    }

    public boolean createChannel(String actor, ChannelInfo requested) {
        if (requested == null || !isValidChannelName(requested.getName())) {
            return false;
        }
        if (!hasChannelPermission(actor, DEFAULT_CHANNEL, Permission.MANAGE_CHANNELS)) {
            return false;
        }
        String normalized = normalizeChannel(requested.getName());
        if (channelsMap.containsKey(normalized)) {
            return false;
        }
        ChannelInfo created = new ChannelInfo(
                normalized,
                requested.getType(),
                requested.getTopic(),
                actor
        );
        created.setDefaultAllowMask(requested.getDefaultAllowMask());
        created.setDefaultDenyMask(requested.getDefaultDenyMask());
        channelsMap.put(normalized, created);
        db.saveChannel(created);
        broadcastChannelList();
        return true;
    }

    public boolean deleteChannel(String actor, String rawChannelName) {
        String normalized = normalizeChannel(rawChannelName);
        if (DEFAULT_CHANNEL.equalsIgnoreCase(normalized)) {
            return false;
        }
        if (!hasChannelPermission(actor, normalized, Permission.MANAGE_CHANNELS)) {
            return false;
        }
        if (channelsMap.remove(normalized) == null) {
            return false;
        }
        db.deleteChannel(normalized);
        voiceParticipants.remove(normalized);

        // Mover usuarios conectados en el canal eliminado a #general
        for (Map.Entry<String, String> entry : userChannels.entrySet()) {
            if (normalized.equalsIgnoreCase(entry.getValue())) {
                entry.setValue(DEFAULT_CHANNEL);
                ClientHandler chHandler = clients.get(entry.getKey());
                if (chHandler != null) {
                    chHandler.deliverBroadcast(Message.joinChannel("SERVER", DEFAULT_CHANNEL));
                    chHandler.deliverBroadcast(Message.system(DEFAULT_CHANNEL,
                            "El canal " + normalized + " fue eliminado. Has sido movido a " + DEFAULT_CHANNEL + "."));
                }
            }
        }
        broadcastChannelList();
        broadcastChannelUserList(DEFAULT_CHANNEL);
        return true;
    }

    public List<String> getAvailableChannels() {
        List<String> list = new ArrayList<>();
        for (ChannelInfo info : channelsMap.values()) {
            list.add(info.getName());
        }
        list.sort((a, b) -> {
            if (a.equals(DEFAULT_CHANNEL)) return -1;
            if (b.equals(DEFAULT_CHANNEL)) return 1;
            return a.compareToIgnoreCase(b);
        });
        return Collections.unmodifiableList(list);
    }

    public List<ChannelInfo> getChannelInfos() {
        List<ChannelInfo> list = new ArrayList<>(channelsMap.values());
        list.sort((a, b) -> {
            if (a.getName().equals(DEFAULT_CHANNEL)) return -1;
            if (b.getName().equals(DEFAULT_CHANNEL)) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return Collections.unmodifiableList(list);
    }

    // =========================================================================
    // HISTORIAL, CURSORES Y ENVÍO DE MENSAJES
    // =========================================================================

    public void recordChannelMessage(String channel, Message message) {
        if (message == null) return;
        String normalized = normalizeChannel(channel);
        message.setChannel(normalized);
        db.insertMessage(message);
    }

    public List<Message> getChannelHistory(String channel) {
        String normalized = normalizeChannel(channel);
        return db.getMessagesCursor(normalized, null, null, MAX_HISTORY_PER_CHANNEL).messages();
    }

    public DatabaseManager.CursorPage getChannelHistoryCursor(String channel, Long beforeId, Long afterId, int limit) {
        return db.getMessagesCursor(normalizeChannel(channel), beforeId, afterId, limit);
    }

    public List<Message> getThreadHistory(long rootMessageId) {
        return db.getThreadMessages(rootMessageId);
    }

    /**
     * Persiste un mensaje de canal enviado por un cliente, envía ACK inmediato con id real
     * y lanza extracción asíncrona de metadatos OpenGraph si detecta URLs.
     */
    public Message dispatchClientChannelMessage(ClientHandler senderHandler, Message incoming) {
        String channel = normalizeChannel(incoming.getChannel());
        incoming.setChannel(channel);

        String sender = incoming.getSender();
        long required = (incoming.getThreadParentId() != null || incoming.getReplyToId() != null)
                ? (Permission.SEND_MESSAGES | Permission.CREATE_THREADS)
                : Permission.SEND_MESSAGES;
        if (!hasChannelPermission(sender, channel, required)) {
            if (senderHandler != null) {
                senderHandler.deliverBroadcast(Message.system("ERROR: No tienes permiso para enviar mensajes en " + channel));
            }
            return null;
        }

        // Poblar preview de reply desde BD si falta
        if (incoming.getReplyToId() != null && incoming.getReplyPreview() == null) {
            Message parent = db.getMessageById(incoming.getReplyToId());
            if (parent != null) {
                incoming.setReplySender(parent.getSender());
                String txt = parent.getContent() != null ? parent.getContent() : "";
                incoming.setReplyPreview(txt.length() > 90 ? txt.substring(0, 87) + "..." : txt);
            }
        }

        Message saved = db.insertMessage(incoming);

        if (senderHandler != null && saved.getTempId() != null) {
            senderHandler.deliverBroadcast(Message.ack(saved.getTempId(), saved.getId(), channel, saved.getTimestamp()));
        }

        broadcast(saved, null);
        scheduleAsyncEmbed(saved);
        return saved;
    }

    /**
     * Usado por Webhooks HTTP externos u otros emisores del servidor.
     */
    public Message dispatchExternalChannelMessage(Message msg) {
        String channel = normalizeChannel(msg.getChannel());
        msg.setChannel(channel);
        registerChannel(channel);
        Message saved = db.insertMessage(msg);
        broadcast(saved, null);
        if (saved.getEmbed() == null) {
            scheduleAsyncEmbed(saved);
        }
        return saved;
    }

    private void scheduleAsyncEmbed(Message saved) {
        if (saved == null || saved.getId() == null || saved.getEmbed() != null
                || Boolean.TRUE.equals(saved.getEncrypted()) || saved.getContent() == null) {
            return;
        }
        if (OpenGraphService.extractFirstUrl(saved.getContent()) == null) {
            return;
        }
        openGraphService.fetchEmbedFromTextAsync(saved.getContent()).thenAccept(embed -> {
            if (embed != null) {
                db.updateEmbed(saved.getId(), embed);
                broadcast(Message.embedUpdate(saved.getId(), saved.getChannel(), embed), null);
            }
        });
    }

    public boolean deleteMessage(String actor, Long messageId, String channel) {
        if (messageId == null) return false;
        Message existing = db.getMessageById(messageId);
        if (existing == null) return false;

        boolean isAuthor = existing.getSender() != null && existing.getSender().equalsIgnoreCase(actor);
        boolean canManage = hasChannelPermission(actor, existing.getChannel(), Permission.MANAGE_MESSAGES);
        if (!isAuthor && !canManage) {
            return false;
        }
        if (db.deleteMessage(messageId)) {
            broadcast(Message.deleteMsg(messageId, existing.getChannel(), actor), null);
            return true;
        }
        return false;
    }

    public void processReactionDelta(String actor, Long messageId, String channel, String emoji, boolean add) {
        if (messageId == null || emoji == null || emoji.isBlank()) return;
        Map<String, List<String>> updated = db.updateReaction(messageId, emoji, actor, add);
        if (updated != null) {
            Message msg = db.getMessageById(messageId);
            String targetChannel = msg != null ? msg.getChannel() : normalizeChannel(channel);
            Message delta = add
                    ? Message.reactionAdd(messageId, targetChannel, actor, emoji)
                    : Message.reactionRemove(messageId, targetChannel, actor, emoji);
            delta.setReactions(updated);
            broadcast(delta, null);
        }
    }

    public void processPollVote(String actor, Long messageId, int optionIndex) {
        if (messageId == null) return;
        PollData updated = db.votePoll(messageId, optionIndex, actor);
        if (updated != null) {
            Message orig = db.getMessageById(messageId);
            String ch = orig != null ? orig.getChannel() : DEFAULT_CHANNEL;
            Message event = Message.pollVote(ch, actor, messageId, optionIndex);
            event.setPoll(updated);
            broadcast(event, null);
        }
    }

    // =========================================================================
    // DIFUSIÓN Y MENSAJERÍA PRIVADA (CON E2EE)
    // =========================================================================

    public void broadcast(Message message, ClientHandler exclude) {
        if (message == null) {
            return;
        }
        String targetChannel = message.getChannel();
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            ClientHandler client = entry.getValue();
            if (client != null && client != exclude) {
                if (targetChannel == null || targetChannel.equalsIgnoreCase(getUserChannel(entry.getKey()))) {
                    client.deliverBroadcast(message);
                }
            }
        }
    }

    public void broadcastToAll(Message message, ClientHandler exclude) {
        if (message == null) {
            return;
        }
        for (ClientHandler client : clients.values()) {
            if (client != null && client != exclude) {
                client.deliverBroadcast(message);
            }
        }
    }

    public boolean sendPrivate(String targetUsername, Message message) {
        return sendPrivate(targetUsername, null, message);
    }

    public boolean sendPrivate(String targetUsername, ClientHandler senderHandler, Message message) {
        if (targetUsername == null || message == null) {
            return false;
        }
        ClientHandler targetClient = clients.get(targetUsername.trim());
        if (targetClient == null) {
            return false;
        }
        Message saved = db.insertMessage(message);
        if (senderHandler != null && saved.getTempId() != null) {
            senderHandler.deliverBroadcast(Message.ack(saved.getTempId(), saved.getId(), null, saved.getTimestamp()));
        }
        targetClient.deliverBroadcast(saved);
        return true;
    }

    public boolean relayDirectFrame(String targetUsername, Message message) {
        if (targetUsername == null || message == null) return false;
        ClientHandler target = clients.get(targetUsername.trim());
        if (target != null) {
            target.deliverBroadcast(message);
            return true;
        }
        return false;
    }

    // =========================================================================
    // PRESENCIA, TYPING, PERFILES Y ROLES
    // =========================================================================

    public void updatePresence(String username, PresenceStatus status, String customStatus, String statusSymbol) {
        if (username == null) return;
        String clean = username.trim();
        UserProfile p = getUserProfile(clean);
        p.setPresence(status != null ? status : PresenceStatus.ONLINE);
        p.setCustomStatus(customStatus);
        p.setStatusSymbol(statusSymbol);
        db.upsertUserProfile(p);
        broadcastToAll(Message.presenceUpdate(clean, p.getPresence(), p.getCustomStatus(), p.getStatusSymbol()), null);
        broadcastUserListSnapshot();
    }

    public boolean applyRoleOrOverrideUpdate(String actor, Message req) {
        // Caso A: Override por canal
        if (req.getChannelInfo() != null && req.getChannelInfo().getName() != null) {
            String chName = normalizeChannel(req.getChannelInfo().getName());
            if (!hasChannelPermission(actor, chName, Permission.MANAGE_CHANNELS)
                    && !hasChannelPermission(actor, chName, Permission.MANAGE_ROLES)) {
                return false;
            }
            ChannelInfo current = channelsMap.get(chName);
            if (current == null) return false;
            ChannelInfo incoming = req.getChannelInfo();
            current.setDefaultAllowMask(incoming.getDefaultAllowMask());
            current.setDefaultDenyMask(incoming.getDefaultDenyMask());
            if (incoming.getUserOverrides() != null) {
                current.setUserOverrides(incoming.getUserOverrides());
            }
            db.saveChannel(current);
            broadcastChannelList();
            return true;
        }

        // Caso B: Rol global o bitfield de un usuario
        if (req.getTarget() != null && !req.getTarget().isBlank()) {
            if (!hasChannelPermission(actor, DEFAULT_CHANNEL, Permission.MANAGE_ROLES)) {
                return false;
            }
            String target = req.getTarget().trim();
            UserProfile targetProf = userProfiles.get(target);
            if (targetProf == null) {
                targetProf = db.getUserProfile(target);
            }
            if (targetProf == null) {
                targetProf = new UserProfile(target);
            }
            if (req.getRole() != null) {
                String normRole = Permission.normalizeRole(req.getRole());
                targetProf.setRole(normRole);
                targetProf.setPermissions(req.getPermissions() != null
                        ? req.getPermissions()
                        : Permission.getDefaultPermissionsForRole(normRole));
            } else if (req.getPermissions() != null) {
                targetProf.setPermissions(req.getPermissions());
            }
            userProfiles.put(targetProf.getUsername(), targetProf);
            db.upsertUserProfile(targetProf);
            broadcastToAll(Message.profileResponse(targetProf), null);
            broadcastUserListSnapshot();
            return true;
        }
        return false;
    }

    public List<String> getActiveUsers() {
        return Collections.unmodifiableList(new ArrayList<>(clients.keySet()));
    }

    public List<String> getActiveUsersInChannel(String channel) {
        String normalized = normalizeChannel(channel);
        List<String> list = new ArrayList<>();
        for (String user : clients.keySet()) {
            if (normalized.equalsIgnoreCase(getUserChannel(user))) {
                list.add(user);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public ClientHandler getClient(String username) {
        if (username == null) {
            return null;
        }
        return clients.get(username.trim());
    }

    public void broadcastUserList() {
        Message listMessage = Message.userList(getActiveUsers());
        listMessage.setUserProfiles(buildOnlineProfilesSnapshot());
        broadcastToAll(listMessage, null);
    }

    public void broadcastUserListSnapshot() {
        for (Map.Entry<String, String> chEntry : getDistinctActiveChannels().entrySet()) {
            broadcastChannelUserList(chEntry.getKey());
        }
    }

    private Map<String, String> getDistinctActiveChannels() {
        Map<String, String> set = new LinkedHashMap<>();
        for (String ch : userChannels.values()) {
            set.put(ch, ch);
        }
        return set;
    }

    public void broadcastChannelUserList(String channel) {
        String normalized = normalizeChannel(channel);
        Message listMessage = Message.userList(normalized, getActiveUsersInChannel(normalized));
        listMessage.setUserProfiles(buildOnlineProfilesSnapshot());
        broadcast(listMessage, null);
    }

    public Map<String, UserProfile> buildOnlineProfilesSnapshot() {
        Map<String, UserProfile> map = new LinkedHashMap<>();
        for (String user : clients.keySet()) {
            map.put(user, getUserProfile(user));
        }
        return map;
    }

    public void broadcastChannelList() {
        broadcastToAll(Message.channelList(getAvailableChannels()), null);
        broadcastToAll(Message.channelMetadata(getChannelInfos()), null);
    }

    public void broadcastTyping(String channel, String sender, boolean isTyping, ClientHandler exclude) {
        String normalized = normalizeChannel(channel);
        Message typingMsg = isTyping
                ? Message.typingStart(sender, normalized)
                : Message.typingStop(sender, normalized);
        broadcast(typingMsg, exclude);
    }

    public UserProfile getUserProfile(String username) {
        if (username == null) {
            return new UserProfile();
        }
        String clean = username.trim();
        return userProfiles.computeIfAbsent(clean, u -> {
            UserProfile fromDb = db.getUserProfile(u);
            return fromDb != null ? fromDb : new UserProfile(u);
        });
    }

    public void updateUserProfile(String username, UserProfile incoming) {
        if (username != null && incoming != null) {
            String clean = username.trim();
            UserProfile current = getUserProfile(clean);
            // Preservar rol y permisos autoritativos del servidor (evitar escalación de privilegios)
            incoming.setUsername(clean);
            incoming.setRole(current.getRole());
            incoming.setPermissions(current.getPermissions());
            if (incoming.getPublicKeyBase64() == null) {
                incoming.setPublicKeyBase64(current.getPublicKeyBase64());
            }
            userProfiles.put(clean, incoming);
            db.upsertUserProfile(incoming);
            broadcastUserListSnapshot();
        }
    }

    public void updateUserPublicKey(String username, String publicKeyBase64) {
        if (username == null || publicKeyBase64 == null) return;
        UserProfile p = getUserProfile(username);
        p.setPublicKeyBase64(publicKeyBase64);
        db.upsertUserProfile(p);
    }

    // =========================================================================
    // WEBHOOKS
    // =========================================================================

    public WebhookInfo createWebhook(String actor, String channel, String name) {
        String normalized = normalizeChannel(channel);
        if (!hasChannelPermission(actor, normalized, Permission.MANAGE_WEBHOOKS)) {
            return null;
        }
        WebhookInfo info = db.createWebhook(normalized, (name == null || name.isBlank()) ? "Webhook" : name.trim(), actor);
        info.setUrl(webhookBaseUrl + "/api/webhooks/" + info.getId() + "/" + info.getToken());
        return info;
    }

    public boolean deleteWebhook(String actor, String channel, String webhookId) {
        if (!hasChannelPermission(actor, normalizeChannel(channel), Permission.MANAGE_WEBHOOKS)) {
            return false;
        }
        return db.deleteWebhook(webhookId);
    }

    public List<WebhookInfo> listWebhooks(String channel) {
        List<WebhookInfo> list = db.getWebhooksForChannel(normalizeChannel(channel));
        for (WebhookInfo w : list) {
            w.setUrl(webhookBaseUrl + "/api/webhooks/" + w.getId() + "/" + w.getToken());
        }
        return list;
    }

    public WebhookInfo validateWebhook(String id, String token) {
        return db.validateWebhook(id, token);
    }

    // =========================================================================
    // SALAS DE VOZ Y PIZARRA COLABORATIVA
    // =========================================================================

    public void handleVoiceState(String username, Message msg) {
        String ch = normalizeChannel(msg.getChannel());
        boolean leaving = msg.getVoiceMuted() == null && msg.getVoiceDeafened() == null && "LEAVE".equalsIgnoreCase(msg.getContent());
        if (leaving) {
            leaveAllVoiceChannels(username);
            return;
        }
        Set<String> members = voiceParticipants.computeIfAbsent(ch, k -> new CopyOnWriteArraySet<>());
        members.add(username);
        Message state = Message.voiceState(
                ch,
                username,
                Boolean.TRUE.equals(msg.getVoiceMuted()),
                Boolean.TRUE.equals(msg.getVoiceDeafened()),
                msg.getVoiceLevel() != null ? msg.getVoiceLevel() : 0.0,
                new ArrayList<>(members)
        );
        broadcastToAll(state, null);
    }

    public void leaveAllVoiceChannels(String username) {
        for (Map.Entry<String, Set<String>> entry : voiceParticipants.entrySet()) {
            if (entry.getValue().removeIf(u -> u.equalsIgnoreCase(username))) {
                Message left = Message.voiceState(entry.getKey(), username, false, false, 0.0,
                        new ArrayList<>(entry.getValue()));
                left.setContent("LEAVE");
                broadcastToAll(left, null);
            }
        }
    }

    public void relayVoiceAudio(String username, Message audioMsg, ClientHandler exclude) {
        String ch = normalizeChannel(audioMsg.getChannel());
        Set<String> members = voiceParticipants.get(ch);
        if (members == null || !members.contains(username)) return;
        for (String member : members) {
            if (!member.equalsIgnoreCase(username)) {
                ClientHandler handler = clients.get(member);
                if (handler != null && handler != exclude) {
                    handler.deliverBroadcast(audioMsg);
                }
            }
        }
    }

    public int getUserCount() {
        return clients.size();
    }

    public void closeAll() {
        for (ClientHandler client : clients.values()) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        clients.clear();
        userChannels.clear();
        voiceParticipants.clear();
        try {
            db.close();
        } catch (Exception ignored) {
        }
    }
}
