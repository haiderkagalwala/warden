package io.github.haiderkagalwala.warden.engine;

import io.github.haiderkagalwala.warden.streams.StreamConsumer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Drains a single {@link InputStream} on a virtual thread.
 * Package-private — used only by the execution engines.
 *
 * <p>Per read cycle:
 * <ul>
 *   <li>If {@code shouldCapture}, the raw bytes are appended to {@code capture}.</li>
 *   <li>The {@code consumer} is always called (pass {@link StreamConsumer#NOOP} to discard).</li>
 * </ul>
 */
final class StreamDrainer implements Callable<Void> {

    private static final int BUFFER_SIZE = 8192;

    private final InputStream stream;
    private final ByteArrayOutputStream capture;
    private final StreamConsumer consumer;
    private final boolean shouldCapture;

    StreamDrainer(InputStream stream,
                  ByteArrayOutputStream capture,
                  StreamConsumer consumer,    // never null — callers pass NOOP if not set
                  boolean shouldCapture) {
        this.stream        = stream;
        this.capture       = capture;
        this.consumer      = consumer;
        this.shouldCapture = shouldCapture;
    }

    @Override
    public Void call() throws Exception {
        var buffer = new byte[BUFFER_SIZE];
        int n;
        while ((n = stream.read(buffer)) != -1) {
//            var chunk = Arrays.copyOf(buffer, n);
            if (shouldCapture) capture.write(buffer);
            if (consumer != null)
                consumer.consume(Arrays.copyOf(buffer, n));
        }
        return null;
    }
}
