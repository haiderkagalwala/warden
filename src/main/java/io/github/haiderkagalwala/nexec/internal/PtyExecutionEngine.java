package io.github.haiderkagalwala.nexec.internal;

import com.pty4j.PtyProcessBuilder;
import io.github.haiderkagalwala.nexec.handle.PtyHandle;
import io.github.haiderkagalwala.nexec.result.ProcessOutcome;
import io.github.haiderkagalwala.nexec.streams.StreamConsumer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Launches a PTY (pseudo-terminal) process via pty4j and returns a {@link PtyHandle}
 * immediately — never blocks the calling thread.
 *
 * <p>The PTY merges stdout and stderr into a single master stream, and the child process
 * sees {@code isatty() == true}, so prompts and line-buffering behave as in a real terminal.
 * A drain task runs on a virtual thread to consume the output stream; if no consumer was
 * configured, output is silently discarded via {@link StreamConsumer#NOOP}. Terminal
 * dimensions can be changed at runtime via {@link PtyHandle#resize(int, int)}.
 *
 * <p>On Windows, the PTY backend (WinPTY vs ConPTY) and ANSI color support are controlled
 * by the {@link PtyConfig#consoleMode()} and {@link PtyConfig#windowsAnsiColorEnabled()} flags.
 */
final class PtyExecutionEngine {

    private final PtyConfig config;

    PtyExecutionEngine(PtyConfig config) {
        this.config = config;
    }

    PtyHandle start() throws IOException {

        var env = new HashMap<>(System.getenv());
        if (!config.extraEnv().isEmpty()) env.putAll(config.extraEnv());

        var ptyBuilder = new PtyProcessBuilder()
                .setCommand(config.command().toArray(new String[0]))
                .setRedirectErrorStream(true)
                .setInitialColumns(config.ptyCols())
                .setInitialRows(config.ptyRows())
                .setEnvironment(env)
                .setConsole(config.consoleMode())
                .setWindowsAnsiColorEnabled(config.windowsAnsiColorEnabled());

        if (config.workingDir() != null)
            ptyBuilder.setDirectory(config.workingDir().toString());

        var process   = ptyBuilder.start();
        var startTime = Instant.now();

        var shutdownHook = Thread.ofVirtual().unstarted(() -> PtyTreeReaper.destroy(process));
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        var executor = Executors.newVirtualThreadPerTaskExecutor();

        var outputConsumer = config.outputConsumer() != null ? config.outputConsumer()
                : StreamConsumer.NOOP;

        executor.submit(new StreamDrainer(
                process.getInputStream(),
                outputConsumer
        ));

        executor.shutdown();

        var baseFuture = process.onExit();
        if (config.timeout() != null) {
            baseFuture = baseFuture.orTimeout(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        var cancelled = new AtomicBoolean(false);

        var outcomeFuture = baseFuture.handle((p, ex) -> {
            try {Runtime.getRuntime().removeShutdownHook(shutdownHook);} catch (Exception ignored) {}

            if (ex != null) PtyTreeReaper.destroy(process);
            executor.close();

            var duration = Duration.between(startTime, Instant.now());

            if (cancelled.get())                return new ProcessOutcome.Killed(duration);
            if (ex instanceof TimeoutException) return new ProcessOutcome.TimedOut(duration);
            if (ex != null)                     return new ProcessOutcome.Failed(ex);

            int exitCode = p.exitValue();
            return (ProcessOutcome) new ProcessOutcome.Completed(exitCode, config.isSuccess(exitCode), duration);
        });

        return new PtyHandle(process, outcomeFuture, cancelled, () -> PtyTreeReaper.destroy(process));
    }
}
