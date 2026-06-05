package io.github.haiderkagalwala.warden.internal;

import io.github.haiderkagalwala.warden.streams.StreamConsumer;

import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Drains a single {@link InputStream} on a virtual thread.
 * Package-private — used only by the execution engines.
 */
final class StreamDrainer implements Callable<Void> {

    private static final int BUFFER_SIZE = 8192;

    private final InputStream stream;
    private final StreamConsumer consumer;

    StreamDrainer(InputStream stream,
                  StreamConsumer consumer) {
        this.stream   = stream;
        this.consumer = consumer;
    }

    @Override
    public Void call() throws Exception {
        var buffer = new byte[BUFFER_SIZE];
        int n;
        while ((n = stream.read(buffer)) != -1) {
            if (consumer != null)
                consumer.consume(Arrays.copyOf(buffer, n));
        }
        return null;
    }
}
