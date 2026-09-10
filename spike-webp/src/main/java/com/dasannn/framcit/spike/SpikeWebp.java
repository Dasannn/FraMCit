package com.dasannn.framcit.spike;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class SpikeWebp {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   FraMCit F0 - Spike TwelveMonkeys ImageIO WebP");
        System.out.println("=================================================");
        System.out.println("Java runtime: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");

        File samplesDir = new File("samples");
        if (!samplesDir.exists() || !samplesDir.isDirectory()) {
            samplesDir = new File("spike-webp/samples");
        }
        if (!samplesDir.exists()) {
            System.err.println("ERROR: samples directory not found!");
            System.exit(1);
        }

        File[] samples = samplesDir.listFiles((dir, name) -> name.endsWith(".webp"));
        if (samples == null || samples.length == 0) {
            System.err.println("ERROR: No .webp files found in " + samplesDir.getAbsolutePath());
            System.exit(1);
        }

        for (File sample : samples) {
            analyzeSample(sample);
        }
    }

    private static void analyzeSample(File file) {
        System.out.println("\n-------------------------------------------------");
        System.out.println("Analizando: " + file.getName() + " (" + file.length() + " bytes)");
        System.out.println("-------------------------------------------------");

        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) {
                System.err.println("  [FAIL] ImageIO.createImageInputStream retorno null.");
                return;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                System.err.println("  [FAIL] No hay ImageReader registrado para este archivo.");
                return;
            }

            ImageReader reader = readers.next();
            System.out.println("  Reader: " + reader.getClass().getName() + " (formato: " + reader.getFormatName() + ")");
            reader.setInput(input, false, false);

            // getNumImages
            int declaredNumImagesFalse = -1;
            int declaredNumImagesTrue = -1;
            try {
                declaredNumImagesFalse = reader.getNumImages(false);
            } catch (Exception e) {
                System.out.println("  reader.getNumImages(false) arrojo: " + e.getMessage());
            }

            try {
                declaredNumImagesTrue = reader.getNumImages(true);
            } catch (Exception e) {
                System.out.println("  reader.getNumImages(true) arrojo: " + e.getMessage());
            }
            System.out.println("  reader.getNumImages(false): " + declaredNumImagesFalse);
            System.out.println("  reader.getNumImages(true):  " + declaredNumImagesTrue);

            // Lectura frame a frame
            int framesRead = 0;
            for (int i = 0; ; i++) {
                try {
                    BufferedImage frame = reader.read(i);
                    framesRead++;
                    int width = frame.getWidth();
                    int height = frame.getHeight();
                    System.out.println("  -> Frame " + i + ": dimensiones " + width + "x" + height);

                    // Inspeccion de metadatos del frame
                    inspectMetadata(reader, i);
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("  Fin de lectura de frames en indice " + i + " (IndexOutOfBoundsException).");
                    break;
                } catch (Exception e) {
                    System.out.println("  Error al leer frame " + i + ": " + e.getClass().getName() + ": " + e.getMessage());
                    break;
                }
            }

            System.out.println("  ==> TOTAL FRAMES LEIDOS CON TwelveMonkeys ImageIO: " + framesRead);

            // Metadatos a nivel de stream
            try {
                IIOMetadata streamMeta = reader.getStreamMetadata();
                if (streamMeta != null) {
                    System.out.println("  [StreamMetadata detectado]");
                    for (String fmt : streamMeta.getMetadataFormatNames()) {
                        System.out.println("    Formato stream: " + fmt);
                        Node root = streamMeta.getAsTree(fmt);
                        dumpTree(root, "    ");
                    }
                } else {
                    System.out.println("  [StreamMetadata: null]");
                }
            } catch (Exception e) {
                System.out.println("  StreamMetadata error: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("  Error procesando archivo: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    private static void inspectMetadata(ImageReader reader, int imageIndex) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(imageIndex);
            if (metadata == null) {
                System.out.println("     Metadatos de imagen: null");
                return;
            }
            String[] formatNames = metadata.getMetadataFormatNames();
            if (formatNames == null || formatNames.length == 0) {
                System.out.println("     Sin formatos de metadatos expuestos.");
                return;
            }
            for (String formatName : formatNames) {
                System.out.println("     Formato metadatos: " + formatName);
                Node root = metadata.getAsTree(formatName);
                dumpTree(root, "       ");
            }
        } catch (Exception e) {
            System.out.println("     Error leyendo metadatos: " + e.getMessage());
        }
    }

    private static void dumpTree(Node node, String indent) {
        if (node == null) return;
        StringBuilder sb = new StringBuilder(indent);
        sb.append("<").append(node.getNodeName());
        NamedNodeMap attrs = node.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                sb.append(" ").append(attr.getNodeName()).append("=\"").append(attr.getNodeValue()).append("\"");
            }
        }
        sb.append(">");
        if (node.getNodeValue() != null) {
            sb.append(node.getNodeValue());
        }
        System.out.println(sb.toString());

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            dumpTree(child, indent + "  ");
        }
    }
}
