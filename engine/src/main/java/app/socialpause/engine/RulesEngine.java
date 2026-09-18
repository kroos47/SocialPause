package app.socialpause.engine;

import java.io.Serializable;
import java.time.*;
import java.util.*;

/** Main-thread state machine. Elapsed time owns app timers; local wall time owns schedules. */
public final class RulesEngine implements Serializable {
    // Keep compatible with v0.1/v0.2 to preserve settings and history during migration.
    private static final long serialVersionUID = 1L;
    public static final long MINUTE = 60_000L, APP_LIMIT = 10 * MINUTE,
            INSTAGRAM_LIMIT = 7 * MINUTE, COOLDOWN = 60 * MINUTE;
    public enum Mode { STOPPED, READY, ACTIVE, LUNCH, LUNCH_COOLDOWN }
    public final Set<String> selected = new LinkedHashSet<>(List.of("com.instagram.android", "com.twitter.android", "com.reddit.frontpage"));
    private final Map<String, Long> usage = new HashMap<>();
    private Map<String, Long> cooldowns = new HashMap<>();
    private int stateVersion = 3;
    private long cycleSerial;
    private UsageHistory history;
    public boolean running, lunchEnabled = true, sleepEnabled = true;
    public int lunchMinute = 840, sleepStart = 1320, sleepEnd = 600;
    private long handledLunch = Long.MIN_VALUE, activeLunch = Long.MIN_VALUE;
    private long pendingAt;
    private int pendingMinute;
    private boolean pendingEnabled;
    private transient long cursor = -1;
    private transient String focused;
    private transient ZoneId zone = ZoneId.systemDefault();

    public void attach(boolean sameBoot) {
        cursor = -1; focused = null; zone = ZoneId.systemDefault();
        if (cooldowns == null) cooldowns = new HashMap<>();
        if (stateVersion < 3) { reset(); stateVersion = 3; } // Exactly one fresh-allowance upgrade.
        if (!sameBoot) { reset(); handledLunch = Long.MIN_VALUE; activeLunch = Long.MIN_VALUE; }
    }
    public void start(long wall, long elapsed) {
        reset(); cycleSerial++; running = true; focused = null; cursor = elapsed; settle(wall, elapsed);
    }
    public void stop(long wall, long elapsed) { advance(wall, elapsed); running = false; focused = null; }
    private void reset() { usage.clear(); cooldowns.clear(); }
    /** Identifies a manual monitoring run, not an individual app cycle. */
    public long cycleId() { return cycleSerial; }
    public UsageHistory history() { if (history == null) history = new UsageHistory(); return history; }
    public long limit(String pkg) { return "com.instagram.android".equals(pkg) ? INSTAGRAM_LIMIT : APP_LIMIT; }
    public long used(String pkg) { return usage.getOrDefault(pkg, 0L); }
    public long remaining(String pkg) { return Math.max(0, limit(pkg) - used(pkg)); }
    public long cooldownRemaining(String pkg, long wall, long elapsed) {
        if (!running || !selected.contains(pkg) || mode(wall, elapsed) == Mode.LUNCH) return 0;
        if (mode(wall, elapsed) == Mode.LUNCH_COOLDOWN) return countdown(wall, elapsed);
        return Math.max(0, cooldowns.getOrDefault(pkg, 0L) - elapsed);
    }
    public String nextAvailableApp(long wall, long elapsed) {
        String result = null; long least = Long.MAX_VALUE;
        for (String pkg : selected) {
            long left = cooldownRemaining(pkg, wall, elapsed);
            if (left > 0 && left < least) { least = left; result = pkg; }
        }
        return result;
    }
    public int availableCount(long wall, long elapsed) {
        int count = 0; for (String pkg : selected) if (!blocked(pkg, wall, elapsed)) count++; return count;
    }
    public String focused() { return focused; }
    public long pendingAt() { return pendingAt; }
    public void select(Set<String> packages) {
        if (packages.isEmpty()) throw new IllegalArgumentException("Select at least one app.");
        if (running) throw new IllegalStateException("Stop tracking before changing apps.");
        selected.clear(); selected.addAll(packages); reset(); focused = null;
    }
    public void sleep(boolean enabled, int start, int end) {
        checkMinute(start); checkMinute(end);
        if (start == end) throw new IllegalArgumentException("Sleep start and end must differ.");
        sleepEnabled = enabled; sleepStart = start; sleepEnd = end;
    }
    public void lunch(boolean enabled, int minute, long wall, long elapsed) {
        checkMinute(minute); advance(wall, elapsed); pendingEnabled = enabled; pendingMinute = minute;
        pendingAt = Instant.ofEpochMilli(wall).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        if (activeLunch != Long.MIN_VALUE) pendingAt = Math.max(pendingAt, activeLunch + 2 * COOLDOWN);
    }
    private static void checkMinute(int value) { if (value < 0 || value >= 1440) throw new IllegalArgumentException("Invalid time."); }
    public boolean quiet(long wall) {
        var t = Instant.ofEpochMilli(wall).atZone(zone); int m = t.getHour() * 60 + t.getMinute();
        return sleepEnabled && (sleepStart < sleepEnd ? m >= sleepStart && m < sleepEnd : m >= sleepStart || m < sleepEnd);
    }
    private long latestLunch(long wall) {
        var now = Instant.ofEpochMilli(wall).atZone(zone);
        var start = now.toLocalDate().atTime(lunchMinute / 60, lunchMinute % 60).atZone(zone);
        if (start.toInstant().toEpochMilli() > wall) start = start.minusDays(1);
        return start.toInstant().toEpochMilli();
    }
    private void settle(long wall, long elapsed) {
        if (pendingAt > 0 && wall >= pendingAt) {
            lunchEnabled = pendingEnabled; lunchMinute = pendingMinute;
            handledLunch = latestLunch(pendingAt - 1); pendingAt = 0;
        }
        if (!running) return;
        if (lunchEnabled) {
            long latest = latestLunch(wall);
            if (latest > handledLunch) { handledLunch = latest; reset(); activeLunch = latest; }
        }
        if (activeLunch != Long.MIN_VALUE && wall >= activeLunch + 2 * COOLDOWN) {
            activeLunch = Long.MIN_VALUE; reset(); focused = null;
        }
        // Finishing one app's cooldown must never clear another app's current focus.
        var iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (elapsed >= entry.getValue()) { usage.remove(entry.getKey()); iterator.remove(); }
        }
        if (mode(wall, elapsed) == Mode.LUNCH_COOLDOWN) focused = null;
    }
    public Mode mode(long wall, long elapsed) {
        if (!running) return Mode.STOPPED;
        if (activeLunch != Long.MIN_VALUE && wall >= activeLunch && wall < activeLunch + COOLDOWN) return Mode.LUNCH;
        if (activeLunch != Long.MIN_VALUE && wall >= activeLunch + COOLDOWN && wall < activeLunch + 2 * COOLDOWN) return Mode.LUNCH_COOLDOWN;
        return focused != null ? Mode.ACTIVE : Mode.READY;
    }
    /** Only global schedule countdowns live here. Usage/cooldown is always queried by app. */
    public long countdown(long wall, long elapsed) {
        Mode m = mode(wall, elapsed);
        if (m == Mode.LUNCH) return activeLunch + COOLDOWN - wall;
        if (m == Mode.LUNCH_COOLDOWN) return activeLunch + 2 * COOLDOWN - wall;
        return 0;
    }
    public boolean blocked(String pkg, long wall, long elapsed) {
        return running && selected.contains(pkg) && mode(wall, elapsed) != Mode.LUNCH
                && (mode(wall, elapsed) == Mode.LUNCH_COOLDOWN || cooldowns.getOrDefault(pkg, 0L) > elapsed);
    }
    public void focus(String pkg, boolean unlocked, long wall, long elapsed) {
        advance(wall, elapsed);
        focused = running && unlocked && selected.contains(pkg) && !blocked(pkg, wall, elapsed) ? pkg : null;
    }
    public void advance(long wall, long elapsed) {
        zone = ZoneId.systemDefault();
        if (cursor < 0 || elapsed < cursor) cursor = elapsed;
        while (cursor < elapsed) {
            long w = wall - (elapsed - cursor); settle(w, cursor);
            long step = Math.min(elapsed - cursor, nextBoundary(w, cursor) - w);
            boolean count = mode(w, cursor) == Mode.ACTIVE && focused != null;
            if (count) step = Math.min(step, remaining(focused));
            if (step <= 0) throw new IllegalStateException("Timing boundary did not advance.");
            if (count) {
                String app = focused;
                history().record(app, w, step, zone);
                usage.put(app, used(app) + step);
                if (remaining(app) == 0) { cooldowns.put(app, cursor + step + COOLDOWN); focused = null; }
            }
            cursor += step;
        }
        settle(wall, elapsed);
    }
    public long nextBoundary(long wall, long elapsed) {
        long next = wall + 24 * 60 * MINUTE;
        if (pendingAt > wall) next = Math.min(next, pendingAt);
        if (lunchEnabled) {
            long tomorrow = Instant.ofEpochMilli(latestLunch(wall)).atZone(zone).plusDays(1).toInstant().toEpochMilli();
            if (tomorrow > wall) next = Math.min(next, tomorrow);
        }
        if (activeLunch != Long.MIN_VALUE) {
            if (activeLunch + COOLDOWN > wall) next = Math.min(next, activeLunch + COOLDOWN);
            if (activeLunch + 2 * COOLDOWN > wall) next = Math.min(next, activeLunch + 2 * COOLDOWN);
        }
        for (long end : cooldowns.values()) if (end > elapsed) next = Math.min(next, wall + end - elapsed);
        if (sleepEnabled) for (int minute : new int[]{sleepStart, sleepEnd}) {
            var now = Instant.ofEpochMilli(wall).atZone(zone);
            var t = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(zone);
            if (t.toInstant().toEpochMilli() <= wall) t = t.plusDays(1);
            next = Math.min(next, t.toInstant().toEpochMilli());
        }
        return next;
    }
}
