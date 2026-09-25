package app.socialpause.engine;

import java.util.TimeZone;
import static app.socialpause.engine.EngineTests.*;
import static app.socialpause.engine.RulesEngine.*;
import static app.socialpause.engine.NotificationDismissalPolicy.*;

/** Exercises notification delivery decisions across real fake-clock lunch and timer transitions. */
final class NotificationDismissalTests {
    private static Clock setup(TimerMode mode) {
        Clock c=new Clock();c.e=new RulesEngine();c.e.lunchEnabled=false;c.e.sleepEnabled=false;
        c.e.setTimerMode(mode);c.e.start(c.wall,c.elapsed);return c;
    }
    private static boolean live(Clock c,long suppressedRun) {
        return requestsLive(c.notification(),c.e.cycleId(),suppressedRun);
    }
    private static Dismissal posted(Clock c,long suppressedRun) {
        return new Dismissal(c.e.cycleId(),live(c,suppressedRun));
    }
    static void run() {
        test("manual lunch and cooldown swipes preserve the next focused live countdown in both modes",()->{
            for(TimerMode mode:TimerMode.values())for(boolean swipe:new boolean[]{false,true}){
                Clock c=setup(mode);long suppressed=NO_RUN,run=c.e.cycleId();c.focus(X);c.minutes(2);
                eq(true,live(c,suppressed));c.e.startManualLunch(c.wall,c.elapsed);
                eq(TimerPresentation.Kind.LUNCH,c.notification().kind);eq(false,live(c,suppressed));
                if(swipe)suppressed=posted(c,suppressed).suppressedRunAfter(suppressed,run);
                c.minutes(60);eq(TimerPresentation.Kind.LUNCH_COOLDOWN,c.notification().kind);
                eq(COOLDOWN,c.cooldown(X));
                if(swipe)suppressed=posted(c,suppressed).suppressedRunAfter(suppressed,run);
                c.minutes(60);eq(Mode.READY,c.mode());c.focus(X);
                eq(run,c.e.cycleId());eq(0L,c.cooldown(X));eq(APP_LIMIT,c.e.remaining(X));
                eq(true,live(c,suppressed));eq("10:00",c.notification().shortCriticalText());
                c.millis(1000);eq("09:59",c.notification().shortCriticalText());
                eq(mode==TimerMode.SHARED?DEFAULT_SHARED_LIMIT-1000:DEFAULT_SHARED_LIMIT,c.e.sharedRemaining());
            }
        });
        test("early lunch Stop and delayed completion restore live usage after ordinary dismissal",()->{
            for(TimerMode mode:TimerMode.values()){
                Clock c=setup(mode);long suppressed=NO_RUN;c.e.startManualLunch(c.wall,c.elapsed);c.minutes(15);
                c.e.stopLunch(c.wall,c.elapsed);eq(COOLDOWN,c.cooldown(I));
                suppressed=posted(c,suppressed).suppressedRunAfter(suppressed,c.e.cycleId());
                c.minutes(65);c.focus(I);eq(true,live(c,suppressed));eq("07:00",c.notification().shortCriticalText());
                eq(0L,c.e.used(I));
            }
        });
        test("delayed ordinary dismissal retains its surface after focused notification replaces it",()->{
            Clock c=setup(TimerMode.INDIVIDUAL);Dismissal idle=posted(c,NO_RUN);c.e.startManualLunch(c.wall,c.elapsed);
            Dismissal lunch=posted(c,NO_RUN);c.minutes(60);Dismissal cooldown=posted(c,NO_RUN);c.minutes(60);c.focus(X);
            Dismissal active=posted(c,NO_RUN);
            for(Dismissal ordinary:new Dismissal[]{idle,lunch,cooldown}){
                eq(false,ordinary.liveRequested());eq(false,ordinary.identity().equals(active.identity()));
                eq(NO_RUN,parse(ordinary.identity()).suppressedRunAfter(NO_RUN,c.e.cycleId()));
            }
            eq(true,live(c,NO_RUN));
        });
        test("intentional live dismissal stays suppressed through lunch cooldown and process recreation",()->{
            for(TimerMode mode:TimerMode.values()){
                Clock c=setup(mode);c.focus(X);Dismissal actual=posted(c,NO_RUN);
                long suppressed=actual.suppressedRunAfter(NO_RUN,c.e.cycleId());eq(false,live(c,suppressed));
                eq(false,posted(c,suppressed).liveRequested());
                c.e.startManualLunch(c.wall,c.elapsed);c.minutes(60);
                suppressed=posted(c,suppressed).suppressedRunAfter(suppressed,c.e.cycleId());
                c.e=copy(c.e,true);c.minutes(60);c.focus(X);eq(false,live(c,suppressed));
                eq("10:00",c.notification().shortCriticalText()); // The drawer countdown still works.
            }
        });
        test("legacy suppression is retained while a fresh Start can clear it",()->{
            Clock c=setup(TimerMode.INDIVIDUAL);c.focus(X);long legacy=c.e.cycleId();
            eq(false,live(c,legacy));c.focus(null);eq(legacy,posted(c,legacy).suppressedRunAfter(legacy,c.e.cycleId()));
            c.focus(X);eq(false,live(c,legacy));eq(true,live(c,NO_RUN));
        });
        test("old-run and future-run dismissal callbacks cannot suppress the current run",()->{
            Clock c=setup(TimerMode.INDIVIDUAL);c.focus(X);long run=c.e.cycleId();
            for(long other:new long[]{run-1,run+1})for(boolean liveRequested:new boolean[]{false,true}){
                Dismissal stale=parse(new Dismissal(other,liveRequested).identity());eq(false,stale.matches(run));
                eq(NO_RUN,stale.suppressedRunAfter(NO_RUN,run));eq(run,stale.suppressedRunAfter(run,run));
            }
            eq(true,live(c,NO_RUN));
        });
        test("dismissal identity is canonical distinct by run and surface and rejects missing provenance",()->{
            for(long run:new long[]{0,1,Long.MAX_VALUE})for(boolean liveRequested:new boolean[]{false,true}){
                Dismissal original=new Dismissal(run,liveRequested);eq(original,parse(original.identity()));
                eq(false,original.identity().equals(new Dismissal(run,!liveRequested).identity()));
            }
            eq(false,new Dismissal(1,true).identity().equals(new Dismissal(2,true).identity()));
            for(String malformed:new String[]{null,"","socialpause://notification-dismiss/1",
                    "socialpause://notification-dismiss/v1/1/unknown","socialpause://notification-dismiss/v1/-1/live",
                    "socialpause://notification-dismiss/v1/01/live","socialpause://notification-dismiss/v1/+1/live",
                    "socialpause://notification-dismiss/v1/1/live/","socialpause://notification-dismiss/v1/9223372036854775808/live",
                    "other://notification-dismiss/v1/1/live"})eq(null,parse(malformed));
        });
        test("sleep and screen locking hide live requests without becoming a dismissal",()->{
            Clock c=setup(TimerMode.INDIVIDUAL);c.focus(X);eq(true,live(c,NO_RUN));
            c.e.focus(X,false,c.wall,c.elapsed);eq(false,live(c,NO_RUN));c.minutes(1);eq(APP_LIMIT,c.e.remaining(X));
            c.focus(X);c.e.sleep(true,600,660);eq(TimerPresentation.Kind.HIDDEN,c.notification().kind);eq(false,live(c,NO_RUN));
            c.e.sleepEnabled=false;eq(true,live(c,NO_RUN));eq("10:00",c.notification().shortCriticalText());
        });
        test("shared limiting countdown keeps live eligibility after lunch overview dismissal",()->{
            Clock c=new Clock();c.e=new RulesEngine();c.e.lunchEnabled=false;c.e.sleepEnabled=false;
            c.e.setTimerMode(TimerMode.SHARED);c.e.setSharedLimit(2*MINUTE);c.e.start(c.wall,c.elapsed);
            c.e.startManualLunch(c.wall,c.elapsed);Dismissal ordinary=posted(c,NO_RUN);c.minutes(120);c.focus(X);
            long suppressed=ordinary.suppressedRunAfter(NO_RUN,c.e.cycleId());
            eq(true,live(c,suppressed));eq(true,c.notification().sharedLimiting);eq("02:00",c.notification().shortCriticalText());
            c.minutes(2);eq(TimerPresentation.Kind.SHARED_COOLDOWN,c.notification().kind);eq(false,live(c,suppressed));
        });
    }
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));run();
        System.out.println(tests+" notification dismissal scenarios passed.");
    }
}
