package proyectopersonal;

import java.io.*;
import java.net.Socket;

public class ClienteChat {

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("127.0.0.1", 5000);
            LT lector = new LT();
            System.out.println("Conectado al servidor");
            System.out.println("Escribe 'salir' si quieres salir del chat");

            System.out.print("Introduce tu nombre: ");
            String nombre = new String(lector.llegirLinia()).trim();

            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Thread hiloLectura = new Thread(() -> {
                try {
                    String mensaje;
                    while ((mensaje = entrada.readLine()) != null) {
                        System.out.println(mensaje);
                    }
                } catch (IOException e) {
                    System.err.println("Conexión cerrada");
                }
            });
            hiloLectura.start();

            while (true) {
                char aux[] = lector.llegirLinia();
                String mensaje = new String(aux).trim();

                if (mensaje.equalsIgnoreCase("salir")) {
                    System.out.println("Desconectando...");
                    socket.close();
                    break;
                }

                salida.println(nombre + ": " + mensaje);
            }

        } catch (IOException e) {
            System.err.println("Error al conectar con el servidor");
        }
    }
}
