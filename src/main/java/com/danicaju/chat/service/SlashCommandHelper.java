package com.danicaju.chat.service;

import com.danicaju.chat.model.PollData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor compartido de comandos Slash (/poll, /roll, /shrug, /clear, etc.)
 * con soporte de autocompletado y parseo robusto de argumentos.
 */
public final class SlashCommandHelper {

    public record CommandSpec(String name, String usage, String description) {
    }

    private static final List<CommandSpec> COMMANDS = List.of(
            new CommandSpec("/poll", "/poll Pregunta | Opción 1 | Opción 2", "Crea una encuesta interactiva en tiempo real"),
            new CommandSpec("/roll", "/roll [NdM]", "Lanza dados aleatorios (ej. /roll 2d6 o /roll d20)"),
            new CommandSpec("/shrug", "/shrug [texto]", "Envía el mensaje con ¯\\_(ツ)_/¯"),
            new CommandSpec("/clear", "/clear", "Limpia visualmente la pantalla del canal actual"),
            new CommandSpec("/join", "/join <#canal>", "Cambia de canal de texto"),
            new CommandSpec("/createchannel", "/createchannel <#nombre> [TEXT|VOICE]", "Crea un nuevo canal (requiere MANAGE_CHANNELS)"),
            new CommandSpec("/deletechannel", "/deletechannel <#nombre>", "Elimina un canal existente (requiere MANAGE_CHANNELS)"),
            new CommandSpec("/search", "/search from:user in:canal has:link", "Abre búsqueda avanzada con filtros"),
            new CommandSpec("/status", "/status <online|idle|dnd> [texto]", "Cambia tu estado de presencia y texto personalizado"),
            new CommandSpec("/w", "/w <usuario> <mensaje>", "Envía un mensaje directo (cifrado E2EE automático)"),
            new CommandSpec("/bio", "/bio <texto>", "Actualiza tu biografía (máximo 240 caracteres)"),
            new CommandSpec("/whiteboard", "/whiteboard", "Abre la pizarra colaborativa del canal")
    );

    private static final Pattern DICE_PATTERN = Pattern.compile("^(\\d*)d(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_ARG = Pattern.compile("\"([^\"]+)\"|'([^']+)'");

    private SlashCommandHelper() {
    }

    public static List<CommandSpec> getAllCommands() {
        return COMMANDS;
    }

    public static List<CommandSpec> autocomplete(String prefix) {
        if (prefix == null || !prefix.startsWith("/")) {
            return List.of();
        }
        String token = prefix.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        List<CommandSpec> matches = new ArrayList<>();
        for (CommandSpec spec : COMMANDS) {
            if (spec.name().startsWith(token)) {
                matches.add(spec);
            }
        }
        return matches;
    }

    /**
     * Parsea "/poll Pregunta | Opción 1 | Opción 2" o '/poll "Pregunta" "Opción 1" "Opción 2"'.
     */
    public static PollData parsePoll(String input) {
        if (input == null) return null;
        String raw = input.trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith("/poll")) {
            return null;
        }
        String body = raw.length() > 5 ? raw.substring(5).trim() : "";
        if (body.isEmpty()) return null;

        List<String> parts = new ArrayList<>();
        if (body.contains("|")) {
            for (String p : body.split("\\|")) {
                String clean = p.trim();
                if (!clean.isEmpty()) parts.add(clean);
            }
        } else {
            Matcher m = QUOTED_ARG.matcher(body);
            while (m.find()) {
                String val = m.group(1) != null ? m.group(1) : m.group(2);
                if (val != null && !val.isBlank()) parts.add(val.trim());
            }
        }
        if (parts.size() < 3) {
            return null;
        }
        String question = parts.get(0);
        List<String> options = parts.subList(1, Math.min(parts.size(), 9));
        return new PollData(question, options);
    }

    /**
     * Evalúa /roll (ej: /roll, /roll 20, /roll 2d6, /roll d100).
     */
    public static String evaluateRoll(String input, String username) {
        String arg = input != null && input.trim().length() > 5 ? input.trim().substring(5).trim() : "1d20";
        if (arg.isEmpty()) arg = "1d20";

        int count = 1;
        int sides = 20;
        Matcher m = DICE_PATTERN.matcher(arg);
        if (m.matches()) {
            count = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
            sides = Integer.parseInt(m.group(2));
        } else if (arg.matches("\\d+")) {
            sides = Integer.parseInt(arg);
        }
        count = Math.max(1, Math.min(count, 20));
        sides = Math.max(2, Math.min(sides, 10000));

        List<Integer> rolls = new ArrayList<>(count);
        int sum = 0;
        for (int i = 0; i < count; i++) {
            int val = ThreadLocalRandom.current().nextInt(1, sides + 1);
            rolls.add(val);
            sum += val;
        }
        String actor = (username != null && !username.isBlank()) ? username : "Usuario";
        if (count == 1) {
            return "[DADO] " + actor + " tiró 1d" + sides + " y obtuvo: **" + sum + "**";
        }
        return "[DADO] " + actor + " tiró " + count + "d" + sides + " -> " + rolls + " (Total: **" + sum + "**)";
    }

    public static String evaluateShrug(String input) {
        String extra = input != null && input.trim().length() > 6 ? input.trim().substring(6).trim() : "";
        return extra.isEmpty() ? "¯\\_(ツ)_/¯" : extra + " ¯\\_(ツ)_/¯";
    }
}
