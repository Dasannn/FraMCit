package com.dasannn.framcit.image;

import com.dasannn.framcit.command.Messages;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Decodificador de imágenes estáticas y animadas.
 * Aplica los límites de seguridad del spec.md (sección 5) antes de la decodificación completa,
 * resuelve la composición de frames parciales de GIF según disposal methods,
 * y normaliza los retardos de reproducción.
 *
 * Configurable dinámicamente mediante config.yml (F7.1, F7.4).
 */
public final class ImageDecoder {

    public static final int MAX_DIMENSION = 4096;
    public static final int MAX_FRAMES = 100;
    public static final int DEFAULT_DELAY_MS = 100; // 10 fps (100 ms por frame)
    public static final int MIN_DELAY_MS = 100;     // Tope de 10 fps

    private static volatile int maxDimension = MAX_DIMENSION;
    private static volatile int maxFrames = MAX_FRAMES;
    private static volatile int defaultDelayMs = DEFAULT_DELAY_MS;
    private static volatile int minDelayMs = MIN_DELAY_MS;

    private ImageDecoder() {}

    /**
     * Configura dinámicamente los límites de decodificación.
     */
    public static void configure(int maxDim, int maxF, int defDelay, int minDelay) {
        maxDimension = maxDim;
        maxFrames = maxF;
        defaultDelayMs = defDelay;
        minDelayMs = minDelay;
    }

    /**
     * Restablece los límites a sus valores por defecto.
     */
    public static void resetDefaults() {
        maxDimension = MAX_DIMENSION;
        maxFrames = MAX_FRAMES;
        defaultDelayMs = DEFAULT_DELAY_MS;
        minDelayMs = MIN_DELAY_MS;
    }

    public static int getMaxDimension() {
        return maxDimension;
    }

    public static int getMaxFrames() {
        return maxFrames;
    }

    public static int getDefaultDelayMs() {
        return defaultDelayMs;
    }

    public static int getMinDelayMs() {
        return minDelayMs;
    }

    /**
     * Decodifica los bytes dados en una {@link DecodedImage} utilizando el límite global por defecto.
     *
     * @param data bytes crudos del archivo descargado
     * @return imagen decodificada con sus frames y retardos normalizados
     * @throws ImageValidationException si el formato no esta soportado, supera limites o esta corrupto
     */
    public static DecodedImage decode(byte[] data) {
        return decode(data, maxFrames);
    }

    /**
     * Decodifica los bytes dados aplicando un límite específico de frames por petición (F11.9, F11.8).
     * El límite efectivo nunca supera el límite global de seguridad (Constitution 6.6).
     *
     * @param data bytes crudos del archivo descargado
     * @param maxAllowedFrames límite de frames permitido para esta petición
     * @return imagen decodificada con sus frames y retardos normalizados
     * @throws ImageValidationException si el formato no esta soportado, supera limites o esta corrupto
     */
    public static DecodedImage decode(byte[] data, int maxAllowedFrames) {
        if (data == null || data.length == 0) {
            throw new ImageValidationException(Messages.ERROR_NOT_AN_IMAGE);
        }

        int effectiveLimit = (maxAllowedFrames > 0) ? Math.min(maxAllowedFrames, maxFrames) : maxFrames;

        ImageFormat format = FormatSniffer.sniff(data)
                .orElseThrow(() -> new ImageValidationException(Messages.ERROR_UNSUPPORTED_FORMAT));

        // Comprobación rápida de cabecera GIF (Logical Screen Descriptor en bytes 6 a 9)
        if (format == ImageFormat.GIF) {
            if (data.length < 10) {
                throw new ImageValidationException(Messages.ERROR_CORRUPTED_FILE);
            }
            int lsdW = (data[6] & 0xFF) | ((data[7] & 0xFF) << 8);
            int lsdH = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
            if (lsdW > maxDimension || lsdH > maxDimension) {
                throw new ImageValidationException(Messages.ERROR_DIMENSIONS_TOO_LARGE);
            }
        }

        // Asegurar registro de TwelveMonkeys si es WEBP
        if (format == ImageFormat.WEBP) {
            TwelveMonkeysRegistrar.register(null);
        }

        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (iis == null) {
                throw new ImageValidationException(Messages.ERROR_CORRUPTED_FILE);
            }

            ImageReader reader = getReader(format, iis);
            if (reader == null) {
                throw new ImageValidationException(Messages.ERROR_UNSUPPORTED_FORMAT);
            }

            try {
                reader.setInput(iis, false, false);

                // 1. Límite de dimensiones leyendo SOLO la cabecera (getWidth / getHeight no decodifican pixels)
                int width;
                int height;
                try {
                    width = reader.getWidth(0);
                    height = reader.getHeight(0);
                } catch (Exception e) {
                    throw new ImageValidationException(Messages.ERROR_CORRUPTED_FILE, e);
                }

                if (width > maxDimension || height > maxDimension) {
                    throw new ImageValidationException(Messages.ERROR_DIMENSIONS_TOO_LARGE);
                }

                // Canvas dimensions para GIF
                int canvasWidth = width;
                int canvasHeight = height;
                if (format == ImageFormat.GIF) {
                    int lsdW = (data[6] & 0xFF) | ((data[7] & 0xFF) << 8);
                    int lsdH = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
                    if (lsdW > 0 && lsdH > 0) {
                        canvasWidth = lsdW;
                        canvasHeight = lsdH;
                    }
                }

                // 2. Límite de frames antes de la decodificación completa
                int declaredFrames = -1;
                if (format == ImageFormat.GIF || format == ImageFormat.WEBP) {
                    try {
                        declaredFrames = reader.getNumImages(true);
                    } catch (Exception ignored) {}

                    if (declaredFrames > effectiveLimit) {
                        if (effectiveLimit < maxFrames) {
                            throw new TooManyFramesException(declaredFrames, effectiveLimit);
                        } else {
                            throw new TooManyFramesException(declaredFrames, effectiveLimit, Messages.ERROR_TOO_MANY_FRAMES);
                        }
                    }
                }

                // 3. Decodificación según formato
                if (format == ImageFormat.GIF) {
                    return decodeGif(reader, canvasWidth, canvasHeight, declaredFrames, effectiveLimit);
                } else if (format == ImageFormat.WEBP) {
                    return decodeWebP(reader, declaredFrames, effectiveLimit);
                } else {
                    return decodeStatic(reader, format, width, height);
                }
            } finally {
                reader.dispose();
            }
        } catch (ImageValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageValidationException(Messages.ERROR_CORRUPTED_FILE, e);
        }
    }

    private static DecodedImage decodeStatic(ImageReader reader, ImageFormat format, int width, int height) throws IOException {
        BufferedImage img = reader.read(0);
        if (img == null) {
            throw new ImageValidationException(Messages.ERROR_CORRUPTED_FILE);
        }
        ImageFrame frame = new ImageFrame(img, 0);
        return new DecodedImage(format, List.of(frame), width, height);
    }

    private static DecodedImage decodeWebP(ImageReader reader, int declaredFrames, int limitFrames) throws IOException {
        List<ImageFrame> frames = new ArrayList<>();
        int limit = (declaredFrames > 0) ? declaredFrames : Integer.MAX_VALUE;

        for (int i = 0; i < limit; i++) {
            if (frames.size() >= limitFrames) {
                int total = (declaredFrames > 0) ? declaredFrames : limitFrames + 1;
                if (limitFrames < maxFrames) {
                    throw new TooManyFramesException(total, limitFrames);
                } else {
                    throw new TooManyFramesException(total, limitFrames, Messages.ERROR_TOO_MANY_FRAMES);
                }
            }
            BufferedImage frameImg;
            try {
                frameImg = reader.read(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (frameImg == null) {
                break;
            }
            if (frameImg.getWidth() > maxDimension || frameImg.getHeight() > maxDimension) {
                throw new ImageValidationException(Messages.ERROR_DIMENSIONS_TOO_LARGE);
            }
            // WEBP animado: retardo por defecto configurado
            frames.add(new ImageFrame(frameImg, defaultDelayMs));
        }

        if (frames.isEmpty()) {
            throw new ImageValidationException(Messages.ERROR_CORRUPTED_FILE);
        }

        // Si es de un solo frame, es estatica (retardo 0)
        if (frames.size() == 1) {
            frames.set(0, new ImageFrame(frames.get(0).getImage(), 0));
        }

        int w = frames.get(0).getImage().getWidth();
        int h = frames.get(0).getImage().getHeight();
        return new DecodedImage(ImageFormat.WEBP, frames, w, h);
    }

    private static DecodedImage decodeGif(ImageReader reader, int canvasWidth, int canvasHeight, int declaredFrames, int limitFrames) throws IOException {
        List<ImageFrame> frames = new ArrayList<>();
        int limit = (declaredFrames > 0) ? declaredFrames : Integer.MAX_VALUE;

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        BufferedImage previousSnapshot = null;

        for (int i = 0; i < limit; i++) {
            if (frames.size() >= limitFrames) {
                int total = (declaredFrames > 0) ? declaredFrames : limitFrames + 1;
                if (limitFrames < maxFrames) {
                    throw new TooManyFramesException(total, limitFrames);
                } else {
                    throw new TooManyFramesException(total, limitFrames, Messages.ERROR_TOO_MANY_FRAMES);
                }
            }

            BufferedImage rawFrame;
            try {
                rawFrame = reader.read(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (rawFrame == null) {
                break;
            }

            IIOMetadata metadata = null;
            try {
                metadata = reader.getImageMetadata(i);
            } catch (Exception ignored) {}

            GifFrameMetadata frameMeta = parseGifFrameMetadata(metadata);
            int delayMs = normalizeDelay(frameMeta.delayCentiseconds);
            int left = frameMeta.left;
            int top = frameMeta.top;
            int frameW = rawFrame.getWidth();
            int frameH = rawFrame.getHeight();

            // Si el frame declara dimensiones mayores al maximo permitido
            if (left + frameW > maxDimension || top + frameH > maxDimension) {
                throw new ImageValidationException(Messages.ERROR_DIMENSIONS_TOO_LARGE);
            }

            // Si el canvas era menor que las dimensiones requeridas por este frame, expandirlo
            if (left + frameW > canvas.getWidth() || top + frameH > canvas.getHeight()) {
                int newW = Math.max(canvas.getWidth(), left + frameW);
                int newH = Math.max(canvas.getHeight(), top + frameH);
                BufferedImage expanded = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gExp = expanded.createGraphics();
                gExp.setComposite(AlphaComposite.Src);
                gExp.drawImage(canvas, 0, 0, null);
                gExp.dispose();
                canvas = expanded;
            }

            // Si el disposal method es RESTORE_TO_PREVIOUS, tomar instantanea antes de dibujar
            if (frameMeta.disposalMethod == GifDisposalMethod.RESTORE_TO_PREVIOUS) {
                previousSnapshot = copyImage(canvas);
            }

            // Componer frame actual sobre el canvas (SrcOver respeta transparencia)
            Graphics2D g = canvas.createGraphics();
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(rawFrame, left, top, null);
            g.dispose();

            // Guardar copia del canvas compuesto para este frame
            frames.add(new ImageFrame(copyImage(canvas), delayMs));

            // Aplicar metodo de disposicion para preparar el canvas del siguiente frame
            switch (frameMeta.disposalMethod) {
                case RESTORE_TO_BACKGROUND -> {
                    Graphics2D gDisp = canvas.createGraphics();
                    gDisp.setComposite(AlphaComposite.Clear);
                    gDisp.fillRect(left, top, frameW, frameH);
                    gDisp.dispose();
                    previousSnapshot = null;
                }
                case RESTORE_TO_PREVIOUS -> {
                    if (previousSnapshot != null) {
                        canvas = previousSnapshot;
                        previousSnapshot = null;
                    }
                }
                case DO_NOT_DISPOSE, NONE -> {
                    // El canvas se mantiene tal como quedo tras dibujar este frame
                    previousSnapshot = null;
                }
            }
        }

        if (frames.isEmpty()) {
            throw new ImageValidationException(Messages.ERROR_CORRUPTED_FILE);
        }

        // Si solo tiene 1 frame, es un GIF estatico (retardo 0)
        if (frames.size() == 1) {
            frames.set(0, new ImageFrame(frames.get(0).getImage(), 0));
        }

        return new DecodedImage(ImageFormat.GIF, frames, canvas.getWidth(), canvas.getHeight());
    }

    /**
     * Normaliza los retardos de GIF (en centésimas de segundo):
     * - Si es <= 0 o basura: retardo por defecto configurado.
     * - Si es menor a minDelayMs: tope según el mínimo configurado.
     * - Si es >= minDelayMs: se respeta el valor.
     */
    public static int normalizeDelay(int delayCentiseconds) {
        if (delayCentiseconds <= 0) {
            return defaultDelayMs;
        }
        int delayMs = delayCentiseconds * 10;
        return Math.max(delayMs, minDelayMs);
    }

    private static GifFrameMetadata parseGifFrameMetadata(IIOMetadata metadata) {
        GifFrameMetadata result = new GifFrameMetadata();
        if (metadata == null) {
            return result;
        }

        try {
            String nativeFormat = metadata.getNativeMetadataFormatName();
            if (nativeFormat == null) {
                return result;
            }
            Node root = metadata.getAsTree(nativeFormat);
            for (int i = 0; i < root.getChildNodes().getLength(); i++) {
                Node child = root.getChildNodes().item(i);
                String name = child.getNodeName();
                if ("GraphicControlExtension".equalsIgnoreCase(name)) {
                    NamedNodeMap attrs = child.getAttributes();
                    if (attrs != null) {
                        Node dispNode = attrs.getNamedItem("disposalMethod");
                        if (dispNode != null) {
                            result.disposalMethod = parseDisposalMethod(dispNode.getNodeValue());
                        }
                        Node delayNode = attrs.getNamedItem("delayTime");
                        if (delayNode != null) {
                            try {
                                result.delayCentiseconds = Integer.parseInt(delayNode.getNodeValue());
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                } else if ("ImageDescriptor".equalsIgnoreCase(name)) {
                    NamedNodeMap attrs = child.getAttributes();
                    if (attrs != null) {
                        Node leftNode = attrs.getNamedItem("imageLeftPosition");
                        if (leftNode != null) {
                            try {
                                result.left = Integer.parseInt(leftNode.getNodeValue());
                            } catch (NumberFormatException ignored) {}
                        }
                        Node topNode = attrs.getNamedItem("imageTopPosition");
                        if (topNode != null) {
                            try {
                                result.top = Integer.parseInt(topNode.getNodeValue());
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return result;
    }

    private static GifDisposalMethod parseDisposalMethod(String value) {
        if (value == null) {
            return GifDisposalMethod.NONE;
        }
        String v = value.trim().toLowerCase();
        return switch (v) {
            case "restoretobackgroundcolor", "restore_to_background", "2" -> GifDisposalMethod.RESTORE_TO_BACKGROUND;
            case "restoretoprevious", "restore_to_previous", "3" -> GifDisposalMethod.RESTORE_TO_PREVIOUS;
            case "donotdispose", "do_not_dispose", "1" -> GifDisposalMethod.DO_NOT_DISPOSE;
            default -> GifDisposalMethod.NONE;
        };
    }

    private static BufferedImage copyImage(BufferedImage img) {
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static ImageReader getReader(ImageFormat format, ImageInputStream iis) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (readers.hasNext()) {
            return readers.next();
        }
        readers = ImageIO.getImageReadersByFormatName(format.name().toLowerCase());
        if (readers.hasNext()) {
            return readers.next();
        }
        return null;
    }

    private enum GifDisposalMethod {
        NONE,
        DO_NOT_DISPOSE,
        RESTORE_TO_BACKGROUND,
        RESTORE_TO_PREVIOUS
    }

    private static final class GifFrameMetadata {
        private GifDisposalMethod disposalMethod = GifDisposalMethod.NONE;
        private int delayCentiseconds = 10;
        private int left = 0;
        private int top = 0;
    }
}
