package app.socialpause.engine;

/** Correlates OS startup timestamps with the current process rather than a reused process ID. */
public final class ProcessStartEvidence {
    private ProcessStartEvidence() { }

    public static boolean isCurrent(long requestedUptime, long initializedUptime, long nowUptime,
                                    Long bindNanoseconds, Long onCreateNanoseconds) {
        if (requestedUptime <= 0 || initializedUptime < requestedUptime || nowUptime < initializedUptime)
            return false;
        // Binding precedes Java start-time initialization, so it uses the request lower bound.
        // Application.onCreate is later; both timestamps use the monotonic uptime timebase.
        return between(bindNanoseconds, requestedUptime, nowUptime)
                || between(onCreateNanoseconds, initializedUptime, nowUptime);
    }

    private static boolean between(Long nanoseconds, long fromMillis, long toMillis) {
        if (nanoseconds == null || nanoseconds <= 0) return false;
        long millis = nanoseconds / 1_000_000L;
        return millis >= fromMillis && millis <= toMillis;
    }
}
