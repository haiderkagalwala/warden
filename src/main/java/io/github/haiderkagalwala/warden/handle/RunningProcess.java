package io.github.haiderkagalwala.warden.handle;

import io.github.haiderkagalwala.warden.result.ProcessOutcome;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle to an asynchronously running process.
 *
 * <h3>Stream ownership</h3>
 * <ul>
 *   <li>{@link #stdin()} — always safe to write to. Flush after each write;
 *       close to send EOF to the child process.</li>
 *   <li>{@link #stdout()} — only read directly if you did <em>not</em> configure
 *       {@code captureStdout()} or {@code onStdout()} on the builder. Those options
 *       attach a background drainer; competing reads produce corrupt, interleaved data.</li>
 *   <li>{@link #stderr()} — same rule as stdout.</li>
 * </ul>
 *
 * <h3>Outcome</h3>
 * The future from {@link #outcome()} completes <em>after</em> the process exits
 * <em>and</em> all background drainers have finished flushing captured bytes.
 */
public final class RunningProcess {

    private final Process process;
    private final CompletableFuture<ProcessOutcome> outcomeFuture;
    private final Runnable cancelAction;
    private final AtomicBoolean cancelled;

    public RunningProcess(Process process,
                          CompletableFuture<ProcessOutcome> outcomeFuture,
                          AtomicBoolean cancelled,
                          Runnable cancelAction) {
        this.process       = process;
        this.outcomeFuture = outcomeFuture;
        this.cancelled     = cancelled;
        this.cancelAction  = cancelAction;
    }

    // ── Stream access ─────────────────────────────────────────────────────

    /** Write to the process stdin. Flush after writes; close to signal EOF. */
    public OutputStream stdin()  { return process.getOutputStream(); }

    /** Read process stdout directly. See class Javadoc for ownership rules. */
    public InputStream  stdout() { return process.getInputStream(); }

    /** Read process stderr directly. See class Javadoc for ownership rules. */
    public InputStream  stderr() { return process.getErrorStream(); }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public boolean isAlive() { return process.isAlive(); }

    /**
     * Gracefully kills the process tree (SIGTERM → 3 s wait → SIGKILL).
     * The outcome future resolves as {@link ProcessOutcome.Killed}.
     */
    public void cancel() {
        cancelled.set(true);
        cancelAction.run();
    }

    // ── Outcome ───────────────────────────────────────────────────────────

    /**
     * Async outcome future. Resolves once the process exits and all background
     * drain tasks have finished. The {@link ProcessOutcome} variant tells you
     * whether it completed normally, timed out, was cancelled, or failed.
     */
    public CompletableFuture<ProcessOutcome> outcome() { return outcomeFuture; }

    /** Blocks the calling thread until the process finishes and returns its outcome. */
    public ProcessOutcome await() { return outcomeFuture.join(); }
}
