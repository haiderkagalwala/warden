package io.github.haiderkagalwala.warden;

import io.github.haiderkagalwala.warden.internal.CommandBuilder;
import io.github.haiderkagalwala.warden.internal.PtyBuilder;

import java.util.List;

/**
 * Entry point for the Warden process execution library.
 *
 * <pre>{@code
 * // Synchronous — blocks until the process exits
 * ProcessOutcome outcome = Warden.run("git", "status")
 *         .onStdout(ProcessStreams.printToStdout())
 *         .execute();
 *
 * // Asynchronous — returns a handle immediately
 * PipeHandle handle = Warden.run("tail", "-f", "/var/log/app.log")
 *         .noTimeout()
 *         .onStdout(ProcessStreams.printToStdout())
 *         .executeAsync();
 * handle.cancel();
 *
 * // Interactive PTY
 * PtyHandle shell = Warden.interactive("bash")
 *         .ptySize(220, 50)
 *         .onOutput(ProcessStreams.printToStdout())
 *         .start();
 * shell.writeLine("echo hello").writeLine("exit");
 * shell.await();
 * }</pre>
 *
 * @see CommandBuilder
 * @see PtyBuilder
 */
public final class Warden {

    private Warden() {}

    /**
     * Creates a builder for a normal (non-PTY) process.
     * Call {@link CommandBuilder#execute()} to block until the process exits,
     * or {@link CommandBuilder#executeAsync()} for a non-blocking {@link io.github.haiderkagalwala.warden.handle.PipeHandle}.
     */
    public static CommandBuilder run(String... command) {
        return new CommandBuilder(List.of(command));
    }

    /**
     * Creates a builder for a PTY (pseudo-terminal) process.
     * Call {@link PtyBuilder#start()} to launch and receive a {@link io.github.haiderkagalwala.warden.handle.PtyHandle}.
     */
    public static PtyBuilder interactive(String... command) {
        return new PtyBuilder(List.of(command));
    }
}
