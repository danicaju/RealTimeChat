package com.danicaju.chat.model;

/**
 * Enumeración que define los tipos de mensajes soportados en el protocolo JSON
 * de comunicación cliente-servidor de RealTimeChat.
 */
public enum MessageType {
    /**
     * Solicitud de registro o unión de un cliente a la sala/servidor.
     * Incluye el nombre de usuario solicitado en el campo sender.
     */
    JOIN,

    /**
     * Mensaje de texto público emitido a los clientes del canal actual.
     */
    CHAT,

    /**
     * Mensaje privado dirigido a un usuario específico mediante target.
     */
    PRIVATE,

    /**
     * Notificación reactiva con la lista de usuarios conectados en el canal o sala.
     */
    USER_LIST,

    /**
     * Solicitud o aviso de desconexión controlada de un cliente.
     */
    LEAVE,

    /**
     * Mensaje informativo, confirmación o error emitido por el servidor.
     */
    SYSTEM,

    /**
     * Solicitud o notificación de cambio de canal de chat (ej: #general, #ia-computacion, #random).
     */
    JOIN_CHANNEL,

    /**
     * Notificación con la lista de canales disponibles en el servidor.
     */
    CHANNEL_LIST,

    /**
     * Entrega del búfer de reproducción (historial reciente) al entrar a un canal.
     */
    HISTORY,

    /**
     * Evento reactivo que indica que un usuario ha comenzado a escribir en un canal.
     */
    TYPING_START,

    /**
     * Evento reactivo que indica que un usuario ha dejado de escribir.
     */
    TYPING_STOP,

    /**
     * Solicitud o aviso de actualización del perfil personal (avatar, biografía, banner).
     */
    PROFILE_UPDATE,

    /**
     * Solicitud de consulta del perfil de un usuario específico.
     */
    PROFILE_REQUEST,

    /**
     * Respuesta del servidor con los datos del perfil solicitado.
     */
    PROFILE_RESPONSE
}
