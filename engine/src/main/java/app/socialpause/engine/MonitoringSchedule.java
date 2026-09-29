package app.socialpause.engine;

/** Window inspection only. Display clocks and phase alarms have separate schedules. */
public final class MonitoringSchedule {
    private boolean recentlySelected;
    public long next(boolean running, boolean unlocked, String resolvedApp, boolean selected) {
        if (!running) { recentlySelected = false; return 0; }
        if (!unlocked) { recentlySelected = false; return 30_000; }
        if (resolvedApp != null) recentlySelected = selected;
        return recentlySelected ? 500 : 2_000;
    }
}
