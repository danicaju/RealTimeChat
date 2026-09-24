package proyectopersonal;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorChat {

    public static void main(String[] args) {
        int puerto = 5000;
        try (ServerSocket servidor = new ServerSocket(puerto)) {
            System.out.println("🚀 Servidor iniciado en el puerto " + puerto);

            SalaChat salaChat = new SalaChat();

            while (true) {
                Socket cliente = servidor.accept();
                System.out.println("📥 Nuevo cliente conectado desde: " + cliente.getInetAddress());
                
                ClienteHandler hilo = new ClienteHandler(cliente, salaChat);
                salaChat.agregarCliente(hilo);
                hilo.start();
            }
        } catch (IOException e) {
            System.err.println("❌ Error al iniciar el servidor: " + e.getMessage());
        }
    }
}
