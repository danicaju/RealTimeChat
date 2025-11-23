package proyectopersonal;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorChat {

    public static void main(String[] args) {
        try {
            ServerSocket servidor = new ServerSocket(5000);
            System.out.println("Servidor iniciado en el puerto 5000");

            SalaChat salaChat = new SalaChat();

            while (true) {
                Socket cliente = servidor.accept();
                System.out.println("Cliente conectado desde: " + cliente.getInetAddress());
                ClienteHandler hilo = new ClienteHandler(cliente, salaChat);
                salaChat.agregarCliente(hilo);
                hilo.start();
            }
        } catch (IOException e) {
            System.err.println("ERROR");
        }
    }

}
