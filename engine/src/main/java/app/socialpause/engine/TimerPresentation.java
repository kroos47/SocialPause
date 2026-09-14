package app.socialpause.engine;

/** Shared, testable interpretation for notification title, icon, countdown and progress. */
public final class TimerPresentation {
    public enum Kind { HIDDEN, APP, SHARED, PAUSED, LUNCH, COOLDOWN }
    public final Kind kind;
    public final String app;
    public final long remaining, limit;
    private TimerPresentation(Kind kind, String app, long remaining, long limit) {
        this.kind = kind; this.app = app; this.remaining = Math.max(0, remaining); this.limit = limit;
    }
    public static TimerPresentation of(RulesEngine e, boolean connected, long wall, long elapsed) {
        if (!e.running || !connected || e.quiet(wall)) return new TimerPresentation(Kind.HIDDEN, null, 0, 1);
        var mode = e.mode(wall, elapsed);
        if (mode == RulesEngine.Mode.COOLDOWN || mode == RulesEngine.Mode.LUNCH)
            return new TimerPresentation(mode == RulesEngine.Mode.LUNCH ? Kind.LUNCH : Kind.COOLDOWN, null, e.countdown(wall, elapsed), RulesEngine.COOLDOWN);
        long shared = RulesEngine.TOTAL_LIMIT - e.total();
        String app = e.focused();
        if (app == null) return new TimerPresentation(Kind.PAUSED, null, shared, RulesEngine.TOTAL_LIMIT);
        if (e.remaining(app) <= shared) return new TimerPresentation(Kind.APP, app, e.remaining(app), RulesEngine.APP_LIMIT);
        return new TimerPresentation(Kind.SHARED, app, shared, RulesEngine.TOTAL_LIMIT);
    }
    public boolean ticking() { return kind != Kind.HIDDEN && kind != Kind.PAUSED; }
    public int progress() { return (int) Math.max(0, Math.min(100, (limit - remaining) * 100 / limit)); }
}
