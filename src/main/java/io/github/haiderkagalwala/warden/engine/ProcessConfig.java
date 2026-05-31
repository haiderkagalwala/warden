package io.github.haiderkagalwala.warden.engine;

import io.github.haiderkagalwala.warden.streams.StreamConsumer;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a {@link CommandBuilder} configuration.
 * Package-private — passed to execution engines so they remain decoupled
 * from the mutable builder.
 */
record ProcessConfig(
        List<String> command,
        Path workingDir,
        Duration timeout,
        boolean timeoutEnabled,
        boolean captureStdout,
        boolean captureStderr,
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
    /** Returns {@code true} if {@code exitCode} is in the configured success-exit-code set. */
    boolean isSuccess(int exitCode) {
        for (int code : successExitCodes) if (code == exitCode) return true;
        return false;
    }
}
