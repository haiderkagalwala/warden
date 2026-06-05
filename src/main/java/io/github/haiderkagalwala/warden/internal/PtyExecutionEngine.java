package io.github.haiderkagalwala.warden.internal;

import com.pty4j.PtyProcessBuilder;
import io.github.haiderkagalwala.warden.handle.PtyHandle;
import io.github.haiderkagalwala.warden.result.ProcessOutcome;
import io.github.haiderkagalwala.warden.streams.StreamConsumer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Launches a PTY (pseudo-terminal) process via pty4j and returns an
 * {@link PtyHandle} handle immediately — never blocks the calling thread.
 *
 * <h3>PTY characteristics</h3>
 * <ul>
 *   <li>stdout and stderr are merged into the single PTY master stream.</li>
 *   <li>The child process sees {@code isatty()} = true, so line-buffering and
 *       prompts work as expected.</li>
 *   <li>Terminal dimensions can be changed at runtime via
 *       {@link PtyHandle#resize(int, int)}.</li>
 * </ul>
 */
final class PtyExecutionEngine {

    private final PtyConfig config;

    PtyExecutionEngine(PtyConfig config) {
        this.config = config;
    }

    PtyHandle start() throws IOException {


        // ── 1. Build PTY environment (inherit current env + overrides) ─────────
        var env = new HashMap<>(System.getenv());
        if (!config.extraEnv().isEmpty()) env.putAll(config.extraEnv());

        // ── 2. Launch PTY process ──────────────────────────────────────────────
        var ptyBuilder = new PtyProcessBuilder()
                .setCommand(config.command().toArray(new String[0]))
                .setRedirectErrorStream(true)
                .setInitialColumns(config.ptyCols())
                .setInitialRows(config.ptyRows())
                .setEnvironment(env);

        if (config.workingDir() != null)
            ptyBuilder.setDirectory(config.workingDir().toString());

        var process   = ptyBuilder.start();
        var startTime = Instant.now();

        // ── 3. Zombie prevention ───────────────────────────────────────────────
        var shutdownHook = Thread.ofVirtual().unstarted(() -> PtyTreeReaper.destroy(process));
        Runtime.getRuntime().addShutdownHook(shutdownHook);


        var executor   = Executors.newVirtualThreadPerTaskExecutor();

        var outputConsumer = config.outputConsumer() != null ? config.outputConsumer()
                : StreamConsumer.NOOP;

        executor.submit(new StreamDrainer(
                process.getInputStream(),
                outputConsumer
        ));


        executor.shutdown();

        // ── 5. Async watchdog ──────────────────────────────────────────────────
        var baseFuture = process.onExit();
        if (config.timeout() != null) {
            baseFuture = baseFuture.orTimeout(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        var cancelled = new AtomicBoolean(false);

        // ── 6. Outcome resolution ──────────────────────────────────────────────
        var outcomeFuture = baseFuture.handle((p, ex) -> {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);

            if (ex != null) PtyTreeReaper.destroy(process);
            executor.close();

            var duration = Duration.between(startTime, Instant.now());

            if (cancelled.get())                return new ProcessOutcome.Killed(duration);
            if (ex instanceof TimeoutException) return new ProcessOutcome.TimedOut(duration);
            if (ex != null)                     return new ProcessOutcome.Failed(ex);

            int exitCode = p.exitValue();
            return (ProcessOutcome) new ProcessOutcome.Completed(exitCode, exitCode == 0, duration);
        });

        return new PtyHandle(process, outcomeFuture, cancelled, () -> PtyTreeReaper.destroy(process));
    }
}
