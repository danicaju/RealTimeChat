package com.danicaju.chat.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Motor de permisos basado en operadores bitfield (máscaras de bits) estilo Discord.
 * Permite evaluar roles globales de servidor y overrides de permitir/denegar por canal.
 */
public final class Permission {

    public static final long NONE = 0L;
    public static final long VIEW_CHANNEL = 1L << 0;
    public static final long SEND_MESSAGES = 1L << 1;
    public static final long MANAGE_MESSAGES = 1L << 2;
    public static final long ADMINISTRATOR = 1L << 3;
    public static final long MANAGE_CHANNELS = 1L << 4;
    public static final long MANAGE_ROLES = 1L << 5;
    public static final long MANAGE_WEBHOOKS = 1L << 6;
    public static final long CREATE_THREADS = 1L << 7;
    public static final long VOICE_CONNECT = 1L << 8;

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MODERATOR = "MODERATOR";
    public static final String ROLE_MEMBER = "MEMBER";
    public static final String ROLE_READ_ONLY = "READ_ONLY";

    public static final long ALL_PERMISSIONS =
            VIEW_CHANNEL | SEND_MESSAGES | MANAGE_MESSAGES | ADMINISTRATOR |
            MANAGE_CHANNELS | MANAGE_ROLES | MANAGE_WEBHOOKS | CREATE_THREADS | VOICE_CONNECT;

    public static final long DEFAULT_ADMIN = ALL_PERMISSIONS;

    public static final long DEFAULT_MODERATOR =
            VIEW_CHANNEL | SEND_MESSAGES | MANAGE_MESSAGES | MANAGE_CHANNELS |
            MANAGE_WEBHOOKS | CREATE_THREADS | VOICE_CONNECT;

    public static final long DEFAULT_MEMBER =
            VIEW_CHANNEL | SEND_MESSAGES | CREATE_THREADS | VOICE_CONNECT;

    public static final long DEFAULT_READ_ONLY = VIEW_CHANNEL;

    private static final Map<Long, String> LABELS;

    static {
        Map<Long, String> map = new LinkedHashMap<>();
        map.put(VIEW_CHANNEL, "Ver canal");
        map.put(SEND_MESSAGES, "Enviar mensajes");
        map.put(MANAGE_MESSAGES, "Gestionar mensajes");
        map.put(ADMINISTRATOR, "Administrador");
        map.put(MANAGE_CHANNELS, "Gestionar canales");
        map.put(MANAGE_ROLES, "Gestionar roles");
        map.put(MANAGE_WEBHOOKS, "Gestionar webhooks");
        map.put(CREATE_THREADS, "Crear hilos y respuestas");
        map.put(VOICE_CONNECT, "Conectar a voz y pizarra");
        LABELS = Collections.unmodifiableMap(map);
    }

    private Permission() {
    }

    /**
     * Comprueba si una máscara efectiva concede el permiso solicitado.
     * Los administradores conceden implícitamente todos los permisos.
     */
    public static boolean hasPermission(long effectivePermissions, long permission) {
        if ((effectivePermissions & ADMINISTRATOR) == ADMINISTRATOR) {
            return true;
        }
        if (permission == NONE) {
            return true;
        }
        return (effectivePermissions & permission) == permission;
    }

    /**
     * Calcula los permisos efectivos aplicando primero el rol base del usuario
     * y después las máscaras deny y allow configuradas en el canal.
     */
    public static long computeEffectivePermissions(long basePermissions, long channelAllow, long channelDeny) {
        if ((basePermissions & ADMINISTRATOR) == ADMINISTRATOR) {
            return ALL_PERMISSIONS;
        }
        return (basePermissions & ~channelDeny) | channelAllow;
    }

    /**
     * Retorna la máscara por defecto asociada a un nombre de rol.
     */
    public static long getDefaultPermissionsForRole(String role) {
        if (role == null) {
            return DEFAULT_MEMBER;
        }
        return switch (role.trim().toUpperCase()) {
            case ROLE_ADMIN -> DEFAULT_ADMIN;
            case ROLE_MODERATOR, "MOD" -> DEFAULT_MODERATOR;
            case ROLE_READ_ONLY, "READONLY", "GUEST" -> DEFAULT_READ_ONLY;
            default -> DEFAULT_MEMBER;
        };
    }

    /**
     * Normaliza el nombre del rol hacia uno de los roles soportados.
     */
    public static String normalizeRole(String role) {
        if (role == null) {
            return ROLE_MEMBER;
        }
        return switch (role.trim().toUpperCase()) {
            case ROLE_ADMIN -> ROLE_ADMIN;
            case ROLE_MODERATOR, "MOD" -> ROLE_MODERATOR;
            case ROLE_READ_ONLY, "READONLY", "GUEST" -> ROLE_READ_ONLY;
            default -> ROLE_MEMBER;
        };
    }

    /**
     * Genera un resumen legible de los permisos activos en la máscara.
     */
    public static String describePermissions(long mask) {
        if ((mask & ADMINISTRATOR) == ADMINISTRATOR) {
            return "Administrador (Acceso total)";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long, String> entry : LABELS.entrySet()) {
            if ((mask & entry.getKey()) == entry.getKey()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(entry.getValue());
            }
        }
        return sb.length() > 0 ? sb.toString() : "Sin permisos";
    }

    public static Map<Long, String> getAllPermissionLabels() {
        return LABELS;
    }
}
