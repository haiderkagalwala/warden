package io.github.haiderkagalwala.warden.stream;

@FunctionalInterface
public interface StreamConsumer {
    public void consume(byte[] buffer) throws Exception;
}

