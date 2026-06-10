package io.github.haiderkagalwala.nexec.internal;

import io.github.haiderkagalwala.nexec.streams.StreamConsumer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a {@link PtyBuilder} configuration, passed to the PTY execution engine.
 * A {@code null} timeout means no timeout.
 */
record PtyConfig(
        List<String> command,
        Path workingDir,
        Duration timeout,
        int ptyCols,
        int ptyRows,
        StreamConsumer outputConsumer,
        Map<String, String> extraEnv,
        boolean consoleMode,
        boolean windowsAnsiColorEnabled,
        int[] successExitCodes
) {
    PtyConfig {
        successExitCodes = successExitCodes.clone();
    }

    /** Returns {@code true} if {@code exitCode} is in the configured success-exit-code set. */
    boolean isSuccess(int exitCode) {
        for (int code : successExitCodes) if (code == exitCode) return true;
        return false;
    }
}
