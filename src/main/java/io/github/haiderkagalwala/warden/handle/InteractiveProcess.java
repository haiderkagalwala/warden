package io.github.haiderkagalwala.warden.handle;

import io.github.haiderkagalwala.warden.result.ProcessOutcome;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle to a running PTY (pseudo-terminal) process.
 *
 * <p>A PTY merges stdout and stderr into a single master stream. Use {@link #output()} to
 * read it directly, or configure {@code onOutput()} / {@code captureOutput()} on the
 * builder to attach a background drainer instead.
 *
 * <p>Write to the child process via {@link #writeLine}, {@link #write(String)}, or
 * {@link #write(byte[])} — all flush immediately. Resize the terminal window at any
 * time with {@link #resize(int, int)}.
 *
 * <p>All write methods return {@code this} for chaining:
 * <pre>{@code
 * shell.writeLine("echo hello")
 *      .writeLine("ls -la")
 *      .writeLine("exit");
 * }</pre>
 */
public final class InteractiveProcess {

    private final Process process; // PtyProcess at runtime
    private final CompletableFuture<ProcessOutcome> outcomeFuture;
    private final Runnable cancelAction;
    private final AtomicBoolean cancelled;

    public InteractiveProcess(Process process,
                               CompletableFuture<ProcessOutcome> outcomeFuture,
                               AtomicBoolean cancelled,
                               Runnable cancelAction) {
        this.process       = process;
        this.outcomeFuture = outcomeFuture;
        this.cancelled     = cancelled;
        this.cancelAction  = cancelAction;
    }

    // ── Stdin writes ──────────────────────────────────────────────────────

    /** Writes {@code line + "\n"} to stdin and flushes immediately. Chainable. */
    public InteractiveProcess writeLine(String line) throws IOException {
        return write((line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes {@code text} without appending a newline — useful for raw control
     * sequences (e.g. TAB completion, arrow keys). Chainable.
     */
    public InteractiveProcess write(String text) throws IOException {
        return write(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes raw bytes to stdin and flushes immediately. Chainable. */
    public InteractiveProcess write(byte[] bytes) throws IOException {
        var stdin = process.getOutputStream();
        stdin.write(bytes);
        stdin.flush();
        return this;
    }

    /** Raw access to the PTY stdin stream. Caller is responsible for flushing. */
    public OutputStream stdin() { return process.getOutputStream(); }

    // ── PTY output ────────────────────────────────────────────────────────

    /**
     * Combined PTY output stream (stdout + stderr merged by the terminal).
     * Only read directly if you did <em>not</em> configure {@code onOutput()} or
     * {@code captureOutput()} — those options attach a background drainer.
     */
    public InputStream output() { return process.getInputStream(); }

    // ── Terminal control ──────────────────────────────────────────────────

    /**
     * Resizes the PTY window and sends SIGWINCH so the child process can re-render
     * (e.g. vim, htop, and bash prompts adapt to the new size).
     * No-op if the underlying process is not a pty4j {@code PtyProcess}.
     */
    public void resize(int cols, int rows) {
        if (process instanceof com.pty4j.PtyProcess pty) {
            pty.setWinSize(new com.pty4j.WinSize(cols, rows));
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public boolean isAlive() { return process.isAlive(); }

    /**
     * Gracefully kills the PTY process tree (SIGTERM → 3 s wait → SIGKILL).
     * The outcome future resolves as {@link ProcessOutcome.Killed}.
     */
    public void cancel() {
        cancelled.set(true);
        cancelAction.run();
    }

    // ── Outcome ───────────────────────────────────────────────────────────

    /** Async outcome future. Completes once the process exits and the output drain finishes. */
    public CompletableFuture<ProcessOutcome> outcome() { return outcomeFuture; }

    /** Blocks the calling thread until the PTY process finishes and returns its outcome. */
    public ProcessOutcome await() { return outcomeFuture.join(); }
}
