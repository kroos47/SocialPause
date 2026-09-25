package app.socialpause;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import java.util.*;
import app.socialpause.engine.RulesEngine;

/** Main-thread coordinator shared by the activity, service, and scheduled receiver. */
public final class AppController {
    public final RulesEngine engine;
    private final StateStore store;
    private final TimerNotifications notifications;
    private final ScheduleAlarms alarms;
    private final MonitoringPermission monitoring;
    private Object monitoringOwner;
    private Runnable monitoringLost;
    public boolean connected;
    private final Context context;
    private boolean startupReconciled;
    private int startupChecks;
    AppController(Context context) {
        this.context = context.getApplicationContext();
        store = new StateStore(context); engine = store.read();
        // Reconcile the current process before any service callback or notification publication.
        reconcileStartup();
        notifications = new TimerNotifications(context); alarms = new ScheduleAlarms(context);
        monitoring = new MonitoringPermission(context);
        monitoring.observe(this::reconcileMonitoring);
        refresh();
    }
    public static AppController get(Context context) { return ((SocialPauseApp) context.getApplicationContext()).controller(); }
    public static long wall() { return System.currentTimeMillis(); }
    public static long elapsed() { return SystemClock.elapsedRealtime(); }
    public void refresh() { reconcileMonitoringState(); engine.advance(wall(), elapsed()); publish(); }
    public void focus(String pkg, boolean unlocked) {
        reconcileMonitoringState();
        engine.focus(connected ? pkg : null, connected && unlocked, wall(), elapsed()); publish();
    }
    public void notificationDismissed(long run, boolean liveRequested) {
        if (!engine.running || run != engine.cycleId()) return;
        notifications.dismissed(run, liveRequested); refresh();
    }
    public void start() {
        requireMonitoring();
        if (engine.running) return;
        engine.start(wall(), elapsed()); startupReconciled = true; notifications.newRun(); publish();
    }
    public void stop() { engine.stop(wall(), elapsed()); publish(); }
    public void startManualLunch() { requireRunningMonitoring(); engine.startManualLunch(wall(), elapsed()); publish(); }
    public void stopLunch() { requireRunningMonitoring(); engine.stopLunch(wall(), elapsed()); publish(); }

    /** Call on resume as well as from permission callbacks; never infer revocation from binding alone. */
    public void reconcileMonitoring() { if (reconcileMonitoringState()) publish(); }
    private boolean reconcileMonitoringState() {
        boolean recovered = reconcileStartup();
        if (monitoring.enabled()) return recovered;
        boolean changed = connected || engine.running || monitoringOwner != null;
        Runnable cleanup = monitoringLost;
        connected = false; monitoringOwner = null; monitoringLost = null;
        if (engine.running) engine.systemStop(wall(), elapsed());
        if (cleanup != null) cleanup.run();
        return changed || recovered;
    }
    private boolean reconcileStartup() {
        if (startupReconciled) return false;
        StartupRecovery.Result result = StartupRecovery.detect(context);
        // The current startup record can be incomplete in Application.onCreate. Retry at
        // subsequent activity/service callbacks, then preserve state if evidence stays absent.
        if (result != StartupRecovery.Result.UNKNOWN || ++startupChecks >= 10) startupReconciled = true;
        if (result != StartupRecovery.Result.FORCE_STOPPED) return false;
        engine.systemStop(wall(), elapsed());
        return true;
    }
    private void requireMonitoring() {
        reconcileMonitoring();
        if (!connected) throw new IllegalStateException("Enable App monitoring and wait for it to connect before starting.");
    }
    private void requireRunningMonitoring() {
        requireMonitoring();
        if (!engine.running) throw new IllegalStateException("Start tracking before using Lunch Break.");
    }
    boolean monitoringConnected(Object owner, Runnable cleanup) {
        reconcileMonitoringState();
        if (!monitoring.enabled()) { cleanup.run(); publish(); return false; }
        Runnable previousCleanup = monitoringOwner != owner ? monitoringLost : null;
        monitoringOwner = owner; monitoringLost = cleanup; connected = true;
        if (previousCleanup != null) previousCleanup.run();
        engine.focus(null, false, wall(), elapsed()); publish(); return true;
    }
    void monitoringDisconnected(Object owner) {
        if (monitoringOwner != owner) return;
        monitoringOwner = null; monitoringLost = null; connected = false;
        engine.focus(null, false, wall(), elapsed());
        reconcileMonitoringState(); publish();
    }
    private void publish() {
        store.save(engine); notifications.update(engine, connected);
        alarms.schedule(engine.running ? engine.nextBoundary(wall(), elapsed()) : 0);
    }
    public static String duration(long ms) {
        long seconds = (Math.max(0, ms) + 999) / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }
    public static String stopDuration(long ms) {
        long seconds = (Math.max(0, ms) + 999) / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    public static String at(long wall) { return java.time.Instant.ofEpochMilli(wall).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")); }
    public static String time(int minute) { return String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60); }
    public static String label(Context c, String pkg) {
        try { return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (PackageManager.NameNotFoundException e) {
            return switch (pkg) { case "com.instagram.android" -> "Instagram"; case "com.twitter.android" -> "X"; case "com.reddit.frontpage" -> "Reddit"; default -> pkg; };
        }
    }
    public static Set<String> protectedPackages(Context c) {
        Set<String> result = new HashSet<>(List.of(c.getPackageName(), "com.android.settings", "com.android.systemui"));
        var home = c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        if (home != null) result.add(home.activityInfo.packageName);
        return result;
    }
}
