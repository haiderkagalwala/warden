package io.github.haiderkagalwala.warden.internal;

import java.util.concurrent.TimeUnit;

/**
 * Terminates a process tree for non-PTY processes.
 *
 * <p>SIGTERMs all known descendants first, then the root. Waits up to 3 seconds for the
 * root to exit gracefully. Any process still alive after the grace period is SIGKILLed.
 * Descendants are snapshotted before the kill so the parent cannot respawn them during
 * the grace period.
 */
final class TreeReaper {

    private TreeReaper() {}

    static void destroy(Process p) {
        var descendants = p.descendants().toList();

        descendants.forEach(ProcessHandle::destroy);
        p.destroy();

        try {
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                descendants.stream()
                        .filter(ProcessHandle::isAlive)
                        .forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
            } else {
                descendants.stream()
                        .filter(ProcessHandle::isAlive)
                        .forEach(ProcessHandle::destroyForcibly);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            descendants.forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
    }
}
