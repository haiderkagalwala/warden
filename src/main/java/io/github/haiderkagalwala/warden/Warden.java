package io.github.haiderkagalwala.warden;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
        private Consumer<byte[]> stdoutConsumer;
        private Consumer<byte[]> stderrConsumer;

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

        // User wants bytes back in ProcessOutcome
        public CommandBuilder captureStdout() {
            this.captureStdout = true;
            return this;
        }

        public CommandBuilder captureStderr() {
            this.captureStderr = true;
            return this;
        }

        // User wants to consume output in real-time
        public CommandBuilder onStdout(Consumer<byte[]> consumer) {
            this.stdoutConsumer = consumer;
            return this;
        }

        public CommandBuilder onStderr(Consumer<byte[]> consumer) {
            this.stderrConsumer = consumer;
            return this;
        }

        public ProcessOutcome execute() {
            throw new UnsupportedOperationException("Not implemented yet");
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

        public InteractiveProcess start() {
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }
}