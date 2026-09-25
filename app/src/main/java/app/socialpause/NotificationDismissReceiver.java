package app.socialpause;

import android.content.*;
import app.socialpause.engine.NotificationDismissalPolicy;

/** Restore only our ordinary timer surface; never re-promote a dismissed live update in this run. */
public final class NotificationDismissReceiver extends BroadcastReceiver {
    static final String ACTION="app.socialpause.NOTIFICATION_DISMISSED";
    @Override public void onReceive(Context c,Intent intent) {
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        var dismissal=NotificationDismissalPolicy.parse(intent.getDataString());
        if(dismissal==null)return;
        AppController controller=AppController.get(c);
        if(controller.engine.running && dismissal.matches(controller.engine.cycleId()))
            controller.notificationDismissed(dismissal.run(),dismissal.liveRequested());
    }
}
