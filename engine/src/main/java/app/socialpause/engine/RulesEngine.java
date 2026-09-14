package app.socialpause.engine;

import java.io.Serializable;
import java.time.*;
import java.util.*;

/** Pure rules, owned by one thread. Usage uses elapsed time; schedules use local wall time. */
public final class RulesEngine implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final long MINUTE = 60_000L, APP_LIMIT = 10 * MINUTE,
            TOTAL_LIMIT = 20 * MINUTE, COOLDOWN = 60 * MINUTE;
    public enum Mode { STOPPED, READY, ACTIVE, LUNCH, COOLDOWN }
    public final Set<String> selected = new LinkedHashSet<>(List.of("com.instagram.android", "com.twitter.android", "com.reddit.frontpage"));
    private final Map<String, Long> usage = new HashMap<>();
    private long cycleSerial;
    private UsageHistory history; // Null in v0.1 saved state; initialized lazily without losing budgets.
    public boolean running, lunchEnabled = true, sleepEnabled = true;
    public int lunchMinute = 840, sleepStart = 1320, sleepEnd = 600;
    private long cooldownEnd, handledLunch = Long.MIN_VALUE, activeLunch = Long.MIN_VALUE;
    private long pendingAt;
    private int pendingMinute;
    private boolean pendingEnabled;
    private transient long cursor = -1;
    private transient String focused;
    private transient ZoneId zone = ZoneId.systemDefault();

    public void attach(boolean sameBoot) {
        cursor = -1; focused = null; zone = ZoneId.systemDefault();
        if (!sameBoot) { reset(); handledLunch = Long.MIN_VALUE; activeLunch = Long.MIN_VALUE; }
    }
    public void start(long wall, long elapsed) {
        reset(); running = true; focused = null; cursor = elapsed; settle(wall, elapsed);
    }
    public void stop(long wall, long elapsed) { advance(wall, elapsed); running = false; focused = null; }
    private void reset() { usage.clear(); cooldownEnd = 0; cycleSerial++; }
    public long cycleId() { return cycleSerial; }
    public UsageHistory history() {
        if (history == null) history = new UsageHistory();
        return history;
    }
    public long used(String pkg) { return usage.getOrDefault(pkg, 0L); }
    public long total() { return usage.values().stream().mapToLong(Long::longValue).sum(); }
    public long remaining(String pkg) { return Math.max(0, APP_LIMIT - used(pkg)); }
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
    private static void checkMinute(int value) {
        if (value < 0 || value >= 1440) throw new IllegalArgumentException("Invalid time.");
    }
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
            // Do not invent yesterday's lunch when tomorrow's edit is applied.
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
        if (cooldownEnd > 0 && elapsed >= cooldownEnd) { reset(); focused = null; }
        if (mode(wall, elapsed) == Mode.COOLDOWN) focused = null;
    }
    public Mode mode(long wall, long elapsed) {
        if (!running) return Mode.STOPPED;
        if (activeLunch != Long.MIN_VALUE && wall >= activeLunch && wall < activeLunch + COOLDOWN) return Mode.LUNCH;
        if ((activeLunch != Long.MIN_VALUE && wall >= activeLunch + COOLDOWN && wall < activeLunch + 2 * COOLDOWN) || cooldownEnd > elapsed) return Mode.COOLDOWN;
        return focused != null ? Mode.ACTIVE : Mode.READY;
    }
    public long countdown(long wall, long elapsed) {
        Mode m = mode(wall, elapsed);
        if (m == Mode.LUNCH) return activeLunch + COOLDOWN - wall;
        if (m == Mode.COOLDOWN) return activeLunch != Long.MIN_VALUE ? Math.max(0, activeLunch + 2 * COOLDOWN - wall) : Math.max(0, cooldownEnd - elapsed);
        return Math.min(TOTAL_LIMIT - total(), focused == null ? TOTAL_LIMIT : remaining(focused));
    }
    public boolean blocked(String pkg, long wall, long elapsed) {
        if (!running || !selected.contains(pkg) || mode(wall, elapsed) == Mode.LUNCH) return false;
        return mode(wall, elapsed) == Mode.COOLDOWN || remaining(pkg) == 0 || total() >= TOTAL_LIMIT;
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
            if (count) step = Math.min(step, Math.min(remaining(focused), TOTAL_LIMIT - total()));
            if (step <= 0) throw new IllegalStateException("Timing boundary did not advance.");
            if (count) {
                history().record(focused, w, step, zone);
                usage.put(focused, used(focused) + step);
                if (total() >= TOTAL_LIMIT) { cooldownEnd = cursor + step + COOLDOWN; focused = null; }
                else if (remaining(focused) == 0) focused = null;
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
        if (cooldownEnd > elapsed) next = Math.min(next, wall + cooldownEnd - elapsed);
        if (sleepEnabled) for (int minute : new int[]{sleepStart, sleepEnd}) {
            var now = Instant.ofEpochMilli(wall).atZone(zone);
            var t = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(zone);
            if (t.toInstant().toEpochMilli() <= wall) t = t.plusDays(1);
            next = Math.min(next, t.toInstant().toEpochMilli());
        }
        return next;
    }
}
