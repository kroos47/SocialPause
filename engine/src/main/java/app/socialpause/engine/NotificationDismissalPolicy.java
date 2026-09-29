package app.socialpause.engine;

/** Notification lifetimes are independent of usage cycles. A swipe never resets a timer. */
public final class NotificationDismissalPolicy {
    private static final String PREFIX = "socialpause://notification-dismiss/v2/";
    private NotificationDismissalPolicy() { }

    public record Dismissal(long run, long generation, boolean liveRequested) {
        public Dismissal {
            if (run < 0 || generation < 1) throw new IllegalArgumentException("Notification provenance is required.");
        }
        /** Data, unlike extras, is part of Android PendingIntent identity. */
        public String identity() { return PREFIX + run + "/" + generation + (liveRequested ? "/live" : "/ordinary"); }
    }
    public static Dismissal parse(String identity) {
        if (identity == null || !identity.startsWith(PREFIX)) return null;
        String[] parts = identity.substring(PREFIX.length()).split("/", -1);
        if (parts.length != 3 || !(parts[2].equals("live") || parts[2].equals("ordinary"))) return null;
        try {
            Dismissal result = new Dismissal(Long.parseLong(parts[0]), Long.parseLong(parts[1]), parts[2].equals("live"));
            return result.identity().equals(identity) ? result : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }

    public record Publication(Dismissal dismissal, boolean replace) { }

    /** Main-thread state machine; only the generation and pending return survive recreation. */
    public static final class State {
        private long generation;
        private boolean awaitingReturn;
        private Dismissal current;
        private String surface;
        private boolean dismissed;
        public State(long generation, boolean awaitingReturn) {
            this.generation = Math.max(0, generation); this.awaitingReturn = awaitingReturn;
        }
        public long generation() { return generation; }
        public boolean awaitingReturn() { return awaitingReturn; }
        public boolean dismiss(Dismissal event) {
            if (event == null || !event.equals(current) || dismissed) return false;
            dismissed = true; awaitingReturn = true; return true;
        }
        public void hidden() { current = null; surface = null; dismissed = false; }
        public void newRun() { hidden(); awaitingReturn = false; }
        public Publication prepare(long run, TimerPresentation p, boolean appVisible) {
            if (current != null && current.run() != run) newRun();
            String nextSurface = p.kind + ":" + p.app + ":" + p.sharedLimiting;
            boolean replace = awaitingReturn && p.activeChip() && appVisible;
            boolean live = p.activeChip() && (!awaitingReturn || appVisible);
            if (current == null || current.run() != run || !nextSurface.equals(surface)
                    || current.liveRequested() != live || dismissed || replace) {
                current = new Dismissal(run, Math.incrementExact(generation), live);
                generation = current.generation(); surface = nextSurface; dismissed = false;
            }
            if (replace) awaitingReturn = false;
            return new Publication(current, replace);
        }
    }
}
