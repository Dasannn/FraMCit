package com.dasannn.framcit.service;

import com.dasannn.framcit.command.Messages;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.DecodedImage;
import com.dasannn.framcit.image.ImageDecoder;
import com.dasannn.framcit.image.ImageValidationException;
import com.dasannn.framcit.image.MapConverter;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.net.DownloadResult;
import com.dasannn.framcit.net.Downloader;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio central de procesamiento de imagenes (F4.1).
 * Encadena la descarga, decodificacion y conversion de imagenes dentro del pool
 * de hilos de DownloadQueue, fuera del hilo principal del servidor.
 *
 * Actua como puente de errores unificando DownloadError e ImageValidationException
 * en un unico ImageProcessResult tipado e inmutable.
 */
public class ImageService {

    private volatile Downloader downloader;
    private final DownloadQueue queue;

    public ImageService(Downloader downloader, DownloadQueue queue) {
        this.downloader = Objects.requireNonNull(downloader, "downloader cannot be null");
        this.queue = Objects.requireNonNull(queue, "queue cannot be null");
    }

    public void setDownloader(Downloader downloader) {
        this.downloader = Objects.requireNonNull(downloader, "downloader cannot be null");
    }

    /**
     * Encola la descarga y procesamiento de una imagen a partir de su URL con límite global por defecto.
     *
     * @param rawUrl URL sin procesar introducida por el usuario
     * @return CompletableFuture que se completa con el ImageProcessResult
     */
    public CompletableFuture<ImageProcessResult> process(String rawUrl) {
        return process(rawUrl, ImageDecoder.getMaxFrames());
    }

    /**
     * Encola la descarga y procesamiento de una imagen aplicando el límite efectivo de frames por petición (F11.8, F11.9).
     *
     * @param rawUrl URL sin procesar introducida por el usuario
     * @param maxFrames límite de frames resuelto en el hilo principal
     * @return CompletableFuture que se completa con el ImageProcessResult
     */
    public CompletableFuture<ImageProcessResult> process(String rawUrl, int maxFrames) {
        if (isProcessStringOverridden()) {
            return process(rawUrl);
        }
        return submitProcess(rawUrl, maxFrames);
    }

    private CompletableFuture<ImageProcessResult> submitProcess(String rawUrl, int maxFrames) {
        return queue.submit(downloader, rawUrl)
            .thenApply(downloadResult -> {
                if (downloadResult instanceof DownloadResult.Failure failure) {
                    return ImageProcessResult.failure(failure.err().userMessage());
                }

                DownloadResult.Success success = (DownloadResult.Success) downloadResult;
                try {
                    DecodedImage decoded = ImageDecoder.decode(success.bytes(), maxFrames);
                    ConvertedImage converted = MapConverter.convert(decoded);
                    return ImageProcessResult.success(converted, success.finalUri(), success.bytes().length);
                } catch (ImageValidationException e) {
                    return ImageProcessResult.failure(e.getMessage(), e);
                } catch (Throwable t) {
                    return ImageProcessResult.failure(Messages.ERROR_CORRUPTED_FILE, t);
                }
            });
    }

    private boolean isProcessStringOverridden() {
        try {
            return getClass().getMethod("process", String.class).getDeclaringClass() != ImageService.class
                && getClass().getMethod("process", String.class, int.class).getDeclaringClass() == ImageService.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public Downloader getDownloader() {
        return downloader;
    }

    public DownloadQueue getQueue() {
        return queue;
    }
}
