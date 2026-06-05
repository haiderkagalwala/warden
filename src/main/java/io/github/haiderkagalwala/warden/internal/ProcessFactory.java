package io.github.haiderkagalwala.warden.internal;

/**
 * Builds a configured {@link ProcessBuilder} from a {@link ProcessConfig} snapshot.
 * Used only by the execution engines.
 */
final class ProcessFactory {

    private ProcessFactory() {}

    /**
     * For synchronous execution.
     * Aggressively applies OS-level {@code DISCARD} for streams that won't be read,
     * avoiding unnecessary pipes and saving heap.
     */
     static ProcessBuilder forSync(ProcessConfig config) {
        var pb = base(config);
        if (config.inheritIO()) {
            return pb;
        }


        if (config.redirectStdout() != null) {
            pb.redirectOutput(config.redirectStdout());
        }

        if (config.redirectStdout() == null && config.stdoutConsumer() == null) {
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }


        if (!config.mergeOutputAndError()) {

            if (config.redirectStderr() != null) {
                pb.redirectError(config.redirectStderr());
            }

            if (config.redirectStderr() ==null && config.stderrConsumer() == null) {
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            }
        }

        return pb;
    }

    static ProcessBuilder forAsync(ProcessConfig config) {
        var pb = base(config);

        if (config.inheritIO()) {
            return pb;
        }

        // If none of them are set, user must use InputStream to handle the process output
        if (config.redirectStdout() != null) pb.redirectOutput(config.redirectStdout());

        if (!config.mergeOutputAndError() && config.redirectStderr() != null) {
            pb.redirectError(config.redirectStderr());
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
            pb.inheritIO();
            return pb;
        }


        if (config.mergeOutputAndError())
            pb.redirectErrorStream(true);

        if (config.redirectStdin() != null)
            pb.redirectInput(config.redirectStdin());


        return pb;
    }
}
