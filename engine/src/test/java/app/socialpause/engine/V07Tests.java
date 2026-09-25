package app.socialpause.engine;

import java.time.LocalDate;
import java.util.*;
import static app.socialpause.engine.EngineTests.*;
import static app.socialpause.engine.RulesEngine.*;
import static app.socialpause.engine.V05Tests.*;
import static app.socialpause.engine.V06Tests.*;

/** Six-hour user Stop lock, system recovery and original-v0.6 serialization scenarios. */
final class V07Tests {
    static long history(Clock c) { return c.e.history().total(LocalDate.of(2026,9,1),LocalDate.of(2026,10,31)); }
    static void run() {
        test("only a stopped-to-running Start establishes a six-hour lock",()->{
            Clock c=new Clock(false);eq(false,c.e.canStop(c.elapsed));eq(0L,c.e.stopLockRemaining(c.elapsed));
            c.e.stop(c.wall,c.elapsed);eq(0L,c.e.cycleId());c.e.start(c.wall,c.elapsed);
            eq(true,c.e.running);eq(1L,c.e.cycleId());eq(360*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(false,c.e.canStop(c.elapsed));
        });
        test("main Stop is rejected without clearing focus usage or notification",()->{
            Clock c=new Clock();c.focus(X);c.minutes(3);long run=c.e.cycleId();
            rejects(IllegalStateException.class,()->c.e.stop(c.wall,c.elapsed));
            eq(true,c.e.running);eq(X,c.e.focused());eq(3*MINUTE,c.e.used(X));eq(3*MINUTE,history(c));
            eq(run,c.e.cycleId());eq(357*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(true,c.notification().activeChip());
        });
        test("the exact six-hour boundary enables Stop without automatically stopping",()->{
            Clock c=new Clock();c.millis(360*MINUTE-1);eq(1L,c.e.stopLockRemaining(c.elapsed));eq(false,c.e.canStop(c.elapsed));
            rejects(IllegalStateException.class,()->c.e.stop(c.wall,c.elapsed));c.millis(1);
            eq(true,c.e.running);eq(true,c.e.canStop(c.elapsed));eq(0L,c.e.stopLockRemaining(c.elapsed));
            c.e.stop(c.wall,c.elapsed);eq(Mode.STOPPED,c.mode());eq(false,c.e.canStop(c.elapsed));eq(0L,c.e.stopLockRemaining(c.elapsed));
        });
        test("Stop deadline does not depend on a timely callback",()->{
            Clock c=new Clock();long end=c.elapsed+360*MINUTE;
            eq(false,c.e.canStop(end-1));eq(true,c.e.canStop(end));eq(true,c.e.running);
            c.e.stop(c.wall+360*MINUTE,end);eq(false,c.e.running);eq(0L,c.e.stopLockRemaining(end));
        });
        test("repeated Start cannot reset budgets focus run identity or lock",()->{
            Clock c=shared(20*MINUTE);c.focus(X);c.minutes(3);long run=c.e.cycleId();
            c.e.start(c.wall,c.elapsed);eq(run,c.e.cycleId());eq(X,c.e.focused());eq(3*MINUTE,c.e.used(X));
            eq(17*MINUTE,c.e.sharedRemaining());eq(357*MINUTE,c.e.stopLockRemaining(c.elapsed));
            c.focus(null);c.minutes(357);c.e.start(c.wall,c.elapsed);eq(true,c.e.canStop(c.elapsed));eq(run,c.e.cycleId());eq(3*MINUTE,c.e.used(X));
        });
        test("another successful Start creates a fresh lock and preserves history",()->{
            Clock c=new Clock();c.focus(I);c.minutes(2);c.stopWhenUnlocked();long run=c.e.cycleId();
            c.e.start(c.wall,c.elapsed);eq(run+1,c.e.cycleId());eq(360*MINUTE,c.e.stopLockRemaining(c.elapsed));
            eq(INSTAGRAM_LIMIT,c.e.remaining(I));eq(2*MINUTE,history(c));rejects(IllegalStateException.class,()->c.e.stop(c.wall,c.elapsed));
        });
        test("wall-clock and time-zone changes cannot shorten the Stop lock",()->{
            Clock c=new Clock();c.minutes(30);TimeZone saved=TimeZone.getDefault();
            try {
                c.wall+=3*24*60*MINUTE;c.e.advance(c.wall,c.elapsed);eq(330*MINUTE,c.e.stopLockRemaining(c.elapsed));
                c.wall-=7*24*60*MINUTE;TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
                c.e.advance(c.wall,c.elapsed);eq(330*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(false,c.e.canStop(c.elapsed));
                c.minutes(330);eq(true,c.e.canStop(c.elapsed));
            } finally { TimeZone.setDefault(saved); }
        });
        test("screen-off and unselected-app time count toward Stop without charging usage",()->{
            Clock c=new Clock();c.focus(X);c.minutes(2);c.e.focus(X,false,c.wall,c.elapsed);c.minutes(180);
            eq(178*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(2*MINUTE,c.e.used(X));
            c.focus("com.android.settings");c.minutes(178);eq(true,c.e.canStop(c.elapsed));eq(2*MINUTE,c.e.used(X));eq(2*MINUTE,history(c));
        });
        test("Sleep Time hides notifications but does not pause the six-hour lock",()->{
            Clock c=new Clock();c.e.sleep(true,600,1080);eq(TimerPresentation.Kind.HIDDEN,c.notification().kind);
            c.minutes(360);eq(true,c.e.quiet(c.wall));eq(true,c.e.canStop(c.elapsed));eq(TimerPresentation.Kind.HIDDEN,c.notification().kind);
        });
        test("app and shared cooldown reset neither refill nor extend the Stop lock",()->{
            Clock c=shared(10*MINUTE);c.focus(X);c.minutes(10);eq(COOLDOWN,c.cooldown(X));
            eq(350*MINUTE,c.e.stopLockRemaining(c.elapsed));c.minutes(60);eq(10*MINUTE,c.e.sharedRemaining());
            eq(290*MINUTE,c.e.stopLockRemaining(c.elapsed));c.focus(I);c.minutes(7);c.minutes(60);
            eq(3*MINUTE,c.e.sharedRemaining());eq(223*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(INSTAGRAM_LIMIT,c.e.remaining(I));
        });
        test("manual lunch and early Stop lunch retain the main Stop deadline",()->{
            Clock c=new Clock();c.focus(X);c.minutes(4);c.e.startManualLunch(c.wall,c.elapsed);
            eq(356*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(APP_LIMIT,c.e.remaining(X));
            c.minutes(17);c.e.stopLunch(c.wall,c.elapsed);eq(COOLDOWN,c.cooldown(X));eq(339*MINUTE,c.e.stopLockRemaining(c.elapsed));
            rejects(IllegalStateException.class,()->c.e.stop(c.wall,c.elapsed));c.minutes(60);
            eq(Mode.READY,c.mode());eq(279*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(4*MINUTE,history(c));
        });
        test("scheduled lunch and its cooldown count toward six-hour unlock",()->{
            Clock c=new Clock();c.lunch();c.minutes(240);eq(Mode.LUNCH,c.mode());eq(120*MINUTE,c.e.stopLockRemaining(c.elapsed));
            c.minutes(60);eq(Mode.LUNCH_COOLDOWN,c.mode());eq(60*MINUTE,c.e.stopLockRemaining(c.elapsed));
            c.minutes(60);eq(Mode.READY,c.mode());eq(true,c.e.canStop(c.elapsed));eq(true,c.e.running);
        });
        test("focused countdown resumes after manual lunch and cooldown in both modes",()->{
            for(TimerMode mode:TimerMode.values()) {
                Clock c=configured(mode,5*MINUTE,7*MINUTE,10*MINUTE,10*MINUTE);
                c.e.startManualLunch(c.wall,c.elapsed);c.minutes(60);eq(TimerPresentation.Kind.LUNCH_COOLDOWN,c.notification().kind);
                c.minutes(60);c.focus(X);eq(TimerPresentation.Kind.APP,c.notification().kind);eq(true,c.notification().activeChip());
                eq(mode==TimerMode.SHARED?"05:00":"10:00",c.notification().shortCriticalText());
                c.millis(1_000);eq(mode==TimerMode.SHARED?"04:59":"09:59",c.notification().shortCriticalText());
                eq(240*MINUTE-1_000,c.e.stopLockRemaining(c.elapsed));
            }
        });
        test("a delayed callback through lunch expiry leaves the original lock intact",()->{
            Clock c=new Clock();c.e.startManualLunch(c.wall,c.elapsed);c.minutes(185);
            eq(Mode.READY,c.mode());eq(175*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(0L,history(c));
            c.focus(X);eq("10:00",c.notification().shortCriticalText());
        });
        test("same-boot process recreation retains the exact unlock deadline and budgets",()->{
            Clock c=shared(20*MINUTE);c.focus(X);c.minutes(3);c.e=copy(c.e,true);c.minutes(57);
            eq(300*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(3*MINUTE,c.e.used(X));eq(17*MINUTE,c.e.sharedRemaining());eq(null,c.e.focused());
            c.e=copy(c.e,true);c.minutes(300);eq(true,c.e.canStop(c.elapsed));eq(true,c.e.running);eq(3*MINUTE,history(c));
        });
        test("system Stop bypasses the lock during usage cooldown and lunch",()->{
            for(int phase=0;phase<3;phase++) {
                Clock c=new Clock();c.focus(I);c.minutes(phase==1?7:2);
                if(phase==2)c.e.startManualLunch(c.wall,c.elapsed);
                rejects(IllegalStateException.class,()->c.e.stop(c.wall,c.elapsed));long total=history(c);
                c.e.systemStop(c.wall,c.elapsed);eq(false,c.e.running);eq(null,c.e.focused());eq(0L,c.e.stopLockRemaining(c.elapsed));
                eq(TimerPresentation.Kind.HIDDEN,c.notification().kind);c.e=copy(c.e,true);c.minutes(1);eq(false,c.e.running);eq(total,history(c));
                c.e.start(c.wall,c.elapsed);eq(360*MINUTE,c.e.stopLockRemaining(c.elapsed));
                if(phase==2){eq(Mode.LUNCH,c.mode());eq(true,c.e.manualLunchUsedToday(c.wall));eq(59*MINUTE,c.e.countdown(c.wall,c.elapsed));}
            }
        });
        test("reboot stops monitoring clears the elapsed lock and preserves history settings",()->{
            Clock c=configured(TimerMode.SHARED,13*MINUTE,2*MINUTE,12*MINUTE,0);c.focus(I);c.minutes(2);c.focus(X);c.minutes(3);
            c.e=copy(c.e,false);c.elapsed=100;c.e.advance(c.wall,c.elapsed);
            eq(Mode.STOPPED,c.mode());eq(0L,c.e.stopLockRemaining(c.elapsed));eq(false,c.blocked(R));eq(13*MINUTE,c.e.sharedRemaining());
            eq(2*MINUTE,c.e.limit(I));eq(12*MINUTE,c.e.limit(X));eq(0L,c.e.limit(R));eq(5*MINUTE,history(c));
            c.minutes(15);eq(false,c.e.running);c.e.start(c.wall,c.elapsed);eq(360*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(true,c.blocked(R));
        });
        test("reboot retains active lunch endpoints and eligibility until explicit Start",()->{
            for(boolean cooldown:new boolean[]{false,true}) {
                Clock c=new Clock();c.lunch();c.e.startManualLunch(c.wall,c.elapsed);c.minutes(cooldown?75:15);
                c.e=copy(c.e,false);c.elapsed=100;c.e.advance(c.wall,c.elapsed);eq(Mode.STOPPED,c.mode());eq(true,c.e.manualLunchUsedToday(c.wall));
                eq(0L,c.e.stopLockRemaining(c.elapsed));c.minutes(10);eq(false,c.e.running);c.e.start(c.wall,c.elapsed);
                eq(cooldown?Mode.LUNCH_COOLDOWN:Mode.LUNCH,c.mode());eq(35*MINUTE,c.e.countdown(c.wall,c.elapsed));
                eq(360*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(false,c.e.manualLunchAvailable(c.wall));eq(wall(14,0)+24*60*MINUTE,c.e.nextScheduledLunch(c.wall));
            }
        });
        test("main Stop guard keeps mode selection app selection and allowance edits stopped-only",()->{
            Clock c=new Clock();rejects(IllegalStateException.class,()->c.e.stop(c.wall,c.elapsed));
            rejects(IllegalStateException.class,()->c.e.select(Set.of(X)));
            rejects(IllegalStateException.class,()->c.e.setTimerMode(TimerMode.SHARED));
            rejects(IllegalStateException.class,()->c.e.setAppLimit(X,MINUTE));
            c.stopWhenUnlocked();c.e.select(Set.of(X));c.e.setTimerMode(TimerMode.SHARED);c.e.setSharedLimit(MINUTE);c.e.setAppLimit(X,2*MINUTE);
            c.e.start(c.wall,c.elapsed);eq(360*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(MINUTE,c.e.sharedRemaining());eq(2*MINUTE,c.e.limit(X));
        });
        test("all-zero configuration retains the main Stop lock without cooldown loops",()->{
            Clock c=configured(TimerMode.SHARED,MINUTE,0,0,0);c.minutes(359);
            eq(MINUTE,c.e.stopLockRemaining(c.elapsed));eq(TimerPresentation.Kind.NO_ALLOWANCE,c.notification().kind);
            rejects(IllegalStateException.class,()->c.e.stop(c.wall,c.elapsed));c.minutes(1);eq(true,c.e.canStop(c.elapsed));eq(0L,c.e.sharedCooldownRemaining(c.elapsed));
        });
        test("v06 partial usage and cooldown migrate without a retrospective Stop lock",()->{
            Clock c=fixture("legacy-v06-partial.bin",wall(10,5),301_000);
            eq(true,c.e.running);eq(true,c.e.canStop(c.elapsed));eq(0L,c.e.stopLockRemaining(c.elapsed));
            eq(2*MINUTE,c.e.used(I));eq(57*MINUTE,c.cooldown(I));eq(3*MINUTE,c.e.used(X));eq(8*MINUTE,c.e.sharedRemaining());eq(5*MINUTE,history(c));
            eq(900,c.e.lunchMinute);eq(true,c.e.lunchEnabled);eq(1260,c.e.sleepStart);eq(540,c.e.sleepEnd);eq(0L,c.e.limit(R));
            c.e.start(c.wall,c.elapsed);eq(0L,c.e.stopLockRemaining(c.elapsed));eq(3*MINUTE,c.e.used(X));
            c.e=copy(c.e,true);eq(true,c.e.canStop(c.elapsed));c.e.stop(c.wall,c.elapsed);c.e.start(c.wall,c.elapsed);
            eq(360*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(13*MINUTE,c.e.sharedRemaining());eq(5*MINUTE,history(c));
        });
        test("v06 active group cooldown migration preserves its deadline and configuration",()->{
            Clock c=fixture("legacy-v06-shared-cooldown.bin",wall(10,18),1_081_000);
            eq(Mode.SHARED_COOLDOWN,c.mode());eq(55*MINUTE,c.cooldown(X));eq(0L,c.e.sharedRemaining());eq(13*MINUTE,history(c));eq(true,c.e.canStop(c.elapsed));
            c.minutes(55);eq(Mode.READY,c.mode());eq(13*MINUTE,c.e.sharedRemaining());eq(12*MINUTE,c.e.remaining(X));eq(0L,c.e.stopLockRemaining(c.elapsed));
        });
        test("v06 lunch and early-stop cooldown migrate without extending phase or granting quota",()->{
            Clock lunch=fixture("legacy-v06-manual-lunch.bin",wall(10,20),1_201_000);
            eq(Mode.LUNCH,lunch.mode());eq(45*MINUTE,lunch.e.countdown(lunch.wall,lunch.elapsed));eq(true,lunch.e.manualLunchUsedToday(lunch.wall));eq(true,lunch.e.canStop(lunch.elapsed));eq(2*MINUTE,history(lunch));
            Clock cooldown=fixture("legacy-v06-manual-cooldown.bin",wall(10,30),1_801_000);
            eq(Mode.LUNCH_COOLDOWN,cooldown.mode());eq(50*MINUTE,cooldown.cooldown(X));eq(true,cooldown.e.manualLunchUsedToday(cooldown.wall));eq(true,cooldown.e.canStop(cooldown.elapsed));
            cooldown.minutes(50);eq(Mode.READY,cooldown.mode());eq(2*MINUTE,history(cooldown));eq(13*MINUTE,cooldown.e.sharedRemaining());
        });
        test("v06 stopped state waits for Start before creating its first lock",()->{
            Clock c=fixture("legacy-v06-stopped.bin",wall(10,5),301_000);eq(Mode.STOPPED,c.mode());eq(0L,c.e.stopLockRemaining(c.elapsed));eq(5*MINUTE,history(c));
            c.e.start(c.wall,c.elapsed);eq(360*MINUTE,c.e.stopLockRemaining(c.elapsed));eq(13*MINUTE,c.e.sharedRemaining());eq(5*MINUTE,history(c));
        });
    }
}
