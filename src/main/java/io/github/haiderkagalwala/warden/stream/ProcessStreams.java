package io.github.haiderkagalwala.warden.stream;


import java.nio.charset.StandardCharsets;

public final class ProcessStreams {


    public static StreamConsumer printToStdout() {
        return chunk -> {
            System.out.print(new String(chunk, StandardCharsets.UTF_8));
            System.out.flush();
        };
    }
}