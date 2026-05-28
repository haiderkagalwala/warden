package io.github.haiderkagalwala.warden.utils;

import io.github.haiderkagalwala.warden.stream.StreamConsumer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;

public class DrainUtils {

    public static void drain(InputStream stream,
                              ByteArrayOutputStream capture,
                              StreamConsumer consumer,
                              boolean shouldCapture) throws Exception {
        var buffer = new byte[4096];
        int n;
        while ((n = stream.read(buffer)) != -1) {
            if (shouldCapture)
                capture.write(buffer, 0, n);
            if (consumer != null)
                consumer.consume(Arrays.copyOf(buffer, n));
        }
    }

}
