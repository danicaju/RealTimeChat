# 💬 RealTimeChat

**Chat multicliente en Java** que permite la **comunicación en tiempo real** entre varios clientes conectados a un servidor central.

---

## 🚀 Características

### 👥 Multicliente
Varias personas pueden chatear al mismo tiempo.

### 📢 Broadcast
Cada mensaje se envía automáticamente a **todos los demás clientes** conectados a la sala.

### 🔒 Desconexión segura
Escribe `salir` para desconectarte de forma controlada o deja que el servidor maneje desconexiones inesperadas sin problemas.

### 🖥️ Consola amigable
Interfaz de consola sencilla, limpia y fácil de usar, con emojis y retroalimentación en tiempo real.

---

## 🛠️ Tecnologías

| **Tecnología**               | **Descripción**                                           |
|-------------------------------|-----------------------------------------------------------|
| **Java 8+**                  | Lenguaje principal                                        |
| **Sockets TCP**              | Comunicación cliente-servidor mediante `java.net.Socket`  |
| **Threads (Hilos)**          | Manejo de múltiples clientes simultáneamente             |
| **Flujos de entrada/salida** | `BufferedReader` / `PrintWriter` para mensajería          |
| **Sincronización segura**    | Uso de `CopyOnWriteArrayList` para prevenir interbloqueos |

---

## 📂 Estructura del Proyecto

```text
proyectopersonal/
 ├── ClienteChat.java       # Cliente que se conecta al servidor
 ├── ServidorChat.java      # Servidor central del chat
 ├── ClienteHandler.java    # Hilo que maneja cada cliente individualmente
 └── SalaChat.java          # Gestor de la sala de chat (broadcast seguro)
```

## ⚙️ Cómo Ejecutarlo

### 1️⃣ Servidor
Abre una terminal y compila y ejecuta el servidor:
```bash
javac proyectopersonal/ServidorChat.java proyectopersonal/SalaChat.java proyectopersonal/ClienteHandler.java
java proyectopersonal.ServidorChat
```

### 2️⃣ Clientes
Abre nuevas terminales (pueden ser en el mismo PC o en distintos si configuras la IP) para cada cliente:
```bash
javac proyectopersonal/ClienteChat.java
java proyectopersonal.ClienteChat
```

## 📈 Mejoras Aplicadas Recientemente
- Se eliminaron dependencias de utilidades externas obsoletas (como `LT.java`) a favor de `java.util.Scanner`.
- Se introdujo el uso de `try-with-resources` para garantizar el cierre seguro de los flujos de red.
- Se implementó `CopyOnWriteArrayList` para mejorar el rendimiento de la transmisión de mensajes y evitar problemas de concurrencia.
- La desconexión y finalización de los sockets es ahora mucho más robusta.

## 🤝 Contribuciones

Este proyecto es de carácter personal y académico, ¡pero se aceptan mejoras! Algunas ideas:
- Mejorar la interfaz con GUI (Swing o JavaFX).
- Añadir historial de mensajes.
- Encriptar mensajes para mayor privacidad.
