package io.github.haiderkagalwala.nexec.internal;

import io.github.haiderkagalwala.nexec.Nexec;
import io.github.haiderkagalwala.nexec.handle.PtyHandle;
import io.github.haiderkagalwala.nexec.streams.StreamConsumer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for PTY (pseudo-terminal) process execution.
 *
 * <p>Obtain an instance via {@link Nexec#interactive(String...)}. Call {@link #start()}
 * to launch the process and receive a {@link PtyHandle} immediately.
 *
 * <p><b>Requires pty4j on the classpath.</b> pty4j is an optional dependency in nexec —
 * add it explicitly if you use PTY features. See {@link Nexec#interactive(String...)} for
 * the Maven coordinates.
 *
 * <pre>{@code
 * PtyHandle shell = Nexec.interactive("bash")
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
    Duration timeout;
    int ptyCols                      = 80;
    int ptyRows                      = 24;
    StreamConsumer outputConsumer;
    Map<String, String> extraEnv     = new HashMap<>();
    boolean consoleMode              = false;
    boolean windowsAnsiColorEnabled  = false;
    int[] successExitCodes           = {0};

    public PtyBuilder(List<String> command) {
        this.command = command;
    }

    /** Sets the working directory for the PTY process. */
    public PtyBuilder workingDir(Path dir)         { this.workingDir = dir; return this; }

    /** Sets the execution timeout. */
    public PtyBuilder timeout(Duration t)          { this.timeout = t; return this; }

    /** Disables the execution timeout. */
    public PtyBuilder noTimeout()                  { this.timeout = null; return this; }

    /** Sets the PTY window dimensions. Default is 80 × 24. */
    public PtyBuilder ptySize(int cols, int rows)  { this.ptyCols = cols; this.ptyRows = rows; return this; }

    /** Registers a consumer called with each raw byte chunk from the PTY output stream (stdout and stderr merged). */
    public PtyBuilder onOutput(StreamConsumer consumer) { this.outputConsumer = consumer; return this; }

    /**
     * Uses the ConPTY backend on Windows (requires Windows 10 version 1903 or later).
     * No-op on Unix. By default, WinPTY is used for broader Windows compatibility.
     */
    public PtyBuilder useConPty()  { this.consoleMode = true; return this; }

    /**
     * Uses the WinPTY backend on Windows. This is the default and works on Windows 7 and later.
     * No-op on Unix.
     */
    public PtyBuilder useWinPty()  { this.consoleMode = false; return this; }

    /**
     * Enables ANSI color sequence processing on Windows.
     * No-op on Unix where ANSI colors are always supported.
     */
    public PtyBuilder windowsAnsiColors() { this.windowsAnsiColorEnabled = true; return this; }

    /** Adds a single environment variable. */
    public PtyBuilder env(String key, String value){ this.extraEnv.put(key, value); return this; }

    /** Adds multiple environment variables. */
    public PtyBuilder envMap(Map<String, String> e){ this.extraEnv.putAll(e); return this; }

    /**
     * Sets the exit codes that are treated as successful. Defaults to {@code {0}}.
     * {@link io.github.haiderkagalwala.nexec.result.ProcessOutcome.Completed#succeeded()} returns
     * {@code true} only when the process exit code is in this set.
     */
    public PtyBuilder successExitCodes(int... codes) { this.successExitCodes = codes.clone(); return this; }

    /**
     * Launches the PTY process and returns a {@link PtyHandle} immediately.
     * Use the handle to write to stdin, resize the terminal, or cancel.
     */
    public PtyHandle start() throws IOException {
        return new PtyExecutionEngine(snapshot()).start();
    }

    private PtyConfig snapshot() {
        return new PtyConfig(
                List.copyOf(command),
                workingDir,
                timeout,
                ptyCols,
                ptyRows,
                outputConsumer,
                Map.copyOf(extraEnv),
                consoleMode,
                windowsAnsiColorEnabled,
                successExitCodes.clone()
        );
    }
}
