package io.github.haiderkagalwala.nexec.result;

import java.time.Duration;

/**
 * The terminal result of a process execution.
 *
 * <p>Switch exhaustively over all four variants:
 * <pre>{@code
 * switch (outcome) {
 *     case ProcessOutcome.Completed c -> System.out.println("exit code: " + c.exitCode());
 *     case ProcessOutcome.TimedOut  t -> System.err.println("timed out after " + t.elapsed());
 *     case ProcessOutcome.Killed    k -> System.err.println("cancelled after " + k.elapsed());
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
     * {@link #succeeded()} returns {@code true} when the exit code is in the set configured
     * via {@code successExitCodes(int...)} on the builder (default: {@code {0}}).
     */
    record Completed(
            int exitCode,
            boolean succeeded,
            Duration duration
    ) implements ProcessOutcome {}

    /** Process was killed because the configured timeout expired. */
    record TimedOut(
            Duration elapsed
    ) implements ProcessOutcome {}

    /**
     * Process was explicitly cancelled by the caller via {@code cancel()}.
     * Distinct from {@link TimedOut} which is system-initiated.
     */
    record Killed(
            Duration elapsed
    ) implements ProcessOutcome {}

    /** An exception occurred — the process never started or an I/O failure happened mid-execution. */
    record Failed(Throwable cause) implements ProcessOutcome {}
}
