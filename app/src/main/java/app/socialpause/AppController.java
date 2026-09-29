package app.socialpause;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import app.socialpause.engine.RulesSnapshot;
import java.util.*;
import app.socialpause.engine.RulesEngine;
import app.socialpause.engine.NotificationDismissalPolicy;

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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean refreshPending;
    private final Runnable pendingRefresh = () -> { refreshPending=false; refresh(); };
    public void requestRefresh() { if(!refreshPending) { refreshPending=true; handler.post(pendingRefresh); } }
    private long boundaryWall = Long.MAX_VALUE, scheduledDisplay;
    private final Runnable displayTick = this::displayTick;
    private void displayTick() {
        scheduledDisplay = 0;
        if (!engine.running) return;
        if (wall() >= boundaryWall || elapsed() >= engine.usageDeadline(wall(), elapsed())) {
            refresh();
            inspectNow();
        } else {
            notifications.update(engine, connected);
            scheduleDisplay();
        }
    }

    AppController(Context context) {
        this.context = context.getApplicationContext();
        store = new StateStore(context); engine = store.read();
        // Reconcile the current process before any service callback or notification publication.
        reconcileStartup();
        notifications = new TimerNotifications(context); alarms = new ScheduleAlarms(context);
        monitoring = new MonitoringPermission(context);
        monitoring.observe(this::reconcileMonitoring);
        BroadcastReceiver environment = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                ApplicationLabels.invalidate(); notifications.invalidate(); requestRefresh();
            }
        };
        IntentFilter packages = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED); packages.addAction(Intent.ACTION_PACKAGE_CHANGED); packages.addDataScheme("package");
        this.context.registerReceiver(environment, packages, Context.RECEIVER_NOT_EXPORTED);
        IntentFilter configuration = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
        configuration.addAction(Intent.ACTION_LOCALE_CHANGED); configuration.addAction(Intent.ACTION_TIME_CHANGED); configuration.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        this.context.registerReceiver(environment, configuration, Context.RECEIVER_NOT_EXPORTED);
        refresh();
    }
    public static AppController get(Context context) { return ((SocialPauseApp) context.getApplicationContext()).controller(); }
    public static long wall() { return System.currentTimeMillis(); }
    public static long elapsed() { return SystemClock.elapsedRealtime(); }
    public void refresh() { reconcileMonitoringState(); engine.advance(wall(), elapsed()); publish(); }
    public void focus(String pkg, boolean unlocked) { focus(pkg, unlocked, unlocked && pkg != null); }
    public void focus(String pkg, boolean unlocked, boolean appVisible) {
        notifications.focusVisibility(connected && unlocked && appVisible);
        engine.focus(connected ? pkg : null, connected && unlocked, wall(), elapsed()); publish();
    }
    public void notificationDismissed(NotificationDismissalPolicy.Dismissal dismissal) {
        if (!engine.running || dismissal.run() != engine.cycleId()) return;
        if(!notifications.dismissed(dismissal))return;
        // The delete callback can precede the Accessibility panel event. Resample metadata
        // before using visibility; cached app focus alone must not restore behind the shade.
        if(monitoringOwner instanceof SocialAccessibilityService service && service.refreshFocusedWindow())return;
        refresh();
    }
    public void start() {
        requireMonitoring();
        if (engine.running) return;
        engine.start(wall(), elapsed()); startupReconciled = true; notifications.newRun(); publish(); inspectNow();
    }
    public void stop() { engine.stop(wall(), elapsed()); publish(); inspectNow(); }
    public RulesSnapshot snapshot() { return engine.snapshot(wall(), elapsed()); }
    public void resumed() { ApplicationLabels.invalidate(); notifications.invalidate(); refresh(); }
    public void setTimerMode(RulesEngine.TimerMode mode) { engine.setTimerMode(mode); publish(); }
    public void setSharedLimit(long limit) { engine.setSharedLimit(limit); publish(); }
    public void setAppLimit(String pkg, long limit) { engine.setAppLimit(pkg, limit); publish(); }
    public void selectApps(Set<String> apps) { engine.select(apps); publish(); }
    public void setLunch(boolean enabled, int minute) { engine.lunch(enabled, minute, wall(), elapsed()); publish(); }
    public void setSleep(boolean enabled, int start, int end) { engine.sleep(enabled, start, end); publish(); }
    private void inspectNow() {
        if (monitoringOwner instanceof SocialAccessibilityService service) service.refreshFocusedWindow();
    }
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
        handler.removeCallbacks(pendingRefresh); refreshPending=false;
        store.save(engine); notifications.update(engine, connected);
        boundaryWall = engine.running ? engine.nextBoundary(wall(), elapsed()) : Long.MAX_VALUE;
        alarms.schedule(engine.running ? boundaryWall : 0);
        scheduleDisplay();
    }
    private void scheduleDisplay() {
        if (!engine.running) { handler.removeCallbacks(displayTick); scheduledDisplay = 0; return; }
        long now = elapsed(), delay = Math.max(1, Math.min(boundaryWall - wall(), engine.usageDeadline(wall(), elapsed()) - now));
        if (notifications.needsClockTicks()) delay = Math.min(delay, 1000 - now % 1000);
        long due = now + delay;
        // Repeated window events cannot postpone a pending display/boundary callback.
        if (scheduledDisplay != 0 && scheduledDisplay <= due) return;
        handler.removeCallbacks(displayTick); scheduledDisplay = due; handler.postDelayed(displayTick, delay);
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
        return ApplicationLabels.label(c,pkg);
    }

    public static Set<String> protectedPackages(Context c) {
        Set<String> result = new HashSet<>(List.of(c.getPackageName(), "com.android.settings", "com.android.systemui"));
        var home = c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        if (home != null) result.add(home.activityInfo.packageName);
        return result;
    }
}
