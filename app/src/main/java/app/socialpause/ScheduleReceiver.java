package app.socialpause;

import android.app.AlarmManager;
import android.content.*;
import java.util.Set;

/** Handles only our explicit schedule alarm and the declared system schedule events. */
public final class ScheduleReceiver extends BroadcastReceiver {
    public static final String TICK = "app.socialpause.SCHEDULE_TICK";
    private static final Set<String> ACTIONS = Set.of(TICK, Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (action != null && ACTIONS.contains(action)) AppController.get(c).refresh();
    }
}
