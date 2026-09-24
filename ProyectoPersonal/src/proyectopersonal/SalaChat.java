package proyectopersonal;

import java.util.concurrent.CopyOnWriteArrayList;

public class SalaChat {

    // Se utiliza CopyOnWriteArrayList para una concurrencia más segura sin necesidad de sincronización explícita pesada.
    private final CopyOnWriteArrayList<ClienteHandler> clientes = new CopyOnWriteArrayList<>();

    public void agregarCliente(ClienteHandler c) {
        clientes.add(c);
    }

    public void eliminarCliente(ClienteHandler c) {
        clientes.remove(c);
    }

    public void enviarMensajeATodos(String mensaje, ClienteHandler remitente) {
        for (ClienteHandler c : clientes) {
            if (c != remitente) {
                c.enviarMensaje(mensaje);
            }
        }
    }
}
