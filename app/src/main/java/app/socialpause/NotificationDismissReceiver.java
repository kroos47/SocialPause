package app.socialpause;

import android.content.*;
import app.socialpause.engine.NotificationDismissalPolicy;

/** A swipe requests restoration on return to app use; stale notification generations are ignored. */
public final class NotificationDismissReceiver extends BroadcastReceiver {
    static final String ACTION="app.socialpause.NOTIFICATION_DISMISSED";
    @Override public void onReceive(Context c,Intent intent) {
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        var dismissal=NotificationDismissalPolicy.parse(intent.getDataString());
        if(dismissal==null)return;
        AppController controller=AppController.get(c);
        if(controller.engine.running && dismissal.run()==controller.engine.cycleId())
            controller.notificationDismissed(dismissal);
    }
}
