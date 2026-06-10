package io.github.haiderkagalwala.nexec;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared utilities for integration tests.
 *
 * <p>Builds commands that invoke {@link TestProcessHelper} via the same JVM that
 * is running the tests, using the current classpath. This keeps tests cross-platform —
 * the only external binary required is {@code java}, which is always present since
 * nexec itself requires Java 21.
 */
final class TestSupport {

    private TestSupport() {}

    /** Path to the {@code java} executable running the current JVM. */
    static final String JAVA = ProcessHandle.current().info().command()
            .orElseThrow(() -> new IllegalStateException("Cannot determine java executable path"));

    /** The classpath of the current JVM, which includes compiled test classes. */
    static final String CP = System.getProperty("java.class.path");

    /** Fully-qualified name of the test helper main class. */
    static final String HELPER = TestProcessHelper.class.getName();

    /**
     * Builds a command array that invokes {@link TestProcessHelper} with the given arguments.
     *
     * <pre>{@code
     * Nexec.run(TestSupport.cmd("exit", "0")).execute();
     * Nexec.run(TestSupport.cmd("stdout", "hello")).onStdout(...).execute();
     * }</pre>
     */
    static String[] cmd(String... helperArgs) {
        var cmd = new ArrayList<String>();
        cmd.add(JAVA);
        cmd.add("-cp");
        cmd.add(CP);
        cmd.add(HELPER);
        cmd.addAll(List.of(helperArgs));
        return cmd.toArray(new String[0]);
    }
}
