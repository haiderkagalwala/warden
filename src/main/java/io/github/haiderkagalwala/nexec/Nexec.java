package io.github.haiderkagalwala.nexec;

import io.github.haiderkagalwala.nexec.internal.CommandBuilder;
import io.github.haiderkagalwala.nexec.internal.PtyBuilder;

import java.util.List;

/**
 * Entry point for the nexec process execution library.
 *
 * <pre>{@code
 * // Synchronous — blocks until the process exits
 * ProcessOutcome outcome = Nexec.run("git", "status")
 *         .onStdout(ProcessStreams.printToStdout())
 *         .execute();
 *
 * // Asynchronous — returns a handle immediately
 * PipeHandle handle = Nexec.run("tail", "-f", "/var/log/app.log")
 *         .noTimeout()
 *         .onStdout(ProcessStreams.printToStdout())
 *         .executeAsync();
 * handle.cancel();
 *
 * // Interactive PTY
 * PtyHandle shell = Nexec.interactive("bash")
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
public final class Nexec {

    private Nexec() {}

    /**
     * Creates a builder for a normal (non-PTY) process.
     * Call {@link CommandBuilder#execute()} to block until the process exits,
     * or {@link CommandBuilder#executeAsync()} for a non-blocking
     * {@link io.github.haiderkagalwala.nexec.handle.PipeHandle}.
     *
     * @param command the program and its arguments; must not be null or empty
     * @throws IllegalArgumentException if {@code command} is null or empty
     */
    public static CommandBuilder run(String... command) {
        if (command == null || command.length == 0)
            throw new IllegalArgumentException("command must not be empty");
        return new CommandBuilder(List.of(command));
    }

    /**
     * Creates a builder for a PTY (pseudo-terminal) process.
     * Call {@link PtyBuilder#start()} to launch and receive a
     * {@link io.github.haiderkagalwala.nexec.handle.PtyHandle}.
     *
     * <p><b>Requires pty4j on the classpath.</b> pty4j is declared as an optional
     * dependency in nexec's POM; add it explicitly to your project if you use this method:
     * <pre>{@code
     * <dependency>
     *     <groupId>org.jetbrains.pty4j</groupId>
     *     <artifactId>pty4j</artifactId>
     *     <version>0.13.11</version>
     * </dependency>
     * }</pre>
     *
     * @param command the program and its arguments; must not be null or empty
     * @throws IllegalArgumentException if {@code command} is null or empty
     */
    public static PtyBuilder interactive(String... command) {
        if (command == null || command.length == 0)
            throw new IllegalArgumentException("command must not be empty");
        return new PtyBuilder(List.of(command));
    }
}
