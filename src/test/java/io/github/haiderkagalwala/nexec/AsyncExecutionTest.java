package io.github.haiderkagalwala.nexec;

import io.github.haiderkagalwala.nexec.handle.PtyHandle;
import io.github.haiderkagalwala.nexec.result.ProcessOutcome;
import io.github.haiderkagalwala.nexec.streams.ProcessStreams;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    void cancel_returnsKilled() throws IOException {
        // executeAsync() returns only after the OS process is running — no sleep needed.
        var handle = Nexec.run(TestSupport.cmd("sleep", "60000"))
                .noTimeout()
                .executeAsync();
        handle.cancel();
        assertInstanceOf(ProcessOutcome.Killed.class, handle.await());
    }

    @Test
    void cancel_idempotent() throws IOException {
        var handle = Nexec.run(TestSupport.cmd("sleep", "60000"))
                .noTimeout()
                .executeAsync();
        assertDoesNotThrow(() -> {
            handle.cancel();
            handle.cancel();
            handle.cancel();
        });
        handle.await();
    }

    @Test
    void isAlive_trueWhileRunning_falseAfterCancel() throws IOException {
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

    @Test
    void withConsumer_largeOutput_doesNotDeadlock_async() throws IOException {
        // In async mode streams are kept as pipes — without a consumer the pipe buffer
        // fills and the process blocks. This test verifies the drain path handles large output.
        var handle = Nexec.run(TestSupport.cmd("bigoutput"))
                .onStdout(chunk -> {})   // drain and discard
                .timeout(Duration.ofSeconds(15))
                .executeAsync();
        assertInstanceOf(ProcessOutcome.Completed.class, handle.await(),
                "Async process deadlocked");
    }



    @Test
    void pty_write_processReceivesInput() throws Exception {
        // "ptyecho" prints "READY" once it is listening on stdin, then reads one line
        // and echoes it. We block on a CountDownLatch until READY is observed — no
        // arbitrary sleep, no timing dependency.
        var ready = new CountDownLatch(1);
        var sb    = new StringBuffer();

        PtyHandle proc = Nexec.interactive(TestSupport.cmd("ptyecho"))
                .onOutput(ProcessStreams.lines(line -> {
                    sb.append(line).append("\n");
                    if (line.contains("READY")) ready.countDown();
                }))
                .start();

        assertTrue(ready.await(5, TimeUnit.SECONDS), "ptyecho did not signal READY");
        proc.write("pty_stdin_data\r");
        proc.await();

        assertTrue(sb.toString().contains("pty_stdin_data"), "got: " + sb);
    }

    @Test
    void pty_cancel_returnsKilled() throws IOException {
        // PtyProcessBuilder.start() returns only after the process is running — no sleep needed.
        PtyHandle shell = Nexec.interactive(TestSupport.cmd("sleep", "60000"))
                .noTimeout()
                .start();
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
    void pty_cancel_idempotent() throws IOException {
        PtyHandle shell = Nexec.interactive(TestSupport.cmd("sleep", "60000"))
                .noTimeout()
                .start();
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
