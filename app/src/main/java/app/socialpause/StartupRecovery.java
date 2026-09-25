package app.socialpause;

import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationStartInfo;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import app.socialpause.engine.ProcessStartEvidence;

/** Reconcile only positive evidence about this process, never a generic process death. */
final class StartupRecovery {
    enum Result { UNKNOWN, FORCE_STOPPED, ORDINARY }
    private StartupRecovery() {}

    static Result detect(Context context) {
        if (Build.VERSION.SDK_INT < 35) return Result.ORDINARY;
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) return Result.UNKNOWN;
        try {
            // Read system evidence only. addStartInfoTimestamp writes the package's newest
            // historical entry, so an app-written marker cannot prove a record is current.
            long requested = Process.getStartRequestedUptimeMillis();
            long started = Process.getStartUptimeMillis();
            long now = SystemClock.uptimeMillis();
            for (ApplicationStartInfo info : manager.getHistoricalProcessStartReasons(10)) {
                if (info.getPid() == Process.myPid()
                        && Application.getProcessName().equals(info.getProcessName())
                        && ProcessStartEvidence.isCurrent(requested, started, now,
                            info.getStartupTimestamps().get(ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION),
                            info.getStartupTimestamps().get(ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE))) {
                    return info.wasForceStopped() ? Result.FORCE_STOPPED : Result.ORDINARY;
                }
            }
        } catch (RuntimeException unavailable) {
            // Some devices do not expose an in-progress record. Unknown preserves the run.
        }
        return Result.UNKNOWN;
    }
}
