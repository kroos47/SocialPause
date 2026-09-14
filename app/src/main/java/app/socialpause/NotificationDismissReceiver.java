package app.socialpause;

import android.content.*;

/** Dismissing the notification affects visibility only, never enforcement. */
public final class NotificationDismissReceiver extends BroadcastReceiver {
    static final String ACTION="app.socialpause.NOTIFICATION_DISMISSED";
    @Override public void onReceive(Context c,Intent intent) {
        if(ACTION.equals(intent.getAction()))c.getSharedPreferences("notification-visibility",Context.MODE_PRIVATE).edit().putLong("dismissed-cycle",intent.getLongExtra("cycle",-1)).apply();
    }
    static boolean dismissed(Context c,long cycle) {return c.getSharedPreferences("notification-visibility",Context.MODE_PRIVATE).getLong("dismissed-cycle",-1)==cycle;}
}
