package io.github.haiderkagalwala.warden.internal;

import java.util.concurrent.TimeUnit;

/**
 * Kills a process tree. Package-private — used only by the execution engines.
 *
 * <p>Strategy: SIGTERM first (graceful), wait 3 seconds, then SIGKILL on anyone still alive.
 * Descendants are targeted explicitly so the parent cannot respawn them during the grace period.
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
