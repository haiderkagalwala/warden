package io.github.haiderkagalwala.warden.engine;

import io.github.haiderkagalwala.warden.engine.ProcessConfig;
import io.github.haiderkagalwala.warden.engine.ProcessFactory;
import io.github.haiderkagalwala.warden.engine.StreamDrainer;
import io.github.haiderkagalwala.warden.engine.TreeReaper;
import io.github.haiderkagalwala.warden.handle.RunningProcess;
import io.github.haiderkagalwala.warden.streams.StreamConsumer;
import io.github.haiderkagalwala.warden.result.ProcessOutcome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async execution engine. Launches a process and returns immediately with a
 * {@link RunningProcess} handle — never blocks the calling thread.
 *
 * <h3>Design</h3>
 * <ol>
 *   <li>Start the process via {@link ProcessFactory#forAsync} (no DISCARD redirects —
 *       raw streams stay open for the caller if desired).</li>
 *   <li>Submit background drain tasks to a virtual-thread executor (only when capture
 *       or consumer callbacks were configured).</li>
 *   <li>Call {@code executor.shutdown()} — non-blocking, just gates new submissions.</li>
 *   <li>Register an async watchdog via {@code process.onExit()} — never blocks.</li>
 *   <li>In the {@code handle()} callback: kill if needed, then call
 *       {@code executor.close()} which waits briefly until drains finish (streams are
 *       at EOF by this point). Only then resolve the outcome future.</li>
 * </ol>
 */
final class AsyncExecutionEngine {

    private final ProcessConfig config;

    AsyncExecutionEngine(ProcessConfig config) {
        this.config = config;
    }

    RunningProcess executeAsync() throws IOException {

        // ── 1. Start process (no DISCARD — expose streams via RunningProcess) ──
        var process   = ProcessFactory.forAsync(config).start();
        var startTime = Instant.now();

        // ── 2. Zombie prevention ───────────────────────────────────────────────
        // addShutdownHook requires a Thread instance. Virtual threads are full
        // Thread objects and work correctly here during normal JVM shutdown.
        var shutdownHook = Thread.ofVirtual().unstarted(() -> TreeReaper.destroy(process));
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // ── 3. Background drain tasks (virtual threads) ────────────────────────
        // Only drain if capture or a consumer callback was requested.
        // When neither is set, the caller reads stdout/stderr via RunningProcess.
        var capturedStdout = new ByteArrayOutputStream();
        var capturedStderr = new ByteArrayOutputStream();
        var executor       = Executors.newVirtualThreadPerTaskExecutor();

        var drainStdout = !config.inheritIO()
                && config.redirectStdout() == null
                && (config.captureStdout() || config.stdoutConsumer() != null);

        var drainStderr = !config.inheritIO()
                && !config.mergeOutputAndError()
                && config.redirectStderr() == null
                && (config.captureStderr() || config.stderrConsumer() != null);

        if (drainStdout) {
            executor.submit(new StreamDrainer(
                    process.getInputStream(), capturedStdout,
                    config.stdoutConsumer() != null ? config.stdoutConsumer() : StreamConsumer.NOOP,
                    config.captureStdout()
            ));
        }
        if (drainStderr) {
            executor.submit(new StreamDrainer(
                    process.getErrorStream(), capturedStderr,
                    config.stderrConsumer() != null ? config.stderrConsumer() : StreamConsumer.NOOP,
                    config.captureStderr()
            ));
        }

        executor.shutdown(); // non-blocking: gates new submissions, drain tasks keep running

        // ── 4. Async watchdog ──────────────────────────────────────────────────
        var baseFuture = process.onExit();
        if (config.timeoutEnabled()) {
            baseFuture = baseFuture.orTimeout(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        // Shared cancel flag — set by RunningProcess.cancel(), read in handle()
        var cancelled = new AtomicBoolean(false);

        // ── 5. Outcome resolution ──────────────────────────────────────────────
        // Kill first (if needed) so streams reach EOF → executor.close() waits
        // briefly until drain tasks finish → then resolve the outcome future.
        var outcomeFuture = baseFuture.handle((p, ex) -> {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);

            if (ex != null) TreeReaper.destroy(process);
            executor.close(); // waits for drain tasks to finish now that process is dead

            var duration = Duration.between(startTime, Instant.now());
            var stdout   = capturedStdout.toByteArray();
            var stderr   = capturedStderr.toByteArray();

            // User-cancel takes priority over system timeout
            if (cancelled.get())                return new ProcessOutcome.Killed(stdout, stderr, duration);
            if (ex instanceof TimeoutException) return new ProcessOutcome.TimedOut(duration, stdout, stderr);
            if (ex != null)                     return new ProcessOutcome.Failed(ex);

            int exitCode = p.exitValue();
            return (ProcessOutcome) new ProcessOutcome.Completed(exitCode, config.isSuccess(exitCode), stdout, stderr, duration);
        });

        return new RunningProcess(process, outcomeFuture, cancelled, () -> TreeReaper.destroy(process));
    }
}
