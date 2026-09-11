package com.dasannn.framcit.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageDecoderConcurrencyRaceTest {

    private static byte[] createMinimalAnimatedGif(int frameCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);

            BufferedImage frameImg = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frameImg.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 2, 2);
            g.dispose();

            for (int i = 0; i < frameCount; i++) {
                writer.writeToSequence(new IIOImage(frameImg, null, null), null);
            }

            writer.endWriteSequence();
            writer.dispose();
        }
        return baos.toByteArray();
    }

    @Test
    @DisplayName("F11.9 [BUG] Reproducir carrera de límite de frames con campo estático compartido")
    void testReproduceStaticMaxFramesRaceCondition() throws Exception {
        byte[] gif20 = createMinimalAnimatedGif(20);

        int iterations = 100;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        AtomicInteger leakedPastQuotaCount = new AtomicInteger(0);
        AtomicInteger falseRejectionCount = new AtomicInteger(0);

        try {
            for (int i = 0; i < iterations; i++) {
                CountDownLatch latch = new CountDownLatch(2);

                // Hilo 1: Jugador con cupo 10 intenta decodificar GIF de 20 frames pasando límite por parámetro (DEBE FALLAR)
                pool.submit(() -> {
                    try {
                        latch.countDown();
                        latch.await();
                        ImageDecoder.decode(gif20, 10);
                        // Si no lanzó excepción, coló el límite
                        leakedPastQuotaCount.incrementAndGet();
                    } catch (ImageValidationException expected) {
                        // Comportamiento correcto esperado para cupo 10
                    } catch (Exception e) {
                        // Ignorar interrupciones
                    }
                });

                // Hilo 2: Jugador con cupo 50 intenta decodificar GIF de 20 frames pasando límite por parámetro (DEBE TENER ÉXITO)
                pool.submit(() -> {
                    try {
                        latch.countDown();
                        latch.await();
                        ImageDecoder.decode(gif20, 50);
                    } catch (ImageValidationException unexpected) {
                        // Si el otro hilo causara interferencia, este sería rechazado erróneamente
                        falseRejectionCount.incrementAndGet();
                    } catch (Exception e) {
                        // Ignorar interrupciones
                    }
                });
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            ImageDecoder.resetDefaults();
        }

        // Con el campo estático compartido, leakedPastQuotaCount o falseRejectionCount será > 0
        // Este test demuestra que existe la condición de carrera exigiendo 0 errores.
        assertEquals(0, leakedPastQuotaCount.get(),
            "Condición de carrera reproducida: Un jugador con cupo 10 pudo colar 20 frames " + leakedPastQuotaCount.get() + " veces porque otro hilo cambió maxFrames a 50");
        assertEquals(0, falseRejectionCount.get(),
            "Condición de carrera reproducida: Un jugador con cupo 50 fue rechazado indebidamente " + falseRejectionCount.get() + " veces porque otro hilo cambió maxFrames a 10");
    }
}
