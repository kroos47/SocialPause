package app.socialpause;

import android.content.*;

/** Restore only our ordinary timer surface; never re-promote a dismissed live update in this run. */
public final class NotificationDismissReceiver extends BroadcastReceiver {
    static final String ACTION="app.socialpause.NOTIFICATION_DISMISSED";
    @Override public void onReceive(Context c,Intent intent) {
        if(!ACTION.equals(intent.getAction()))return;
        AppController controller=AppController.get(c);
        if(intent.getLongExtra("run",-1)==controller.engine.cycleId())controller.notificationDismissed();
    }
}
