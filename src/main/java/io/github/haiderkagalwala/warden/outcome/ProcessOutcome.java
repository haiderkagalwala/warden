package io.github.haiderkagalwala.warden.outcome;

import java.time.Duration;

public sealed interface ProcessOutcome permits
        ProcessOutcome.Completed,
        ProcessOutcome.TimedOut,
        ProcessOutcome.Failed {

    // Process ran to natural completion — check exitCode for success/failure
    record Completed(
            int exitCode,
            byte[] stdout,
            byte[] stderr,
            Duration duration
    ) implements ProcessOutcome {

        public boolean succeeded() { return exitCode == 0; }

        public String stdoutAsString() {
            return new String(stdout, java.nio.charset.StandardCharsets.UTF_8);
        }

        public String stderrAsString() {
            return new String(stderr, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    // Process was killed because timeout expired
    record TimedOut(
            Duration elapsed,
            byte[] stdout,   // partial output — still useful
            byte[] stderr
    ) implements ProcessOutcome {}

    // Exception during execution — process never started or IO failure
    record Failed(Throwable cause) implements ProcessOutcome {}
}