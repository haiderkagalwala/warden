package io.github.haiderkagalwala.warden.internal;

import io.github.haiderkagalwala.warden.Warden;
import io.github.haiderkagalwala.warden.handle.PipeHandle;
import io.github.haiderkagalwala.warden.streams.StreamConsumer;
import io.github.haiderkagalwala.warden.result.ProcessOutcome;

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
 * <p>Obtain an instance via {@link Warden#run(String...)}. Call {@link #execute()} to
 * block and collect a result, or {@link #executeAsync()} to get a non-blocking handle.
 *
 * <pre>{@code
 * // Synchronous
 * ProcessOutcome r = Warden.run("git", "status")
 *         .captureStdout()
 *         .execute();
 *
 * // Asynchronous
 * RunningProcess rp = Warden.run("tail", "-f", "/var/log/app.log")
 *         .noTimeout()
 *         .onStdout(ProcessStreams.printToStdout())
 *         .executeAsync();
 * rp.cancel();
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

    // ── Configuration ─────────────────────────────────────────────────────

    public CommandBuilder workingDir(Path dir)           { this.workingDir = dir; return this; }
    public CommandBuilder timeout(Duration t)            { this.timeout = t; this.timeoutEnabled = true; return this; }
    public CommandBuilder noTimeout()                    { this.timeoutEnabled = false; return this; }
    public CommandBuilder inheritIO()                    { this.inheritIO = true; return this; }
    public CommandBuilder clearEnv()                     { this.clearEnv = true; return this; }
//    public CommandBuilder successExitCodes(int... codes) { this.successExitCodes = codes; return this; }

    public CommandBuilder onStdout(StreamConsumer consumer) {
        this.stdoutConsumer = consumer;
        return this;
    }

    public CommandBuilder onStderr(StreamConsumer consumer) {
        if (!mergeOutputAndError) this.stderrConsumer = consumer;
        return this;
    }

    public CommandBuilder mergeOutputAndError() {
        this.mergeOutputAndError = true;
        this.stderrConsumer = null;
        return this;
    }

    public CommandBuilder redirectStdout(File file)      { this.redirectStdout = file; return this; }
    public CommandBuilder redirectStdout(Path path)      { return redirectStdout(path.toFile()); }
    public CommandBuilder redirectStderr(File file)      { this.redirectStderr = file; return this; }
    public CommandBuilder redirectStderr(Path path)      { return redirectStderr(path.toFile()); }
    public CommandBuilder redirectStdin(File file)       { this.redirectStdin = file; return this; }

    public CommandBuilder env(String key, String value)  { this.extraEnv.put(key, value); return this; }
    public CommandBuilder envMap(Map<String, String> e)  { this.extraEnv.putAll(e); return this; }

    // ── Execution ─────────────────────────────────────────────────────────

    /**
     * Blocks the calling thread until the process exits and all output has been consumed.
     *
     * @return the process outcome — never null, never throws for non-zero exit codes
     */
    public ProcessOutcome execute() throws IOException {
        return new SyncExecutionEngine(snapshot()).execute();
    }

    /**
     * Launches the process and returns immediately with a {@link PipeHandle} handle.
     * The handle's {@link PipeHandle#outcome()} future completes once the process exits
     * <em>and</em> all background drainers have finished flushing captured bytes.
     */
    public PipeHandle executeAsync() throws IOException {
        return new AsyncExecutionEngine(snapshot()).executeAsync();
    }

    // ── Internal ──────────────────────────────────────────────────────────

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
                clearEnv
        );
    }
}
