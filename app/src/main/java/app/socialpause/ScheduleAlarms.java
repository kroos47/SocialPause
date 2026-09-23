package app.socialpause;

import android.app.*;
import android.content.*;

final class ScheduleAlarms {
    private final AlarmManager manager;
    private final PendingIntent intent;
    // Unknown until this instance has touched AlarmManager: an older process may have left an alarm.
    private long scheduled = -1;
    private boolean exact;
    ScheduleAlarms(Context c) {
        manager = c.getSystemService(AlarmManager.class);
        intent = PendingIntent.getBroadcast(c, 1, new Intent(c, ScheduleReceiver.class).setAction(ScheduleReceiver.TICK), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    void schedule(long wall) {
        boolean canExact = manager.canScheduleExactAlarms();
        if (exact == canExact && ((wall == 0 && scheduled == 0) || (wall > 0 && scheduled > 0 && Math.abs(scheduled - wall) < 1000))) return;
        scheduled = wall; exact = canExact;
        manager.cancel(intent);
        if (wall == 0) return;
        try {
            if (canExact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wall, intent);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wall, intent);
        } catch (SecurityException revoked) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wall, intent); }
    }
}
