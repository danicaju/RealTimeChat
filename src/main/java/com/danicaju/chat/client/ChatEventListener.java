package com.danicaju.chat.client;

import com.danicaju.chat.model.Message;

import java.util.List;

/**
 * Interfaz de eventos reactivos para clientes de RealTimeChat.
 * Permite desacoplar el motor de red de las interfaces de usuario (CLI o Swing GUI).
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
     * Invocado cuando la lista de usuarios activos de la sala es actualizada.
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
}
