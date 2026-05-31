package io.github.haiderkagalwala.warden.streams;

/**
 * Factory methods for common {@link StreamConsumer} implementations.
 *
 * <p>Consumers can be composed with {@link #tee(StreamConsumer...)} to fan out a
 * single stream to multiple destinations simultaneously.
 */
public final class ProcessStreams {

//    private ProcessStreams() {}
//
//    /** Prints each chunk to {@code System.out} as UTF-8 text, flushing after each chunk. */
//    public static StreamConsumer printToStdout() {
//        return chunk -> {
//            System.out.print(new String(chunk, StandardCharsets.UTF_8));
//            System.out.flush();
//        };
//    }
//
//    /** Prints each chunk to {@code System.err} as UTF-8 text, flushing after each chunk. */
//    public static StreamConsumer printToStderr() {
//        return chunk -> {
//            System.err.print(new String(chunk, StandardCharsets.UTF_8));
//            System.err.flush();
//        };
//    }
//
//    /**
//     * Accumulates all chunks into {@code sb} as UTF-8 text.
//     * The full output is available in {@code sb} once the process exits.
//     */
//    public static StreamConsumer toStringBuilder(StringBuilder sb) {
//        return chunk -> sb.append(new String(chunk, StandardCharsets.UTF_8));
//    }
//
//    /**
//     * Forwards each raw chunk to {@code out} and flushes immediately.
//     * The caller is responsible for opening and closing the stream.
//     *
//     * <pre>{@code
//     * try (var fos = new FileOutputStream("out.txt")) {
//     *     Warden.run("...").onStdout(ProcessStreams.toOutputStream(fos)).execute();
//     * }
//     * }</pre>
//     */
//    public static StreamConsumer toOutputStream(OutputStream out) {
//        return chunk -> {
//            out.write(chunk);
//            out.flush();
//        };
//    }
//
//    /**
//     * Buffers raw chunks and fires {@code lineAction} once per complete line
//     * (newline stripped). Lines that span chunk boundaries are handled correctly —
//     * the internal buffer persists across calls. Windows {@code \r\n} endings are
//     * normalised to bare strings.
//     *
//     * <p>Each call to {@code lines()} produces an independent stateful consumer.
//     */
//    public static StreamConsumer lines(Consumer<String> lineAction) {
//        var buf = new StringBuilder();
//        return chunk -> {
//            buf.append(new String(chunk, StandardCharsets.UTF_8));
//            int idx;
//            while ((idx = buf.indexOf("\n")) != -1) {
//                var line = buf.substring(0, idx).stripTrailing(); // strips \r on Windows
//                lineAction.accept(line);
//                buf.delete(0, idx + 1);
//            }
//        };
//    }
//
//    /**
//     * Fans out each chunk to all given consumers in order. If a consumer throws,
//     * the remaining consumers are still called. The first exception (if any) is
//     * re-thrown after all consumers have been invoked.
//     *
//     * <pre>{@code
//     * // Print live AND accumulate into a StringBuilder
//     * var sb = new StringBuilder();
//     * ProcessStreams.tee(ProcessStreams.printToStdout(), ProcessStreams.toStringBuilder(sb))
//     * }</pre>
//     */
//    public static StreamConsumer tee(StreamConsumer... consumers) {
//        return chunk -> {
//            Exception first = null;
//            for (var c : consumers) {
//                try {
//                    c.consume(chunk);
//                } catch (Exception e) {
//                    if (first == null) first = e;
//                }
//            }
//            if (first != null) throw first;
//        };
//    }
}
