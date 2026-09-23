package app.socialpause;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;
import java.util.*;
import app.socialpause.engine.FocusResolver;

/** Reads package identity only, not screen text. Blocking redirects to Home, not force-stop. */
public final class SocialAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppController controller;
    private BlockOverlay overlay;
    private long lastHome;
    private final FocusResolver focusResolver = new FocusResolver();
    private boolean recents;
    private boolean registered;
    private boolean active;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!active) return;
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) { focusResolver.reset(); recents=false; controller.focus(null, false); handler.removeCallbacks(pulse); if (active) handler.postDelayed(pulse,30_000); if(overlay != null) overlay.dismiss(); }
            else { inspect(); handler.removeCallbacks(pulse); if (active) handler.postDelayed(pulse,500); }
        }
    };
    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            if (!active) return;
            inspect();
            if (active) handler.postDelayed(this, controller.engine.running && getSystemService(PowerManager.class).isInteractive() ? 500 : 30_000);
        }
    };
    @Override protected void onServiceConnected() {
        suspendMonitoring();
        controller = AppController.get(this); overlay = new BlockOverlay(this); active = true;
        if (!controller.monitoringConnected(this, this::suspendMonitoring)) return;
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_SCREEN_ON); f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED); registered = true;
        handler.removeCallbacks(pulse); handler.post(pulse);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (active && controller != null) {
            if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                String cls=event.getClassName()==null?"":event.getClassName().toString().toLowerCase(Locale.ROOT);
                String pkg=event.getPackageName()==null?"":event.getPackageName().toString();
                if("com.android.systemui".equals(pkg))recents=cls.contains("recents")||cls.contains("overview");
                else if(!pkg.isEmpty())recents=false;
            }
            inspect(); handler.removeCallbacks(pulse); if (active) handler.postDelayed(pulse, 500);
        }
    }
    private void inspect() {
        if (!active || controller == null) return;
        controller.reconcileMonitoring();
        if (!active) return;
        boolean unlocked = getSystemService(PowerManager.class).isInteractive() && !getSystemService(KeyguardManager.class).isKeyguardLocked();
        if (overlay != null && overlay.visible()) {
            controller.focus(null, false);
            if (!unlocked || !controller.engine.blocked(overlay.blockedPackage(), AppController.wall(), AppController.elapsed())) overlay.dismiss();
            else return;
        }
        List<FocusResolver.Window> windows=new ArrayList<>();String fallback=null;
        if(unlocked && controller.engine.running) {
            for(AccessibilityWindowInfo w:getWindows()) {
                AccessibilityNodeInfo root=w.getRoot();
                String owner=root==null||root.getPackageName()==null?null:root.getPackageName().toString();
                // Android may redact SystemUI roots; TYPE_SYSTEM still identifies a panel.
                FocusResolver.Kind kind=w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD?FocusResolver.Kind.INPUT_METHOD
                        :("com.android.systemui".equals(owner)||(owner==null&&w.getType()==AccessibilityWindowInfo.TYPE_SYSTEM))?FocusResolver.Kind.SYSTEM_PANEL
                        :w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION?FocusResolver.Kind.APP:FocusResolver.Kind.OTHER;
                windows.add(new FocusResolver.Window(owner,kind,w.isFocused(),w.isActive(),w.getLayer()));
            }
            AccessibilityNodeInfo root=getRootInActiveWindow();
            if(root!=null&&root.getPackageName()!=null)fallback=root.getPackageName().toString();
        }
        String pkg=focusResolver.resolve(unlocked,controller.engine.running,windows,fallback,recents);
        controller.focus(pkg, unlocked);
        if (controller.engine.blocked(pkg, AppController.wall(), AppController.elapsed()) && AppController.elapsed() - lastHome > 700) {
            lastHome = AppController.elapsed();
            focusResolver.reset();
            overlay.show(pkg, controller.engine);
            controller.focus(null, false);
        }
    }
    @Override public void onInterrupt() {
        // Feedback interruption is not permission revocation.
        focusResolver.reset();
        if (active && controller != null) {
            controller.focus(null, false); handler.removeCallbacks(pulse);
            if (active) handler.postDelayed(pulse,500);
        }
        if (overlay != null) overlay.dismiss();
    }
    private void suspendMonitoring() {
        active = false; recents = false; focusResolver.reset();
        handler.removeCallbacksAndMessages(null);
        if (overlay != null) overlay.dismiss();
        if (registered) { unregisterReceiver(screen); registered = false; }
    }
    private void disconnectMonitoring() {
        suspendMonitoring();
        if (controller != null) controller.monitoringDisconnected(this);
    }
    @Override public boolean onUnbind(Intent intent) {
        disconnectMonitoring();
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        disconnectMonitoring();
        super.onDestroy();
    }
}
