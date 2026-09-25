package app.socialpause;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.view.*;
import android.widget.*;
import app.socialpause.engine.RulesEngine;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.socialpause.engine.RulesEngine.*;

/**
 * Dependency-free instrumentation for an explicitly disposable emulator only.
 * Test-only reflection seeds deadlines/state; production durations and code paths are unchanged.
 * Run with: am instrument -w -e synthetic true app.socialpause.test/app.socialpause.RuntimeChecks
 */
public final class RuntimeChecks extends Instrumentation {
    private static final String I="com.instagram.android", X="com.twitter.android";
    private Bundle arguments;
    private Context target;
    private AppController controller;
    private TimerNotifications notifications;
    private MainActivity activity;
    private int passed;
    private long runSerial=1_000_000;
    private final StringBuilder report=new StringBuilder();
    @FunctionalInterface private interface Check { void run() throws Exception; }

    @Override public void onCreate(Bundle args) { super.onCreate(args);arguments=args==null?new Bundle():args;start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            target=getTargetContext();
            boolean emulator=Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish");
            require(emulator&&"true".equals(arguments.getString("synthetic"))
                    &&(target.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0,
                    "Refusing runtime checks: use an Android Emulator, a debug build, and -e synthetic true.");
            getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            main(()->controller=AppController.get(target));
            String phase=arguments.getString("phase","checks");
            if(phase.equals("inspect")) {
                main(()->report.append(snapshot()));
            } else {
                enableSyntheticPermissions();
                eventually(()->onMain(()->controller.connected),15_000,"Accessibility service did not connect");
                main(()->notifications=(TimerNotifications)field(controller,"notifications"));
                if(Set.of("usage","cooldown","lunch").contains(phase)) {
                    seed(phase);
                } else {
                    require(phase.equals("checks")||phase.equals("ui"),"Unknown phase "+phase);
                    // Isolate synthetic app focus from the emulator launcher. Permission remains
                    // enabled; external seed phases retain the actual service polling behavior.
                    main(()->{
                        Object owner=field(controller,"monitoringOwner");
                        require(owner instanceof SocialAccessibilityService,"Expected a bound service");
                        invoke(owner,"suspendMonitoring");
                    });
                    if(phase.equals("checks"))notificationChecks();
                    uiChecks();
                    main(()->{controller.engine.systemStop(AppController.wall(),AppController.elapsed());controller.refresh();});
                }
            }
            result.putInt("passed",passed);result.putString("stream",report+"\nPASS: "+passed+" runtime checks.\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            StringWriter trace=new StringWriter();failure.printStackTrace(new PrintWriter(trace));
            result.putString("stream",report+"\nFAIL: "+trace);finish(Activity.RESULT_CANCELED,result);
        }
    }

    private void enableSyntheticPermissions() throws Exception {
        shell("pm grant app.socialpause android.permission.POST_NOTIFICATIONS");
        // A previous failed instrumentation run may leave Android's crashed-service
        // suppression active. Toggle only on the explicitly disposable emulator.
        shell("settings put secure enabled_accessibility_services ''");
        shell("settings put secure accessibility_enabled 0");
        eventually(()->onMain(()->!controller.connected),3_000,"Accessibility did not disconnect for synthetic setup");
        Thread.sleep(200);
        shell("settings put secure enabled_accessibility_services app.socialpause/.SocialAccessibilityService");
        shell("settings put secure accessibility_enabled 1");
        shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");
    }
    private String shell(String command) throws IOException {
        try(var input=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command))) {
            return new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private RulesEngine fresh(TimerMode mode,boolean running) throws Exception {
        RulesEngine e=new RulesEngine();e.lunchEnabled=false;e.sleepEnabled=false;
        e.setTimerMode(mode);if(mode==TimerMode.SHARED)e.setSharedLimit(5*MINUTE);
        if(running)e.start(AppController.wall(),AppController.elapsed());
        // Reserve room for explicit Stop/Start within each scenario; their increment
        // must never collide with the next synthetic scenario's monitoring identity.
        runSerial+=100;set(e,"cycleSerial",runSerial);return e;
    }
    private void install(RulesEngine e) throws Exception {
        notificationStep(()->{set(controller,"engine",e);controller.connected=true;notifications.newRun();controller.refresh();});
    }
    private void showFocused() throws Exception {
        notificationStep(()->controller.focus(X,true));
    }
    private Notification focused(boolean promoted) throws Exception {
        return awaitNotification(n->n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
                &&n.extras.getBoolean("android.requestPromotedOngoing")==promoted);
    }
    private void verifyLivePayload(Notification n,boolean shared) {
        require(n.extras.getBoolean("android.requestPromotedOngoing"),"Missing promoted-ongoing request");
        require(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),"Missing drawer countdown");
        require(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),"Drawer chronometer disabled");
        require(n.bigContentView==null,"Focused countdown must retain the standard expanded template");
        String title=String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE));
        require(title.equals(shared?"Shared allowance · X":"X"),"Unexpected focused title: "+title);
        int expectedIcon=shared?R.drawable.ic_pause:Design.appIcon(X);
        require(n.getSmallIcon()!=null&&n.getSmallIcon().getResId()==expectedIcon,"Focused limiting icon changed");
        if(Build.VERSION.SDK_INT>=36) {
            String template=n.extras.getString(Notification.EXTRA_TEMPLATE,"");
            require(template.contains("ProgressStyle"),"Focused style was "+template);
            String text=n.extras.getString("android.shortCriticalText","");
            require(text.matches("[0-9]{2}:[0-9]{2}"),"Missing compact timer text, extras="+n.extras.keySet());
            require(n.hasPromotableCharacteristics(),"OS rejected live format");
        }
    }
    private void notificationChecks() throws Exception {
        for(TimerMode mode:TimerMode.values()) {
            check(mode+": ordinary lunch and cooldown dismissal preserve the next live payload",()->{
                RulesEngine e=fresh(mode,true);install(e);
                notificationStep(()->controller.startManualLunch());
                Notification lunch=awaitNotification(n->String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE)).startsWith("Lunch break"));
                require(!lunch.extras.getBoolean("android.requestPromotedOngoing"),"Lunch should be ordinary");
                dismiss(lunch);require(suppressed()!=e.cycleId(),"Lunch dismissal suppressed live promotion");
                notificationStep(()->{set(e,"lunchEnd",AppController.wall()-1);set(e,"lunchCooldownEnd",AppController.wall()+COOLDOWN);controller.refresh();});
                Notification cooldown=awaitNotification(n->String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE)).startsWith("After lunch"));
                dismiss(cooldown);require(suppressed()!=e.cycleId(),"Cooldown dismissal suppressed live promotion");
                notificationStep(()->{set(e,"lunchCooldownEnd",AppController.wall()-1);controller.refresh();controller.focus(X,true);});
                verifyLivePayload(focused(true),mode==TimerMode.SHARED);
                // A late callback still belongs to the old ordinary surface after replacement.
                dismiss(lunch);showFocused();verifyLivePayload(focused(true),mode==TimerMode.SHARED);
            });
        }
        check("real focused deleteIntent suppresses only the current monitoring run",()->{
            RulesEngine e=fresh(TimerMode.INDIVIDUAL,true);install(e);showFocused();Notification live=focused(true);
            dismiss(live);eventually(()->suppressed()==e.cycleId(),3_000,"Live dismissal did not persist suppression");
            verifyOrdinaryFocused(focused(false));
            notificationStep(()->{controller.startManualLunch();set(e,"lunchEnd",AppController.wall()-COOLDOWN);set(e,"lunchCooldownEnd",AppController.wall()-1);controller.refresh();controller.focus(X,true);});
            verifyOrdinaryFocused(focused(false));
            notificationStep(()->{controller.engine.systemStop(AppController.wall(),AppController.elapsed());controller.start();controller.focus(X,true);});
            verifyLivePayload(focused(true),false);
            dismiss(live);showFocused();verifyLivePayload(focused(true),false);
        });
        check("existing ordinary-run suppression survives until explicit Start",()->{
            RulesEngine e=fresh(TimerMode.INDIVIDUAL,true);install(e);
            target.getSharedPreferences("notification-visibility",Context.MODE_PRIVATE).edit().putLong("ordinary-run",e.cycleId()).commit();
            showFocused();verifyOrdinaryFocused(focused(false));
            notificationStep(()->{controller.engine.systemStop(AppController.wall(),AppController.elapsed());controller.start();controller.focus(X,true);});
            verifyLivePayload(focused(true),false);
        });
        check("controller rejects early Stop and repeated Start preserves the live run",()->{
            RulesEngine e=fresh(TimerMode.INDIVIDUAL,true);install(e);showFocused();long run=e.cycleId();
            notificationStep(()->{
                boolean rejected=false;try{controller.stop();}catch(IllegalStateException expected){rejected=true;}
                require(rejected&&e.running,"Early controller Stop bypassed lock");
                long before=e.stopLockRemaining(AppController.elapsed());controller.start();
                require(run==e.cycleId()&&e.stopLockRemaining(AppController.elapsed())<=before,"Repeated Start rearmed run/lock");
            });
            verifyLivePayload(focused(true),false);
        });
    }
    private void verifyOrdinaryFocused(Notification n) {
        require(!n.extras.getBoolean("android.requestPromotedOngoing"),"Suppressed live request returned");
        require(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),"Ordinary focused drawer lost countdown");
    }
    private long suppressed() { return target.getSharedPreferences("notification-visibility",Context.MODE_PRIVATE).getLong("ordinary-run",-1); }
    private interface NotificationPredicate { boolean matches(Notification n); }
    private Notification awaitNotification(NotificationPredicate predicate) throws Exception {
        Notification[] found={null};
        try {
            eventually(()->{
                for(StatusBarNotification entry:target.getSystemService(NotificationManager.class).getActiveNotifications())
                    if(entry.getId()==10&&predicate.matches(entry.getNotification())){found[0]=entry.getNotification();return true;}
                return false;
            },5_000,"Expected timer notification did not arrive");
        } catch(AssertionError failure) {
            main(()->report.append("Notification timeout: ").append(snapshot()).append("run=").append(controller.engine.cycleId()).append(", suppressed=").append(suppressed()).append('\n'));
            for(StatusBarNotification entry:target.getSystemService(NotificationManager.class).getActiveNotifications()) {
                Notification n=entry.getNotification();report.append("Notification id=").append(entry.getId())
                    .append(", title=").append(n.extras.getCharSequence(Notification.EXTRA_TITLE))
                    .append(", template=").append(n.extras.getString(Notification.EXTRA_TEMPLATE))
                    .append(", liveRequested=").append(n.extras.getBoolean("android.requestPromotedOngoing"))
                    .append(", chronometer=").append(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
                    .append(", shortText=").append(n.extras.getString("android.shortCriticalText"))
                    .append(", extras=").append(n.extras.keySet()).append('\n');
            }
            throw failure;
        }
        return found[0];
    }
    private void dismiss(Notification notification) throws Exception {
        Thread.sleep(650); // Real user transitions must not become a >5/s synthetic enqueue burst.
        require(notification.deleteIntent!=null,"Missing deleteIntent");CountDownLatch finished=new CountDownLatch(1);
        notification.deleteIntent.send(target,0,null,(pending,intent,code,data,extras)->finished.countDown(),new Handler(Looper.getMainLooper()));
        require(finished.await(5,TimeUnit.SECONDS),"Dismissal receiver did not complete");waitForIdleSync();
    }

    private void uiChecks() throws Exception {
        install(fresh(TimerMode.INDIVIDUAL,false));
        activity=(MainActivity)startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        check("Home Start creates a disabled Stop and readable six-hour countdown",()->{
            main(()->{
                Button start=button("Start");require(start.isEnabled(),"Start disabled with connected permission");
                require(findText("Starting locks Stop for 6 hours.")!=null,"Missing pre-Start explanation");start.performClick();
                require(!button("Stop").isEnabled(),"Stop should be disabled after Start");
                TextView hint=findPrefix("Stop available in ");require(hint!=null&&hint.getText().toString().matches("Stop available in [0-9]{2}:[0-9]{2}:[0-9]{2}"),"Missing HH:MM:SS hint");
            });
        });
        check("Home ticker enables Stop at its deadline without stopping monitoring",()->{
            main(()->set(controller.engine,"stopUnlockElapsed",AppController.elapsed()+1_500));
            eventually(()->onMain(()->button("Stop").isEnabled()),5_000,"Stop did not enable on the activity ticker");
            main(()->{require(controller.engine.running,"Unlock unexpectedly stopped monitoring");button("Stop").performClick();require(!controller.engine.running,"Unlocked Stop failed");button("Start").performClick();});
        });
        check("Lunch Start and Stop remain usable while the main Stop stays locked",()->{
            main(()->{
                require(!button("Stop").isEnabled(),"Main Stop unexpectedly enabled");
                require(button("Start lunch").isEnabled(),"Manual lunch unavailable");button("Start lunch").performClick();
                require(button("Stop lunch").isEnabled(),"Stop lunch was affected by main lock");
                require(!button("Start lunch").isEnabled(),"Daily manual quota was not consumed");button("Stop lunch").performClick();
                require(controller.engine.mode(AppController.wall(),AppController.elapsed())==Mode.LUNCH_COOLDOWN,"Stop lunch did not start cooldown");
                require(!button("Stop").isEnabled(),"Early lunch Stop cleared main lock");
            });
        });
        main(()->activity.finish());waitForIdleSync();activity=null;
    }
    private TextView findText(String text) { return find(activity.getWindow().getDecorView(),text,false); }
    private TextView findPrefix(String text) { return find(activity.getWindow().getDecorView(),text,true); }
    private static TextView find(View root,String text,boolean prefix) {
        if(root instanceof TextView value&&(prefix?value.getText().toString().startsWith(text):value.getText().toString().equals(text)))return value;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),text,prefix);if(found!=null)return found;}
        return null;
    }
    private Button button(String text) { TextView view=findText(text);require(view instanceof Button,"Missing button "+text);return (Button)view; }

    /** Leave full-duration synthetic data for separate ADB permission/kill/reboot checks. */
    @SuppressWarnings("unchecked") private void seed(String phase) throws Exception {
        main(()->{
            RulesEngine e=fresh(TimerMode.INDIVIDUAL,true);set(controller,"engine",e);notifications.newRun();
            long wall=AppController.wall(),elapsed=AppController.elapsed();
            ((Map<String,Long>)field(e,"usage")).put(I,phase.equals("cooldown")?INSTAGRAM_LIMIT:MINUTE);
            e.history().record(I,wall-MINUTE,MINUTE,ZoneId.systemDefault());
            if(phase.equals("cooldown"))((Map<String,Long>)field(e,"cooldowns")).put(I,elapsed+COOLDOWN);
            if(phase.equals("lunch"))e.startManualLunch(wall,elapsed);
            controller.refresh();report.append("Seeded ").append(phase).append(" with full production durations.\n").append(snapshot());
            // apply() data is flushed before the instrumentation process exits.
            target.getSharedPreferences("socialpause-v1",Context.MODE_PRIVATE).edit().putBoolean("runtime-synthetic",true).commit();
        });
    }
    private String snapshot() {
        RulesEngine e=controller.engine;long wall=AppController.wall(),elapsed=AppController.elapsed();
        return "running="+e.running+", connected="+controller.connected+", mode="+e.mode(wall,elapsed)
                +", stopLockMs="+e.stopLockRemaining(elapsed)+", instagramUsedMs="+e.used(I)
                +", instagramCooldownMs="+e.cooldownRemaining(I,wall,elapsed)+", manualUsed="+e.manualLunchUsedToday(wall)+"\n";
    }
    private void check(String name,Check action) throws Exception {
        Thread.sleep(1_000); // Separate scenarios so Android notification rate limits remain realistic.
        action.run();passed++;report.append("PASS ").append(name).append('\n');
    }
    private void notificationStep(Check action) throws Exception {
        // Android can silently shed excess updates. Production ticks at one-second text
        // boundaries; pace synthetic transitions rather than weakening payload assertions.
        Thread.sleep(650);main(action);
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    private void main(Check action) throws Exception {
        Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable failure){error[0]=failure;}});
        if(error[0]!=null){if(error[0] instanceof Exception exception)throw exception;throw new AssertionError(error[0]);}
    }
    private boolean onMain(BooleanSupplier condition) {
        boolean[] result={false};try{main(()->result[0]=condition.getAsBoolean());}catch(Exception failure){throw new IllegalStateException(failure);}return result[0];
    }
    private static void eventually(BooleanSupplier condition,long timeout,String message) throws InterruptedException {
        long end=SystemClock.elapsedRealtime()+timeout;
        do {if(condition.getAsBoolean())return;Thread.sleep(30);}while(SystemClock.elapsedRealtime()<end);
        throw new AssertionError(message);
    }
    private static Object field(Object owner,String name) throws Exception { Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner); }
    private static void set(Object owner,String name,Object value) throws Exception { Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value); }
    private static void invoke(Object owner,String name) throws Exception { Method method=owner.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(owner); }
}
