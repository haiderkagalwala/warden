package io.github.haiderkagalwala.warden.internal;

import io.github.haiderkagalwala.warden.streams.StreamConsumer;

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
        Map<String, String> extraEnv
) {}
