package io.github.haiderkagalwala.nexec.internal;

import io.github.haiderkagalwala.nexec.result.ProcessOutcome;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Synchronous execution engine. Blocks the calling thread until the process exits.
 *
 * <p>Streams with a configured consumer are drained on virtual threads to prevent the
 * 64 KB pipe-buffer deadlock on large output. The calling thread blocks on
 * {@code waitFor()} and joins the drain futures before returning the outcome.
 * If neither stream needs draining (both OS-managed via {@code inheritIO} or file redirects),
 * execution takes a fast path that skips the executor entirely.
 */
final class SyncExecutionEngine {

    private final ProcessConfig config;

    SyncExecutionEngine(ProcessConfig config) {
        this.config = config;
    }

    ProcessOutcome execute() {

        Process process;
        try {
            process = ProcessFactory.forSync(config).start();
        } catch (IOException e) {
            return new ProcessOutcome.Failed(e);
        }

        var startTime = Instant.now();

        if (isOsManaged()) {
            return waitAndBuild(process, startTime);
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            Future<?> stdoutFuture = null;
            if (config.stdoutConsumer() != null && config.redirectStdout() == null) {
                stdoutFuture = executor.submit(new StreamDrainer(
                        process.getInputStream(),
                        config.stdoutConsumer()
                ));
            }

            Future<?> stderrFuture = null;
            if (config.stderrConsumer() != null && !config.mergeOutputAndError() && config.redirectStderr() == null) {
                stderrFuture = executor.submit(new StreamDrainer(
                        process.getErrorStream(),
                        config.stderrConsumer()
                ));
            }

            boolean finished;
            try {
                if (config.timeoutEnabled()) {
                    finished = process.waitFor(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
                } else {
                    process.waitFor();
                    finished = true;
                }
                if (!finished) {
                    TreeReaper.destroy(process);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                TreeReaper.destroy(process);
                return new ProcessOutcome.Failed(e);
            }

            try {
                if (stdoutFuture != null) stdoutFuture.get();
                if (stderrFuture != null) stderrFuture.get();
            } catch (Exception e) {
                // If we already timed out, report that — not a drain failure.
                if (!finished) return new ProcessOutcome.TimedOut(Duration.between(startTime, Instant.now()));
                return new ProcessOutcome.Failed(e);
            }

            var duration = Duration.between(startTime, Instant.now());
            if (!finished) return new ProcessOutcome.TimedOut(duration);
            return buildCompleted(process, duration);

        } catch (Exception e) {
            TreeReaper.destroy(process);
            return new ProcessOutcome.Failed(e);
        }
    }

    private ProcessOutcome waitAndBuild(Process process, Instant startTime) {
        try {
            boolean finished;
            if (config.timeoutEnabled()) {
                finished = process.waitFor(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
            } else {
                process.waitFor();
                finished = true;
            }

            var duration = Duration.between(startTime, Instant.now());

            if (!finished) {
                TreeReaper.destroy(process);
                return new ProcessOutcome.TimedOut(duration);
            }
            return buildCompleted(process, duration);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            TreeReaper.destroy(process);
            return new ProcessOutcome.Failed(e);
        }
    }

    private ProcessOutcome buildCompleted(Process process, Duration duration) {
        int exitCode = process.exitValue();
        return new ProcessOutcome.Completed(exitCode, config.isSuccess(exitCode), duration);
    }

    /** Returns {@code true} when both streams are fully OS-managed and need no draining. */
    private boolean isOsManaged() {
        if (config.inheritIO()) return true;
        boolean stdoutHandled = config.redirectStdout() != null;
        boolean stderrHandled = config.redirectStderr() != null || config.mergeOutputAndError();
        return stdoutHandled && stderrHandled;
    }
}
