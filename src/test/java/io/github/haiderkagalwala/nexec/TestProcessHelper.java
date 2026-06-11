package io.github.haiderkagalwala.nexec;

import java.util.Arrays;

/**
 * Subprocess entry point used exclusively by integration tests.
 *
 * <p>Run via: {@code java -cp <classpath> io.github.haiderkagalwala.nexec.TestProcessHelper <command> [arg]}
 *
 * <p>Commands:
 * <ul>
 *   <li>{@code exit <code>}    — exits with the given integer exit code</li>
 *   <li>{@code stdout <text>}  — prints text to stdout and exits 0</li>
 *   <li>{@code stderr <text>}  — prints text to stderr and exits 0</li>
 *   <li>{@code both <text>}    — prints text to both stdout and stderr</li>
 *   <li>{@code sleep <ms>}     — sleeps for the given number of milliseconds</li>
 *   <li>{@code cat}            — reads stdin to EOF and echoes to stdout</li>
 *   <li>{@code infinite}       — writes output lines forever (never exits)</li>
 * </ul>
 */
public class TestProcessHelper {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) return;
        switch (args[0]) {
            case "exit" -> System.exit(Integer.parseInt(args[1]));

            case "stdout" -> {
                System.out.println(args[1]);
                System.out.flush();
            }

            case "stderr" -> {
                System.err.println(args[1]);
                System.err.flush();
            }

            case "both" -> {
                System.out.println(args[1]);
                System.out.flush();
                System.err.println(args[1]);
                System.err.flush();
            }

            case "sleep" -> Thread.sleep(Long.parseLong(args[1]));

            case "cat" -> {
                var buf = new byte[4096];
                int n;
                while ((n = System.in.read(buf)) != -1) {
                    System.out.write(buf, 0, n);
                }
                System.out.flush();
            }

            case "infinite" -> {
                while (true) {
                    System.out.println("output");
                    System.out.flush();
                }
            }
            case "bigoutput" -> {
                // 2MB — well past the 64KB pipe wall
                byte[] data = new byte[2 * 1024 * 1024];
                Arrays.fill(data, (byte) 'x');
                System.out.write(data);
                System.out.flush();
            }

            case "ptyecho" -> {
                // Signals readiness, then reads one line from PTY stdin, echoes it,
                // and exits. The READY marker lets the test synchronise without sleeping.
                System.out.println("READY");
                System.out.flush();
                var line = new java.io.BufferedReader(
                        new java.io.InputStreamReader(System.in)).readLine();
                if (line != null) {
                    System.out.println(line);
                    System.out.flush();
                }
            }
            case "env" -> {
                String val = System.getenv(args[1]);
                System.out.println(val != null ? val : "NOT_SET");
                System.out.flush();
            }
        }
    }
}
