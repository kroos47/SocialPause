package app.socialpause.engine;

import java.util.*;

/** Immutable notification snapshot. An overview contains every selected app, never a shared budget. */
public final class TimerPresentation {
    public enum Kind { HIDDEN, APP, OVERVIEW, ALL_COOLDOWN, LUNCH, LUNCH_COOLDOWN }
    public record Row(String app, long remaining, boolean cooling, long limit) {
        public int progress() { return (int)Math.max(0,Math.min(100,(limit-remaining)*100/limit)); }
    }
    public final Kind kind;
    public final String app;
    public final long remaining, limit;
    public final List<Row> rows;
    private TimerPresentation(Kind kind, String app, long remaining, long limit, List<Row> rows) {
        this.kind = kind; this.app = app; this.remaining = Math.max(0, remaining); this.limit = limit; this.rows = List.copyOf(rows);
    }
    public static TimerPresentation of(RulesEngine e, boolean connected, long wall, long elapsed) {
        if (!e.running || !connected || e.quiet(wall)) return new TimerPresentation(Kind.HIDDEN, null, 0, 1, List.of());
        List<Row> rows = new ArrayList<>();
        for (String pkg : e.selected) {
            long cooldown = e.cooldownRemaining(pkg, wall, elapsed);
            rows.add(new Row(pkg, cooldown > 0 ? cooldown : e.remaining(pkg), cooldown > 0, cooldown > 0 ? RulesEngine.COOLDOWN : e.limit(pkg)));
        }
        var mode = e.mode(wall, elapsed);
        if (mode == RulesEngine.Mode.LUNCH || mode == RulesEngine.Mode.LUNCH_COOLDOWN)
            return new TimerPresentation(mode == RulesEngine.Mode.LUNCH ? Kind.LUNCH : Kind.LUNCH_COOLDOWN, null, e.countdown(wall, elapsed), RulesEngine.COOLDOWN, rows);
        String app = e.focused();
        if (app != null) return new TimerPresentation(Kind.APP, app, e.remaining(app), e.limit(app), rows);
        String next = e.nextAvailableApp(wall, elapsed);
        return new TimerPresentation(e.availableCount(wall, elapsed) == 0 ? Kind.ALL_COOLDOWN : Kind.OVERVIEW,
                next, next == null ? 0 : e.cooldownRemaining(next, wall, elapsed), RulesEngine.COOLDOWN, rows);
    }
    public boolean activeChip() { return kind == Kind.APP; }
    /** Explicit, compact chip content; independent of locale and notification chrono fallback. */
    public String shortCriticalText() {
        if (!activeChip() || remaining <= 0) return "";
        long seconds = (remaining + 999) / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
    public boolean ticking() { return kind != Kind.HIDDEN && remaining > 0; }
    public int progress() { return (int) Math.max(0, Math.min(100, (limit - remaining) * 100 / limit)); }
}
