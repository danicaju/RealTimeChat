package com.danicaju.chat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias del TokenBucketRateLimiter")
class TokenBucketRateLimiterTest {

    @Test
    @DisplayName("Inicialización con capacidad completa y rechazo al agotar tokens")
    void testCapacityAndExhaustion() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3.0, 1.0);
        assertEquals(3.0, limiter.getCapacity());
        assertEquals(1.0, limiter.getRefillRate());

        // Consumir los 3 tokens de ráfaga disponibles
        assertTrue(limiter.tryConsume());
        assertTrue(limiter.tryConsume());
        assertTrue(limiter.tryConsume());

        // El 4to intento inmediato debe ser rechazado
        assertFalse(limiter.tryConsume());
    }

    @Test
    @DisplayName("Recarga gradual de tokens a lo largo del tiempo")
    void testTokenRefill() throws InterruptedException {
        // 2 tokens de capacidad, recarga de 10 tokens por segundo (1 token cada 100ms)
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2.0, 10.0);

        assertTrue(limiter.tryConsume());
        assertTrue(limiter.tryConsume());
        assertFalse(limiter.tryConsume());

        // Esperar 250ms (deberían reponerse 2 tokens)
        Thread.sleep(250);

        assertTrue(limiter.tryConsume());
    }

    @Test
    @DisplayName("Validación de argumentos en constructor")
    void testInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(-5, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(5, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(5, -1.0));
    }

    @Test
    @DisplayName("Concurrencia multihilo en consumo atómico de tokens")
    void testConcurrentConsumption() throws InterruptedException {
        int capacity = 50;
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(capacity, 0.001); // Sin recarga apreciable en el test

        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger consumedTokens = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 10; j++) {
                        if (limiter.tryConsume()) {
                            consumedTokens.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        // Exactamente 50 tokens debieron ser consumidos con éxito
        assertEquals(50, consumedTokens.get());
        assertFalse(limiter.tryConsume());
    }
}
