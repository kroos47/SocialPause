package app.socialpause.engine;

import java.io.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static app.socialpause.engine.EngineTests.*;
import static app.socialpause.engine.RulesEngine.*;

/** New-mode, lunch policy and real-v0.4 migration scenarios. */
final class V05Tests {
    static Clock shared(long limit) {
        Clock c = new Clock(); c.e.stop(c.wall,c.elapsed); c.e.setTimerMode(TimerMode.SHARED);
        c.e.setSharedLimit(limit); c.e.start(c.wall,c.elapsed); return c;
    }
    static void rejects(Class<? extends RuntimeException> type, Runnable action) {
        try { action.run(); } catch (RuntimeException ex) {
            if (type.isInstance(ex)) return; throw ex;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    static long wall(int hour,int minute) { return LocalDate.of(2026,9,16).atTime(hour,minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    static Clock fixture(String name,long now,long elapsed) {
        Clock c = new Clock();
        try (var in = new ObjectInputStream(Objects.requireNonNull(V05Tests.class.getResourceAsStream("/"+name)))) {
            c.e = (RulesEngine) in.readObject(); c.e.attach(true); c.wall=now; c.elapsed=elapsed; c.e.advance(now,elapsed); return c;
        } catch (Exception ex) { throw new AssertionError(ex); }
    }
    static void run() {
        test("shared configuration defaults and stopped Shared-mode validation",()->{
            Clock c=new Clock();eq(TimerMode.INDIVIDUAL,c.e.timerMode());eq(20*MINUTE,c.e.sharedLimit());
            rejects(IllegalStateException.class,()->c.e.setTimerMode(TimerMode.SHARED));
            rejects(IllegalStateException.class,()->c.e.setSharedLimit(10*MINUTE));
            c.e.stop(c.wall,c.elapsed);rejects(IllegalStateException.class,()->c.e.setSharedLimit(20*MINUTE));c.e.setTimerMode(TimerMode.SHARED);
            for(long value:new long[]{0,30*MINUTE+1,31*MINUTE,MINUTE+1})rejects(IllegalArgumentException.class,()->c.e.setSharedLimit(value));
            for(long value:new long[]{MINUTE,20*MINUTE,30*MINUTE}){c.e.setSharedLimit(value);eq(value,c.e.sharedLimit());eq(value,c.e.configuredSharedLimit());}
        });
        test("shared budget counts actual usage across app switches and pauses",()->{
            Clock c=shared(20*MINUTE);c.focus(I);c.minutes(3);c.focus(X);c.minutes(4);c.focus(null);c.minutes(60);
            eq(13*MINUTE,c.e.sharedRemaining());eq(4*MINUTE,c.e.remaining(I));eq(6*MINUTE,c.e.remaining(X));
            c.e.focus(R,false,c.wall,c.elapsed);c.minutes(5);eq(13*MINUTE,c.e.sharedRemaining());
        });
        test("individual cooldown still starts before shared exhaustion",()->{
            Clock c=shared(20*MINUTE);c.focus(I);c.minutes(7);eq(COOLDOWN,c.cooldown(I));eq(13*MINUTE,c.e.sharedRemaining());eq(false,c.blocked(X));
            c.focus(X);c.minutes(10);eq(COOLDOWN,c.cooldown(X));eq(3*MINUTE,c.e.sharedRemaining());eq(50*MINUTE,c.cooldown(I));
            c.focus(R);c.minutes(3);eq(Mode.SHARED_COOLDOWN,c.mode());eq(COOLDOWN,c.e.sharedCooldownRemaining(c.elapsed));
            for(String p:c.e.selected){eq(true,c.blocked(p));eq(COOLDOWN,c.cooldown(p));}
        });
        test("shared exhaustion clears all allowances once after the full hour",()->{
            Clock c=shared(5*MINUTE);long run=c.e.cycleId();c.focus(I);c.minutes(5);c.minutes(59);eq(true,c.blocked(X));c.minutes(1);
            eq(Mode.READY,c.mode());eq(5*MINUTE,c.e.sharedRemaining());for(String p:c.e.selected){eq(c.e.limit(p),c.e.remaining(p));eq(false,c.blocked(p));}
            eq(run,c.e.cycleId());eq(5*MINUTE,c.e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));
            c.focus(X);c.minutes(2);eq(3*MINUTE,c.e.sharedRemaining());
        });
        test("shared and app simultaneous exhaustion have one global end",()->{
            Clock c=shared(10*MINUTE);c.focus(X);c.minutes(10);eq(COOLDOWN,c.cooldown(X));eq(COOLDOWN,c.cooldown(I));c.minutes(60);
            eq(0L,c.cooldown(X));eq(APP_LIMIT,c.e.remaining(X));eq(10*MINUTE,c.e.sharedRemaining());
        });
        test("individual reset never restores consumed shared minutes",()->{
            Clock c=shared(20*MINUTE);c.focus(I);c.minutes(7);c.minutes(60);eq(INSTAGRAM_LIMIT,c.e.remaining(I));eq(13*MINUTE,c.e.sharedRemaining());
            c.focus(I);c.minutes(7);eq(6*MINUTE,c.e.sharedRemaining());
        });
        test("shared delayed callback anchors exhaustion and does not count after reset",()->{
            Clock c=shared(5*MINUTE);c.focus(X);c.minutes(12);eq(53*MINUTE,c.e.sharedCooldownRemaining(c.elapsed));eq(5*MINUTE,c.e.used(X));
            c.minutes(60);eq(5*MINUTE,c.e.sharedRemaining());eq(null,c.e.focused());eq(5*MINUTE,c.e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));
        });
        test("shared deadline survives process recovery and is an alarm boundary",()->{
            Clock c=shared(5*MINUTE);c.focus(X);c.minutes(5);eq(c.wall+COOLDOWN,c.e.nextBoundary(c.wall,c.elapsed));c.e=copy(c.e,true);c.minutes(17);
            eq(43*MINUTE,c.cooldown(R));eq(0L,c.e.sharedRemaining());eq(TimerMode.SHARED,c.e.timerMode());
        });
        test("partial shared usage survives process recreation",()->{
            Clock c=shared(20*MINUTE);c.focus(X);c.minutes(3);c.e=copy(c.e,true);c.minutes(20);eq(17*MINUTE,c.e.sharedRemaining());eq(3*MINUTE,c.e.used(X));eq(null,c.e.focused());
        });
        test("shared mode switching preserves history and selected settings",()->{
            Clock c=shared(20*MINUTE);c.focus(I);c.minutes(3);c.e.stop(c.wall,c.elapsed);c.e.setTimerMode(TimerMode.INDIVIDUAL);c.e.start(c.wall,c.elapsed);
            eq(TimerMode.INDIVIDUAL,c.e.timerMode());eq(INSTAGRAM_LIMIT,c.e.remaining(I));eq(3*MINUTE,c.e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));
        });
        test("notification shows the shared limiter and retains app rows",()->{
            Clock c=shared(20*MINUTE);c.focus(I);c.minutes(7);c.focus(X);c.minutes(10);c.focus(R);var p=c.notification();
            eq(TimerPresentation.Kind.APP,p.kind);eq(R,p.app);eq(true,p.sharedLimiting);eq(3*MINUTE,p.remaining);eq(20*MINUTE,p.limit);eq("03:00",p.shortCriticalText());
            eq(APP_LIMIT,p.rows.get(2).remaining());eq(3*MINUTE,p.sharedRemaining);c.focus(null);eq(false,c.notification().activeChip());eq(3*MINUTE,c.notification().sharedRemaining);
        });
        test("notification chooses app limit first and no chip for global cooldown",()->{
            Clock c=shared(20*MINUTE);c.focus(I);var p=c.notification();eq(false,p.sharedLimiting);eq(INSTAGRAM_LIMIT,p.remaining);eq(INSTAGRAM_LIMIT,p.limit);
            c.minutes(7);c.focus(X);c.minutes(10);c.focus(R);c.minutes(3);p=c.notification();eq(TimerPresentation.Kind.SHARED_COOLDOWN,p.kind);eq(false,p.activeChip());eq("",p.shortCriticalText());
            for(var row:p.rows){eq(true,row.cooling());eq(COOLDOWN,row.remaining());}
            Clock individual=new Clock();eq(0L,individual.notification().sharedLimit);eq(0L,individual.notification().sharedRemaining);
        });
        test("manual lunch overrides individual cooldown and excludes history",()->{
            Clock c=new Clock();c.focus(I);c.minutes(7);c.e.startManualLunch(c.wall,c.elapsed);eq(Mode.LUNCH,c.mode());eq(true,c.e.isManualLunch());eq(false,c.blocked(I));
            c.focus(X);c.minutes(60);eq(Mode.LUNCH_COOLDOWN,c.mode());eq(COOLDOWN,c.cooldown(I));c.minutes(60);eq(Mode.READY,c.mode());eq(false,c.e.isManualLunch());
            eq(7*MINUTE,c.e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));
        });
        test("manual lunch overrides shared cooldown and refreshes shared allowance",()->{
            Clock c=shared(5*MINUTE);c.focus(X);c.minutes(5);c.e.startManualLunch(c.wall,c.elapsed);eq(Mode.LUNCH,c.mode());eq(0L,c.e.sharedCooldownRemaining(c.elapsed));eq(5*MINUTE,c.e.sharedRemaining());
            c.minutes(120);eq(Mode.READY,c.mode());eq(5*MINUTE,c.e.sharedRemaining());
        });
        test("manual before automatic replaces the whole same-day scheduled lunch",()->{
            Clock c=new Clock();c.lunch();c.minutes(120);c.e.startManualLunch(c.wall,c.elapsed);eq(wall(14,0)+24*60*MINUTE,c.e.nextScheduledLunch(c.wall));
            c.minutes(120);eq(Mode.READY,c.mode());c.minutes(60);eq(Mode.READY,c.mode());eq(false,c.blocked(X));
        });
        test("manual lunch after automatic is one extra unrestricted hour",()->{
            Clock c=new Clock();c.lunch();c.minutes(360);eq(Mode.READY,c.mode());c.e.startManualLunch(c.wall,c.elapsed);eq(Mode.LUNCH,c.mode());eq(COOLDOWN,c.e.countdown(c.wall,c.elapsed));
            c.minutes(120);rejects(IllegalStateException.class,()->c.e.startManualLunch(c.wall,c.elapsed));
        });
        test("manual can replace active automatic lunch or its post-lunch block",()->{
            for(int offset:new int[]{250,310}){Clock c=new Clock();c.lunch();c.minutes(offset);c.e.startManualLunch(c.wall,c.elapsed);eq(true,c.e.isManualLunch());eq(COOLDOWN,c.e.countdown(c.wall,c.elapsed));c.minutes(60);eq(COOLDOWN,c.cooldown(X));}
        });
        test("manual quota survives stop start selection and process recovery",()->{
            Clock c=new Clock();c.e.startManualLunch(c.wall,c.elapsed);c.minutes(120);c.e.stop(c.wall,c.elapsed);c.e.select(Set.of(X));c.e.setTimerMode(TimerMode.SHARED);c.e.start(c.wall,c.elapsed);c.e=copy(c.e,true);
            eq(true,c.e.manualLunchUsedToday(c.wall));eq(false,c.e.manualLunchAvailable(c.wall));rejects(IllegalStateException.class,()->c.e.startManualLunch(c.wall,c.elapsed));
        });
        test("manual quota and lunch phase survive reboot without replay",()->{
            Clock c=new Clock();c.lunch();c.minutes(180);c.e.startManualLunch(c.wall,c.elapsed);c.minutes(30);c.e=copy(c.e,false);c.elapsed=100;c.e.advance(c.wall,c.elapsed);
            eq(true,c.e.manualLunchUsedToday(c.wall));eq(Mode.LUNCH,c.mode());eq(30*MINUTE,c.e.countdown(c.wall,c.elapsed));c.minutes(90);eq(Mode.READY,c.mode());eq(wall(14,0)+24*60*MINUTE,c.e.nextScheduledLunch(c.wall));
        });
        test("stopped manual phase still resumes correctly after reboot",()->{
            Clock c=new Clock();c.e.startManualLunch(c.wall,c.elapsed);c.minutes(10);c.e.stop(c.wall,c.elapsed);c.e=copy(c.e,false);c.elapsed=50;c.wall+=20*MINUTE;c.e.start(c.wall,c.elapsed);
            eq(Mode.LUNCH,c.mode());eq(30*MINUTE,c.e.countdown(c.wall,c.elapsed));eq(true,c.e.manualLunchUsedToday(c.wall));
        });
        test("manual quota renews on next local day while retaining overnight phase",()->{
            Clock c=new Clock();c.minutes(810);c.e.startManualLunch(c.wall,c.elapsed);c.minutes(40);eq(Mode.LUNCH,c.mode());eq(false,c.e.manualLunchUsedToday(c.wall));eq(true,c.e.manualLunchAvailable(c.wall));
            c.e.startManualLunch(c.wall,c.elapsed);eq(COOLDOWN,c.e.countdown(c.wall,c.elapsed));eq(true,c.e.manualLunchUsedToday(c.wall));
        });
        test("stop manual lunch early starts a full hour and cannot be repeated",()->{
            Clock c=new Clock();c.e.startManualLunch(c.wall,c.elapsed);c.minutes(17);c.e.stopLunch(c.wall,c.elapsed);eq(Mode.LUNCH_COOLDOWN,c.mode());eq(COOLDOWN,c.cooldown(I));
            rejects(IllegalStateException.class,()->c.e.stopLunch(c.wall,c.elapsed));c.minutes(60);eq(Mode.READY,c.mode());eq(true,c.e.manualLunchUsedToday(c.wall));
        });
        test("stop automatic lunch early starts a full hour without consuming manual quota",()->{
            Clock c=new Clock();c.lunch();c.minutes(240);c.minutes(10);c.e.stopLunch(c.wall,c.elapsed);eq(COOLDOWN,c.cooldown(X));eq(false,c.e.manualLunchUsedToday(c.wall));
            c.minutes(60);eq(Mode.READY,c.mode());c.minutes(50);eq(Mode.READY,c.mode());eq(wall(14,0)+24*60*MINUTE,c.e.nextScheduledLunch(c.wall));
        });
        test("manual lunch requires running and early stop requires an active lunch",()->{
            Clock c=new Clock();rejects(IllegalStateException.class,()->c.e.stopLunch(c.wall,c.elapsed));c.e.stop(c.wall,c.elapsed);eq(false,c.e.manualLunchAvailable(c.wall));rejects(IllegalStateException.class,()->c.e.startManualLunch(c.wall,c.elapsed));
        });
        test("schedule can move later or earlier today when both starts are future",()->{
            Clock c=new Clock();c.lunch();c.focus(X);c.minutes(2);c.focus(null);c.e.lunch(true,780,c.wall,c.elapsed);eq(0L,c.e.pendingAt());eq(780,c.e.lunchMinute);eq(2*MINUTE,c.e.used(X));eq(wall(13,0),c.e.nextScheduledLunch(c.wall));
            c.e.lunch(true,900,c.wall,c.elapsed);eq(wall(15,0),c.e.nextScheduledLunch(c.wall));eq(2*MINUTE,c.e.used(X));
        });
        test("proposed past or exact-now lunch start waits until tomorrow",()->{
            for(int minute:new int[]{540,600}){Clock c=new Clock();c.lunch();c.e.lunch(true,minute,c.wall,c.elapsed);eq(840,c.e.lunchMinute);eq(minute,c.e.pendingLunchMinute());eq(wall(0,0)+24*60*MINUTE,c.e.pendingAt());}
        });
        test("old scheduled start reached forces tomorrow including exact boundary",()->{
            Clock c=new Clock();c.lunch();c.minutes(240);c.e.lunch(true,960,c.wall,c.elapsed);eq(Mode.LUNCH,c.mode());eq(840,c.e.lunchMinute);eq(960,c.e.pendingLunchMinute());eq(wall(0,0)+24*60*MINUTE,c.e.pendingAt());
        });
        test("manual lunch today forces future schedule edit to tomorrow",()->{
            Clock c=new Clock();c.lunch();c.e.startManualLunch(c.wall,c.elapsed);c.minutes(120);c.e.lunch(true,900,c.wall,c.elapsed);
            eq(840,c.e.lunchMinute);eq(wall(0,0)+24*60*MINUTE,c.e.pendingAt());eq(wall(15,0)+24*60*MINUTE,c.e.nextScheduledLunch(c.wall));
        });
        test("pending disable excludes tomorrow scheduled lunch",()->{
            Clock c=new Clock();c.lunch();c.minutes(240);c.e.lunch(false,840,c.wall,c.elapsed);eq(0L,c.e.nextScheduledLunch(c.wall));c.minutes(600);eq(false,c.e.lunchEnabled);eq(0L,c.e.pendingAt());
        });
        test("automatic occurrence inside overnight manual phase is skipped without replay",()->{
            Clock c=new Clock();c.e.lunchMinute=15;c.lunch();c.minutes(810);c.e.startManualLunch(c.wall,c.elapsed);c.minutes(120);
            eq(Mode.READY,c.mode());eq(wall(0,15)+2*24*60*MINUTE,c.e.nextScheduledLunch(c.wall));c.minutes(60);eq(Mode.READY,c.mode());
        });
        test("process recovery after overlapping schedule does not replay it",()->{
            Clock c=new Clock();c.e.lunchMinute=15;c.lunch();c.minutes(810);c.e.startManualLunch(c.wall,c.elapsed);c.e=copy(c.e,true);
            c.wall+=135*MINUTE;c.elapsed+=135*MINUTE;c.e.advance(c.wall,c.elapsed);eq(Mode.READY,c.mode());eq(0L,c.cooldown(X));
        });
        test("overlap is not replayed after reboot or stopped monitoring recovery",()->{
            for(int recovery=0;recovery<3;recovery++){
                Clock c=new Clock();c.e.lunchMinute=30;c.lunch();c.minutes(810);c.e.startManualLunch(c.wall,c.elapsed);c.minutes(5);
                if(recovery==2)c.e.stop(c.wall,c.elapsed);
                c.e=copy(c.e,recovery!=1);c.wall+=145*MINUTE;c.elapsed=recovery==1?100:c.elapsed+145*MINUTE;
                if(recovery==2)c.e.start(c.wall,c.elapsed);else c.e.advance(c.wall,c.elapsed);
                eq(Mode.READY,c.mode());eq(0L,c.cooldown(X));eq(wall(0,30)+2*24*60*MINUTE,c.e.nextScheduledLunch(c.wall));
            }
        });
        test("overnight pending edit and manual phase preserve both deadlines",()->{
            Clock c=new Clock();c.e.lunchMinute=1410;c.lunch();c.minutes(810);c.e.lunch(true,30,c.wall,c.elapsed);
            c.minutes(10);c.e.startManualLunch(c.wall,c.elapsed);long end=c.wall+2*COOLDOWN;
            c.e=copy(c.e,true);c.wall=end+15*MINUTE;c.elapsed+=135*MINUTE;c.e.advance(c.wall,c.elapsed);
            eq(Mode.READY,c.mode());eq(30,c.e.lunchMinute);eq(0L,c.e.pendingAt());eq(wall(0,30)+2*24*60*MINUTE,c.e.nextScheduledLunch(c.wall));
        });
        test("next scheduled lunch skips handled automatic occurrence and disabled schedule",()->{
            Clock c=new Clock();eq(0L,c.e.nextScheduledLunch(c.wall));c.lunch();eq(wall(14,0),c.e.nextScheduledLunch(c.wall));c.minutes(240);eq(wall(14,0)+24*60*MINUTE,c.e.nextScheduledLunch(c.wall));
        });
        test("v04 partial usage migrates without resetting timers or history",()->{
            Clock c=fixture("legacy-v04-partial.bin",wall(10,5),301000);eq(TimerMode.INDIVIDUAL,c.e.timerMode());eq(20*MINUTE,c.e.sharedLimit());eq(3*MINUTE,c.e.used(I));eq(2*MINUTE,c.e.used(X));eq(null,c.e.focused());eq(1260,c.e.sleepStart);eq(540,c.e.sleepEnd);
            eq(5*MINUTE,c.e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));c.focus(I);c.minutes(1);c.e=copy(c.e,true);eq(4*MINUTE,c.e.used(I));
        });
        test("v04 cooldowns and pending schedule migrate without resetting deadlines",()->{
            Clock c=fixture("legacy-v04-cooldowns.bin",wall(10,19),1141000);eq(48*MINUTE,c.cooldown(I));eq(58*MINUTE,c.cooldown(X));eq(2*MINUTE,c.e.used(R));eq(900,c.e.pendingLunchMinute());eq(wall(0,0)+24*60*MINUTE,c.e.pendingAt());
            c.minutes(48);eq(false,c.blocked(I));eq(10*MINUTE,c.cooldown(X));eq(19*MINUTE,c.e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));
        });
        test("v04 ongoing lunch and post-lunch phases migrate without extending",()->{
            Clock lunch=fixture("legacy-v04-lunch.bin",wall(14,20),15601000);eq(Mode.LUNCH,lunch.mode());eq(40*MINUTE,lunch.e.countdown(lunch.wall,lunch.elapsed));eq(false,lunch.e.isManualLunch());
            Clock cool=fixture("legacy-v04-lunch.bin",wall(15,20),19201000);eq(Mode.LUNCH_COOLDOWN,cool.mode());eq(40*MINUTE,cool.cooldown(X));eq(false,cool.e.manualLunchUsedToday(cool.wall));cool.minutes(40);eq(Mode.READY,cool.mode());
        });
        test("v04 migration survives reboot and retains active scheduled phase",()->{
            Clock c=fixture("legacy-v04-lunch.bin",wall(14,20),15601000);c.e=copy(c.e,false);c.elapsed=100;c.e.advance(c.wall,c.elapsed);eq(Mode.LUNCH,c.mode());eq(40*MINUTE,c.e.countdown(c.wall,c.elapsed));
        });
        randomShared();
    }
    static void randomShared() {
        test("five thousand shared transitions match an independent model",()->{
            Clock c=shared(20*MINUTE);Random random=new Random(501);String[] packages={I,X,R};long[] used=new long[3],deadlines=new long[3];long sharedUsed=0,globalEnd=0,history=0;
            for(int n=0;n<5000;n++){
                int focus=random.nextInt(5);boolean unlocked=random.nextBoolean();String pkg=focus<3?packages[focus]:null;
                c.e.focus(pkg,unlocked,c.wall,c.elapsed);long delta=random.nextInt(300001),end=c.elapsed+delta;
                if(focus<3&&unlocked&&globalEnd==0&&deadlines[focus]==0){
                    long limit=focus==0?INSTAGRAM_LIMIT:APP_LIMIT;
                    long charged=Math.min(delta,Math.min(limit-used[focus],20*MINUTE-sharedUsed));
                    used[focus]+=charged;sharedUsed+=charged;history+=charged;
                    if(used[focus]==limit)deadlines[focus]=c.elapsed+charged+COOLDOWN;
                    if(sharedUsed==20*MINUTE)globalEnd=c.elapsed+charged+COOLDOWN;
                }
                if(globalEnd>0&&end>=globalEnd){Arrays.fill(used,0);Arrays.fill(deadlines,0);sharedUsed=0;globalEnd=0;}
                for(int a=0;a<3;a++)if(deadlines[a]>0&&end>=deadlines[a]){deadlines[a]=0;used[a]=0;}
                c.millis(delta);eq(20*MINUTE-sharedUsed,c.e.sharedRemaining());
                for(int a=0;a<3;a++){eq(used[a],c.e.used(packages[a]));eq(globalEnd>end||deadlines[a]>end,c.blocked(packages[a]));eq(Math.max(0,(globalEnd>0?globalEnd:deadlines[a])-end),c.cooldown(packages[a]));}
            }
            eq(history,c.e.history().total(LocalDate.of(2026,9,1),LocalDate.of(2026,10,31)));
        });
    }
}
