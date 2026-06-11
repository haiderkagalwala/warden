package io.github.haiderkagalwala.nexec.internal;

import io.github.haiderkagalwala.nexec.Nexec;
import io.github.haiderkagalwala.nexec.handle.PipeHandle;
import io.github.haiderkagalwala.nexec.streams.StreamConsumer;
import io.github.haiderkagalwala.nexec.result.ProcessOutcome;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for normal (non-PTY) process execution.
 *
 * <p>Obtain an instance via {@link Nexec#run(String...)}. Call {@link #execute()} to
 * block until the process exits, or {@link #executeAsync()} for a non-blocking {@link PipeHandle}.
 *
 * <pre>{@code
 * // Synchronous
 * ProcessOutcome outcome = Nexec.run("git", "status")
 *         .onStdout(ProcessStreams.printToStdout())
 *         .execute();
 *
 * // Asynchronous
 * PipeHandle handle = Nexec.run("tail", "-f", "/var/log/app.log")
 *         .noTimeout()
 *         .onStdout(ProcessStreams.printToStdout())
 *         .executeAsync();
 * handle.cancel();
 * }</pre>
 */
public final class CommandBuilder {

    final List<String> command;
    Path workingDir;
    Duration timeout            = Duration.ofSeconds(30);
    boolean timeoutEnabled      = true;
    boolean mergeOutputAndError = false;
    StreamConsumer stdoutConsumer;
    StreamConsumer stderrConsumer;
    boolean inheritIO           = false;
    File redirectStdout;
    File redirectStderr;
    File redirectStdin;
    Map<String, String> extraEnv = new HashMap<>();
    boolean clearEnv            = false;
    int[] successExitCodes      = {0};

    public CommandBuilder(List<String> command) {
        this.command = command;
    }

    /** Sets the working directory for the process. */
    public CommandBuilder workingDir(Path dir)           { this.workingDir = dir; return this; }

    /** Sets the execution timeout. Defaults to 30 seconds. */
    public CommandBuilder timeout(Duration t)            { this.timeout = t; this.timeoutEnabled = true; return this; }

    /** Disables the execution timeout. */
    public CommandBuilder noTimeout()                    { this.timeoutEnabled = false; return this; }

    /** Inherits the parent process's stdin, stdout, and stderr. */
    public CommandBuilder inheritIO()                    { this.inheritIO = true; return this; }

    /** Clears the inherited environment before applying additions via {@link #env} or {@link #envMap}. */
    public CommandBuilder clearEnv()                     { this.clearEnv = true; return this; }

    /** Registers a consumer called with each raw byte chunk from stdout. */
    public CommandBuilder onStdout(StreamConsumer consumer) {
        this.stdoutConsumer = consumer;
        return this;
    }

    /** Registers a consumer called with each raw byte chunk from stderr. Has no effect if {@link #mergeOutputAndError()} is set. */
    public CommandBuilder onStderr(StreamConsumer consumer) {
        if (!mergeOutputAndError) this.stderrConsumer = consumer;
        return this;
    }

    /** Merges stderr into stdout. Any previously configured stderr consumer is discarded. */
    public CommandBuilder mergeOutputAndError() {
        this.mergeOutputAndError = true;
        this.stderrConsumer = null;
        return this;
    }

    /** Redirects stdout to a file. */
    public CommandBuilder redirectStdout(File file)      { this.redirectStdout = file; return this; }

    /** Redirects stdout to a file. */
    public CommandBuilder redirectStdout(Path path)      { return redirectStdout(path.toFile()); }

    /** Redirects stderr to a file. */
    public CommandBuilder redirectStderr(File file)      { this.redirectStderr = file; return this; }

    /** Redirects stderr to a file. */
    public CommandBuilder redirectStderr(Path path)      { return redirectStderr(path.toFile()); }

    /** Redirects stdin from a file. */
    public CommandBuilder redirectStdin(File file)       { this.redirectStdin = file; return this; }

    /** Adds a single environment variable. */
    public CommandBuilder env(String key, String value)  { this.extraEnv.put(key, value); return this; }

    /** Adds multiple environment variables. */
    public CommandBuilder envMap(Map<String, String> e)  { this.extraEnv.putAll(e); return this; }

    /**
     * Sets the exit codes that are treated as successful. Defaults to {@code {0}}.
     * {@link io.github.haiderkagalwala.nexec.result.ProcessOutcome.Completed#succeeded()} returns
     * {@code true} only when the process exit code is in this set.
     */
    public CommandBuilder successExitCodes(int... codes) { this.successExitCodes = codes.clone(); return this; }

    /**
     * Blocks until the process exits and all output has been consumed.
     * Never throws for non-zero exit codes — check {@link ProcessOutcome.Completed#succeeded()} instead.
     */
    public ProcessOutcome execute() {
        return new SyncExecutionEngine(snapshot()).execute();
    }

    /**
     * Launches the process and returns immediately with a {@link PipeHandle}.
     * The handle's {@link PipeHandle#outcome()} future resolves once the process exits
     * and all background drain tasks have finished.
     */
    public PipeHandle executeAsync() throws IOException {
        return new AsyncExecutionEngine(snapshot()).executeAsync();
    }

    private ProcessConfig snapshot() {
        return new ProcessConfig(
                List.copyOf(command),
                workingDir,
                timeout,
                timeoutEnabled,
                mergeOutputAndError,
                stdoutConsumer,
                stderrConsumer,
                inheritIO,
                redirectStdout,
                redirectStderr,
                redirectStdin,
                Map.copyOf(extraEnv),
                clearEnv,
                successExitCodes.clone()
        );
    }
}
