package app.socialpause;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

/** App-lifetime observer of this service's configured permission, independent of binding. */
final class MonitoringPermission {
    private final ContentResolver resolver;
    private final ComponentName service;
    private final AccessibilityManager manager;
    private final Context context;
    private AccessibilityManager.AccessibilityServicesStateChangeListener listener;
    private ContentObserver observer;

    MonitoringPermission(Context context) {
        this.context = context.getApplicationContext();
        resolver = this.context.getContentResolver();
        service = new ComponentName(this.context, SocialAccessibilityService.class);
        manager = this.context.getSystemService(AccessibilityManager.class);
    }

    boolean enabled() {
        // A bound-service list can temporarily omit us during process recovery. The persisted
        // component setting distinguishes that interruption from the user revoking permission.
        // ACCESSIBILITY_ENABLED is also derived from running services, so it is not a revoke signal.
        String configured = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (configured == null) return false;
        for (String name : configured.split(":")) {
            if (service.equals(ComponentName.unflattenFromString(name))) return true;
        }
        return false;
    }

    void observe(Runnable changed) {
        if (listener != null) return;
        listener = ignored -> changed.run();
        manager.addAccessibilityServicesStateChangeListener(context.getMainExecutor(), listener);
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { changed.run(); }
        };
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer);
    }
}
