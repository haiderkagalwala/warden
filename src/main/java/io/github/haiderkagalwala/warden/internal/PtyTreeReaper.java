package io.github.haiderkagalwala.warden.internal;

import java.util.concurrent.TimeUnit;

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
