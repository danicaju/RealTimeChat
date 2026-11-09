package com.danicaju.chat.server;

import com.danicaju.chat.model.Message;
import com.danicaju.chat.model.MessageType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Manejador individual de cada cliente conectado en el servidor.
 * Se ejecuta dentro de un hilo administrado por el ThreadPool (ExecutorService).
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatRoom chatRoom;
    private PrintWriter writer;
    private BufferedReader reader;
    private String username;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, ChatRoom chatRoom) {
        this.socket = socket;
        this.chatRoom = chatRoom;
    }

    @Override
    public void run() {
        try {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);

            // Fase 1: Handshake y autenticación del nickname
            if (!performHandshake()) {
                return;
            }

            // Fase 2: Bucle principal de procesamiento de mensajes JSON
            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    Message msg = Message.fromJson(line);
                    processMessage(msg);
                } catch (Exception e) {
                    sendMessageRaw(Message.system("Error de formato en el mensaje enviado.").toJson());
                }
            }
        } catch (IOException e) {
            // Desconexión habitual por caída de red o cierre de ventana
        } finally {
            disconnect();
        }
    }

    /**
     * Realiza el handshake inicial esperando un mensaje JOIN válido.
     */
    private boolean performHandshake() throws IOException {
        String line;
        while (running && (line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            try {
                Message msg = Message.fromJson(line);
                if (msg.getType() == MessageType.JOIN) {
                    String candidate = msg.getSender() != null ? msg.getSender().trim() : "";
                    if (!chatRoom.isValidUsername(candidate)) {
                        sendMessageRaw(Message.system("ERROR: El nickname debe contener entre 2 y 20 caracteres alfanuméricos (a-z, 0-9, _, -, .).").toJson());
                        continue;
                    }
                    if (chatRoom.isUsernameTaken(candidate)) {
                        sendMessageRaw(Message.system("ERROR: El nombre de usuario '" + candidate + "' ya está en uso. Por favor, elige otro.").toJson());
                        continue;
                    }

                    // Registro exitoso
                    this.username = candidate;
                    if (chatRoom.registerClient(this.username, this)) {
                        sendMessageRaw(Message.system("OK: Conectado correctamente como " + this.username).toJson());
                        chatRoom.broadcast(Message.system("🟢 " + this.username + " se ha unido a la sala."), this);
                        chatRoom.broadcastUserList();
                        return true;
                    }
                } else if (msg.getType() == MessageType.LEAVE) {
                    return false;
                }
            } catch (Exception e) {
                sendMessageRaw(Message.system("ERROR: Se esperaba un mensaje de unión (JOIN) válido.").toJson());
            }
        }
        return false;
    }

    /**
     * Procesa los mensajes según su tipo de protocolo.
     */
    private void processMessage(Message msg) {
        if (msg.getType() == null) {
            return;
        }

        switch (msg.getType()) {
            case CHAT -> {
                String content = msg.getContent() != null ? msg.getContent().trim() : "";
                if (!content.isEmpty()) {
                    Message broadcastMsg = Message.chat(this.username, content);
                    chatRoom.broadcast(broadcastMsg, null);
                }
            }
            case PRIVATE -> {
                String target = msg.getTarget() != null ? msg.getTarget().trim() : "";
                String content = msg.getContent() != null ? msg.getContent().trim() : "";
                if (target.isEmpty() || content.isEmpty()) {
                    sendMessageRaw(Message.system("ERROR: El mensaje privado requiere destinatario y contenido.").toJson());
                    return;
                }
                if (target.equalsIgnoreCase(this.username)) {
                    sendMessageRaw(Message.system("No puedes enviarte un mensaje privado a ti mismo.").toJson());
                    return;
                }

                Message privateMsg = Message.privateMsg(this.username, target, content);
                boolean delivered = chatRoom.sendPrivate(target, privateMsg);
                if (delivered) {
                    // Enviamos confirmación / copia al remitente
                    sendMessageRaw(privateMsg.toJson());
                } else {
                    sendMessageRaw(Message.system("ERROR: El usuario '" + target + "' no existe o está desconectado.").toJson());
                }
            }
            case USER_LIST -> {
                sendMessageRaw(Message.userList(chatRoom.getActiveUsers()).toJson());
            }
            case LEAVE -> {
                running = false;
            }
            default -> {
                // Tipos no procesables directamente desde el cliente
            }
        }
    }

    /**
     * Envía una cadena JSON cruda de manera sincronizada para evitar intercalado de bytes.
     */
    public synchronized void sendMessageRaw(String json) {
        if (writer != null && socket != null && !socket.isClosed()) {
            writer.println(json);
        }
    }

    /**
     * Cierra los recursos y notifica la salida del usuario a la sala.
     */
    public void disconnect() {
        running = false;
        if (username != null) {
            boolean removed = chatRoom.unregisterClient(username, this);
            if (removed) {
                chatRoom.broadcast(Message.system("🔴 " + username + " ha abandonado la sala."), null);
                chatRoom.broadcastUserList();
            }
            username = null;
        }
        close();
    }

    /**
     * Cierra streams y socket de forma segura.
     */
    public void close() {
        running = false;
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
    }

    public String getUsername() {
        return username;
    }
}
