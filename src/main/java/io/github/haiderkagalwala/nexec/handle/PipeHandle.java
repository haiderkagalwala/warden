package io.github.haiderkagalwala.nexec.handle;

import io.github.haiderkagalwala.nexec.result.ProcessOutcome;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle to an asynchronously running process.
 *
 * <p>Write to the child process via {@link #writeLine}, {@link #write(String)}, or
 * {@link #write(byte[])} — all flush immediately.
 *
 * <h3>Stream ownership</h3>
 * <p>{@link #stdout()} and {@link #stderr()} are safe to read directly only if no
 * {@code onStdout} / {@code onStderr} consumer was configured on the builder. A configured
 * consumer runs a background drainer on the same stream; competing reads produce corrupt,
 * interleaved data.
 *
 * <p>{@link #stdin()} is always safe to write to. Flush after each write; close to signal
 * EOF to the child process.
 *
 * <h3>Outcome</h3>
 * <p>The future from {@link #outcome()} completes after the process exits and all background
 * drain tasks have finished.
 */
public final class PipeHandle {

    private final Process process;
    private final CompletableFuture<ProcessOutcome> outcomeFuture;
    private final Runnable cancelAction;
    private final AtomicBoolean cancelled;

    public PipeHandle(Process process,
                      CompletableFuture<ProcessOutcome> outcomeFuture,
                      AtomicBoolean cancelled,
                      Runnable cancelAction) {
        this.process       = process;
        this.outcomeFuture = outcomeFuture;
        this.cancelled     = cancelled;
        this.cancelAction  = cancelAction;
    }

    /** Writes {@code line + "\n"} to stdin and flushes immediately. Returns {@code this} for chaining. */
    public PipeHandle writeLine(String line) throws IOException {
        return write((line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /** Writes {@code text} to stdin without a trailing newline and flushes immediately. Returns {@code this} for chaining. */
    public PipeHandle write(String text) throws IOException {
        return write(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes raw bytes to stdin and flushes immediately. Returns {@code this} for chaining. */
    public PipeHandle write(byte[] bytes) throws IOException {
        var stdin = process.getOutputStream();
        stdin.write(bytes);
        stdin.flush();
        return this;
    }

    /** Returns the process stdin stream. Flush after writes; close to signal EOF to the child process. */
    public OutputStream stdin()  { return process.getOutputStream(); }

    /** Returns the process stdout stream. See class Javadoc for ownership rules. */
    public InputStream  stdout() { return process.getInputStream(); }

    /** Returns the process stderr stream. See class Javadoc for ownership rules. */
    public InputStream  stderr() { return process.getErrorStream(); }

    /** Returns {@code true} if the process is still running. */
    public boolean isAlive() { return process.isAlive(); }

    /**
     * Kills the process tree (SIGTERM → 3 s wait → SIGKILL).
     * The outcome future resolves as {@link ProcessOutcome.Killed}.
     * Idempotent — safe to call more than once.
     */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelAction.run();
        }
    }

    /**
     * Returns a future that resolves once the process exits and all background drain tasks
     * have finished. The {@link ProcessOutcome} variant indicates whether the process
     * completed normally, timed out, was cancelled, or failed.
     */
    public CompletableFuture<ProcessOutcome> outcome() { return outcomeFuture; }

    /** Blocks until the process finishes and returns its outcome. */
    public ProcessOutcome await() { return outcomeFuture.join(); }
}
