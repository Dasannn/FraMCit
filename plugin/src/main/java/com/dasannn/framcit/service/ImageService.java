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

    private final Downloader downloader;
    private final DownloadQueue queue;

    public ImageService(Downloader downloader, DownloadQueue queue) {
        this.downloader = Objects.requireNonNull(downloader, "downloader cannot be null");
        this.queue = Objects.requireNonNull(queue, "queue cannot be null");
    }

    /**
     * Encola la descarga y procesamiento de una imagen a partir de su URL.
     * Todo el trabajo pesado (red, decodificacion y conversion) corre en el pool de DownloadQueue.
     *
     * @param rawUrl URL sin procesar introducida por el usuario
     * @return CompletableFuture que se completa con el ImageProcessResult
     */
    public CompletableFuture<ImageProcessResult> process(String rawUrl) {
        return queue.submit(downloader, rawUrl)
            .thenApply(downloadResult -> {
                if (downloadResult instanceof DownloadResult.Failure failure) {
                    return ImageProcessResult.failure(failure.err().userMessage());
                }

                DownloadResult.Success success = (DownloadResult.Success) downloadResult;
                try {
                    DecodedImage decoded = ImageDecoder.decode(success.bytes());
                    ConvertedImage converted = MapConverter.convert(decoded);
                    return ImageProcessResult.success(converted, success.finalUri(), success.bytes().length);
                } catch (ImageValidationException e) {
                    return ImageProcessResult.failure(e.getMessage(), e);
                } catch (Throwable t) {
                    return ImageProcessResult.failure(Messages.ERROR_CORRUPTED_FILE, t);
                }
            });
    }

    public Downloader getDownloader() {
        return downloader;
    }

    public DownloadQueue getQueue() {
        return queue;
    }
}
