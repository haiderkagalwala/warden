package io.github.haiderkagalwala.warden.internal;

/**
 * Builds a configured {@link ProcessBuilder} from a {@link ProcessConfig} snapshot.
 */
final class ProcessFactory {

    private ProcessFactory() {}

    /**
     * Builds a {@link ProcessBuilder} for synchronous execution.
     * Applies OS-level {@code DISCARD} to any stream that has neither a consumer nor a file
     * redirect, avoiding unnecessary pipe allocation.
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

    /**
     * Builds a {@link ProcessBuilder} for async execution.
     * Does not apply {@code DISCARD} — streams remain open and accessible via
     * {@link io.github.haiderkagalwala.warden.handle.PipeHandle} for direct reading.
     */
    static ProcessBuilder forAsync(ProcessConfig config) {
        var pb = base(config);

        if (config.inheritIO()) {
            return pb;
        }

        if (config.redirectStdout() != null) pb.redirectOutput(config.redirectStdout());

        if (!config.mergeOutputAndError() && config.redirectStderr() != null) {
            pb.redirectError(config.redirectStderr());
        }


        return pb;
    }

    /**
     * Applies configuration common to both sync and async execution: command, working directory,
     * environment, {@code inheritIO}, merged error stream, and stdin redirect.
     */
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
