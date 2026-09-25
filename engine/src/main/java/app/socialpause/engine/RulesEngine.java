package app.socialpause.engine;

import java.io.Serializable;
import java.time.*;
import java.util.*;

/** Main-thread state machine. Elapsed time owns usage/cooldowns; local wall time owns lunch schedules. */
public final class RulesEngine implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final long MINUTE = 60_000L, APP_LIMIT = 10 * MINUTE,
            INSTAGRAM_LIMIT = 7 * MINUTE, COOLDOWN = 60 * MINUTE,
            DEFAULT_SHARED_LIMIT = 20 * MINUTE, STOP_LOCK = 6 * COOLDOWN;
    public enum Mode { STOPPED, READY, ACTIVE, SHARED_COOLDOWN, LUNCH, LUNCH_COOLDOWN }
    public enum TimerMode { INDIVIDUAL, SHARED }
    public final Set<String> selected = new LinkedHashSet<>(List.of("com.instagram.android", "com.twitter.android", "com.reddit.frontpage"));
    private final Map<String, Long> usage = new HashMap<>();
    private Map<String, Long> cooldowns = new HashMap<>();
    private int stateVersion = 6;
    private long cycleSerial;
    // Monotonic deadline for the main Stop button; independent of allowance/lunch resets.
    private long stopUnlockElapsed;
    private UsageHistory history;
    public boolean running, lunchEnabled = true, sleepEnabled = true;
    public int lunchMinute = 840, sleepStart = 1320, sleepEnd = 600;
    private long handledLunch = Long.MIN_VALUE, activeLunch = Long.MIN_VALUE;
    private long pendingAt;
    private int pendingMinute;
    private boolean pendingEnabled;
    private TimerMode timerMode = TimerMode.INDIVIDUAL;
    // Keep the old serialized sharedLimit field as the active cycle's budget. In an
    // upgraded running cycle it may exceed the new setting range until the next reset.
    private long sharedLimit = DEFAULT_SHARED_LIMIT, sharedUsed, sharedCooldownEnd;
    private long configuredSharedLimit = DEFAULT_SHARED_LIMIT;
    private Map<String, Long> appLimits = new HashMap<>();
    // Explicit endpoints support an early lunch stop without changing its start-day identity.
    private long lunchEnd, lunchCooldownEnd;
    private boolean manualOrigin;
    private long manualLunchDay = Long.MIN_VALUE, suppressedAutoDay = Long.MIN_VALUE,
            lastLunchDay = Long.MIN_VALUE;
    private transient long cursor = -1;
    private transient String focused;
    private transient ZoneId zone = ZoneId.systemDefault();

    public void attach(boolean sameBoot) {
        cursor = -1; focused = null; zone = ZoneId.systemDefault();
        if (cooldowns == null) cooldowns = new HashMap<>();
        if (stateVersion < 3) reset(); // Preserve the original pre-v0.3 one-time migration.
        if (stateVersion < 4) {
            // Java deserialization does not run field initializers for newly added fields.
            timerMode = TimerMode.INDIVIDUAL; sharedLimit = DEFAULT_SHARED_LIMIT;
            sharedUsed = 0; sharedCooldownEnd = 0;
            manualLunchDay = suppressedAutoDay = Long.MIN_VALUE;
            lastLunchDay = handledLunch == Long.MIN_VALUE ? Long.MIN_VALUE : day(handledLunch);
            if (activeLunch != Long.MIN_VALUE) {
                lunchEnd = activeLunch + COOLDOWN; lunchCooldownEnd = lunchEnd + COOLDOWN;
                lastLunchDay = Math.max(lastLunchDay, day(activeLunch));
            }
            manualOrigin = false; stateVersion = 4;
        }
        if (stateVersion < 5) {
            appLimits = new HashMap<>(); // Missing entries retain the original 7/10-minute defaults.
            configuredSharedLimit = Math.max(MINUTE, Math.min(30 * MINUTE, sharedLimit));
            if (!running || timerMode != TimerMode.SHARED) sharedLimit = configuredSharedLimit;
            stateVersion = 5;
        }
        if (stateVersion < 6) {
            // Existing runs remain stoppable. Only the next explicit Start creates a lock.
            stopUnlockElapsed = 0;
            stateVersion = 6;
        }
        // Reboot may refresh elapsed-time allowances, but never grants a second manual lunch
        // or discards a wall-time lunch/post-lunch phase already in progress. Monitoring
        // stays stopped until Start; elapsed deadlines cannot be carried across boots.
        if (!sameBoot) { reset(); running = false; stopUnlockElapsed = 0; }
    }
    public void start(long wall, long elapsed) {
        if (running) return;
        reset(); cycleSerial++; running = true; stopUnlockElapsed = elapsed + STOP_LOCK;
        focused = null; cursor = elapsed; settle(wall, elapsed);
    }
    /** Main user Stop. Platform permission/recovery paths must explicitly use systemStop. */
    public void stop(long wall, long elapsed) {
        if (!running) return;
        if (!canStop(elapsed)) throw new IllegalStateException("Stop is locked for six hours after Start.");
        systemStop(wall, elapsed);
    }
    /** Stop after confirmed permission loss or an explicit platform stop, regardless of the UI lock. */
    public void systemStop(long wall, long elapsed) {
        advance(wall, elapsed); running = false; focused = null; stopUnlockElapsed = 0;
    }
    public long stopLockRemaining(long elapsed) { return running ? Math.max(0, stopUnlockElapsed - elapsed) : 0; }
    public boolean canStop(long elapsed) { return running && stopLockRemaining(elapsed) == 0; }
    private void reset() {
        usage.clear(); cooldowns.clear(); sharedUsed = 0; sharedCooldownEnd = 0;
        sharedLimit = configuredSharedLimit;
    }
    public long cycleId() { return cycleSerial; }
    public UsageHistory history() { if (history == null) history = new UsageHistory(); return history; }
    public TimerMode timerMode() { return timerMode; }
    public void setTimerMode(TimerMode value) {
        requireStopped(); timerMode = Objects.requireNonNull(value); reset();
    }
    /** Effective budget for the current cycle, including an uninterrupted pre-v0.6 cycle. */
    public long sharedLimit() { return sharedLimit; }
    /** Saved budget to use at the next whole-cycle reset. */
    public long configuredSharedLimit() { return configuredSharedLimit; }
    public void setSharedLimit(long value) {
        requireStopped();
        if (timerMode != TimerMode.SHARED) throw new IllegalStateException("Select Shared mode to change its allowance.");
        if (value < MINUTE || value > 30 * MINUTE || value % MINUTE != 0)
            throw new IllegalArgumentException("Shared allowance must be 1–30 minutes in one-minute steps.");
        configuredSharedLimit = value; reset();
    }
    private void requireStopped() { if (running) throw new IllegalStateException("Stop tracking before changing allowances or apps."); }
    public long sharedRemaining() { return Math.max(0, sharedLimit - sharedUsed); }
    public long sharedCooldownRemaining(long elapsed) {
        return running && timerMode == TimerMode.SHARED ? Math.max(0, sharedCooldownEnd - elapsed) : 0;
    }
    public long maximumLimit(String pkg) { return "com.instagram.android".equals(pkg) ? INSTAGRAM_LIMIT : 12 * MINUTE; }
    public long limit(String pkg) {
        return appLimits.getOrDefault(pkg, "com.instagram.android".equals(pkg) ? INSTAGRAM_LIMIT : APP_LIMIT);
    }
    public void setAppLimit(String pkg, long value) {
        requireStopped(); Objects.requireNonNull(pkg);
        if (pkg.isBlank()) throw new IllegalArgumentException("An app package is required.");
        if (value < 0 || value > maximumLimit(pkg) || value % MINUTE != 0)
            throw new IllegalArgumentException("App allowance must be whole minutes within its supported range.");
        appLimits.put(pkg, value); reset();
    }
    public boolean noAllowance(String pkg) { return limit(pkg) == 0; }
    public long used(String pkg) { return usage.getOrDefault(pkg, 0L); }
    public long remaining(String pkg) { return Math.max(0, limit(pkg) - used(pkg)); }
    public long cooldownRemaining(String pkg, long wall, long elapsed) {
        if (!running || !selected.contains(pkg) || noAllowance(pkg)) return 0;
        Mode current = mode(wall, elapsed);
        if (current == Mode.LUNCH) return 0;
        if (current == Mode.LUNCH_COOLDOWN) return countdown(wall, elapsed);
        if (current == Mode.SHARED_COOLDOWN) return sharedCooldownRemaining(elapsed);
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
    public int pendingLunchMinute() { return pendingMinute; }
    public boolean pendingLunchEnabled() { return pendingEnabled; }
    public void select(Set<String> packages) {
        if (packages.isEmpty()) throw new IllegalArgumentException("Select at least one app.");
        requireStopped(); selected.clear(); selected.addAll(packages); reset(); focused = null;
    }
    public void sleep(boolean enabled, int start, int end) {
        checkMinute(start); checkMinute(end);
        if (start == end) throw new IllegalArgumentException("Sleep start and end must differ.");
        sleepEnabled = enabled; sleepStart = start; sleepEnd = end;
    }
    public void lunch(boolean enabled, int minute, long wall, long elapsed) {
        checkMinute(minute); advance(wall, elapsed);
        LocalDate today = date(wall);
        long oldStart = at(today, lunchMinute), proposedStart = at(today, minute);
        if (wall < oldStart && wall < proposedStart && lastLunchDay != today.toEpochDay()) {
            lunchEnabled = enabled; lunchMinute = minute; pendingAt = 0;
            // Changing a future schedule must not replay yesterday's occurrence or reset usage.
            handledLunch = Math.max(handledLunch, latestLunch(wall));
        } else {
            pendingEnabled = enabled; pendingMinute = minute;
            pendingAt = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            if (activeLunch != Long.MIN_VALUE) pendingAt = Math.max(pendingAt, lunchCooldownEnd);
        }
    }
    public boolean manualLunchUsedToday(long wall) { return manualLunchDay == day(wall); }
    public boolean manualLunchAvailable(long wall) { return running && !manualLunchUsedToday(wall); }
    public boolean isManualLunch() { return activeLunch != Long.MIN_VALUE && manualOrigin; }
    public void startManualLunch(long wall, long elapsed) {
        advance(wall, elapsed);
        if (!manualLunchAvailable(wall)) throw new IllegalStateException("Manual lunch is available once per day while tracking.");
        manualLunchDay = day(wall); suppressedAutoDay = manualLunchDay;
        beginLunch(wall, true);
    }
    public void stopLunch(long wall, long elapsed) {
        advance(wall, elapsed);
        if (mode(wall, elapsed) != Mode.LUNCH) throw new IllegalStateException("No lunch break is active.");
        lunchEnd = wall; lunchCooldownEnd = wall + COOLDOWN; focused = null;
    }
    /** The next eligible automatic occurrence; skipped overlaps are never replayed. */
    public long nextScheduledLunch(long wall) {
        long result = Long.MAX_VALUE;
        long effectivePending = pendingAt;
        if (effectivePending > 0 && activeLunch != Long.MIN_VALUE)
            effectivePending = Math.max(effectivePending, lunchCooldownEnd);
        LocalDate today = date(wall);
        for (int offset = 0; offset < 4; offset++) {
            LocalDate date = today.plusDays(offset);
            long old = at(date, lunchMinute);
            if (lunchEnabled && (effectivePending == 0 || old < effectivePending) && eligibleScheduled(old, wall))
                result = Math.min(result, old);
            long updated = at(date, pendingMinute);
            if (effectivePending > 0 && pendingEnabled && updated >= effectivePending && eligibleScheduled(updated, wall))
                result = Math.min(result, updated);
        }
        return result == Long.MAX_VALUE ? 0 : result;
    }
    private boolean eligibleScheduled(long candidate, long wall) {
        return candidate > wall && candidate > handledLunch && day(candidate) != suppressedAutoDay
                && !(activeLunch != Long.MIN_VALUE && candidate >= activeLunch && candidate < lunchCooldownEnd);
    }
    private static void checkMinute(int value) { if (value < 0 || value >= 1440) throw new IllegalArgumentException("Invalid time."); }
    private LocalDate date(long wall) { return Instant.ofEpochMilli(wall).atZone(zone).toLocalDate(); }
    private long day(long wall) { return date(wall).toEpochDay(); }
    private long at(LocalDate day, int minute) { return day.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli(); }
    public boolean quiet(long wall) {
        var t = Instant.ofEpochMilli(wall).atZone(zone); int m = t.getHour() * 60 + t.getMinute();
        return sleepEnabled && (sleepStart < sleepEnd ? m >= sleepStart && m < sleepEnd : m >= sleepStart || m < sleepEnd);
    }
    private long latestLunch(long wall) {
        LocalDate today = date(wall); long start = at(today, lunchMinute);
        return start > wall ? at(today.minusDays(1), lunchMinute) : start;
    }
    private void beginLunch(long start, boolean manual) {
        reset(); focused = null; activeLunch = start; lunchEnd = start + COOLDOWN;
        lunchCooldownEnd = lunchEnd + COOLDOWN; manualOrigin = manual; lastLunchDay = day(start);
    }
    private void finishLunch() {
        activeLunch = Long.MIN_VALUE; lunchEnd = lunchCooldownEnd = 0; manualOrigin = false;
        reset(); focused = null;
    }
    private void settle(long wall, long elapsed) {
        if (pendingAt > 0 && wall >= pendingAt) {
            if (activeLunch != Long.MIN_VALUE && wall < lunchCooldownEnd) pendingAt = lunchCooldownEnd;
            else {
                lunchEnabled = pendingEnabled; lunchMinute = pendingMinute;
                handledLunch = Math.max(handledLunch, latestLunch(pendingAt - 1)); pendingAt = 0;
            }
        }
        if (!running) return;
        if (lunchEnabled) {
            long latest = latestLunch(wall);
            if (latest > handledLunch) {
                handledLunch = latest;
                // Compare the scheduled start against the persisted previous interval before
                // clearing it. Process recovery after that interval must not replay an overlap.
                if (day(latest) != suppressedAutoDay && (activeLunch == Long.MIN_VALUE || latest >= lunchCooldownEnd)) {
                    if (activeLunch != Long.MIN_VALUE) finishLunch();
                    beginLunch(latest, false);
                }
            }
        }
        if (activeLunch != Long.MIN_VALUE && wall >= lunchCooldownEnd) finishLunch();
        if (sharedCooldownEnd > 0 && elapsed >= sharedCooldownEnd) { reset(); focused = null; }
        var iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (elapsed >= entry.getValue()) { usage.remove(entry.getKey()); iterator.remove(); }
        }
        Mode current = mode(wall, elapsed);
        if (current == Mode.LUNCH_COOLDOWN || current == Mode.SHARED_COOLDOWN) focused = null;
    }
    public Mode mode(long wall, long elapsed) {
        if (!running) return Mode.STOPPED;
        if (activeLunch != Long.MIN_VALUE && wall >= activeLunch && wall < lunchEnd) return Mode.LUNCH;
        if (activeLunch != Long.MIN_VALUE && wall >= lunchEnd && wall < lunchCooldownEnd) return Mode.LUNCH_COOLDOWN;
        if (sharedCooldownRemaining(elapsed) > 0) return Mode.SHARED_COOLDOWN;
        return focused != null ? Mode.ACTIVE : Mode.READY;
    }
    /** Remaining time in the current global lunch or shared-cooldown phase. */
    public long countdown(long wall, long elapsed) {
        Mode m = mode(wall, elapsed);
        if (m == Mode.LUNCH) return lunchEnd - wall;
        if (m == Mode.LUNCH_COOLDOWN) return lunchCooldownEnd - wall;
        if (m == Mode.SHARED_COOLDOWN) return sharedCooldownRemaining(elapsed);
        return 0;
    }
    public boolean blocked(String pkg, long wall, long elapsed) {
        return running && selected.contains(pkg) && mode(wall, elapsed) != Mode.LUNCH
                && (noAllowance(pkg) || cooldownRemaining(pkg, wall, elapsed) > 0);
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
            if (count) {
                step = Math.min(step, remaining(focused));
                if (timerMode == TimerMode.SHARED) step = Math.min(step, sharedRemaining());
            }
            if (step <= 0) throw new IllegalStateException("Timing boundary did not advance.");
            if (count) {
                String app = focused;
                history().record(app, w, step, zone); usage.put(app, used(app) + step);
                if (timerMode == TimerMode.SHARED) sharedUsed += step;
                if (remaining(app) == 0) { cooldowns.put(app, cursor + step + COOLDOWN); focused = null; }
                if (timerMode == TimerMode.SHARED && sharedRemaining() == 0) {
                    sharedCooldownEnd = cursor + step + COOLDOWN; focused = null;
                }
            }
            cursor += step;
        }
        settle(wall, elapsed);
    }
    public long nextBoundary(long wall, long elapsed) {
        long next = wall + 24 * 60 * MINUTE;
        if (pendingAt > wall) next = Math.min(next, pendingAt);
        if (lunchEnabled) {
            long tomorrow = at(date(latestLunch(wall)).plusDays(1), lunchMinute);
            if (tomorrow > wall) next = Math.min(next, tomorrow);
        }
        if (activeLunch != Long.MIN_VALUE) {
            if (lunchEnd > wall) next = Math.min(next, lunchEnd);
            if (lunchCooldownEnd > wall) next = Math.min(next, lunchCooldownEnd);
        }
        if (sharedCooldownEnd > elapsed) next = Math.min(next, wall + sharedCooldownEnd - elapsed);
        for (long end : cooldowns.values()) if (end > elapsed) next = Math.min(next, wall + end - elapsed);
        if (sleepEnabled) for (int minute : new int[]{sleepStart, sleepEnd}) {
            LocalDate today = date(wall); long t = at(today, minute);
            if (t <= wall) t = at(today.plusDays(1), minute);
            next = Math.min(next, t);
        }
        return next;
    }
}
