package io.github.haiderkagalwala.warden.internal;

import io.github.haiderkagalwala.warden.streams.StreamConsumer;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a {@link CommandBuilder}
 * configuration, passed to the execution engines.
 */
record ProcessConfig(
        List<String> command,
        Path workingDir,
        Duration timeout,
        boolean timeoutEnabled,
        boolean mergeOutputAndError,
        StreamConsumer stdoutConsumer,
        StreamConsumer stderrConsumer,
        boolean inheritIO,
        File redirectStdout,
        File redirectStderr,
        File redirectStdin,
        Map<String, String> extraEnv,
        boolean clearEnv
) {
    /** Returns {@code true} if {@code exitCode} is in the configured success-exit-code set. */

}
