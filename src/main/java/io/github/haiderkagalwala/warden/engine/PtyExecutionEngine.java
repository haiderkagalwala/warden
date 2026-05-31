package io.github.haiderkagalwala.warden.engine;

import com.pty4j.PtyProcessBuilder;
import io.github.haiderkagalwala.warden.engine.PtyConfig;
import io.github.haiderkagalwala.warden.engine.StreamDrainer;
import io.github.haiderkagalwala.warden.engine.TreeReaper;
import io.github.haiderkagalwala.warden.handle.InteractiveProcess;
import io.github.haiderkagalwala.warden.streams.StreamConsumer;
import io.github.haiderkagalwala.warden.result.ProcessOutcome;

import java.io.ByteArrayOutputStream;
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
 * {@link InteractiveProcess} handle immediately — never blocks the calling thread.
 *
 * <h3>PTY characteristics</h3>
 * <ul>
 *   <li>stdout and stderr are merged into the single PTY master stream.</li>
 *   <li>The child process sees {@code isatty()} = true, so line-buffering and
 *       prompts work as expected.</li>
 *   <li>Terminal dimensions can be changed at runtime via
 *       {@link InteractiveProcess#resize(int, int)}.</li>
 * </ul>
 *
 * <h3>Design</h3>
 * Same async pattern as {@link AsyncExecutionEngine}: virtual-thread drainer →
 * {@code executor.shutdown()} → {@code process.onExit()} watchdog →
 * {@code executor.close()} inside {@code handle()} to guarantee drain completion
 * before the outcome future resolves.
 */
final class PtyExecutionEngine {

    private final PtyConfig config;

    PtyExecutionEngine(PtyConfig config) {
        this.config = config;
    }

    InteractiveProcess start() throws IOException {

        // ── 1. Build PTY environment (inherit current env + overrides) ─────────
        var env = new HashMap<>(System.getenv());
        if (!config.extraEnv().isEmpty()) env.putAll(config.extraEnv());

        // ── 2. Launch PTY process ──────────────────────────────────────────────
        var ptyBuilder = new PtyProcessBuilder()
                .setCommand(config.command().toArray(new String[0]))
                .setInitialColumns(config.ptyCols())
                .setInitialRows(config.ptyRows())
                .setEnvironment(env);

        if (config.workingDir() != null)
            ptyBuilder.setDirectory(config.workingDir().toString());

        var process   = ptyBuilder.start();
        var startTime = Instant.now();

        // ── 3. Zombie prevention ───────────────────────────────────────────────
        // addShutdownHook requires a Thread instance. Virtual threads are full
        // Thread objects and work correctly here during normal JVM shutdown.
        var shutdownHook = Thread.ofVirtual().unstarted(() -> TreeReaper.destroy(process));
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // ── 4. PTY output drain (virtual thread) ───────────────────────────────
        // PTY has one combined stream. Only drain if a consumer or capture was
        // requested; otherwise the caller reads output() directly.
        var capturedOutput = new ByteArrayOutputStream();
        var executor       = Executors.newVirtualThreadPerTaskExecutor();

        var shouldDrain = config.outputConsumer() != null || config.captureOutput();
        if (shouldDrain) {
            executor.submit(new StreamDrainer(
                    process.getInputStream(),
                    capturedOutput,
                    config.outputConsumer() != null ? config.outputConsumer() : StreamConsumer.NOOP,
                    config.captureOutput()
            ));
        }

        executor.shutdown(); // non-blocking: drain task continues

        // ── 5. Async watchdog ──────────────────────────────────────────────────
        var baseFuture = process.onExit();
        if (config.timeout() != null) {
            baseFuture = baseFuture.orTimeout(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        var cancelled = new AtomicBoolean(false);

        // ── 6. Outcome resolution ──────────────────────────────────────────────
        var outcomeFuture = baseFuture.handle((p, ex) -> {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);

            if (ex != null) TreeReaper.destroy(process);
            executor.close(); // waits for drain task to finish now that process is dead

            var duration = Duration.between(startTime, Instant.now());
            var output   = capturedOutput.toByteArray();

            if (cancelled.get())                return new ProcessOutcome.Killed(output, new byte[0], duration);
            if (ex instanceof TimeoutException) return new ProcessOutcome.TimedOut(duration, output, new byte[0]);
            if (ex != null)                     return new ProcessOutcome.Failed(ex);

            int exitCode = p.exitValue();
            return (ProcessOutcome) new ProcessOutcome.Completed(exitCode, exitCode == 0, output, new byte[0], duration);
        });

        return new InteractiveProcess(process, outcomeFuture, cancelled, () -> TreeReaper.destroy(process));
    }
}
