package app.socialpause.engine;

/** Keeps a dismissal tied to the notification that was actually shown, not its replacement. */
public final class NotificationDismissalPolicy {
    public static final long NO_RUN = -1;
    private static final String PREFIX = "socialpause://notification-dismiss/v1/";
    private NotificationDismissalPolicy() { }

    public record Dismissal(long run, boolean liveRequested) {
        public Dismissal {
            if (run < 0) throw new IllegalArgumentException("A monitoring run is required.");
        }
        /** Android includes Intent data in PendingIntent identity; extras alone are insufficient. */
        public String identity() { return PREFIX + run + (liveRequested ? "/live" : "/ordinary"); }
        public boolean matches(long currentRun) { return run == currentRun; }
        public long suppressedRunAfter(long previousSuppressedRun, long currentRun) {
            return matches(currentRun) && liveRequested ? run : previousSuppressedRun;
        }
    }

    /** Reject legacy/external callbacks without a trustworthy original surface and run. */
    public static Dismissal parse(String identity) {
        if (identity == null || !identity.startsWith(PREFIX)) return null;
        String[] parts = identity.substring(PREFIX.length()).split("/", -1);
        if (parts.length != 2 || !(parts[1].equals("live") || parts[1].equals("ordinary"))) return null;
        try {
            Dismissal result = new Dismissal(Long.parseLong(parts[0]), parts[1].equals("live"));
            return result.identity().equals(identity) ? result : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }

    public static boolean requestsLive(TimerPresentation presentation, long run, long suppressedRun) {
        return presentation.activeChip() && run != suppressedRun;
    }
}
