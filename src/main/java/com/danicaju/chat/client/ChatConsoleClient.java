package com.danicaju.chat.client;

import com.danicaju.chat.model.Message;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

/**
 * Cliente de consola ágil y robusto para RealTimeChat.
 * Soporta comandos de terminal (/w, /list, /help, /quit).
 */
public class ChatConsoleClient implements ChatEventListener {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ChatClientConnection connection;
    private final Scanner scanner;
    private volatile boolean running = true;

    public ChatConsoleClient() {
        this.connection = new ChatClientConnection();
        this.scanner = new Scanner(System.in);
    }

    public void run() {
        run(new String[0]);
    }

    public void run(String[] args) {
        System.out.println("==================================================");
        System.out.println("         RealTimeChat - Consola Interactiva       ");
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
            } else if (input.startsWith("/w ") || input.startsWith("/msg ")) {
                handlePrivateCommand(input);
            } else {
                connection.sendChatMessage(input);
            }
        }

        System.out.println("Sesión finalizada. ¡Hasta pronto!");
        System.exit(0);
    }

    private void handlePrivateCommand(String input) {
        String[] parts = input.split("\\s+", 3);
        if (parts.length < 3) {
            System.out.println("Uso: /w <usuario> <mensaje>");
            return;
        }
        String target = parts[1];
        String content = parts[2];
        connection.sendPrivateMessage(target, content);
    }

    private void printHelp() {
        System.out.println("--------------------------------------------------");
        System.out.println("Comandos disponibles:");
        System.out.println("  /w <usuario> <mensaje>  : Enviar mensaje privado");
        System.out.println("  /list                  : Listar usuarios conectados");
        System.out.println("  /help                  : Ver esta guía de ayuda");
        System.out.println("  /quit o /salir         : Salir del chat");
        System.out.println("  <texto>                : Mensaje público a la sala");
        System.out.println("--------------------------------------------------");
    }

    private String formatTimestamp(long timestamp) {
        return "[" + TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp)) + "]";
    }

    @Override
    public void onConnected(String username) {
        System.out.println("[OK] Conectado exitosamente como " + username + "!");
    }

    @Override
    public void onMessageReceived(Message message) {
        if (message == null || message.getType() == null) {
            return;
        }

        String time = formatTimestamp(message.getTimestamp());
        switch (message.getType()) {
            case CHAT -> {
                String sender = message.getSender();
                if (connection.getUsername() != null && connection.getUsername().equalsIgnoreCase(sender)) {
                    System.out.println(time + " [Tú]: " + message.getContent());
                } else {
                    System.out.println(time + " " + sender + ": " + message.getContent());
                }
            }
            case PRIVATE -> {
                String sender = message.getSender();
                String target = message.getTarget();
                if (connection.getUsername() != null && connection.getUsername().equalsIgnoreCase(sender)) {
                    System.out.println(time + " [PRIVADO a " + target + "]: " + message.getContent());
                } else {
                    System.out.println(time + " [PRIVADO de " + sender + "]: " + message.getContent());
                }
            }
            case SYSTEM -> {
                System.out.println(time + " [SISTEMA] " + message.getContent());
            }
            case LEAVE -> {
                System.out.println(time + " [SALIDA] " + message.getSender() + " ha salido.");
            }
            default -> {
            }
        }
    }

    @Override
    public void onUserListUpdated(List<String> users) {
        String time = formatTimestamp(System.currentTimeMillis());
        System.out.println(time + " [USUARIOS] Usuarios en línea (" + users.size() + "): " + String.join(", ", users));
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
