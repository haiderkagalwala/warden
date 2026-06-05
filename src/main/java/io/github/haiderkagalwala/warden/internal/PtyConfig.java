package io.github.haiderkagalwala.warden.internal;

import io.github.haiderkagalwala.warden.streams.StreamConsumer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of an {@link InteractiveBuilder}
 * configuration, passed to the PTY execution engine.
 */
record PtyConfig(
        List<String> command,
        Path workingDir,
        Duration timeout,           // null = no timeout
        int ptyCols,
        int ptyRows,
        StreamConsumer outputConsumer,
        Map<String, String> extraEnv
) {}
