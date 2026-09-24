package proyectopersonal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClienteHandler extends Thread {

    private final Socket socketCliente;
    private PrintWriter salida;
    private final SalaChat salaChat;

    public ClienteHandler(Socket socketCliente, SalaChat salaChat) throws IOException {
        this.socketCliente = socketCliente;
        this.salaChat = salaChat;
        this.salida = new PrintWriter(socketCliente.getOutputStream(), true);
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()))) {
            String mensaje;
            while ((mensaje = reader.readLine()) != null) {

                if (mensaje.equalsIgnoreCase("salir")) {
                    System.out.println("🚪 Cliente " + socketCliente.getInetAddress() + " se ha desconectado de forma segura.");
                    break;
                }

                salaChat.enviarMensajeATodos(mensaje, this);
            }
        } catch (IOException ex) {
            System.err.println("⚠️ Cliente desconectado inesperadamente: " + socketCliente.getInetAddress());
        } finally {
            salaChat.eliminarCliente(this);
            try {
                if (socketCliente != null && !socketCliente.isClosed()) {
                    socketCliente.close();
                }
                if (salida != null) {
                    salida.close();
                }
            } catch (IOException e) {
                System.err.println("❌ Error al cerrar el socket: " + e.getMessage());
            }
        }
    }

    public void enviarMensaje(String mensaje) {
        if (salida != null) {
            salida.println(mensaje);
        }
    }
}
