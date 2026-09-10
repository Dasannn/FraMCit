package com.dasannn.framcit.store;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Serializador y deserializador del formato binario .fmc de FraMCit (F5.1, F5.2, F5.14).
 *
 * Estructura de cabecera (16 bytes):
 * - 4 bytes: Magic bytes ('F', 'M', 'C', 0x00)
 * - 4 bytes (int Big-Endian): Versión del formato (actualmente 1)
 * - 4 bytes (int Big-Endian): Versión de paleta de mapa con la que se grabó (F5.14, riesgo R7)
 * - 4 bytes (int Big-Endian): Número de frames (1..100)
 *
 * Estructura de cada frame (16388 bytes):
 * - 4 bytes (int Big-Endian): Retardo del frame en milisegundos (delayMs)
 * - 16384 bytes: Bytes crudos de índice de paleta de mapa de Minecraft (128x128)
 */
public final class FmcFormat {

    public static final byte[] MAGIC = new byte[] { 'F', 'M', 'C', 0x00 };
    public static final int FORMAT_VERSION = 1;
    public static final int PALETTE_VERSION = 1; // F5.14: Paper MapPalette legacy palette version
    public static final int MAX_FRAMES = 100;

    private FmcFormat() {}

    /**
     * Contenedor con la imagen convertida y los metadatos de cabecera del archivo .fmc.
     */
    public record FmcData(int formatVersion, int paletteVersion, ConvertedImage image) {
        public FmcData {
            Objects.requireNonNull(image, "image cannot be null");
        }
    }

    @FunctionalInterface
    public interface OutputConsumer {
        void accept(OutputStream out) throws IOException;
    }

    /**
     * Escribe una imagen en formato .fmc a un OutputStream.
     *
     * @param out flujo de salida
     * @param image imagen convertida
     * @param paletteVersion versión de paleta a registrar en cabecera
     * @throws IOException si ocurre un error de I/O
     */
    public static void write(OutputStream out, ConvertedImage image, int paletteVersion) throws IOException {
        Objects.requireNonNull(out, "out cannot be null");
        Objects.requireNonNull(image, "image cannot be null");

        DataOutputStream dos = new DataOutputStream(out);
        dos.write(MAGIC);
        dos.writeInt(FORMAT_VERSION);
        dos.writeInt(paletteVersion);
        dos.writeInt(image.getFrameCount());

        for (ConvertedFrame frame : image.getFrames()) {
            dos.writeInt(frame.getDelayMs());
            dos.write(frame.getMapBytes());
        }
        dos.flush();
    }

    /**
     * Escribe una imagen en formato .fmc usando la versión de paleta por defecto.
     */
    public static void write(OutputStream out, ConvertedImage image) throws IOException {
        write(out, image, PALETTE_VERSION);
    }

    /**
     * Escribe atómicamente a un archivo temporal y renombra al destino (F5.2).
     */
    public static void writeAtomically(Path targetPath, OutputConsumer writer) throws IOException {
        Objects.requireNonNull(targetPath, "targetPath cannot be null");
        Objects.requireNonNull(writer, "writer cannot be null");

        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String tmpName = targetPath.getFileName().toString() + "." + UUID.randomUUID() + ".tmp";
        Path tempPath = (parent != null) ? parent.resolve(tmpName) : Path.of(tmpName);

        try {
            try (OutputStream fos = Files.newOutputStream(tempPath);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                writer.accept(bos);
                bos.flush();
            }

            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {}
        }
    }

    /**
     * Escribe una imagen de forma atómica en disco (F5.2):
     * Escribe primero a un archivo temporal en el mismo directorio y luego realiza
     * un renombrado atómico sobre el archivo de destino. Si ocurre un fallo a mitad de
     * camino, el archivo temporal se elimina y el archivo de destino jamás queda corrupto.
     *
     * @param targetPath ruta final del archivo .fmc
     * @param image imagen a guardar
     * @param paletteVersion versión de paleta a registrar
     * @throws IOException si falla la escritura o el renombrado
     */
    public static void writeToFile(Path targetPath, ConvertedImage image, int paletteVersion) throws IOException {
        Objects.requireNonNull(image, "image cannot be null");
        writeAtomically(targetPath, out -> write(out, image, paletteVersion));
    }

    public static void writeToFile(Path targetPath, ConvertedImage image) throws IOException {
        writeToFile(targetPath, image, PALETTE_VERSION);
    }

    /**
     * Lee un archivo .fmc desde un InputStream y valida rigurosamente su cabecera y contenido.
     *
     * @param in flujo de entrada
     * @param fallbackFormat formato de imagen a asignar (PNG por defecto si es null)
     * @return FmcData con la versión de cabecera y la ConvertedImage
     * @throws IOException si el archivo está truncado, tiene magia inválida o versión incompatible
     */
    public static FmcData read(InputStream in, ImageFormat fallbackFormat) throws IOException {
        Objects.requireNonNull(in, "in cannot be null");
        DataInputStream dis = new DataInputStream(in);

        byte[] magic = dis.readNBytes(4);
        if (magic.length < 4) {
            throw new EOFException("Cabecera FMC truncada: se esperaban 4 bytes de magia");
        }
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("Magia FMC inválida");
        }

        int formatVersion = dis.readInt();
        if (formatVersion != FORMAT_VERSION) {
            throw new IOException("Versión de formato FMC incompatible: " + formatVersion + " (soportada: " + FORMAT_VERSION + ")");
        }

        int paletteVersion = dis.readInt();
        int frameCount = dis.readInt();
        if (frameCount <= 0) {
            throw new IOException("Número de frames inválido en FMC: " + frameCount);
        }
        if (frameCount > MAX_FRAMES) {
            throw new IOException("FMC supera el límite máximo de frames permitidos: " + frameCount + " > " + MAX_FRAMES);
        }

        List<ConvertedFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int delayMs = dis.readInt();
            byte[] mapBytes = dis.readNBytes(ConvertedFrame.EXPECTED_BYTES);
            if (mapBytes.length < ConvertedFrame.EXPECTED_BYTES) {
                throw new EOFException(String.format(
                    "Frame %d truncado: esperados %d bytes, leídos %d",
                    i, ConvertedFrame.EXPECTED_BYTES, mapBytes.length
                ));
            }
            frames.add(new ConvertedFrame(mapBytes, delayMs));
        }

        ImageFormat format = (fallbackFormat != null)
            ? fallbackFormat
            : (frameCount > 1 ? ImageFormat.GIF : ImageFormat.PNG);

        ConvertedImage image = new ConvertedImage(format, frames);
        return new FmcData(formatVersion, paletteVersion, image);
    }

    /**
     * Lee únicamente la ConvertedImage de un InputStream.
     */
    public static ConvertedImage readImage(InputStream in, ImageFormat fallbackFormat) throws IOException {
        return read(in, fallbackFormat).image();
    }

    /**
     * Lee un archivo .fmc directamente desde disco.
     */
    public static FmcData readFromFile(Path path, ImageFormat fallbackFormat) throws IOException {
        Objects.requireNonNull(path, "path cannot be null");
        try (InputStream fis = Files.newInputStream(path);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            return read(bis, fallbackFormat);
        }
    }

    /**
     * Lee la ConvertedImage directamente desde disco.
     */
    public static ConvertedImage readImageFromFile(Path path, ImageFormat fallbackFormat) throws IOException {
        return readFromFile(path, fallbackFormat).image();
    }
}
