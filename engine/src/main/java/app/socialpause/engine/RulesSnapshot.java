package app.socialpause.engine;

import java.util.*;

/** Read-only values captured on the engine's owning thread. No advancement or saving. */
public record RulesSnapshot(long wall, long elapsed, RulesEngine.Mode mode, long phaseDeadline,
                            long stopDeadline, long sharedRemaining, Map<String, App> apps) {
    public record App(long remaining, long used, long cooldownDeadline) {
        public long cooldown(long elapsed) { return Math.max(0, cooldownDeadline - elapsed); }
    }
    public long countdown() { return Math.max(0, phaseDeadline - wall); }
    public long stopRemaining() { return Math.max(0, stopDeadline - elapsed); }
    static RulesSnapshot capture(RulesEngine e, long wall, long elapsed) {
        long pending = e.pendingUsage(wall, elapsed);
        Map<String, App> apps = new LinkedHashMap<>();
        for (String pkg : e.selected) {
            long extra = pkg.equals(e.focused()) ? pending : 0;
            long cooldown = e.cooldownRemaining(pkg, wall, elapsed);
            apps.put(pkg, new App(Math.max(0, e.remaining(pkg) - extra), e.used(pkg) + extra,
                    cooldown > 0 ? elapsed + cooldown : 0));
        }
        return new RulesSnapshot(wall, elapsed, e.mode(wall, elapsed), wall + e.countdown(wall, elapsed),
                elapsed + e.stopLockRemaining(elapsed), Math.max(0, e.sharedRemaining() -
                (e.timerMode() == RulesEngine.TimerMode.SHARED ? pending : 0)), Collections.unmodifiableMap(apps));
    }
}
