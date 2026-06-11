package io.github.haiderkagalwala.nexec;

import io.github.haiderkagalwala.nexec.result.ProcessOutcome;
import io.github.haiderkagalwala.nexec.streams.ProcessStreams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SyncExecutionTest {

    // ── Outcomes ──────────────────────────────────────────────────────────────

    @Test
    void exitZero_returnsCompleted_succeeded() {
        var outcome = Nexec.run(TestSupport.cmd("exit", "0")).execute();
        assertInstanceOf(ProcessOutcome.Completed.class, outcome);
        var c = (ProcessOutcome.Completed) outcome;
        assertEquals(0, c.exitCode());
        assertTrue(c.succeeded());
    }

    @Test
    void exitNonZero_returnsCompleted_notSucceeded() {
        var outcome = Nexec.run(TestSupport.cmd("exit", "42")).execute();
        assertInstanceOf(ProcessOutcome.Completed.class, outcome);
        var c = (ProcessOutcome.Completed) outcome;
        assertEquals(42, c.exitCode());
        assertFalse(c.succeeded());
    }

    @Test
    void invalidCommand_returnsFailed() {
        var outcome = Nexec.run("this_command_does_not_exist_xyz").execute();
        assertInstanceOf(ProcessOutcome.Failed.class, outcome);
        assertNotNull(((ProcessOutcome.Failed) outcome).cause());
    }

    @Test
    void duration_isNonNegative() {
        var c = (ProcessOutcome.Completed) Nexec.run(TestSupport.cmd("exit", "0")).execute();
        assertNotNull(c.duration());
        assertFalse(c.duration().isNegative());
    }

    // ── Stdout / stderr ───────────────────────────────────────────────────────

    @Test
    void onStdout_receivesOutput() {
        var sb = new StringBuffer();
        Nexec.run(TestSupport.cmd("stdout", "hello_nexec"))
                .onStdout(ProcessStreams.toStringBuilder(sb))
                .execute();
        assertTrue(sb.toString().contains("hello_nexec"), "got: " + sb);
    }

    @Test
    void onStderr_receivesOutput() {
        var sb = new StringBuffer();
        Nexec.run(TestSupport.cmd("stderr", "error_nexec"))
                .onStderr(ProcessStreams.toStringBuilder(sb))
                .execute();
        assertTrue(sb.toString().contains("error_nexec"), "got: " + sb);
    }

    @Test
    void mergeOutputAndError_bothReachStdoutConsumer() {
        var sb = new StringBuffer();
        Nexec.run(TestSupport.cmd("both", "merged"))
                .mergeOutputAndError()
                .onStdout(ProcessStreams.toStringBuilder(sb))
                .execute();
        var text = sb.toString();
        assertTrue(text.indexOf("merged") != text.lastIndexOf("merged"),
                "expected 'merged' to appear twice (stdout + stderr), got: " + text);
    }

    @Test
    void mergeOutputAndError_onStderrAfterMerge_isIgnored() {
        var stderrSb = new StringBuffer();
        var stdoutSb = new StringBuffer();
        Nexec.run(TestSupport.cmd("both", "msg"))
                .mergeOutputAndError()
                .onStdout(ProcessStreams.toStringBuilder(stdoutSb))
                .onStderr(ProcessStreams.toStringBuilder(stderrSb))
                .execute();
        assertFalse(stdoutSb.toString().isEmpty(), "stdout consumer must receive output");
        assertTrue(stderrSb.toString().isEmpty(), "stderr consumer must be ignored after merge");
    }

    // ── Timeout ───────────────────────────────────────────────────────────────

    @Test
    void timeout_returnsTimedOut() {
        var outcome = Nexec.run(TestSupport.cmd("sleep", "60000"))
                .timeout(Duration.ofMillis(500))
                .execute();
        assertInstanceOf(ProcessOutcome.TimedOut.class, outcome);
    }

    @Test
    void timeout_withActiveConsumer_returnsTimedOut_notFailed() {
        var sb = new StringBuffer();
        var outcome = Nexec.run(TestSupport.cmd("infinite"))
                .onStdout(ProcessStreams.toStringBuilder(sb))
                .timeout(Duration.ofMillis(500))
                .execute();
        assertInstanceOf(ProcessOutcome.TimedOut.class, outcome);
    }

    @Test
    void timeout_elapsed_isPositive() {
        var t = (ProcessOutcome.TimedOut) Nexec.run(TestSupport.cmd("sleep", "60000"))
                .timeout(Duration.ofMillis(500))
                .execute();
        assertFalse(t.elapsed().isNegative());
        assertFalse(t.elapsed().isZero());
    }

    @Test
    void noConsumer_largeOutput_noDeadlock() {
        var outcome = Nexec.run(TestSupport.cmd("infinite"))
                .timeout(Duration.ofMillis(500))
                .execute();
        assertInstanceOf(ProcessOutcome.TimedOut.class, outcome);
    }

    // ── Redirect ──────────────────────────────────────────────────────────────

    @Test
    void redirectStdout_writesOutputToFile(@TempDir Path tmp) throws IOException {
        // Files.readString throws IOException — the only legitimate checked throw in this file
        var outFile = tmp.resolve("out.txt");
        Nexec.run(TestSupport.cmd("stdout", "file_content"))
                .redirectStdout(outFile)
                .execute();
        assertTrue(Files.readString(outFile).contains("file_content"));
    }

    // ── Builder options ───────────────────────────────────────────────────────

    @Test
    void env_addedVariablePassedToChild() {
        var sb = new StringBuffer();
        Nexec.run(TestSupport.cmd("env", "NEXEC_TEST"))
                .env("NEXEC_TEST", "expected_value")
                .onStdout(ProcessStreams.toStringBuilder(sb))
                .execute();
        assertTrue(sb.toString().contains("expected_value"),
                "env var not received by child, got: " + sb);
    }

    @Test
    void workingDir_doesNotThrow(@TempDir Path tmp) {
        var outcome = Nexec.run(TestSupport.cmd("exit", "0"))
                .workingDir(tmp)
                .execute();
        assertInstanceOf(ProcessOutcome.Completed.class, outcome);
    }

    // ── Custom success exit codes ─────────────────────────────────────────────

    @Test
    void successExitCodes_nonZeroTreatedAsSuccess() {
        var outcome = Nexec.run(TestSupport.cmd("exit", "42"))
                .successExitCodes(42)
                .execute();
        var c = (ProcessOutcome.Completed) outcome;
        assertEquals(42, c.exitCode());
        assertTrue(c.succeeded());
    }

    @Test
    void successExitCodes_multipleCodesAccepted() {
        for (int code : new int[]{0, 1, 2}) {
            var c = (ProcessOutcome.Completed) Nexec.run(TestSupport.cmd("exit", String.valueOf(code)))
                    .successExitCodes(0, 1, 2)
                    .execute();
            assertTrue(c.succeeded(), "exit " + code + " should be succeeded with codes [0,1,2]");
        }
    }

    @Test
    void successExitCodes_defaultIsZeroOnly() {
        var c = (ProcessOutcome.Completed) Nexec.run(TestSupport.cmd("exit", "1")).execute();
        assertFalse(c.succeeded(), "exit 1 must not succeed with default codes");
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void multipleSequentialExecutions_allSucceed() {
        for (int i = 0; i < 3; i++) {
            var outcome = Nexec.run(TestSupport.cmd("exit", "0")).execute();
            assertInstanceOf(ProcessOutcome.Completed.class, outcome, "failed on iteration " + i);
        }
    }

    @Test
    void concurrentAsyncExecutions_allComplete() throws IOException {
        // executeAsync() propagates IOException from Process.start() — throws is legitimate here
        var handles = new ArrayList<io.github.haiderkagalwala.nexec.handle.PipeHandle>();
        for (int i = 0; i < 5; i++) {
            handles.add(Nexec.run(TestSupport.cmd("exit", "0")).executeAsync());
        }
        for (var h : handles) {
            assertInstanceOf(ProcessOutcome.Completed.class, h.await());
        }
    }

    @Test
    void noConsumer_largeOutput_doesNotDeadlock() {
        // Sync mode DISCARDs streams with no consumer at the OS level — the process
        // can write freely without filling the pipe buffer. Must complete, not time out.
        var outcome = Nexec.run(TestSupport.cmd("bigoutput"))
                .timeout(Duration.ofSeconds(15))
                .execute();
        assertInstanceOf(ProcessOutcome.Completed.class, outcome,
                "Process deadlocked — stream draining is broken");
    }

    @Test
    void ptyBuilder_windowsOptions_doNotThrow() {
        assertDoesNotThrow(() ->
                Nexec.interactive(TestSupport.JAVA, "-version")
                        .useConPty()
                        .windowsAnsiColors()
                        .useWinPty()
        );
    }
}
