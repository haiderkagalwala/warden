package io.github.haiderkagalwala.nexec.internal;

import io.github.haiderkagalwala.nexec.streams.StreamConsumer;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a {@link CommandBuilder} configuration, passed to the execution engines.
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
        boolean clearEnv,
        int[] successExitCodes
) {
    ProcessConfig {
        successExitCodes = successExitCodes.clone();
    }

    /** Returns {@code true} if {@code exitCode} is in the configured success-exit-code set. */
    boolean isSuccess(int exitCode) {
        for (int code : successExitCodes) if (code == exitCode) return true;
        return false;
    }
}
