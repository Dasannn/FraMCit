package com.dasannn.framcit.image;

import com.dasannn.framcit.command.Messages;

/**
 * Excepción lanzada cuando una animación supera el cupo de frames permitido (F11.8, F11.9, F11.14).
 */
public class TooManyFramesException extends ImageValidationException {

    private final int frameCount;
    private final int limit;

    public TooManyFramesException(int frameCount, int limit) {
        super(Messages.quotaTooManyFrames(frameCount, limit));
        this.frameCount = frameCount;
        this.limit = limit;
    }

    public TooManyFramesException(int frameCount, int limit, String customMessage) {
        super(customMessage);
        this.frameCount = frameCount;
        this.limit = limit;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int getLimit() {
        return limit;
    }
}
