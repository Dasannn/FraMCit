package com.dasannn.framcit.store;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FmcFormatTest {

    @TempDir
    Path tempDir;

    private ConvertedImage createStaticImage(byte fillValue) {
        byte[] bytes = new byte[ConvertedFrame.EXPECTED_BYTES];
        Arrays.fill(bytes, fillValue);
        ConvertedFrame frame = new ConvertedFrame(bytes, 0);
        return new ConvertedImage(ImageFormat.PNG, List.of(frame));
    }

    private ConvertedImage createAnimatedImage() {
        byte[] f1 = new byte[ConvertedFrame.EXPECTED_BYTES];
        Arrays.fill(f1, (byte) 10);
        byte[] f2 = new byte[ConvertedFrame.EXPECTED_BYTES];
        Arrays.fill(f2, (byte) 20);
        byte[] f3 = new byte[ConvertedFrame.EXPECTED_BYTES];
        Arrays.fill(f3, (byte) 30);

        List<ConvertedFrame> frames = List.of(
            new ConvertedFrame(f1, 100),
            new ConvertedFrame(f2, 200),
            new ConvertedFrame(f3, 300)
        );
        return new ConvertedImage(ImageFormat.GIF, frames);
    }

    @Test
    @DisplayName("F5.1 - Ida y vuelta del .fmc: escribir y releer da exactamente los mismos bytes (estático)")
    void testRoundtripStaticImage() throws IOException {
        ConvertedImage original = createStaticImage((byte) 42);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FmcFormat.write(baos, original, FmcFormat.PALETTE_VERSION);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        FmcFormat.FmcData readData = FmcFormat.read(bais, ImageFormat.PNG);

        assertEquals(FmcFormat.FORMAT_VERSION, readData.formatVersion());
        assertEquals(FmcFormat.PALETTE_VERSION, readData.paletteVersion());
        assertEquals(1, readData.image().getFrameCount());
        assertFalse(readData.image().isAnimated());
        assertEquals(ImageFormat.PNG, readData.image().getFormat());

        ConvertedFrame readFrame = readData.image().getFrames().getFirst();
        assertEquals(0, readFrame.getDelayMs());
        assertArrayEquals(original.getFrames().getFirst().getMapBytes(), readFrame.getMapBytes());
    }

    @Test
    @DisplayName("F5.1 & F5.14 - Ida y vuelta de .fmc animado a archivo en disco con version de paleta")
    void testRoundtripAnimatedFileWithPaletteVersion() throws IOException {
        ConvertedImage original = createAnimatedImage();
        Path file = tempDir.resolve("anim.fmc");
        int customPaletteVersion = 5;

        FmcFormat.writeToFile(file, original, customPaletteVersion);
        assertTrue(Files.exists(file));

        FmcFormat.FmcData readData = FmcFormat.readFromFile(file, ImageFormat.GIF);

        assertEquals(FmcFormat.FORMAT_VERSION, readData.formatVersion());
        assertEquals(customPaletteVersion, readData.paletteVersion());
        assertEquals(3, readData.image().getFrameCount());
        assertTrue(readData.image().isAnimated());

        for (int i = 0; i < 3; i++) {
            ConvertedFrame expectedFrame = original.getFrames().get(i);
            ConvertedFrame actualFrame = readData.image().getFrames().get(i);

            assertEquals(expectedFrame.getDelayMs(), actualFrame.getDelayMs());
            assertArrayEquals(expectedFrame.getMapBytes(), actualFrame.getMapBytes());
        }
    }

    @Test
    @DisplayName("F5.1 - Cabecera inválida: magia incorrecta se detecta y lanza IOException")
    void testInvalidMagic() {
        byte[] badMagic = new byte[] { 'X', 'Y', 'Z', 0x00, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1 };
        ByteArrayInputStream in = new ByteArrayInputStream(badMagic);

        IOException ex = assertThrows(IOException.class, () -> FmcFormat.read(in, ImageFormat.PNG));
        assertTrue(ex.getMessage().contains("Magia FMC inválida"));
    }

    @Test
    @DisplayName("F5.1 - Versión de formato incompatible se detecta y no revienta")
    void testUnsupportedFormatVersion() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.write(FmcFormat.MAGIC);
        dos.writeInt(99); // unsupported version
        dos.writeInt(1);
        dos.writeInt(1);

        ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
        IOException ex = assertThrows(IOException.class, () -> FmcFormat.read(in, ImageFormat.PNG));
        assertTrue(ex.getMessage().contains("incompatible"));
    }

    @Test
    @DisplayName("F5.1 - Cabecera truncada (< 16 bytes) se detecta limpiamente")
    void testTruncatedHeader() {
        byte[] truncated = new byte[] { 'F', 'M', 'C' }; // only 3 bytes
        ByteArrayInputStream in = new ByteArrayInputStream(truncated);

        assertThrows(IOException.class, () -> FmcFormat.read(in, ImageFormat.PNG));
    }

    @Test
    @DisplayName("F5.1 - Frame truncado (menos de 16384 bytes) se detecta limpiamente")
    void testTruncatedFrameData() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.write(FmcFormat.MAGIC);
        dos.writeInt(FmcFormat.FORMAT_VERSION);
        dos.writeInt(FmcFormat.PALETTE_VERSION);
        dos.writeInt(1); // 1 frame
        dos.writeInt(100); // delayMs
        dos.write(new byte[500]); // only 500 bytes instead of 16384

        ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
        assertThrows(IOException.class, () -> FmcFormat.read(in, ImageFormat.PNG));
    }

    @Test
    @DisplayName("F5.1 - Cero frames o más del límite máximo de 100 frames se rechazan")
    void testInvalidFrameCounts() throws IOException {
        // Cero frames
        ByteArrayOutputStream b1 = new ByteArrayOutputStream();
        DataOutputStream d1 = new DataOutputStream(b1);
        d1.write(FmcFormat.MAGIC);
        d1.writeInt(1);
        d1.writeInt(1);
        d1.writeInt(0);
        assertThrows(IOException.class, () -> FmcFormat.read(new ByteArrayInputStream(b1.toByteArray()), ImageFormat.PNG));

        // 101 frames
        ByteArrayOutputStream b2 = new ByteArrayOutputStream();
        DataOutputStream d2 = new DataOutputStream(b2);
        d2.write(FmcFormat.MAGIC);
        d2.writeInt(1);
        d2.writeInt(1);
        d2.writeInt(101);
        assertThrows(IOException.class, () -> FmcFormat.read(new ByteArrayInputStream(b2.toByteArray()), ImageFormat.PNG));
    }

    @Test
    @DisplayName("F5.2 - Escritura atómica: un fallo a mitad no deja un .fmc corrupto en su sitio")
    void testAtomicWriteFailureLeavesTargetUntouched() throws IOException {
        Path target = tempDir.resolve("target.fmc");
        ConvertedImage original = createStaticImage((byte) 99);

        // 1. Escribir archivo original válido
        FmcFormat.writeToFile(target, original);
        byte[] originalContent = Files.readAllBytes(target);

        // 2. Intentar escribir sobre el mismo target simulando un fallo a mitad de la escritura
        assertThrows(IOException.class, () -> {
            FmcFormat.writeAtomically(target, out -> {
                out.write(new byte[] { 1, 2, 3 });
                out.flush();
                throw new IOException("Simulated crash during write");
            });
        });

        // 3. Comprobar que el archivo de destino sigue 100% intacto con su contenido original
        assertTrue(Files.exists(target));
        byte[] afterFailedWrite = Files.readAllBytes(target);
        assertArrayEquals(originalContent, afterFailedWrite, "El archivo de destino original no debe ser tocado ni corrompido");

        // 4. Comprobar que ningún archivo temporal .tmp quedó huérfano en el directorio
        try (var stream = Files.list(tempDir)) {
            List<Path> tmpFiles = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList();
            assertTrue(tmpFiles.isEmpty(), "No deben quedar archivos .tmp tras un fallo");
        }
    }
}
