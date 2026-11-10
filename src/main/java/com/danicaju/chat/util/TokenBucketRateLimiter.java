package com.danicaju.chat.util;

/**
 * Implementación del algoritmo Token Bucket para limitación de tasa (Rate Limiting)
 * por cliente en RealTimeChat.
 * Previene ataques de spam y saturación de ancho de banda garantizando un flujo continuo
 * con tolerancia a ráfagas controladas.
 */
public class TokenBucketRateLimiter {

    private final double capacity;
    private final double refillRate; // Tokens añadidos por segundo
    private double availableTokens;
    private long lastRefillNanos;

    /**
     * Construye un limitador con capacidad y tasa de recarga por segundo especificadas.
     *
     * @param capacity   Ráfaga máxima de tokens acumulables (ej: 5)
     * @param refillRate Tasa de reposición de tokens por segundo (ej: 2.0)
     */
    public TokenBucketRateLimiter(double capacity, double refillRate) {
        if (capacity <= 0 || refillRate <= 0) {
            throw new IllegalArgumentException("Capacidad y tasa de recarga deben ser mayores que cero.");
        }
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.availableTokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Intenta consumir 1 token del bucket.
     *
     * @return true si había token disponible y fue consumido; false si la tasa fue superada
     */
    public synchronized boolean tryConsume() {
        return tryConsume(1.0);
    }

    /**
     * Intenta consumir una cantidad específica de tokens.
     *
     * @param tokens Cantidad a consumir
     * @return true si la solicitud fue aprobada, false si se superó el límite
     */
    public synchronized boolean tryConsume(double tokens) {
        if (tokens <= 0) {
            return true;
        }
        refill();
        if (availableTokens >= tokens) {
            availableTokens -= tokens;
            return true;
        }
        return false;
    }

    /**
     * Repone tokens de acuerdo al tiempo transcurrido desde la última recarga.
     */
    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        if (elapsedSeconds > 0) {
            availableTokens = Math.min(capacity, availableTokens + (elapsedSeconds * refillRate));
            lastRefillNanos = now;
        }
    }

    public synchronized double getAvailableTokens() {
        refill();
        return availableTokens;
    }

    public double getCapacity() {
        return capacity;
    }

    public double getRefillRate() {
        return refillRate;
    }
}
