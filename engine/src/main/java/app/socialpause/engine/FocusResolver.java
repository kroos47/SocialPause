package app.socialpause.engine;

import java.util.*;

/** Resolves app identity without reading screen text. Owned by the monitoring service, never persisted. */
public final class FocusResolver {
    public enum Kind { APP, SYSTEM_PANEL, INPUT_METHOD, OTHER }
    public record Window(String app, Kind kind, boolean focused, boolean active, int layer) {}
    /** Usage identity survives the drawer; visibility requires current app-window evidence. */
    public record Focus(String app, boolean appVisible) {}
    private String lastApp;
    public void reset() { lastApp = null; }
    public String resolve(boolean unlocked, boolean running, List<Window> windows, String fallback, boolean recents) {
        return resolveFocus(unlocked, running, windows, fallback, recents).app();
    }
    public Focus resolveFocus(boolean unlocked, boolean running, List<Window> windows, String fallback, boolean recents) {
        if (!unlocked || !running || recents) { reset(); return new Focus(null, false); }
        List<Window> ordered = new ArrayList<>(windows);
        ordered.sort(Comparator.comparingInt(Window::layer).reversed());
        for (Window w : ordered) if (w.focused() && w.kind() != Kind.INPUT_METHOD) return accept(w, fallback, ordered);
        for (Window w : ordered) if (w.active() && w.kind() != Kind.INPUT_METHOD) return accept(w, fallback, ordered);
        // An IME root is not evidence that the app beneath it is uncovered.
        if (fallback != null && ordered.stream().anyMatch(w -> w.kind() == Kind.INPUT_METHOD && fallback.equals(w.app())))
            return new Focus(lastApp, false);
        return fallback(fallback);
    }
    private Focus fallback(String fallback) {
        if ("com.android.systemui".equals(fallback)) return new Focus(lastApp, false);
        if (fallback != null) { lastApp = fallback; return new Focus(lastApp, true); }
        // No window evidence must not keep charging a stale app indefinitely.
        reset(); return new Focus(null, false);
    }
    private Focus accept(Window window, String fallback, List<Window> windows) {
        if (window.kind() == Kind.SYSTEM_PANEL) return new Focus(lastApp, false);
        if (window.kind() == Kind.APP) {
            // Some OEMs leave the app focused while making the shade active above it.
            // Inactive status/navigation bars must not count as a covering panel.
            boolean covered = windows.stream().anyMatch(w -> w.kind() == Kind.SYSTEM_PANEL
                    && (w.focused() || w.active()) && w.layer() > window.layer());
            if (covered || "com.android.systemui".equals(fallback)) return new Focus(lastApp, false);
            return fallback(window.app() != null ? window.app() : fallback);
        }
        reset(); return new Focus(null, false);
    }
}
