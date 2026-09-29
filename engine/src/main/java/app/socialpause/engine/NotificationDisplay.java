package app.socialpause.engine;

import java.util.*;

/** Cheap value key checked before Android views, labels, styling, and notification builders. */
public record NotificationDisplay(TimerPresentation.Kind kind, String app, boolean sharedLimiting,
                                  long seconds, long limit, long sharedSeconds, long sharedLimit,
                                  List<Row> rows, long generation, boolean awake, long appearance,
                                  long phaseMinute) {
    public record Row(String app, TimerPresentation.RowState state, long seconds, long limit, int progress, long untilMinute) { }
    public static NotificationDisplay of(TimerPresentation p, long generation, boolean awake, long appearance, long wall) {
        List<Row> rows = new ArrayList<>();
        for (var row : p.rows) {
            if (p.activeChip() && !row.app().equals(p.app)) continue;
            long seconds = seconds(row.remaining());
            // Progress changes are published with the displayed second, not a second stream of ticks.
            int progress = row.limit() <= 0 ? 0 : (int)Math.max(0,Math.min(100,(row.limit()-seconds*1000)*100/row.limit()));
            rows.add(new Row(row.app(), row.state(), seconds, row.limit(), progress,
                    row.cooling() ? (wall + row.remaining()) / 60_000 : 0));
        }
        return new NotificationDisplay(p.kind, p.app, p.sharedLimiting, seconds(p.remaining), p.limit,
                seconds(p.sharedRemaining), p.sharedLimit, List.copyOf(rows), generation, awake, appearance,
                p.kind == TimerPresentation.Kind.APP || p.remaining == 0 ? 0 : (wall+p.remaining)/60_000);
    }
    /** State changes bypass the one-per-second clock gate. */
    public boolean sameSurface(NotificationDisplay other) {
        if (other == null || kind != other.kind || !Objects.equals(app,other.app) || sharedLimiting != other.sharedLimiting
                || limit != other.limit || sharedLimit != other.sharedLimit || generation != other.generation
                || awake != other.awake || appearance != other.appearance || rows.size() != other.rows.size()) return false;
        for (int i=0;i<rows.size();i++) {
            Row a=rows.get(i),b=other.rows.get(i);
            if (!a.app.equals(b.app) || a.state != b.state || a.limit != b.limit) return false;
        }
        return true;
    }
    private static long seconds(long ms) { return (Math.max(0,ms)+999)/1000; }
}
