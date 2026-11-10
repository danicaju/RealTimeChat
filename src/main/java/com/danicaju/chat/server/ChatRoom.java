package com.danicaju.chat.server;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.UserProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Pattern;

/**
 * Gestor centralizado de salas, canales, historial y perfiles de usuario en RealTimeChat.
 * Administra conexiones de clientes de forma atómica y concurrente,
 * particiona la mensajería en canales (#general, #ia-computacion, #random),
 * almacena el búfer de reproducción (replay buffer) y conserva los perfiles de usuario.
 */
public class ChatRoom {

    public static final String DEFAULT_CHANNEL = Message.DEFAULT_CHANNEL;
    public static final int MAX_HISTORY_PER_CHANNEL = 50;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{2,20}$");
    private static final Pattern CHANNEL_NAME_PATTERN = Pattern.compile("^#?[a-zA-Z0-9_-]{2,30}$");

    private final ConcurrentMap<String, ClientHandler> clients = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    private final ConcurrentMap<String, String> userChannels = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    private final ConcurrentMap<String, UserProfile> userProfiles = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    private final ConcurrentMap<String, List<Message>> channelHistories = new ConcurrentHashMap<>();
    private final Set<String> registeredChannels = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);

    public ChatRoom() {
        // Inicializar canales por defecto
        registerChannel("#general");
        registerChannel("#ia-computacion");
        registerChannel("#random");
    }

    public synchronized void registerChannel(String channelName) {
        if (channelName != null && !channelName.trim().isEmpty() && isValidChannelName(channelName)) {
            String clean = normalizeChannel(channelName);
            registeredChannels.add(clean);
            channelHistories.computeIfAbsent(clean, k -> Collections.synchronizedList(new ArrayList<>()));
        }
    }

    public static boolean isValidChannelName(String channelName) {
        if (channelName == null) {
            return false;
        }
        String trimmed = channelName.trim();
        return CHANNEL_NAME_PATTERN.matcher(trimmed).matches();
    }

    public static String normalizeChannel(String raw) {
        if (raw == null || raw.trim().isEmpty() || !isValidChannelName(raw)) {
            return DEFAULT_CHANNEL;
        }
        String clean = raw.trim().toLowerCase();
        if (!clean.startsWith("#")) {
            clean = "#" + clean;
        }
        return clean;
    }

    public List<String> getAvailableChannels() {
        return Collections.unmodifiableList(new ArrayList<>(registeredChannels));
    }

    public boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    /**
     * Registra un cliente de forma atómica y lo asigna al canal por defecto.
     */
    public boolean registerClient(String username, ClientHandler handler) {
        if (!isValidUsername(username) || handler == null) {
            return false;
        }
        String cleanUsername = username.trim();
        if (clients.putIfAbsent(cleanUsername, handler) == null) {
            userChannels.put(cleanUsername, DEFAULT_CHANNEL);
            userProfiles.putIfAbsent(cleanUsername, new UserProfile(cleanUsername));
            return true;
        }
        return false;
    }

    /**
     * Desregistra un cliente de la sala y de su canal.
     */
    public boolean unregisterClient(String username, ClientHandler handler) {
        if (username == null || handler == null) {
            return false;
        }
        String cleanUsername = username.trim();
        boolean removed = clients.remove(cleanUsername, handler);
        if (removed) {
            userChannels.remove(cleanUsername);
        }
        return removed;
    }

    public boolean isUsernameTaken(String username) {
        if (username == null) {
            return false;
        }
        return clients.containsKey(username.trim());
    }

    /**
     * Retorna el canal en el que se encuentra actualmente un usuario.
     */
    public String getUserChannel(String username) {
        if (username == null) {
            return DEFAULT_CHANNEL;
        }
        return userChannels.getOrDefault(username.trim(), DEFAULT_CHANNEL);
    }

    /**
     * Cambia el canal activo de un cliente y garantiza que el canal quede registrado.
     *
     * @param username Nombre del usuario
     * @param channel  Nuevo canal destino
     * @return Nombre del canal normalizado al que se cambió
     */
    public String switchUserChannel(String username, String channel) {
        if (username == null) {
            return DEFAULT_CHANNEL;
        }
        String normalized = normalizeChannel(channel);
        registerChannel(normalized);
        userChannels.put(username.trim(), normalized);
        return normalized;
    }

    /**
     * Envía un mensaje en broadcast. Si el mensaje tiene un canal asociado,
     * se restringe a los clientes conectados en dicho canal. Si no tiene canal,
     * se distribuye globalmente a todos los clientes del servidor.
     */
    public void broadcast(Message message, ClientHandler exclude) {
        if (message == null) {
            return;
        }

        String targetChannel = message.getChannel();
        if (targetChannel != null && !targetChannel.trim().isEmpty()) {
            recordChannelMessage(targetChannel, message);
            String normTarget = normalizeChannel(targetChannel);
            String payload = message.toJson();

            for (java.util.Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                ClientHandler client = entry.getValue();
                if (exclude != null && client == exclude) {
                    continue;
                }
                String clientChannel = getUserChannel(entry.getKey());
                if (normTarget.equalsIgnoreCase(clientChannel)) {
                    client.sendMessageRaw(payload);
                }
            }
        } else {
            // Emisión global a todos los clientes sin importar el canal
            String payload = message.toJson();
            for (ClientHandler client : clients.values()) {
                if (exclude == null || client != exclude) {
                    client.sendMessageRaw(payload);
                }
            }
        }
    }

    /**
     * Emisión global forzada a todos los clientes del servidor.
     */
    public void broadcastToAll(Message message, ClientHandler exclude) {
        if (message == null) {
            return;
        }
        String payload = message.toJson();
        for (ClientHandler client : clients.values()) {
            if (exclude == null || client != exclude) {
                client.sendMessageRaw(payload);
            }
        }
    }

    /**
     * Registra un mensaje de chat en el búfer de reproducción del canal especificado.
     */
    public void recordChannelMessage(String channel, Message message) {
        if (channel == null || message == null || message.getType() != com.danicaju.chat.model.MessageType.CHAT) {
            return;
        }
        String norm = normalizeChannel(channel);
        registerChannel(norm);
        List<Message> history = channelHistories.get(norm);
        if (history != null) {
            synchronized (history) {
                history.add(message);
                while (history.size() > MAX_HISTORY_PER_CHANNEL) {
                    history.remove(0);
                }
            }
        }
    }

    /**
     * Obtiene una copia inmutable del historial reciente de un canal.
     */
    public List<Message> getChannelHistory(String channel) {
        String norm = normalizeChannel(channel);
        List<Message> history = channelHistories.get(norm);
        if (history == null) {
            return Collections.emptyList();
        }
        synchronized (history) {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
    }

    /**
     * Envía un mensaje privado a un cliente específico.
     */
    public boolean sendPrivate(String targetUsername, Message message) {
        if (targetUsername == null || message == null) {
            return false;
        }
        ClientHandler targetHandler = clients.get(targetUsername.trim());
        if (targetHandler != null) {
            targetHandler.sendMessageRaw(message.toJson());
            return true;
        }
        return false;
    }

    /**
     * Obtiene la lista ordenada de todos los usuarios registrados en el servidor.
     */
    public List<String> getActiveUsers() {
        return Collections.unmodifiableList(new ArrayList<>(clients.keySet()));
    }

    /**
     * Obtiene los usuarios presentes en un canal determinado.
     */
    public List<String> getActiveUsersInChannel(String channel) {
        String norm = normalizeChannel(channel);
        List<String> result = new ArrayList<>();
        for (String user : clients.keySet()) {
            if (norm.equalsIgnoreCase(getUserChannel(user))) {
                result.add(user);
            }
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return Collections.unmodifiableList(result);
    }

    /**
     * Difunde la lista de usuarios activos a todos los clientes del servidor.
     */
    public void broadcastUserList() {
        // Enviar lista por canal a cada usuario
        for (java.util.Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            String ch = getUserChannel(entry.getKey());
            List<String> channelUsers = getActiveUsersInChannel(ch);
            entry.getValue().sendMessageRaw(Message.userList(ch, channelUsers).toJson());
        }
    }

    /**
     * Difunde la lista de usuarios activos de un canal concreto a sus miembros.
     */
    public void broadcastChannelUserList(String channel) {
        String norm = normalizeChannel(channel);
        List<String> channelUsers = getActiveUsersInChannel(norm);
        Message listMsg = Message.userList(norm, channelUsers);
        String payload = listMsg.toJson();

        for (java.util.Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (norm.equalsIgnoreCase(getUserChannel(entry.getKey()))) {
                entry.getValue().sendMessageRaw(payload);
            }
        }
    }

    /**
     * Transmite el evento de escritura (typing) a los demás participantes del canal.
     */
    public void broadcastTyping(String channel, String sender, boolean isTyping, ClientHandler exclude) {
        if (channel == null || sender == null) {
            return;
        }
        String norm = normalizeChannel(channel);
        Message typingMsg = isTyping ? Message.typingStart(sender, norm) : Message.typingStop(sender, norm);
        String payload = typingMsg.toJson();

        for (java.util.Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            ClientHandler client = entry.getValue();
            if (exclude != null && client == exclude) {
                continue;
            }
            if (norm.equalsIgnoreCase(getUserChannel(entry.getKey()))) {
                client.sendMessageRaw(payload);
            }
        }
    }

    /**
     * Obtiene o genera el perfil de un usuario.
     */
    public UserProfile getUserProfile(String username) {
        if (username == null) {
            return new UserProfile("Anónimo");
        }
        String clean = username.trim();
        return userProfiles.computeIfAbsent(clean, UserProfile::new);
    }

    /**
     * Actualiza el perfil de un usuario.
     */
    public void updateUserProfile(String username, UserProfile profile) {
        if (username != null && profile != null) {
            String clean = username.trim();
            profile.setUsername(clean);
            userProfiles.put(clean, profile);
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
    }
}
