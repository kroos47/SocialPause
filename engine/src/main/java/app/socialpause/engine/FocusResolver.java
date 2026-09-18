package app.socialpause.engine;

import java.util.*;

/** Resolves app identity without reading screen text. Owned by the monitoring service, never persisted. */
public final class FocusResolver {
    public enum Kind { APP, SYSTEM_PANEL, INPUT_METHOD, OTHER }
    public record Window(String app, Kind kind, boolean focused, boolean active, int layer) {}
    private String lastApp;
    public void reset() { lastApp = null; }
    public String resolve(boolean unlocked, boolean running, List<Window> windows, String fallback, boolean recents) {
        if (!unlocked || !running || recents) { reset(); return null; }
        List<Window> ordered = new ArrayList<>(windows);
        ordered.sort(Comparator.comparingInt(Window::layer).reversed());
        for (Window w : ordered) if (w.focused() && w.kind() != Kind.INPUT_METHOD) return accept(w, fallback);
        for (Window w : ordered) if (w.active() && w.kind() != Kind.INPUT_METHOD) return accept(w, fallback);
        return fallback(fallback);
    }
    private String fallback(String fallback) {
        if ("com.android.systemui".equals(fallback)) return lastApp;
        if (fallback != null) { lastApp = fallback; return lastApp; }
        // No window evidence must not keep charging a stale app indefinitely.
        reset(); return null;
    }
    private String accept(Window window, String fallback) {
        if (window.kind() == Kind.SYSTEM_PANEL) return lastApp;
        if (window.kind() == Kind.APP) return fallback(window.app() != null ? window.app() : fallback);
        reset(); return null;
    }
}
