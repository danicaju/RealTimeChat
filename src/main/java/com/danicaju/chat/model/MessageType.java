package com.danicaju.chat.model;

/**
 * Enumeración que define los tipos de mensajes soportados en el protocolo JSON
 * de comunicación cliente-servidor de RealTimeChat.
 */
public enum MessageType {
    // --- Protocolo base ---
    JOIN,
    CHAT,
    PRIVATE,
    USER_LIST,
    LEAVE,
    SYSTEM,

    // --- Canales e historial ---
    JOIN_CHANNEL,
    CHANNEL_LIST,
    HISTORY,

    // --- Indicadores de escritura y perfiles ---
    TYPING_START,
    TYPING_STOP,
    PROFILE_UPDATE,
    PROFILE_REQUEST,
    PROFILE_RESPONSE,

    // --- FASE 1: Lifecycle, Threads, Presencia, Reacciones ---
    MESSAGE_ACK,
    MESSAGE_DELETE,
    REACTION_ADD,
    REACTION_REMOVE,
    PRESENCE_UPDATE,
    PING,
    PONG,

    // --- FASE 2: Paginación por Cursores, Gestión de Canales y Permisos Bitfield ---
    HISTORY_PAGE,
    CHANNEL_CREATE,
    CHANNEL_DELETE,
    CHANNEL_METADATA,
    ROLE_UPDATE,

    // --- FASE 3: Comandos (/poll), Webhooks y Búsqueda Avanzada ---
    POLL_VOTE,
    WEBHOOK_CREATE,
    WEBHOOK_DELETE,
    WEBHOOK_LIST,
    SEARCH_REQUEST,
    SEARCH_RESPONSE,

    // --- FASE 4: OpenGraph Embeds, E2EE, Pizarra Colaborativa y Voz/WebRTC ---
    MESSAGE_EMBED_UPDATE,
    KEY_EXCHANGE,
    WHITEBOARD_EVENT,
    VOICE_STATE,
    VOICE_AUDIO,
    WEBRTC_SIGNAL
}
