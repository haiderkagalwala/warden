package io.github.haiderkagalwala.warden.internal;

import io.github.haiderkagalwala.warden.result.ProcessOutcome;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Synchronous execution engine. Blocks the calling thread until the process exits.
 *
 * <h3>Design</h3>
 * <ol>
 *   <li>Start process via {@link ProcessFactory#forSync} — aggressively applies OS-level
 *       {@code DISCARD} for unused streams to avoid unnecessary pipes.</li>
 *   <li>Submit stdout and stderr drain tasks to a virtual-thread executor concurrently,
 *       preventing the 64 KB pipe-buffer deadlock on large output.</li>
 *   <li>Block on {@code waitFor()} — safe because drains run on separate virtual threads.</li>
 *   <li>Join drain futures before reading captured bytes.</li>
 * </ol>
 */
final class ExecutionEngine {

    private final ProcessConfig config;

    ExecutionEngine(ProcessConfig config) {
        this.config = config;
    }

    ProcessOutcome execute() {

        // ── 1. Start process ──────────────────────────────────────────────
        Process process;
        try {
            process = ProcessFactory.forSync(config).start();
        } catch (IOException e) {
            return new ProcessOutcome.Failed(e);
        }

        var startTime = Instant.now();

        // ── 2. Shortcut: inheritIO or both streams redirected to file ─────
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

            // ── 4. Block until the process exits ──────────────────────────
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
                return new ProcessOutcome.Failed(e);
            }

            var duration = Duration.between(startTime, Instant.now());

            if (!finished) {
                return new ProcessOutcome.TimedOut(duration);
            }

            return buildCompleted(process, duration);

        } catch (Exception e) {
            TreeReaper.destroy(process);
            return new ProcessOutcome.Failed(e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ProcessOutcome waitAndBuild(
            Process process, Instant startTime) {
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

    private ProcessOutcome buildCompleted(
            Process process, Duration duration) {
        int exitCode = process.exitValue();
        return new ProcessOutcome.Completed(exitCode, exitCode == 0,duration);
    }
    private boolean isOsManaged() {
        if (config.inheritIO()) return true;
        boolean stdoutHandled = config.redirectStdout() != null;
        boolean stderrHandled = config.redirectStderr() != null || config.mergeOutputAndError();
        return stdoutHandled && stderrHandled;
    }
}
