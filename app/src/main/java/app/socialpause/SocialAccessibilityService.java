package app.socialpause;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;
import java.util.*;
import app.socialpause.engine.FocusResolver;
import app.socialpause.engine.MonitoringSchedule;

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
    private final MonitoringSchedule schedule = new MonitoringSchedule();
    private long fallbackDelay;
    private boolean inspectionPending;
    private final Runnable pendingInspection = () -> { inspectionPending = false; inspect(); scheduleFallback(); };
    private void scheduleFallback() {
        handler.removeCallbacks(pulse);
        if (active && controller.engine.running && fallbackDelay > 0) handler.postDelayed(pulse, fallbackDelay);
    }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!active) return;
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) { focusResolver.reset(); recents=false; controller.focus(null, false); fallbackDelay = schedule.next(controller.engine.running,false,null,false); handler.removeCallbacks(pendingInspection); inspectionPending=false; scheduleFallback(); if(overlay != null) overlay.dismiss(); }
            else { inspect(); scheduleFallback(); }
        }
    };
    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            if (!active) return;
            inspect();
            scheduleFallback();
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
        if (active && controller != null && controller.engine.running) {
            if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                String cls=event.getClassName()==null?"":event.getClassName().toString().toLowerCase(Locale.ROOT);
                String pkg=event.getPackageName()==null?"":event.getPackageName().toString();
                if("com.android.systemui".equals(pkg))recents=cls.contains("recents")||cls.contains("overview");
                else if(!pkg.isEmpty())recents=false;
            }
            // Batch events already queued on this main-thread turn. Lock/permission changes
            // and dismissal restoration bypass this queue and inspect immediately.
            if (!inspectionPending) { inspectionPending = true; handler.post(pendingInspection); }
        }
    }
    /** Refresh drawer visibility before a dismissal callback decides whether to restore live UI. */
    boolean refreshFocusedWindow() {
        if (!active || controller == null) return false;
        handler.removeCallbacks(pendingInspection); inspectionPending=false;
        inspect(); scheduleFallback();
        return true;
    }
    private void inspect() {
        if (!active || controller == null) return;
        controller.reconcileMonitoring();
        if (!active) return;
        if (!controller.engine.running) {
            fallbackDelay=schedule.next(false,false,null,false);handler.removeCallbacks(pulse);
            focusResolver.reset();recents=false;if(overlay!=null)overlay.dismiss();return;
        }
        boolean unlocked = getSystemService(PowerManager.class).isInteractive() && !getSystemService(KeyguardManager.class).isKeyguardLocked();
        if (overlay != null && overlay.visible()) {
            controller.focus(null, false);
            if (!unlocked || !controller.engine.blocked(overlay.blockedPackage(), AppController.wall(), AppController.elapsed())) overlay.dismiss();
            else { fallbackDelay=schedule.next(true,unlocked,getPackageName(),false); return; }
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
        FocusResolver.Focus focus=focusResolver.resolveFocus(unlocked,controller.engine.running,windows,fallback,recents);
        String pkg=focus.app();
        fallbackDelay=schedule.next(controller.engine.running,unlocked,pkg,controller.engine.selected.contains(pkg));
        controller.focus(pkg, unlocked, focus.appVisible());
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
            fallbackDelay=schedule.next(controller.engine.running,true,null,false); scheduleFallback();
        }
        if (overlay != null) overlay.dismiss();
    }
    private void suspendMonitoring() {
        active = false; recents = false; inspectionPending=false; fallbackDelay=0; schedule.next(false,false,null,false); focusResolver.reset();
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
