package proyectopersonal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ClienteChat {

    public static void main(String[] args) {
        try (Socket socket = new Socket("127.0.0.1", 5000);
             Scanner scanner = new Scanner(System.in);
             PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("✅ Conectado al servidor en 127.0.0.1:5000");
            System.out.println("ℹ️  Escribe 'salir' si quieres desconectarte del chat.");
            System.out.print("👤 Introduce tu nombre: ");
            
            String nombre = scanner.nextLine().trim();
            if (nombre.isEmpty()) {
                nombre = "Anonimo";
            }

            // Hilo para leer mensajes del servidor
            Thread hiloLectura = new Thread(() -> {
                try {
                    String mensaje;
                    while ((mensaje = entrada.readLine()) != null) {
                        System.out.println(mensaje);
                    }
                } catch (IOException e) {
                    System.err.println("❌ Conexión con el servidor cerrada.");
                }
            });
            hiloLectura.start();

            // Bucle principal para enviar mensajes
            while (true) {
                String mensaje = scanner.nextLine().trim();

                if (mensaje.equalsIgnoreCase("salir")) {
                    System.out.println("👋 Desconectando...");
                    break;
                }

                if (!mensaje.isEmpty()) {
                    salida.println(nombre + ": " + mensaje);
                }
            }

        } catch (IOException e) {
            System.err.println("❌ Error al conectar con el servidor: " + e.getMessage());
        }
    }
}
