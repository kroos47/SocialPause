package app.socialpause.engine;

import java.util.*;

/** Immutable notification snapshot, including both the shared and individual limits when enabled. */
public final class TimerPresentation {
    public enum Kind { HIDDEN, APP, OVERVIEW, NO_ALLOWANCE, ALL_COOLDOWN, SHARED_COOLDOWN, LUNCH, LUNCH_COOLDOWN }
    public enum RowState { AVAILABLE, USAGE, NO_ALLOWANCE, COOLDOWN }
    public record Row(String app, long remaining, RowState state, long limit) {
        public boolean cooling() { return state == RowState.COOLDOWN; }
        public boolean noAllowance() { return state == RowState.NO_ALLOWANCE; }
        public int progress() { return limit <= 0 ? 0 : (int)Math.max(0,Math.min(100,(limit-remaining)*100/limit)); }
    }
    public final Kind kind;
    public final String app;
    public final long remaining, limit, sharedRemaining, sharedLimit;
    public final boolean sharedLimiting;
    public final List<Row> rows;
    private TimerPresentation(Kind kind, String app, long remaining, long limit, List<Row> rows,
                              long sharedRemaining, long sharedLimit, boolean sharedLimiting) {
        this.kind = kind; this.app = app; this.remaining = Math.max(0, remaining); this.limit = limit;
        this.rows = List.copyOf(rows); this.sharedRemaining = sharedRemaining;
        this.sharedLimit = sharedLimit; this.sharedLimiting = sharedLimiting;
    }
    public static TimerPresentation of(RulesEngine e, boolean connected, long wall, long elapsed) {
        long sharedLimit = e.timerMode() == RulesEngine.TimerMode.SHARED ? e.sharedLimit() : 0;
        long sharedRemaining = sharedLimit > 0 ? e.sharedRemaining() : 0;
        if (!e.running || !connected || e.quiet(wall))
            return new TimerPresentation(Kind.HIDDEN, null, 0, 1, List.of(), sharedRemaining, sharedLimit, false);
        List<Row> rows = new ArrayList<>();
        for (String pkg : e.selected) {
            long cooldown = e.cooldownRemaining(pkg, wall, elapsed);
            RowState state = e.noAllowance(pkg) ? RowState.NO_ALLOWANCE
                    : cooldown > 0 ? RowState.COOLDOWN : e.used(pkg) > 0 ? RowState.USAGE : RowState.AVAILABLE;
            rows.add(new Row(pkg, cooldown > 0 ? cooldown : e.remaining(pkg), state,
                    cooldown > 0 ? RulesEngine.COOLDOWN : e.limit(pkg)));
        }
        var mode = e.mode(wall, elapsed);
        if (mode == RulesEngine.Mode.LUNCH || mode == RulesEngine.Mode.LUNCH_COOLDOWN || mode == RulesEngine.Mode.SHARED_COOLDOWN) {
            Kind kind = switch (mode) {
                case LUNCH -> Kind.LUNCH;
                case LUNCH_COOLDOWN -> Kind.LUNCH_COOLDOWN;
                default -> Kind.SHARED_COOLDOWN;
            };
            return new TimerPresentation(kind, null, e.countdown(wall, elapsed), RulesEngine.COOLDOWN,
                    rows, sharedRemaining, sharedLimit, false);
        }
        String app = e.focused();
        if (app != null) {
            boolean sharedLimiting = sharedLimit > 0 && sharedRemaining <= e.remaining(app);
            return new TimerPresentation(Kind.APP, app, sharedLimiting ? sharedRemaining : e.remaining(app),
                    sharedLimiting ? sharedLimit : e.limit(app), rows, sharedRemaining, sharedLimit, sharedLimiting);
        }
        String next = e.nextAvailableApp(wall, elapsed);
        Kind overview = rows.stream().allMatch(Row::noAllowance) ? Kind.NO_ALLOWANCE
                : rows.stream().allMatch(Row::cooling) ? Kind.ALL_COOLDOWN : Kind.OVERVIEW;
        return new TimerPresentation(overview, next, next == null ? 0 : e.cooldownRemaining(next, wall, elapsed), RulesEngine.COOLDOWN,
                rows, sharedRemaining, sharedLimit, false);
    }
    public boolean activeChip() { return kind == Kind.APP; }
    /** Explicit, compact chip content; independent of locale and notification chrono fallback. */
    public String shortCriticalText() {
        if (!activeChip() || remaining <= 0) return "";
        long seconds = (remaining + 999) / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
    public boolean ticking() { return kind != Kind.HIDDEN && remaining > 0; }
    public int progress() { return limit <= 0 ? 0 : (int) Math.max(0, Math.min(100, (limit - remaining) * 100 / limit)); }
}
