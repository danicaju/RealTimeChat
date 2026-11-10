# ==============================================================================
# RealTimeChat NextGen - Multi-Stage Dockerfile
# ==============================================================================

# Etapa 1: Compilación y empaquetado con Maven y JDK 21
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache de dependencias POM
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compilación y creación del Fat JAR sombreado
COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: Imagen de ejecución ligera con JRE 21 Alpine
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Crear usuario sin privilegios para mayor seguridad
RUN addgroup -S chatgroup && adduser -S chatuser -G chatgroup
USER chatuser

# Copiar el artefacto empaquetado desde la etapa de compilación
COPY --from=builder /build/target/realtimechat-1.0.0.jar app.jar

# Puerto por defecto del servidor RealTimeChat
EXPOSE 5000

# Variables de entorno por defecto
ENV SERVER_PORT=5000

# Comando de arranque del servidor con Virtual Threads y Garbage Collector de baja latencia
ENTRYPOINT ["sh", "-c", "java -XX:+UseG1GC -cp app.jar com.danicaju.chat.server.ChatServer ${SERVER_PORT}"]
