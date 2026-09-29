package app.socialpause.engine;

import java.util.TimeZone;
import static app.socialpause.engine.EngineTests.*;
import static app.socialpause.engine.RulesEngine.*;
import static app.socialpause.engine.NotificationDismissalPolicy.*;

/** Fake-clock regression coverage for dismissal independently of usage/cooldown state. */
final class NotificationDismissalTests {
    private static Clock setup(TimerMode mode) {
        Clock c=new Clock();c.e=new RulesEngine();c.e.lunchEnabled=false;c.e.sleepEnabled=false;
        c.e.setTimerMode(mode);c.e.start(c.wall,c.elapsed);return c;
    }
    private static Publication post(State s,Clock c,boolean visible){return s.prepare(c.e.cycleId(),c.notification(),visible);}
    static void run() {
        test("focused swipe restores on drawer closure without restarting usage or the six-hour lock",()->{
            for(TimerMode mode:TimerMode.values()) {
                Clock c=setup(mode);State s=new State(0,false);c.focus(X);c.minutes(2);
                long run=c.e.cycleId(),lock=c.e.stopLockRemaining(c.elapsed),used=c.e.used(X);
                Dismissal live=post(s,c,true).dismissal();eq(true,live.liveRequested());
                eq(true,s.dismiss(live));eq(false,s.dismiss(live));
                Publication covered=post(s,c,false);eq(false,covered.dismissal().liveRequested());eq(false,covered.replace());
                c.millis(15_000);eq(used+15_000,c.e.used(X));
                Publication recovered=post(s,c,true);eq(true,recovered.dismissal().liveRequested());eq(true,recovered.replace());
                eq(lock-15_000,c.e.stopLockRemaining(c.elapsed));eq(run,c.e.cycleId());
                eq(false,post(s,c,true).replace());eq(recovered.dismissal(),post(s,c,false).dismissal());
                eq(false,s.dismiss(live));eq(false,s.dismiss(covered.dismissal()));
                eq(true,s.dismiss(recovered.dismissal()));eq(true,post(s,c,true).replace());
            }
        });
        test("Home dismissal waits for selected app use and never invents a live idle countdown",()->{
            for(TimerMode mode:TimerMode.values()) {
                Clock c=setup(mode);State s=new State(0,false);Dismissal idle=post(s,c,true).dismissal();
                eq(false,idle.liveRequested());eq(true,s.dismiss(idle));
                Publication overview=post(s,c,true);eq(false,overview.dismissal().liveRequested());eq(false,overview.replace());
                c.minutes(1);eq(0L,c.e.used(X));c.focus(X);
                Publication active=post(s,c,true);eq(true,active.dismissal().liveRequested());eq(true,active.replace());
                eq(false,s.dismiss(idle));eq(false,s.dismiss(overview.dismissal()));
            }
        });
        test("manual lunch through cooldown restores focused promotion with and without ordinary dismissal",()->{
            for(TimerMode mode:TimerMode.values())for(boolean swipe:new boolean[]{false,true}) {
                Clock c=setup(mode);State s=new State(0,false);c.focus(X);c.minutes(2);
                post(s,c,true);c.e.startManualLunch(c.wall,c.elapsed);Dismissal lunch=post(s,c,true).dismissal();
                eq(false,lunch.liveRequested());if(swipe)eq(true,s.dismiss(lunch));post(s,c,false);
                c.minutes(60);eq(TimerPresentation.Kind.LUNCH_COOLDOWN,c.notification().kind);
                Dismissal cooldown=post(s,c,true).dismissal();if(swipe)eq(true,s.dismiss(cooldown));post(s,c,false);
                c.minutes(60);c.focus(X);Publication result=post(s,c,true);
                eq(true,result.dismissal().liveRequested());eq(swipe,result.replace());eq("10:00",c.notification().shortCriticalText());
                eq(false,s.dismiss(lunch));eq(false,s.dismiss(cooldown));eq(0L,c.e.used(X));
            }
        });
        test("early lunch Stop and delayed callbacks leave usage and cooldown unchanged",()->{
            Clock c=setup(TimerMode.INDIVIDUAL);State s=new State(0,false);c.e.startManualLunch(c.wall,c.elapsed);
            Dismissal lunch=post(s,c,true).dismissal();c.minutes(15);c.e.stopLunch(c.wall,c.elapsed);
            Dismissal cooldown=post(s,c,true).dismissal();eq(COOLDOWN,c.cooldown(I));eq(false,s.dismiss(lunch));
            eq(true,s.dismiss(cooldown));c.minutes(65);c.focus(I);eq(true,post(s,c,true).dismissal().liveRequested());
            eq("07:00",c.notification().shortCriticalText());eq(0L,c.e.used(I));
        });
        test("recreation preserves pending return but retires old callback identity",()->{
            Clock c=setup(TimerMode.INDIVIDUAL);State s=new State(0,false);c.focus(X);c.minutes(2);
            Dismissal live=post(s,c,true).dismissal();s.dismiss(live);post(s,c,false);
            c.e=copy(c.e,true);s=new State(s.generation(),s.awaitingReturn());
            eq(false,s.dismiss(live));c.focus(X);eq(false,post(s,c,false).dismissal().liveRequested());
            eq(true,post(s,c,true).replace());eq(2*MINUTE,c.e.used(X));
        });
        test("old live suppression cannot be encoded in new state and repeated ticks retain generation",()->{
            Clock c=setup(TimerMode.INDIVIDUAL);c.focus(X);State s=new State(55,false);
            Dismissal first=post(s,c,true).dismissal();eq(56L,first.generation());eq(true,first.liveRequested());
            c.millis(500);eq(first,post(s,c,true).dismissal());
            s.hidden();Dismissal next=post(s,c,true).dismissal();eq(57L,next.generation());eq(false,s.dismiss(first));
        });
        test("wrong run generation or original surface cannot affect the current notification",()->{
            Clock c=setup(TimerMode.INDIVIDUAL);State s=new State(0,false);c.focus(X);Dismissal current=post(s,c,true).dismissal();
            for(Dismissal wrong:new Dismissal[]{new Dismissal(current.run()+1,current.generation(),true),
                    new Dismissal(current.run(),current.generation()+1,true),new Dismissal(current.run(),current.generation(),false)})eq(false,s.dismiss(wrong));
            eq(false,s.awaitingReturn());eq(true,s.dismiss(current));s.newRun();eq(false,s.awaitingReturn());eq(false,s.dismiss(current));
        });
        test("canonical v2 dismissal identity rejects legacy malformed and missing provenance",()->{
            for(long run:new long[]{0,1,Long.MAX_VALUE})for(boolean live:new boolean[]{false,true}) {
                Dismissal d=new Dismissal(run,42,live);eq(d,parse(d.identity()));
                eq(false,d.identity().equals(new Dismissal(run,43,live).identity()));
            }
            for(String bad:new String[]{null,"","socialpause://notification-dismiss/v1/1/live",
                    "socialpause://notification-dismiss/v2/1/0/live","socialpause://notification-dismiss/v2/-1/1/live",
                    "socialpause://notification-dismiss/v2/01/1/live","socialpause://notification-dismiss/v2/1/+1/live",
                    "socialpause://notification-dismiss/v2/1/1/live/","socialpause://notification-dismiss/v2/1/1/unknown",
                    "socialpause://notification-dismiss/v2/1/9223372036854775808/live"})eq(null,parse(bad));
        });
        test("pending recovery respects screen locking sleep and lunch",()->{
            Clock c=setup(TimerMode.INDIVIDUAL);State s=new State(0,false);c.focus(X);s.dismiss(post(s,c,true).dismissal());
            c.e.focus(X,false,c.wall,c.elapsed);eq(false,post(s,c,false).dismissal().liveRequested());
            c.minutes(1);eq(APP_LIMIT,c.e.remaining(X));c.focus(X);
            c.e.sleep(true,600,660);eq(TimerPresentation.Kind.HIDDEN,c.notification().kind);s.hidden();
            eq(true,s.awaitingReturn());c.e.sleepEnabled=false;eq(true,post(s,c,true).replace());
            c.e.startManualLunch(c.wall,c.elapsed);eq(false,post(s,c,true).dismissal().liveRequested());
        });
        test("shared limiting countdown remains correct after ordinary dismissal and shared exhaustion",()->{
            Clock c=new Clock();c.e=new RulesEngine();c.e.lunchEnabled=false;c.e.sleepEnabled=false;
            c.e.setTimerMode(TimerMode.SHARED);c.e.setSharedLimit(2*MINUTE);c.e.start(c.wall,c.elapsed);State s=new State(0,false);
            s.dismiss(post(s,c,true).dismissal());c.focus(X);eq(true,post(s,c,true).replace());
            eq(true,c.notification().sharedLimiting);eq("02:00",c.notification().shortCriticalText());
            c.minutes(2);eq(TimerPresentation.Kind.SHARED_COOLDOWN,c.notification().kind);eq(false,post(s,c,true).dismissal().liveRequested());
        });
    }
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));run();
        System.out.println(tests+" notification dismissal scenarios passed.");
    }
}
