package io.github.haiderkagalwala.warden.engine;

/**
 * Builds a configured {@link ProcessBuilder} from a {@link ProcessConfig} snapshot.
 * Package-private — called only by the execution engines.
 */
final class ProcessFactory {

    private ProcessFactory() {}

    /**
     * For synchronous execution.
     * <p>
     * Aggressively applies OS-level {@code DISCARD} for streams that won't be read,
     * avoiding unnecessary pipes and saving heap. Safe when the caller blocks on
     * {@code waitFor()} and reads the outcome synchronously.
     */
    static ProcessBuilder forSync(ProcessConfig config) {
        var pb = base(config);

        if (!config.inheritIO()) {
            // Stdout: discard at OS level if nothing will read it
            if (config.redirectStdout() != null) {
                pb.redirectOutput(config.redirectStdout());
            } else if (config.stdoutConsumer() == null && !config.captureStdout()) {
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            }

            // Stderr: discard if not merged and nothing will read it
            if (!config.mergeOutputAndError()) {
                if (config.redirectStderr() != null) {
                    pb.redirectError(config.redirectStderr());
                } else if (config.stderrConsumer() == null && !config.captureStderr()) {
                    pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                }
            }
        }

        return pb;
    }

    /**
     * For async execution.
     * <p>
     * Does NOT apply OS-level DISCARD — streams remain open and accessible via
     * {@link io.github.haiderkagalwala.warden.handle.RunningProcess} for direct reading.
     * If the caller sets capture/consumer options, background drainers consume those
     * streams instead.
     */
    static ProcessBuilder forAsync(ProcessConfig config) {
        var pb = base(config);

        if (!config.inheritIO()) {
            if (config.redirectStdout() != null) pb.redirectOutput(config.redirectStdout());
            if (!config.mergeOutputAndError() && config.redirectStderr() != null) {
                pb.redirectError(config.redirectStderr());
            }
        }

        return pb;
    }

    // ── Shared base configuration ──────────────────────────────────────────

    private static ProcessBuilder base(ProcessConfig config) {
        var pb = new ProcessBuilder(config.command());

        if (config.workingDir() != null)
            pb.directory(config.workingDir().toFile());

        if (config.clearEnv())
            pb.environment().clear();
        if (!config.extraEnv().isEmpty())
            pb.environment().putAll(config.extraEnv());

        if (config.inheritIO()) {
            pb.inheritIO(); // overrides all stream config
        } else {
            if (config.mergeOutputAndError())
                pb.redirectErrorStream(true);
            if (config.redirectStdin() != null)
                pb.redirectInput(config.redirectStdin());
        }

        return pb;
    }
}
