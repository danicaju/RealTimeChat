package com.danicaju.chat.client;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.UserProfile;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

/**
 * Cliente de consola ágil y robusto para RealTimeChat NextGen.
 * Soporta canales (/join, /channels), perfiles (/bio, /avatar, /profile),
 * asistente AI (@bot, /ai), mensajería privada (/w) e historial reproducido.
 */
public class ChatConsoleClient implements ChatEventListener {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ChatClientConnection connection;
    private final Scanner scanner;
    private volatile boolean running = true;
    private UserProfile myProfile;

    public ChatConsoleClient() {
        this.connection = new ChatClientConnection();
        this.scanner = new Scanner(System.in);
    }

    public void run() {
        run(new String[0]);
    }

    public void run(String[] args) {
        System.out.println("==================================================");
        System.out.println("     RealTimeChat NextGen - Consola Interactiva   ");
        System.out.println("==================================================");

        String host = args != null && args.length > 0 && !args[0].trim().isEmpty() ? args[0].trim() : "";
        if (host.isEmpty()) {
            System.out.print("Servidor [127.0.0.1]: ");
            String hostInput = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            host = hostInput.isEmpty() ? "127.0.0.1" : hostInput;
        }

        int port = 5000;
        if (args != null && args.length > 1 && !args[1].trim().isEmpty()) {
            try {
                port = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException e) {
                System.out.println("Puerto inválido en argumentos, usando 5000 por defecto.");
            }
        } else {
            System.out.print("Puerto [5000]: ");
            String portInput = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            if (!portInput.isEmpty()) {
                try {
                    port = Integer.parseInt(portInput);
                } catch (NumberFormatException e) {
                    System.out.println("Puerto inválido, usando 5000 por defecto.");
                }
            }
        }

        String username = args != null && args.length > 2 && !args[2].trim().isEmpty() ? args[2].trim() : "";
        while (username.isEmpty()) {
            System.out.print("Introduce tu nombre de usuario (alfanumérico, 2-20 caracteres): ");
            if (!scanner.hasNextLine()) {
                return;
            }
            username = scanner.nextLine().trim();
        }

        System.out.println("Conectando a " + host + ":" + port + " como '" + username + "'...");
        boolean connected = connection.connect(host, port, username, this);
        if (!connected) {
            System.err.println("[ERROR] No se pudo establecer la sesión con el servidor.");
            return;
        }

        myProfile = new UserProfile(username);

        Thread shutdownHook = new Thread(connection::disconnect, "console-client-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
        }

        printHelp();

        // Bucle de entrada por teclado
        while (running && connection.isConnected()) {
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            if (input.equalsIgnoreCase("/quit") || input.equalsIgnoreCase("/salir")) {
                System.out.println("Desconectando...");
                running = false;
                connection.disconnect();
                break;
            } else if (input.equalsIgnoreCase("/help") || input.equalsIgnoreCase("/ayuda")) {
                printHelp();
            } else if (input.equalsIgnoreCase("/list") || input.equalsIgnoreCase("/usuarios")) {
                connection.requestUserList();
            } else if (input.equalsIgnoreCase("/channels") || input.equalsIgnoreCase("/canales")) {
                connection.requestChannelList();
            } else if (input.startsWith("/join") || input.startsWith("/canal")) {
                handleJoinCommand(input);
            } else if (input.startsWith("/bio")) {
                handleBioCommand(input);
            } else if (input.startsWith("/avatar")) {
                handleAvatarCommand(input);
            } else if (input.startsWith("/banner")) {
                handleBannerCommand(input);
            } else if (input.startsWith("/profile") || input.startsWith("/perfil")) {
                handleProfileCommand(input);
            } else if (input.startsWith("/w") || input.startsWith("/msg")) {
                handlePrivateCommand(input);
            } else {
                connection.sendChatMessage(input);
            }
        }

        System.out.println("Sesión finalizada. ¡Hasta pronto!");
        System.exit(0);
    }

    private void handleJoinCommand(String input) {
        String[] parts = input.trim().split("\\s+", 2);
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            System.out.println("Uso: /join <#canal>");
            return;
        }
        connection.switchChannel(parts[1].trim());
    }

    private void handleBioCommand(String input) {
        String[] parts = input.trim().split("\\s+", 2);
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            System.out.println("Uso: /bio <texto de tu biografía>");
            return;
        }
        String bio = parts[1].trim();
        myProfile.setBio(bio);
        connection.updateProfile(myProfile);
        System.out.println("[PERFIL] Biografía actualizada a: \"" + myProfile.getBio() + "\"");
    }

    private void handleAvatarCommand(String input) {
        String[] parts = input.trim().split("\\s+", 2);
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            System.out.println("Uso: /avatar <cyber-neon | synthwave | cosmic | emerald | sunset | bot-matrix | golden | retro-pixel>");
            return;
        }
        String avatar = parts[1].trim();
        myProfile.setAvatar(avatar);
        connection.updateProfile(myProfile);
        System.out.println("[PERFIL] Avatar establecido a: " + avatar);
    }

    private void handleBannerCommand(String input) {
        String[] parts = input.trim().split("\\s+", 2);
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            System.out.println("Uso: /banner <#color_hex | gradient-cyber | gradient-sunset | gradient-cosmic>");
            return;
        }
        String banner = parts[1].trim();
        myProfile.setBanner(banner);
        connection.updateProfile(myProfile);
        System.out.println("[PERFIL] Banner establecido a: " + banner);
    }

    private void handleProfileCommand(String input) {
        String[] parts = input.trim().split("\\s+", 2);
        String target = parts.length > 1 ? parts[1].trim() : connection.getUsername();
        connection.requestProfile(target);
    }

    private void handlePrivateCommand(String input) {
        String[] parts = input.trim().split("\\s+", 3);
        if (parts.length < 3 || parts[1].trim().isEmpty() || parts[2].trim().isEmpty()) {
            System.out.println("Uso: /w <usuario> <mensaje>");
            return;
        }
        String target = parts[1].trim();
        String content = parts[2].trim();
        connection.sendPrivateMessage(target, content);
    }

    private void printHelp() {
        System.out.println("--------------------------------------------------");
        System.out.println("Comandos disponibles en RealTimeChat NextGen:");
        System.out.println("  /join <#canal>          : Cambiar de canal de chat");
        System.out.println("  /channels              : Ver lista de canales disponibles");
        System.out.println("  /w <usuario> <mensaje>  : Enviar mensaje privado");
        System.out.println("  /bio <texto>           : Actualizar tu biografía");
        System.out.println("  /avatar <preset>       : Cambiar tu avatar (ej: cyber-neon, cosmic)");
        System.out.println("  /banner <preset/#hex>  : Cambiar tu banner (ej: gradient-cyber, #5865F2)");
        System.out.println("  /profile <usuario>     : Ver el perfil de un usuario");
        System.out.println("  @bot <pregunta>        : Consultar al asistente inteligente AI");
        System.out.println("  /list                  : Listar usuarios en tu canal actual");
        System.out.println("  /help                  : Ver esta guía de ayuda");
        System.out.println("  /quit o /salir         : Salir del chat");
        System.out.println("  <texto>                : Mensaje público al canal (" + connection.getCurrentChannel() + ")");
        System.out.println("--------------------------------------------------");
    }

    private String formatTimestamp(long timestamp) {
        return "[" + TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp)) + "]";
    }

    @Override
    public void onConnected(String username) {
        System.out.println("[OK] Conectado exitosamente como @" + username + "!");
    }

    @Override
    public void onMessageReceived(Message message) {
        if (message == null || message.getType() == null) {
            return;
        }

        String time = formatTimestamp(message.getTimestamp());
        String ch = message.getChannel() != null ? " (" + message.getChannel() + ")" : "";

        switch (message.getType()) {
            case CHAT -> {
                String sender = message.getSender();
                if (connection.getUsername() != null && connection.getUsername().equalsIgnoreCase(sender)) {
                    System.out.println(time + ch + " [Tú]: " + message.getContent());
                } else if ("RealTimeBot".equalsIgnoreCase(sender)) {
                    System.out.println(time + ch + " 🤖 [AI RealTimeBot]: " + message.getContent());
                } else {
                    System.out.println(time + ch + " @" + sender + ": " + message.getContent());
                }
            }
            case PRIVATE -> {
                String sender = message.getSender();
                String target = message.getTarget();
                if (connection.getUsername() != null && connection.getUsername().equalsIgnoreCase(sender)) {
                    System.out.println(time + " [PRIVADO a @" + target + "]: " + message.getContent());
                } else {
                    System.out.println(time + " [PRIVADO de @" + sender + "]: " + message.getContent());
                }
            }
            case SYSTEM -> {
                System.out.println(time + " [SISTEMA] " + message.getContent());
            }
            case LEAVE -> {
                System.out.println(time + " [SALIDA] @" + message.getSender() + " ha salido.");
            }
            default -> {
            }
        }
    }

    @Override
    public void onUserListUpdated(List<String> users) {
        String time = formatTimestamp(System.currentTimeMillis());
        System.out.println(time + " [USUARIOS " + connection.getCurrentChannel() + "] (" + users.size() + "): " + String.join(", ", users));
    }

    @Override
    public void onChannelListUpdated(List<String> channels) {
        System.out.println("[CANALES DISPONIBLES]: " + String.join(", ", channels));
    }

    @Override
    public void onChannelChanged(String newChannel) {
        System.out.println("🔄 [CANAL ACTIVO]: Ahora te encuentras en " + newChannel);
    }

    @Override
    public void onTypingUpdated(String sender, String channel, boolean isTyping) {
        if (isTyping && !sender.equalsIgnoreCase(connection.getUsername())) {
            System.out.println("✍️  @" + sender + " está escribiendo en " + channel + "...");
        }
    }

    @Override
    public void onHistoryReceived(String channel, List<Message> history) {
        if (history != null && !history.isEmpty()) {
            System.out.println("--- Historial reciente de " + channel + " (" + history.size() + " mensajes) ---");
            for (Message m : history) {
                String time = formatTimestamp(m.getTimestamp());
                System.out.println("  " + time + " @" + m.getSender() + ": " + m.getContent());
            }
            System.out.println("-----------------------------------------------------------------");
        }
    }

    @Override
    public void onProfileReceived(UserProfile profile) {
        if (profile != null) {
            System.out.println("==================================================");
            System.out.println("  Perfil de @" + profile.getUsername());
            System.out.println("  Avatar : " + profile.getAvatar());
            System.out.println("  Banner : " + profile.getBanner());
            System.out.println("  Bio    : " + profile.getBio());
            System.out.println("==================================================");
        }
    }

    @Override
    public void onDisconnected(String reason) {
        System.out.println("[AVISO] " + reason);
        running = false;
    }

    @Override
    public void onError(String errorMessage) {
        System.err.println("[ERROR] " + errorMessage);
    }

    public static void main(String[] args) {
        new ChatConsoleClient().run(args);
    }
}
