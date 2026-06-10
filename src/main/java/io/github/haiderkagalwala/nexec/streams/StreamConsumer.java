package io.github.haiderkagalwala.nexec.streams;

/**
 * Receives raw byte chunks drained from a process stream.
 *
 * <p>Implementations are called once per read from the underlying stream, so chunk
 * sizes vary with I/O throughput. Buffer and reassemble in the implementation if
 * line-oriented or structured data is needed — or use the pre-built helpers in
 * {@link ProcessStreams}.
 *
 * <p>Checked exceptions are permitted so consumers can propagate I/O failures
 * (e.g. writing to a file or WebSocket) without wrapping.
 */
@FunctionalInterface
public interface StreamConsumer {

    /** A no-op consumer that silently discards all bytes. */
    StreamConsumer NOOP = chunk -> {};

    void consume(byte[] chunk) throws Exception;
}
