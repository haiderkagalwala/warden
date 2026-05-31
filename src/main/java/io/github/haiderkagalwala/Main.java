package io.github.haiderkagalwala;

import io.github.haiderkagalwala.warden.engine.Warden;
import io.github.haiderkagalwala.warden.result.ProcessOutcome;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Main {

    public static void main(String[] args) throws Exception {


        var processOutcome = Warden.run("git", "status")
                .onStdout((buf) -> {

                            System.out.print(new String(buf, UTF_8));

                })
                .execute();
        switch (processOutcome) {
            case ProcessOutcome.Completed c-> System.out.println(c.exitCode());
            default -> System.out.println("");
        }

    }
}


/*

//       var p = Warden.interactive("/bin/bash")
//                .ptySize(216, 16)
////                .onOutput(ProcessStreams::printToStdout)
//                .noTimeout()
//                .start();

      var p  = Warden.run()
                .inheritIO()
                .executeAsync();
        p.await().
        // ── 1. Synchronous — capture stdout into outcome ──────────────────────
        ProcessOutcome outcome = Warden.run("git", "log", "--oneline", "-5")
                .captureStdout()
                .captureStderr()
                .execute();

        switch (outcome) {
            case ProcessOutcome.Completed c ->
                    System.out.printf("[sync] exit=%d%n%s%n", c.exitCode(), c.stdoutAsString());
            case ProcessOutcome.TimedOut t ->
                    System.err.println("[sync] timed out after " + t.elapsed());
            case ProcessOutcome.Killed k ->
                    System.err.println("[sync] killed");
            case ProcessOutcome.Failed f ->
                    f.cause().printStackTrace();
        }


        // ── 2. Async — stream lines live, non-blocking ────────────────────────
        RunningProcess rp = Warden.run("git", "log", "--oneline", "-10")
                .onStdout(ProcessStreams.lines(line -> System.out.println("[async] " + line)))
                .timeout(Duration.ofSeconds(10))
                .executeAsync();

        // The calling thread is free to do other work here.
        rp.await();


        // ── 3. Async — capture + stream simultaneously (tee) ─────────────────
        var sb = new StringBuilder();
        RunningProcess teeProcess = Warden.run("git", "status")
                .onStdout(ProcessStreams.tee(
                        ProcessStreams.printToStdout(),
                        ProcessStreams.toStringBuilder(sb)
                ))
                .captureStdout()
                .executeAsync();

        if (teeProcess.await() instanceof ProcessOutcome.Completed c) {
            System.out.println("[tee] captured " + sb.length() + " chars");
        }


        // ── 4. Async — write to stdin, read stdout directly ───────────────────
        RunningProcess writer = Warden.run("cat")
                .noTimeout()
                .executeAsync();

        try (var stdin = writer.stdin()) {
            stdin.write("hello from warden\n".getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } // closing stdin sends EOF → cat exits

        writer.await();


        // ── 5. Interactive PTY — write commands to a shell ───────────────────
        InteractiveProcess shell = Warden.interactive("bash")
                .ptySize(220, 50)
                .timeout(Duration.ofSeconds(15))
                .onOutput(ProcessStreams.lines(line -> System.out.println("[pty] " + line)))
                .start();

        shell.writeLine("echo 'hello from PTY'")
             .writeLine("pwd")
             .writeLine("exit 0");

        switch (shell.await()) {
            case ProcessOutcome.Completed c -> System.out.println("[pty] exited " + c.exitCode());
            case ProcessOutcome.Killed   k -> System.out.println("[pty] cancelled");
            default                        -> System.out.println("[pty] unexpected outcome");
        }
 */