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
    public boolean connected;
    AppController(Context context) {
        store = new StateStore(context); engine = store.read();
        notifications = new TimerNotifications(context); alarms = new ScheduleAlarms(context);
    }
    public static AppController get(Context context) { return ((SocialPauseApp) context.getApplicationContext()).controller(); }
    public static long wall() { return System.currentTimeMillis(); }
    public static long elapsed() { return SystemClock.elapsedRealtime(); }
    public void refresh() { engine.advance(wall(), elapsed()); publish(); }
    public void focus(String pkg, boolean unlocked) { engine.focus(pkg, unlocked, wall(), elapsed()); publish(); }
    public void start() { engine.start(wall(), elapsed()); publish(); }
    public void stop() { engine.stop(wall(), elapsed()); publish(); }
    private void publish() {
        store.save(engine); notifications.update(engine, connected);
        alarms.schedule(engine.running ? engine.nextBoundary(wall(), elapsed()) : 0);
    }
    public static String duration(long ms) {
        long seconds = (Math.max(0, ms) + 999) / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }
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
