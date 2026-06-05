package io.github.haiderkagalwala.warden.internal;

import io.github.haiderkagalwala.warden.Warden;
import io.github.haiderkagalwala.warden.handle.PtyHandle;
import io.github.haiderkagalwala.warden.streams.StreamConsumer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for PTY (pseudo-terminal) process execution.
 *
 * <p>Obtain an instance via {@link Warden#interactive(String...)}. Call {@link #start()}
 * to launch the process and receive an {@link PtyHandle} handle immediately.
 *
 * <pre>{@code
 * InteractiveProcess shell = Warden.interactive("bash")
 *         .ptySize(220, 50)
 *         .onOutput(ProcessStreams.printToStdout())
 *         .start();
 *
 * shell.writeLine("echo hello")
 *      .writeLine("exit");
 *
 * shell.await();
 * }</pre>
 */
public final class PtyBuilder {

    final List<String> command;
    Path workingDir;
    Duration timeout;                   // null = no timeout
    int ptyCols                 = 80;
    int ptyRows                 = 24;
    StreamConsumer outputConsumer;      // called per chunk on the combined PTY stream
    Map<String, String> extraEnv = new HashMap<>();

    public PtyBuilder(List<String> command) {
        this.command = command;
    }

    // ── Configuration ─────────────────────────────────────────────────────

    public PtyBuilder workingDir(Path dir)         { this.workingDir = dir; return this; }
    public PtyBuilder timeout(Duration t)          { this.timeout = t; return this; }
    public PtyBuilder noTimeout()                  { this.timeout = null; return this; }

    /** Sets the PTY window dimensions. Default is 80 × 24. */
    public PtyBuilder ptySize(int cols, int rows)  { this.ptyCols = cols; this.ptyRows = rows; return this; }

    /**
     * Registers a consumer called with each raw byte chunk from the PTY output.
     * The PTY merges stdout and stderr — there is only one combined stream.
     */
    public PtyBuilder onOutput(StreamConsumer consumer) { this.outputConsumer = consumer; return this; }

    /**
     * Captures all PTY output into the {@code stdout} field of
     * {@link io.github.haiderkagalwala.warden.result.ProcessOutcome.Completed}.
     * Bytes are available once the outcome future resolves.
     */

    public PtyBuilder env(String key, String value){ this.extraEnv.put(key, value); return this; }
    public PtyBuilder envMap(Map<String, String> e){ this.extraEnv.putAll(e); return this; }

    // ── Execution ─────────────────────────────────────────────────────────

    /**
     * Launches the PTY process and returns an {@link PtyHandle} handle immediately.
     * Use the handle to write to stdin, resize the terminal, or cancel.
     */
    public PtyHandle start() throws IOException {
        return new PtyExecutionEngine(snapshot()).start();
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private PtyConfig snapshot() {
        return new PtyConfig(
                List.copyOf(command),
                workingDir,
                timeout,
                ptyCols,
                ptyRows,
                outputConsumer,
                Map.copyOf(extraEnv)
        );
    }
}
