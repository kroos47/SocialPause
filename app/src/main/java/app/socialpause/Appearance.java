package app.socialpause;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;

/** App-only appearance preference. It never touches monitoring or persisted timer state. */
final class Appearance {
    private static final String PREFERENCES = "appearance", DARK = "dark";
    private Appearance() {}
    static boolean isDark(Context context) {
        boolean system = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(DARK, system);
    }
    static void apply(Context context) {
        if (context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).contains(DARK)) applyMode(context, isDark(context));
    }
    static void setDark(Context context, boolean dark) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(DARK, dark).apply();
        applyMode(context, dark);
    }
    private static void applyMode(Context context, boolean dark) {
        context.getSystemService(UiModeManager.class).setApplicationNightMode(dark ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
    }
}
