package io.github.haiderkagalwala.warden;

import io.github.haiderkagalwala.warden.internal.CommandBuilder;
import io.github.haiderkagalwala.warden.internal.PtyBuilder;

import java.util.List;

/**
 * Entry point for the warden process execution library.
 *
 * <pre>{@code
 * // Synchronous
 * ProcessOutcome r = Warden.run("git", "status")
 *         .captureStdout()
 *         .execute();
 *
 * // Asynchronous — non-blocking, returns a handle immediately
 * RunningProcess rp = Warden.run("tail", "-f", "/var/log/app.log")
 *         .noTimeout()
 *         .onStdout(ProcessStreams.printToStdout())
 *         .executeAsync();
 * rp.cancel();
 *
 * // Interactive PTY
 * InteractiveProcess shell = Warden.interactive("bash")
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
     * Creates a builder for a normal (non-PTY) command.
     * Use {@link CommandBuilder#execute()} to block, or
     * {@link CommandBuilder#executeAsync()} for a non-blocking handle.
     */
    public static CommandBuilder run(String... command) {
        return new CommandBuilder(List.of(command));
    }

    /**
     * Creates a builder for a PTY (pseudo-terminal) command.
     * Use {@link PtyBuilder#start()} to launch.
     */
    public static PtyBuilder interactive(String... command) {
        return new PtyBuilder(List.of(command));
    }
}
