package io.github.haiderkagalwala.warden.engine;

import io.github.haiderkagalwala.warden.engine.ProcessConfig;
import io.github.haiderkagalwala.warden.engine.ProcessFactory;
import io.github.haiderkagalwala.warden.engine.StreamDrainer;
import io.github.haiderkagalwala.warden.engine.TreeReaper;
import io.github.haiderkagalwala.warden.result.ProcessOutcome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
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
        // The OS handles stream forwarding; no draining needed — just wait.
        if (config.inheritIO()
                || (config.redirectStdout() != null && config.redirectStderr() != null)) {
            return waitAndBuild(process, startTime, new byte[0], new byte[0]);
        }

        // ── 3. Drain stdout + stderr concurrently on virtual threads ──────
        // Both streams must be drained before (or while) waitFor() completes
        // to prevent the 64 KB pipe-buffer deadlock on large output.
        var capturedStdout = new ByteArrayOutputStream();
        var capturedStderr = new ByteArrayOutputStream();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // Stdout — skip if redirected to file (OS handles it)
            var stdoutFuture = config.redirectStdout() != null ? null :
                    executor.submit(new StreamDrainer(
                            process.getInputStream(),
                            capturedStdout,
                            config.stdoutConsumer(),
                            config.captureStdout()
                    ));

            // Stderr — skip if merged into stdout or redirected to file
            var stderrFuture = (config.mergeOutputAndError() || config.redirectStderr() != null) ? null :
                    executor.submit(new StreamDrainer(
                            process.getErrorStream(),
                            capturedStderr,
                            config.stderrConsumer(),
                            config.captureStderr()
                    ));

            // ── 4. Block until the process exits ──────────────────────────
            boolean finished;
            try {
                if (config.timeoutEnabled()) {
                    finished = process.waitFor(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
                } else {
                    process.waitFor();
                    finished = true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                TreeReaper.destroy(process);
                return new ProcessOutcome.Failed(e);
            }

            if (!finished) {
                TreeReaper.destroy(process); // kill firststreams reach EOF
            }
            // ── 5. Join drainers before reading captured bytes ────────────
            try {
                if (stdoutFuture != null) stdoutFuture.get();
                if (stderrFuture != null) stderrFuture.get();
            } catch (Exception e) {
                return new ProcessOutcome.Failed(e);
            }


            var duration = Duration.between(startTime, Instant.now());
            var stdout   = capturedStdout.toByteArray();
            var stderr   = capturedStderr.toByteArray();

            // ── 6. Timeout — kill tree, return partial output ─────────────
            if (!finished) {
                TreeReaper.destroy(process);
                return new ProcessOutcome.TimedOut(duration, stdout, stderr);
            }

            return buildCompleted(process, stdout, stderr, duration);

        } catch (Exception e) {
            TreeReaper.destroy(process);
            return new ProcessOutcome.Failed(e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Used when streams are OS-managed — just wait, then build the outcome. */
    private ProcessOutcome waitAndBuild(
            Process process, Instant startTime,
            byte[] stdout, byte[] stderr) {
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
                return new ProcessOutcome.TimedOut(duration, stdout, stderr);
            }
            return buildCompleted(process, stdout, stderr, duration);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            TreeReaper.destroy(process);
            return new ProcessOutcome.Failed(e);
        }
    }

    private ProcessOutcome buildCompleted(
            Process process, byte[] stdout, byte[] stderr, Duration duration) {
        int exitCode = process.exitValue();
        return new ProcessOutcome.Completed(exitCode, config.isSuccess(exitCode), stdout, stderr, duration);
    }
}
