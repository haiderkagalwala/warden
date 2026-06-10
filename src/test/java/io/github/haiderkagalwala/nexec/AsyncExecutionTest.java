package io.github.haiderkagalwala.nexec;

import io.github.haiderkagalwala.nexec.handle.PtyHandle;
import io.github.haiderkagalwala.nexec.result.ProcessOutcome;
import io.github.haiderkagalwala.nexec.streams.ProcessStreams;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AsyncExecutionTest {


    @Test
    void exitZero_completedNormally() throws IOException {
        var outcome = Nexec.run(TestSupport.cmd("exit", "0")).executeAsync().await();
        assertInstanceOf(ProcessOutcome.Completed.class, outcome);
        assertTrue(((ProcessOutcome.Completed) outcome).succeeded());
    }

    @Test
    void exitNonZero_completedNotSucceeded() throws IOException {
        var outcome = Nexec.run(TestSupport.cmd("exit", "1")).executeAsync().await();
        assertInstanceOf(ProcessOutcome.Completed.class, outcome);
        assertFalse(((ProcessOutcome.Completed) outcome).succeeded());
    }

    @Test
    void onStdout_receivesOutput() throws IOException {
        var sb = new StringBuffer();
        Nexec.run(TestSupport.cmd("stdout", "async_hello"))
                .onStdout(ProcessStreams.toStringBuilder(sb))
                .executeAsync()
                .await();
        assertTrue(sb.toString().contains("async_hello"), "got: " + sb);
    }

    @Test
    void timeout_returnsTimedOut() throws IOException {
        var outcome = Nexec.run(TestSupport.cmd("sleep", "60000"))
                .timeout(Duration.ofMillis(500))
                .executeAsync()
                .await();
        assertInstanceOf(ProcessOutcome.TimedOut.class, outcome);
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancel_returnsKilled() throws IOException, InterruptedException {
        var handle = Nexec.run(TestSupport.cmd("sleep", "60000"))
                .noTimeout()
                .executeAsync();
        Thread.sleep(100);
        handle.cancel();
        assertInstanceOf(ProcessOutcome.Killed.class, handle.await());
    }

    @Test
    void cancel_idempotent() throws IOException, InterruptedException {
        var handle = Nexec.run(TestSupport.cmd("sleep", "60000"))
                .noTimeout()
                .executeAsync();
        Thread.sleep(100);
        assertDoesNotThrow(() -> {
            handle.cancel();
            handle.cancel();
            handle.cancel();
        });
        handle.await();
    }

    @Test
    void isAlive_trueWhileRunning_falseAfterCancel() throws IOException, InterruptedException {
        var handle = Nexec.run(TestSupport.cmd("sleep", "60000"))
                .noTimeout()
                .executeAsync();
        assertTrue(handle.isAlive());
        handle.cancel();
        handle.await();
        assertFalse(handle.isAlive());
    }

    // ── Stdin ─────────────────────────────────────────────────────────────────

    @Test
    void writeLine_stdinReachesProcess() throws IOException {
        var sb = new StringBuffer();
        var handle = Nexec.run(TestSupport.cmd("cat"))
                .noTimeout()
                .onStdout(ProcessStreams.toStringBuilder(sb))
                .executeAsync();
        handle.writeLine("stdin_data");
        handle.stdin().close();
        handle.await();
        assertTrue(sb.toString().contains("stdin_data"), "got: " + sb);
    }

    @Test
    void outcomeResolvesAfterDrainsComplete() throws IOException {
        var sb = new StringBuffer();
        var handle = Nexec.run(TestSupport.cmd("stdout", "drain_check"))
                .onStdout(ProcessStreams.toStringBuilder(sb))
                .executeAsync();
        handle.await();
        assertFalse(sb.toString().isEmpty(), "Output must be available after await()");
    }

    // ── PTY execution ─────────────────────────────────────────────────────────

    @Test
    void pty_exitZero_returnsCompleted() throws IOException {
        var outcome = Nexec.interactive(TestSupport.cmd("exit", "0"))
                .start()
                .await();
        assertInstanceOf(ProcessOutcome.Completed.class, outcome);
    }

    @Test
    void pty_exitNonZero_completedNotSucceeded() throws IOException {
        var outcome = Nexec.interactive(TestSupport.cmd("exit", "7"))
                .start()
                .await();
        assertInstanceOf(ProcessOutcome.Completed.class, outcome);
        assertFalse(((ProcessOutcome.Completed) outcome).succeeded());
        assertEquals(7, ((ProcessOutcome.Completed) outcome).exitCode());
    }

    @Test
    void pty_onOutput_receivesOutput() throws IOException {
        var sb = new StringBuffer();
        Nexec.interactive(TestSupport.cmd("stdout", "pty_hello"))
                .onOutput(ProcessStreams.toStringBuilder(sb))
                .start()
                .await();
        assertTrue(sb.toString().contains("pty_hello"), "got: " + sb);
    }

//    @Test
//    void pty_writeLine_processReceivesInput() throws IOException {
//        var sb = new StringBuffer();
//
//        // 1. Launch a shell instead of cat
//        PtyHandle shell = Nexec.interactive(TestSupport.cmd("sh")) // or "bash"
//                .onOutput(ProcessStreams.toStringBuilder(sb))
//                .start();
//
//        // 2. Send the echo command to prove we can write to the PTY
//        shell.writeLine("echo pty_stdin_data");
//
//        // 3. Explicitly command the shell to terminate
//        shell.writeLine("exit");
//
//        // 4. Block until the shell exits and the stream drains
//        shell.await();
//
//        System.out.println("-----> " + sb.toString());
//        assertTrue(sb.toString().contains("pty_stdin_data"), "got: " + sb);
//    }

    @Test
    void pty_cancel_returnsKilled() throws IOException, InterruptedException {
        PtyHandle shell = Nexec.interactive(TestSupport.cmd("sleep", "60000"))
                .noTimeout()
                .start();
        Thread.sleep(100);
        shell.cancel();
        assertInstanceOf(ProcessOutcome.Killed.class, shell.await());
    }

    @Test
    void pty_timeout_returnsTimedOut() throws IOException {
        var outcome = Nexec.interactive(TestSupport.cmd("sleep", "60000"))
                .timeout(Duration.ofMillis(500))
                .start()
                .await();
        assertInstanceOf(ProcessOutcome.TimedOut.class, outcome);
    }

    @Test
    void pty_cancel_idempotent() throws IOException, InterruptedException {
        PtyHandle shell = Nexec.interactive(TestSupport.cmd("sleep", "60000"))
                .noTimeout()
                .start();
        Thread.sleep(100);
        assertDoesNotThrow(() -> {
            shell.cancel();
            shell.cancel();
        });
        shell.await();
    }

    @Test
    void pty_isAlive_falseAfterCompletion() throws IOException {
        PtyHandle shell = Nexec.interactive(TestSupport.cmd("exit", "0")).start();
        shell.await();
        assertFalse(shell.isAlive());
    }
}
