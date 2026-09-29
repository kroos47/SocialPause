package app.socialpause;

import android.content.Context;
import android.content.pm.PackageManager;
import java.util.*;

/** Label cache shared with the app-picker worker. Broadcasts/resume invalidate it; keys also include locale. */
final class ApplicationLabels {
    private static final Map<String,String> labels = new HashMap<>();
    private static String locale = "";
    private static long revision;
    static synchronized void invalidate() { labels.clear(); revision++; }
    static synchronized long revision(Context context) { configuration(context); return revision; }
    private static void configuration(Context context) {
        String current = context.getResources().getConfiguration().getLocales().toLanguageTags();
        if (!locale.equals(current)) { locale = current; invalidate(); }
    }
    static synchronized String label(Context context, String pkg) {
        configuration(context);
        return labels.computeIfAbsent(pkg, key -> {
            try { return context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(key, 0)).toString(); }
            catch (PackageManager.NameNotFoundException missing) {
                return switch (key) { case "com.instagram.android" -> "Instagram"; case "com.twitter.android" -> "X"; case "com.reddit.frontpage" -> "Reddit"; default -> key; };
            }
        });
    }
}
