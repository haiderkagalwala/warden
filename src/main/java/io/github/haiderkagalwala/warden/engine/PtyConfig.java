package io.github.haiderkagalwala.warden.engine;

import io.github.haiderkagalwala.warden.streams.StreamConsumer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of an {@link InteractiveBuilder} configuration.
 * Package-private — passed to {@link PtyExecutionEngine} so it remains decoupled
 * from the mutable builder.
 */
record PtyConfig(
        List<String> command,
        Path workingDir,
        Duration timeout,           // null = no timeout
        int ptyCols,
        int ptyRows,
        StreamConsumer outputConsumer,
        boolean captureOutput,
        Map<String, String> extraEnv
) {}
