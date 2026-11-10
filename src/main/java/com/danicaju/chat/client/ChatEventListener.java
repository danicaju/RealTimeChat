package com.danicaju.chat.client;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.UserProfile;

import java.util.List;

/**
 * Interfaz de eventos reactivos para clientes de RealTimeChat.
 * Desacopla el motor de red TCP y protocolo JSON de las interfaces de usuario (CLI o Swing GUI),
 * añadiendo soporte para canales múltiples, historial de mensajes, estados de escritura (typing)
 * y personalización de perfiles.
 */
public interface ChatEventListener {

    /**
     * Invocado cuando la conexión y el handshake JOIN han sido confirmados por el servidor.
     *
     * @param username Nickname asignado con éxito
     */
    void onConnected(String username);

    /**
     * Invocado al recibir un mensaje (público, privado o del sistema).
     *
     * @param message Objeto de mensaje recibido
     */
    void onMessageReceived(Message message);

    /**
     * Invocado cuando la lista de usuarios activos de la sala/canal es actualizada.
     *
     * @param users Lista de usuarios conectados
     */
    void onUserListUpdated(List<String> users);

    /**
     * Invocado cuando la conexión finaliza (por desconexión voluntaria o error).
     *
     * @param reason Causa o motivo de la desconexión
     */
    void onDisconnected(String reason);

    /**
     * Invocado cuando ocurre un error en la conexión o en el envío de comandos.
     *
     * @param errorMessage Descripción del error
     */
    void onError(String errorMessage);

    /**
     * Invocado cuando se recibe la lista de canales disponibles en el servidor.
     *
     * @param channels Lista de nombres de canales (ej: #general, #ia-computacion, #random)
     */
    default void onChannelListUpdated(List<String> channels) {}

    /**
     * Invocado cuando el cliente cambia exitosamente de canal.
     *
     * @param newChannel Nuevo canal activo
     */
    default void onChannelChanged(String newChannel) {}

    /**
     * Invocado cuando un usuario comienza o deja de escribir en el canal actual.
     *
     * @param sender   Usuario que escribe
     * @param channel  Canal donde ocurre la acción
     * @param isTyping true si comenzó a escribir, false si se detuvo
     */
    default void onTypingUpdated(String sender, String channel, boolean isTyping) {}

    /**
     * Invocado cuando se recibe el búfer de reproducción (historial reciente) del canal.
     *
     * @param channel Canal al que pertenece el historial
     * @param history Lista de mensajes recientes
     */
    default void onHistoryReceived(String channel, List<Message> history) {}

    /**
     * Invocado cuando se recibe el perfil de un usuario (propio o consultado).
     *
     * @param profile Perfil de usuario con avatar, bio y banner
     */
    default void onProfileReceived(UserProfile profile) {}
}
