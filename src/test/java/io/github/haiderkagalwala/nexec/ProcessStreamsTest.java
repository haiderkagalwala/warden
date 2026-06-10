package io.github.haiderkagalwala.nexec;

import io.github.haiderkagalwala.nexec.streams.ProcessStreams;
import io.github.haiderkagalwala.nexec.streams.StreamConsumer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link ProcessStreams} and {@link StreamConsumer}.
 * No process is spawned — these test the consumer implementations directly.
 */
class ProcessStreamsTest {

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void toStringBuilder_accumulates() throws Exception {
        var sb = new StringBuffer();
        var c = ProcessStreams.toStringBuilder(sb);
        c.consume(utf8("hello "));
        c.consume(utf8("world"));
        assertEquals("hello world", sb.toString());
    }

    @Test
    void lines_firesOncePerNewline() throws Exception {
        var received = new ArrayList<String>();
        var c = ProcessStreams.lines(received::add);
        c.consume(utf8("line1\nline2\nline3\n"));
        assertEquals(3, received.size());
        assertEquals("line1", received.get(0));
        assertEquals("line2", received.get(1));
        assertEquals("line3", received.get(2));
    }

    @Test
    void lines_handlesChunkBoundary() throws Exception {
        var received = new ArrayList<String>();
        var c = ProcessStreams.lines(received::add);
        c.consume(utf8("hel"));
        c.consume(utf8("lo\n"));
        assertEquals(1, received.size());
        assertEquals("hello", received.get(0));
    }

    @Test
    void lines_stripsWindowsCarriageReturn() throws Exception {
        var received = new ArrayList<String>();
        ProcessStreams.lines(received::add).consume(utf8("windows\r\n"));
        assertEquals("windows", received.get(0));
    }

    @Test
    void lines_incompleteLineFinalChunk_notFired() throws Exception {
        var received = new ArrayList<String>();
        ProcessStreams.lines(received::add).consume(utf8("no_newline"));
        assertTrue(received.isEmpty());
    }

    @Test
    void lines_eachCallIsIndependent() throws Exception {
        var r1 = new ArrayList<String>();
        var r2 = new ArrayList<String>();
        var c1 = ProcessStreams.lines(r1::add);
        var c2 = ProcessStreams.lines(r2::add);
        c1.consume(utf8("partial"));
        c2.consume(utf8("other\n"));
        c1.consume(utf8("_done\n"));
        assertEquals("partial_done", r1.get(0));
        assertEquals("other", r2.get(0));
    }

    @Test
    void tee_forwardsToAllConsumers() throws Exception {
        var sb1 = new StringBuffer();
        var sb2 = new StringBuffer();
        ProcessStreams.tee(
                ProcessStreams.toStringBuilder(sb1),
                ProcessStreams.toStringBuilder(sb2)
        ).consume(utf8("tee_data"));
        assertEquals("tee_data", sb1.toString());
        assertEquals("tee_data", sb2.toString());
    }

    @Test
    void tee_firstThrows_secondStillCalled() {
        var secondCalled = new AtomicBoolean(false);
        var tee = ProcessStreams.tee(
                chunk -> { throw new RuntimeException("first fails"); },
                chunk -> secondCalled.set(true)
        );
        assertThrows(Exception.class, () -> tee.consume(new byte[]{1}));
        assertTrue(secondCalled.get());
    }

    @Test
    void noop_doesNotThrow() {
        assertDoesNotThrow(() -> StreamConsumer.NOOP.consume(new byte[]{1, 2, 3}));
        assertDoesNotThrow(() -> StreamConsumer.NOOP.consume(new byte[0]));
    }
}
