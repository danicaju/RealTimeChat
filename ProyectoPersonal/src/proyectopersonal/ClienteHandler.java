package proyectopersonal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClienteHandler extends Thread {

    private Socket socketCliente;
    private PrintWriter salida;
    private SalaChat salaChat;

    public ClienteHandler(Socket socketCliente, SalaChat salaChat) throws IOException {
        this.socketCliente = socketCliente;
        this.salaChat = salaChat;
        salida = new PrintWriter(socketCliente.getOutputStream(), true);
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()));
            String mensaje;
            while ((mensaje = reader.readLine()) != null) {

                if (mensaje.equalsIgnoreCase("salir")) {
                    System.out.println("Cliente " + socketCliente.getInetAddress() + " se ha desconectado.");
                    salaChat.eliminarCliente(this);
                    socketCliente.close();
                    break;
                }

                salaChat.enviarMensajeATodos(mensaje, this);
            }
        } catch (IOException ex) {
            System.out.println("Cliente desconectado inesperadamente: " + socketCliente.getInetAddress());
            salaChat.eliminarCliente(this);
        }
    }

    public void enviarMensaje(String mensaje) {
        salida.println(mensaje);
    }
}
