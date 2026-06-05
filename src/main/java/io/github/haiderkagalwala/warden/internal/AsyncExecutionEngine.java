package io.github.haiderkagalwala.warden.internal;

import io.github.haiderkagalwala.warden.handle.WardenHandle;
import io.github.haiderkagalwala.warden.result.ProcessOutcome;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async execution engine. Launches a process and returns immediately with a
 * {@link WardenHandle} handle — never blocks the calling thread.
 *
 * <h3>Design</h3>
 * <ol>
 *   <li>Start the process via  (no DISCARD redirects —
 *       raw streams stay open for the caller if desired).</li>
 *   <li>Submit background drain tasks to a virtual-thread executor (only when a
 *       consumer callback was configured).</li>
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

    WardenHandle executeAsync() throws IOException {

        // ── 1. Start process (no DISCARD — expose streams via RunningProcess) ──
        var process   = ProcessFactory.forAsync(config).start();
        var startTime = Instant.now();

        // ── 2. Zombie prevention ───────────────────────────────────────────────
        var shutdownHook = Thread.ofVirtual().unstarted(() -> TreeReaper.destroy(process));
        Runtime.getRuntime().addShutdownHook(shutdownHook);


        var executor = Executors.newVirtualThreadPerTaskExecutor();

        var drainStdout = !config.inheritIO()
                && config.redirectStdout() == null
                && config.stdoutConsumer() != null;

        var drainStderr = !config.inheritIO()
                && !config.mergeOutputAndError()
                && config.redirectStderr() == null
                && config.stderrConsumer() != null;

        if (drainStdout) {
            executor.submit(new StreamDrainer(
                    process.getInputStream(),
                    config.stdoutConsumer()
            ));
        }
        if (drainStderr) {
            executor.submit(new StreamDrainer(
                    process.getErrorStream(),
                    config.stderrConsumer()
            ));
        }

        executor.shutdown(); // non-blocking: gates new submissions, drain tasks keep running

        // ── 4. Async watchdog ──────────────────────────────────────────────────
        var baseFuture = process.onExit();
        if (config.timeoutEnabled()) {
            baseFuture = baseFuture.orTimeout(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        var cancelled = new AtomicBoolean(false);

        // ── 5. Outcome resolution ──────────────────────────────────────────────
        var outcomeFuture = baseFuture.handle((p, ex) -> {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);

            if (ex != null) TreeReaper.destroy(process);
            executor.close(); // waits for drain tasks to finish now that process is dead

            var duration = Duration.between(startTime, Instant.now());

            if (cancelled.get())                return new ProcessOutcome.Killed(duration);
            if (ex instanceof TimeoutException) return new ProcessOutcome.TimedOut(duration);
            if (ex != null)                     return new ProcessOutcome.Failed(ex);

            int exitCode = p.exitValue();
            return (ProcessOutcome) new ProcessOutcome.Completed(exitCode, exitCode == 0,  duration);
        });

        return new WardenHandle(process, outcomeFuture, cancelled, () -> TreeReaper.destroy(process));
    }
}
