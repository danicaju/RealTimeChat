package com.danicaju.chat.model;

/**
 * Enumeración que define los tipos de mensajes soportados en el protocolo JSON
 * de comunicación cliente-servidor de RealTimeChat.
 */
public enum MessageType {
    /**
     * Solicitud de registro o unión de un cliente a la sala.
     * Incluye el nombre de usuario solicitado en el campo sender.
     */
    JOIN,

    /**
     * Mensaje de texto público emitido a todos los clientes de la sala.
     */
    CHAT,

    /**
     * Mensaje privado dirigido a un usuario específico mediante target.
     */
    PRIVATE,

    /**
     * Notificación reactiva con la lista completa de usuarios conectados.
     */
    USER_LIST,

    /**
     * Solicitud o aviso de desconexión controlada de un cliente.
     */
    LEAVE,

    /**
     * Mensaje informativo, confirmación o error emitido por el servidor.
     */
    SYSTEM
}
