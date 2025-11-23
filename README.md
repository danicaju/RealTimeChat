# 🟢 ProyectoChatJava

💬 **Chat multicliente en Java** que permite la **comunicación en tiempo real** entre varios clientes conectados a un servidor central.  

---

## 🌟 Características

### 🔹 Multicliente
Varias personas pueden chatear al mismo tiempo.

### 🔹 Broadcast
Cada mensaje se envía automáticamente a **todos los demás clientes**.

### 🔹 Desconexión segura
Escribe `**salir**` para desconectarte o maneja desconexiones inesperadas.

### 🔹 Consola amigable
Interfaz simple y clara para los usuarios.

---

## 🛠 Tecnologías

| **Tecnología**               | **Descripción**                                           |
|-------------------------------|-----------------------------------------------------------|
| **Java 8+**                  | Lenguaje principal                                        |
| **Sockets TCP**              | Comunicación cliente-servidor                             |
| **Threads**                  | Manejo de múltiples clientes simultáneamente             |
| **BufferedReader / PrintWriter** | Lectura y escritura de mensajes                        |
| **Sincronización**           | Evita conflictos al enviar mensajes a todos los clientes |

---

## 📂 Estructura del Proyecto

```text
proyectopersonal/
├── ClienteChat.java       # Cliente que se conecta al servidor
├── ServidorChat.java      # Servidor central del chat
├── ClienteHandler.java    # Hilo que maneja cada cliente
├── SalaChat.java          # Gestor de la sala de chat (broadcast)
└── LT.java                # Utilidad para leer entradas del usuario


## ⚙️ Cómo Ejecutarlo

```bash
### 1️⃣ Servidor
javac ServidorChat.java SalaChat.java ClienteHandler.java
java proyectopersonal.ServidorChat

### 2️⃣ Clientes (varias terminales o PCs)
javac ClienteChat.java LT.java
java proyectopersonal.ClienteChat
´´´
## 📝 Ejemplo de Uso
<img width="2200" height="558" alt="ejemplo de uso 0" src="https://github.com/user-attachments/assets/7874435c-151c-4e7d-b8b6-49c10053ae7c" />
<img width="2186" height="572" alt="ejemplo de uso 1" src="https://github.com/user-attachments/assets/f7d58daf-3535-45b2-ab64-5f9b72fdb17b" />
<img width="2202" height="552" alt="ejemplo de uso 2" src="https://github.com/user-attachments/assets/105fa2c0-74d2-4c0d-88aa-986b8341bbca" />
<img width="2202" height="562" alt="ejemplo de uso 3" src="https://github.com/user-attachments/assets/cfd7b398-8523-4783-9344-ee7fedd630ba" />

## 👥 Contribuciones

### 💡 Este proyecto es personal, pero se aceptan mejoras:

### Mejorar la interfaz con GUI (Swing o JavaFX)

### Añadir historial de mensajes

### Encriptar mensajes para mayor seguridad
