package io.github.haiderkagalwala.nexec.handle;

import io.github.haiderkagalwala.nexec.result.ProcessOutcome;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle to a running PTY (pseudo-terminal) process.
 *
 * <p>A PTY merges stdout and stderr into a single master stream. Read it directly via
 * {@link #output()}, or configure {@code onOutput()} on the builder to attach a background
 * drainer instead — do not do both simultaneously.
 *
 * <p>Write to the child process via {@link #writeLine}, {@link #write(String)}, or
 * {@link #write(byte[])} — all flush immediately. Resize the terminal at any time with
 * {@link #resize(int, int)}.
 *
 * <p>All write methods return {@code this} for chaining:
 * <pre>{@code
 * shell.writeLine("echo hello")
 *      .writeLine("ls -la")
 *      .writeLine("exit");
 * }</pre>
 */
public final class PtyHandle {

    private final Process process;
    private final CompletableFuture<ProcessOutcome> outcomeFuture;
    private final Runnable cancelAction;
    private final AtomicBoolean cancelled;

    public PtyHandle(Process process,
                     CompletableFuture<ProcessOutcome> outcomeFuture,
                     AtomicBoolean cancelled,
                     Runnable cancelAction) {
        this.process       = process;
        this.outcomeFuture = outcomeFuture;
        this.cancelled     = cancelled;
        this.cancelAction  = cancelAction;
    }

    /** Writes {@code line + "\n"} to stdin and flushes immediately. Returns {@code this} for chaining. */
    public PtyHandle writeLine(String line) throws IOException {
        return write((line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes {@code text} to stdin without a trailing newline and flushes immediately.
     * Useful for raw control sequences such as TAB completion or arrow keys.
     * Returns {@code this} for chaining.
     */
    public PtyHandle write(String text) throws IOException {
        return write(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes raw bytes to stdin and flushes immediately. Returns {@code this} for chaining. */
    public PtyHandle write(byte[] bytes) throws IOException {
        var stdin = process.getOutputStream();
        stdin.write(bytes);
        stdin.flush();
        return this;
    }

    /** Returns the PTY stdin stream. Caller is responsible for flushing. */
    public OutputStream stdin() { return process.getOutputStream(); }

    /**
     * Returns the combined PTY output stream (stdout and stderr merged by the terminal).
     * Read directly only if no {@code onOutput()} consumer was configured on the builder.
     */
    public InputStream output() { return process.getInputStream(); }

    /**
     * Resizes the PTY window and sends SIGWINCH to the child process so it can re-render.
     * No-op if the underlying process is not a pty4j {@code PtyProcess}.
     */
    public void resize(int cols, int rows) {
        if (process instanceof com.pty4j.PtyProcess pty) {
            pty.setWinSize(new com.pty4j.WinSize(cols, rows));
        }
    }

    /** Returns {@code true} if the PTY process is still running. */
    public boolean isAlive() { return process.isAlive(); }

    /**
     * Kills the PTY process (SIGTERM → 3 s wait → SIGKILL).
     * The outcome future resolves as {@link ProcessOutcome.Killed}.
     * Idempotent — safe to call more than once.
     */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelAction.run();
        }
    }

    /** Returns a future that resolves once the PTY process exits and the output drain finishes. */
    public CompletableFuture<ProcessOutcome> outcome() { return outcomeFuture; }

    /** Blocks until the PTY process finishes and returns its outcome. */
    public ProcessOutcome await() { return outcomeFuture.join(); }
}
