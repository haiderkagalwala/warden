package io.github.haiderkagalwala.warden.result;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The terminal result of a process execution.
 *
 * <p>Switch exhaustively over all four variants:
 * <pre>{@code
 * switch (outcome) {
 *     case ProcessOutcome.Completed c -> System.out.println(c.stdoutAsString());
 *     case ProcessOutcome.TimedOut  t -> System.err.println("timed out after " + t.elapsed());
 *     case ProcessOutcome.Killed    k -> System.err.println("cancelled");
 *     case ProcessOutcome.Failed    f -> f.cause().printStackTrace();
 * }
 * }</pre>
 */
public sealed interface ProcessOutcome permits
        ProcessOutcome.Completed,
        ProcessOutcome.TimedOut,
        ProcessOutcome.Killed,
        ProcessOutcome.Failed {

    /**
     * Process ran to natural completion.
     * Check {@link #succeeded()} — it respects any custom {@code successExitCodes}
     * configured on the builder, not just {@code exitCode == 0}.
     */
    record Completed(
            int exitCode,
            boolean success,
            Duration duration
    ) implements ProcessOutcome {

        /**
         * Returns {@code true} if the exit code matched the builder's configured
         * success-exit-code set. Delegates to the {@code success} field — does NOT
         * hardcode {@code exitCode == 0}.
         */
        public boolean succeeded() { return success; }

    }

    /**
     * Process was killed because the configured timeout expired.
     * Carries any output collected before the kill.
     */
    record TimedOut(
            Duration elapsed
    ) implements ProcessOutcome {
    }

    /**
     * Process was explicitly cancelled by the caller via {@code cancel()}.
     * Distinct from {@link TimedOut} (system-initiated) — this is user-initiated.
     * Carries any output collected before the kill.
     */
    record Killed(
            Duration elapsed
    ) implements ProcessOutcome {
    }

    /** An exception occurred — process never started, or an I/O failure happened mid-execution. */
    record Failed(Throwable cause) implements ProcessOutcome {}
}
