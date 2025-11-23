package proyectopersonal;

import java.util.ArrayList;

public class SalaChat {

    private ArrayList<ClienteHandler> clientes = new ArrayList<>();

    public synchronized void agregarCliente(ClienteHandler c) {
        clientes.add(c);
    }

    public synchronized void eliminarCliente(ClienteHandler c) {
        clientes.remove(c);
    }

    public synchronized void enviarMensajeATodos(String mensaje, ClienteHandler remitente) {
        for (ClienteHandler c : clientes) {
            if (c != remitente) {
                c.enviarMensaje(mensaje);
            }
        }
    }
}
