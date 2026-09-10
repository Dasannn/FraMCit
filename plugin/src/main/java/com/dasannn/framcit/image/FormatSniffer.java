package com.dasannn.framcit.image;

import java.util.Optional;

/**
 * Identifica el formato de una imagen a partir de sus magic bytes (cabecera binaria).
 * No se fia de la extension de la URL ni del header Content-Type (spec.md seccion 3.3).
 */
public final class FormatSniffer {

    private static final byte[] PNG_MAGIC = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final byte[] GIF87A_MAGIC = new byte[] { 'G', 'I', 'F', '8', '7', 'a' };
    private static final byte[] GIF89A_MAGIC = new byte[] { 'G', 'I', 'F', '8', '9', 'a' };

    private static final byte[] BMP_MAGIC = new byte[] { 0x42, 0x4D }; // "BM"

    private FormatSniffer() {}

    /**
     * Determina el formato de la imagen leyendo exclusivamente sus primeros bytes.
     *
     * @param data bytes de la imagen (o al menos los primeros 12 bytes)
     * @return el formato identificado, o Optional.empty() si no coincide con ninguno soportado
     */
    public static Optional<ImageFormat> sniff(byte[] data) {
        if (data == null || data.length < 2) {
            return Optional.empty();
        }

        // BMP: 2 bytes ("BM")
        if (matches(data, 0, BMP_MAGIC)) {
            return Optional.of(ImageFormat.BMP);
        }

        // JPEG: 3 bytes (FF D8 FF)
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF) {
            return Optional.of(ImageFormat.JPEG);
        }

        // GIF: 6 bytes (GIF87a o GIF89a)
        if (data.length >= 6) {
            if (matches(data, 0, GIF87A_MAGIC) || matches(data, 0, GIF89A_MAGIC)) {
                return Optional.of(ImageFormat.GIF);
            }
        }

        // PNG: 8 bytes
        if (data.length >= 8 && matches(data, 0, PNG_MAGIC)) {
            return Optional.of(ImageFormat.PNG);
        }

        // WEBP: 12 bytes (RIFF????WEBP)
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return Optional.of(ImageFormat.WEBP);
        }

        return Optional.empty();
    }

    private static boolean matches(byte[] data, int offset, byte[] pattern) {
        if (data.length < offset + pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }
}
