package io.github.haiderkagalwala.warden;

import io.github.haiderkagalwala.warden.stream.StreamConsumer;
import io.github.haiderkagalwala.warden.outcome.ProcessOutcome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Warden {

    public static CommandBuilder run(String... command) {
        return new CommandBuilder(List.of(command));
    }

    public static InteractiveBuilder interactive(String... command) {
        return new InteractiveBuilder(List.of(command));
    }

    private Warden() {}

    // ─── Normal execution builder ───────────────────────────────
    public static final class CommandBuilder {

        private final List<String> command;
        private Path workingDir;
        private Duration timeout        = Duration.ofSeconds(30);
        private boolean timeoutEnabled  = true;
        private boolean captureStdout   = false;
        private boolean captureStderr   = false;
        private boolean mergeOutputAndError = false;
        private StreamConsumer stdoutConsumer;
        private StreamConsumer stderrConsumer;


        private CommandBuilder(List<String> command) {
            this.command = command;
        }

        public CommandBuilder workingDir(Path dir) {
            this.workingDir = dir;
            return this;
        }

        public CommandBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            this.timeoutEnabled = true;
            return this;
        }

        public CommandBuilder noTimeout() {
            this.timeoutEnabled = false;
            return this;
        }

        public CommandBuilder captureStdout() {
            this.captureStdout = true;
            return this;
        }

        public CommandBuilder captureStderr() {
            this.captureStderr = true;
            return this;
        }

        public CommandBuilder onStdout(StreamConsumer consumer) {
            this.stdoutConsumer = consumer;
            return this;
        }

        public CommandBuilder onStderr(StreamConsumer consumer) {
            if (!mergeOutputAndError)
                this.stderrConsumer = consumer;
            return this;
        }
        public CommandBuilder mergeOutputAndError(boolean mergeOutputAndError) {
            this.mergeOutputAndError = mergeOutputAndError;
            this.stderrConsumer = null;
            return this;
        }

        public ProcessOutcome execute() throws Exception {
            // 1. Configure ProcessBuilder
            var pb = new ProcessBuilder(this.command);
            if (workingDir != null) pb.directory(workingDir.toFile());
            if (mergeOutputAndError) pb.redirectErrorStream(true);
            if (stdoutConsumer == null && !captureStdout)
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            if (stderrConsumer == null && !captureStderr && !mergeOutputAndError)
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);

            // 2. Start process
            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                return new ProcessOutcome.Failed(e); // ← never started
            }

            var startTime = Instant.now();
            var capturedStdout = new ByteArrayOutputStream();
            var capturedStderr = new ByteArrayOutputStream();

            // 3. Drain concurrently using virtual threads
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

                var stdoutFuture = executor.submit(() -> {
                    drain(process.getInputStream(), capturedStdout, stdoutConsumer, captureStdout);
                    return null;
                });

                var stderrFuture = mergeOutputAndError ? null :
                        executor.submit(() -> {
                            drain(process.getErrorStream(), capturedStderr, stderrConsumer, captureStderr);
                            return null;
                        });

                // 4. Wait — concurrently with draining
                boolean finished;
                if (timeoutEnabled) {
                    finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } else {
                    process.waitFor();
                    finished = true;
                }

                // 5. Wait for drainers to finish
                stdoutFuture.get();
                if (stderrFuture != null) stderrFuture.get();

                var duration = Duration.between(startTime, Instant.now());
                var stdout = capturedStdout.toByteArray();
                var stderr = capturedStderr.toByteArray();

                // 6. Return correct outcome type
                if (!finished) {
                    process.destroyForcibly(); // ← kill before returning
                    return new ProcessOutcome.TimedOut(duration, stdout, stderr);
                }

                return new ProcessOutcome.Completed(
                        process.exitValue(),
                        stdout,
                        stderr,
                        duration
                );

            } catch (Exception e) {
                return new ProcessOutcome.Failed(e);
            }
        }

        // Private helper — drains one stream
        private void drain(InputStream stream,
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

    // ─── Interactive execution builder ──────────────────────────
    public static final class InteractiveBuilder {

        private final List<String> command;
        private Path workingDir;
        private Duration idleTimeout;
        private boolean usePty          = false;
        private int ptyCols             = 80;
        private int ptyRows             = 24;
        private Consumer<byte[]> outputConsumer;

        private InteractiveBuilder(List<String> command) {
            this.command = command;
        }

        public InteractiveBuilder workingDir(Path dir) {
            this.workingDir = dir;
            return this;
        }

        public InteractiveBuilder idleTimeout(Duration timeout) {
            this.idleTimeout = timeout;
            return this;
        }

        public InteractiveBuilder withPty(int cols, int rows) {
            this.usePty = true;
            this.ptyCols = cols;
            this.ptyRows = rows;
            return this;
        }

        public InteractiveBuilder onOutput(Consumer<byte[]> consumer) {
            this.outputConsumer = consumer;
            return this;
        }

//        public InteractiveProcess start() {
////            throw new UnsupportedOperationException("Not implemented yet");
//        }
    }
}