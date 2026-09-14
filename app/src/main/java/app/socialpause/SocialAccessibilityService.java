package app.socialpause;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;

/** Reads package identity only, not screen text. Blocking redirects to Home, not force-stop. */
public final class SocialAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppController controller;
    private BlockOverlay overlay;
    private long lastHome;
    private boolean registered;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) { controller.focus(null, false); if(overlay != null) overlay.dismiss(); }
            else inspect();
        }
    };
    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            inspect();
            handler.postDelayed(this, controller.engine.running && getSystemService(PowerManager.class).isInteractive() ? 500 : 30_000);
        }
    };
    @Override protected void onServiceConnected() {
        controller = AppController.get(this); controller.connected = true; overlay = new BlockOverlay(this);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_SCREEN_ON); f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED); registered = true;
        handler.removeCallbacks(pulse); handler.post(pulse);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (controller != null) { inspect(); handler.removeCallbacks(pulse); handler.postDelayed(pulse, 500); }
    }
    private void inspect() {
        boolean unlocked = getSystemService(PowerManager.class).isInteractive() && !getSystemService(KeyguardManager.class).isKeyguardLocked();
        if (overlay != null && overlay.visible()) {
            controller.focus(null, false);
            if (!unlocked || !controller.engine.blocked(overlay.blockedPackage(), AppController.wall(), AppController.elapsed())) overlay.dismiss();
            else return;
        }
        String pkg = null;
        if (unlocked && controller.engine.running) {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w.isFocused() && w.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    AccessibilityNodeInfo root = w.getRoot();
                    if (root != null && root.getPackageName() != null) pkg = root.getPackageName().toString();
                    break;
                }
            }
            if (pkg == null) {
                for (AccessibilityWindowInfo w : getWindows()) {
                    if (w.isActive() && w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        AccessibilityNodeInfo root = w.getRoot();
                        if (root != null && root.getPackageName() != null) pkg = root.getPackageName().toString();
                        break;
                    }
                }
            }
            if (pkg == null) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null && root.getPackageName() != null) pkg = root.getPackageName().toString();
            }
        }
        controller.focus(pkg, unlocked);
        if (controller.engine.blocked(pkg, AppController.wall(), AppController.elapsed()) && AppController.elapsed() - lastHome > 700) {
            lastHome = AppController.elapsed();
            overlay.show(pkg, controller.engine);
            controller.focus(null, false);
        }
    }
    @Override public void onInterrupt() { if (controller != null) controller.focus(null, false); if(overlay != null) overlay.dismiss(); }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (overlay != null) overlay.dismiss();
        if (registered) unregisterReceiver(screen);
        if (controller != null) { controller.connected = false; controller.focus(null, false); }
        super.onDestroy();
    }
}
