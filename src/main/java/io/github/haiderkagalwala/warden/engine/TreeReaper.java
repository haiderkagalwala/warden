package io.github.haiderkagalwala.warden.engine;

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
        try {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();

            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                // Root survived the grace period — escalate everything
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
            } else {
                // Root exited cleanly — mop up descendants that ignored SIGTERM
                p.descendants()
                 .filter(ProcessHandle::isAlive)
                 .forEach(ProcessHandle::destroyForcibly);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
    }
}
