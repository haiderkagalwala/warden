package io.github.haiderkagalwala.warden.internal;

import java.util.concurrent.TimeUnit;

/**
 * Terminates a PTY process.
 *
 * <p>PTY processes do not have a Java-visible process tree, so only the root process is
 * targeted. SIGTERMs first, waits up to 3 seconds, then SIGKILLs if still alive.
 */
final class PtyTreeReaper {
    private PtyTreeReaper() {}

    static void destroy(Process p) {
        try {
            p.destroy();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }
}
