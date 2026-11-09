package com.danicaju.chat.server;

import com.danicaju.chat.model.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;

/**
 * Gestor de la sala de chat centralizada.
 * Administra los clientes conectados de manera concurrente y atómica,
 * garantizando la unicidad de nombres de usuario (insensible a mayúsculas/minúsculas)
 * y el enrutamiento seguro de mensajes.
 */
public class ChatRoom {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{2,20}$");

    private final ConcurrentMap<String, ClientHandler> clients = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * Valida si un nombre de usuario cumple con los criterios de formato.
     *
     * @param username Nombre a validar
     * @return true si cumple el formato alfanumérico entre 2 y 20 caracteres
     */
    public boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    /**
     * Registra un cliente de forma atómica en la sala si el nombre está disponible y es válido.
     *
     * @param username Nombre de usuario solicitado
     * @param handler  Manejador del cliente
     * @return true si el registro fue exitoso; false si el formato es inválido o ya existe
     */
    public boolean registerClient(String username, ClientHandler handler) {
        if (!isValidUsername(username) || handler == null) {
            return false;
        }
        String cleanUsername = username.trim();
        return clients.putIfAbsent(cleanUsername, handler) == null;
    }

    /**
     * Desregistra un cliente de la sala de forma atómica.
     *
     * @param username Nombre del cliente
     * @param handler  Instancia del manejador que solicita la baja
     * @return true si fue desregistrado
     */
    public boolean unregisterClient(String username, ClientHandler handler) {
        if (username == null || handler == null) {
            return false;
        }
        return clients.remove(username.trim(), handler);
    }

    /**
     * Comprueba si un nickname ya se encuentra conectado.
     *
     * @param username Nombre a consultar
     * @return true si ya está registrado
     */
    public boolean isUsernameTaken(String username) {
        if (username == null) {
            return false;
        }
        return clients.containsKey(username.trim());
    }

    /**
     * Envía un mensaje en broadcast a todos los clientes conectados, con opción de excluir al remitente.
     *
     * @param message Mensaje a emitir
     * @param exclude Manejador a excluir (o null si se emite a todos sin excepción)
     */
    public void broadcast(Message message, ClientHandler exclude) {
        if (message == null) {
            return;
        }
        String payload = message.toJson();
        for (ClientHandler client : clients.values()) {
            if (exclude == null || client != exclude) {
                client.sendMessageRaw(payload);
            }
        }
    }

    /**
     * Envía un mensaje privado a un cliente específico.
     *
     * @param targetUsername Destinatario
     * @param message        Mensaje a enviar
     * @return true si el destinatario existe y se le entregó el mensaje, false en caso contrario
     */
    public boolean sendPrivate(String targetUsername, Message message) {
        if (targetUsername == null || message == null) {
            return false;
        }
        ClientHandler targetHandler = clients.get(targetUsername.trim());
        if (targetHandler != null) {
            targetHandler.sendMessageRaw(message.toJson());
            return true;
        }
        return false;
    }

    /**
     * Retorna una lista inmutable y ordenada alfabéticamente de los usuarios actualmente conectados.
     *
     * @return Lista de nombres de usuario
     */
    public List<String> getActiveUsers() {
        return Collections.unmodifiableList(new ArrayList<>(clients.keySet()));
    }

    /**
     * Difunde a todos los clientes la lista actualizada de usuarios activos.
     */
    public void broadcastUserList() {
        Message listMessage = Message.userList(getActiveUsers());
        broadcast(listMessage, null);
    }

    /**
     * Retorna el número de clientes conectados.
     *
     * @return Cantidad de usuarios
     */
    public int getUserCount() {
        return clients.size();
    }

    /**
     * Cierra todas las conexiones activas (usado en el shutdown del servidor).
     */
    public void closeAll() {
        for (ClientHandler client : clients.values()) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        clients.clear();
    }
}
