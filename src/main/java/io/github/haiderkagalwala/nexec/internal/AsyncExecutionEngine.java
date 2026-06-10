package io.github.haiderkagalwala.nexec.internal;

import io.github.haiderkagalwala.nexec.handle.PipeHandle;
import io.github.haiderkagalwala.nexec.result.ProcessOutcome;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async execution engine. Launches a process and returns a {@link PipeHandle} immediately —
 * never blocks the calling thread.
 *
 * <p>Streams are not DISCARDed so the caller can read them directly via the handle.
 * If consumers were configured, background drain tasks run on virtual threads. The executor
 * is shut down after all tasks are submitted (non-blocking), then closed inside the
 * {@code process.onExit()} callback once the process is dead and streams are at EOF.
 * The outcome future resolves only after drains complete.
 */
final class AsyncExecutionEngine {

    private final ProcessConfig config;

    AsyncExecutionEngine(ProcessConfig config) {
        this.config = config;
    }

    PipeHandle executeAsync() throws IOException {

        var process   = ProcessFactory.forAsync(config).start();
        var startTime = Instant.now();

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

        executor.shutdown();

        var baseFuture = process.onExit();
        if (config.timeoutEnabled()) {
            baseFuture = baseFuture.orTimeout(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        var cancelled = new AtomicBoolean(false);

        var outcomeFuture = baseFuture.handle((p, ex) -> {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);

            if (ex != null) TreeReaper.destroy(process);
            executor.close();

            var duration = Duration.between(startTime, Instant.now());

            if (cancelled.get())                return new ProcessOutcome.Killed(duration);
            if (ex instanceof TimeoutException) return new ProcessOutcome.TimedOut(duration);
            if (ex != null)                     return new ProcessOutcome.Failed(ex);

            int exitCode = p.exitValue();
            return (ProcessOutcome) new ProcessOutcome.Completed(exitCode, config.isSuccess(exitCode), duration);
        });

        return new PipeHandle(process, outcomeFuture, cancelled, () -> TreeReaper.destroy(process));
    }
}
