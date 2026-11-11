package com.danicaju.chat.model;

/**
 * Estados de presencia soportados por el sistema estilo Discord.
 */
public enum PresenceStatus {
    ONLINE("En línea", "#23A55A"),
    IDLE("Ausente", "#F0B232"),
    DND("No molestar", "#F23F43"),
    OFFLINE("Desconectado", "#80848E");

    private final String label;
    private final String colorHex;

    PresenceStatus(String label, String colorHex) {
        this.label = label;
        this.colorHex = colorHex;
    }

    public String getLabel() {
        return label;
    }

    public String getColorHex() {
        return colorHex;
    }

    public static PresenceStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ONLINE;
        }
        try {
            return PresenceStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ONLINE;
        }
    }
}
