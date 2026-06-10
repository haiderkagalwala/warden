package io.github.haiderkagalwala.nexec;

import io.github.haiderkagalwala.nexec.result.ProcessOutcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link ProcessOutcome} record variants.
 * Tests construction, accessors, and sealed-interface pattern matching.
 */
class ProcessOutcomeTest {

    @Test
    void completed_exitCodeZero_succeededTrue() {
        var c = new ProcessOutcome.Completed(0, true, Duration.ofMillis(10));
        assertEquals(0, c.exitCode());
        assertTrue(c.succeeded());
        assertTrue(c.success());
    }

    @Test
    void completed_exitCodeNonZero_succeededFalse() {
        var c = new ProcessOutcome.Completed(1, false, Duration.ofMillis(10));
        assertEquals(1, c.exitCode());
        assertFalse(c.succeeded());
    }

    @Test
    void completed_duration_preserved() {
        var d = Duration.ofSeconds(3);
        var c = new ProcessOutcome.Completed(0, true, d);
        assertEquals(d, c.duration());
    }

    @Test
    void timedOut_elapsed_preserved() {
        var d = Duration.ofMillis(500);
        var t = new ProcessOutcome.TimedOut(d);
        assertEquals(d, t.elapsed());
    }

    @Test
    void killed_elapsed_preserved() {
        var d = Duration.ofMillis(200);
        var k = new ProcessOutcome.Killed(d);
        assertEquals(d, k.elapsed());
    }

    @Test
    void failed_cause_preserved() {
        var cause = new RuntimeException("test_cause");
        var f = new ProcessOutcome.Failed(cause);
        assertSame(cause, f.cause());
    }

    @Test
    void sealedInterface_patternMatchCoversAllVariants() {
        ProcessOutcome outcome = new ProcessOutcome.Completed(0, true, Duration.ZERO);
        // Compiler will flag this as non-exhaustive if a variant is missing
        String label = switch (outcome) {
            case ProcessOutcome.Completed c -> "completed";
            case ProcessOutcome.TimedOut  t -> "timedout";
            case ProcessOutcome.Killed    k -> "killed";
            case ProcessOutcome.Failed    f -> "failed";
        };
        assertEquals("completed", label);
    }
}
