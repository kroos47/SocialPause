package app.socialpause.engine;

import java.time.LocalDate;
import java.util.*;
import static app.socialpause.engine.EngineTests.*;
import static app.socialpause.engine.V05Tests.*;
import static app.socialpause.engine.RulesEngine.*;

/** Configurable allowances retain the v0.5 independent-cooldown and lunch contracts. */
final class V06Tests {
    static Clock configured(TimerMode mode,long shared,long instagram,long x,long reddit) {
        Clock c=new Clock(false);c.e.setTimerMode(mode);
        if(mode==TimerMode.SHARED)c.e.setSharedLimit(shared);
        c.e.setAppLimit(I,instagram);c.e.setAppLimit(X,x);c.e.setAppLimit(R,reddit);c.e.start(c.wall,c.elapsed);return c;
    }
    static TimerPresentation.Row row(Clock c,String pkg) {
        return c.notification().rows.stream().filter(r->r.app().equals(pkg)).findFirst().orElseThrow();
    }
    static Clock partial45() {return fixture("legacy-v05-shared45-partial.bin",wall(10,19),1_141_000);}
    static long total(Clock c) {return c.e.history().total(LocalDate.of(2026,9,1),LocalDate.of(2026,10,31));}
    static void run() {
        test("app allowance defaults and independent maximums",()->{
            Clock c=new Clock();eq(7*MINUTE,c.e.limit(I));eq(10*MINUTE,c.e.limit(X));eq(10*MINUTE,c.e.limit("other"));
            eq(7*MINUTE,c.e.maximumLimit(I));eq(12*MINUTE,c.e.maximumLimit(X));eq(12*MINUTE,c.e.maximumLimit("other"));
            c.stopWhenUnlocked();c.e.setAppLimit(I,0);c.e.setAppLimit(X,12*MINUTE);eq(0L,c.e.limit(I));eq(12*MINUTE,c.e.limit(X));
        });
        test("app limits require stopped monitoring and whole-minute bounds",()->{
            Clock c=new Clock();rejects(IllegalStateException.class,()->c.e.setAppLimit(I,MINUTE));c.stopWhenUnlocked();
            for(long value:new long[]{-MINUTE,1,8*MINUTE})rejects(IllegalArgumentException.class,()->c.e.setAppLimit(I,value));
            for(long value:new long[]{-1,13*MINUTE,MINUTE+1})rejects(IllegalArgumentException.class,()->c.e.setAppLimit(X,value));
            rejects(IllegalArgumentException.class,()->c.e.setAppLimit("",MINUTE));
        });
        test("configured app boundaries start real individual cooldowns",()->{
            Clock c=configured(TimerMode.INDIVIDUAL,20*MINUTE,2*MINUTE,12*MINUTE,MINUTE);
            c.focus(I);c.millis(2*MINUTE-1);eq(false,c.blocked(I));c.millis(1);eq(COOLDOWN,c.cooldown(I));eq(2*MINUTE,c.e.used(I));
            c.focus(X);c.minutes(12);eq(COOLDOWN,c.cooldown(X));eq(12*MINUTE,c.e.used(X));eq(48*MINUTE,c.cooldown(I));
        });
        test("partial configured allowance pauses and delayed exhaustion anchors correctly",()->{
            Clock c=configured(TimerMode.INDIVIDUAL,20*MINUTE,2*MINUTE,4*MINUTE,MINUTE);
            c.focus(X);c.millis(90_000);c.focus(null);c.minutes(20);eq(150_000L,c.e.remaining(X));
            c.e.focus(X,false,c.wall,c.elapsed);c.minutes(30);eq(150_000L,c.e.remaining(X));c.focus(X);c.minutes(5);
            eq(COOLDOWN-150_000,c.cooldown(X));eq(4*MINUTE,total(c));
        });
        test("zero allowance blocks focus without a timed cooldown",()->{
            Clock c=configured(TimerMode.INDIVIDUAL,20*MINUTE,0,10*MINUTE,10*MINUTE);
            eq(true,c.e.noAllowance(I));eq(true,c.blocked(I));eq(0L,c.cooldown(I));c.focus(I);eq(null,c.e.focused());c.minutes(120);
            eq(0L,c.e.used(I));eq(0L,c.cooldown(I));eq(true,c.blocked(I));eq(2,c.e.availableCount(c.wall,c.elapsed));eq(0L,total(c));
        });
        test("all-zero apps never start an individual or shared cooldown loop",()->{
            for(TimerMode mode:TimerMode.values()){
                Clock c=configured(mode,MINUTE,0,0,0);for(String p:c.e.selected){c.focus(p);c.minutes(70);eq(null,c.e.focused());eq(0L,c.cooldown(p));}
                eq(Mode.READY,c.mode());eq(0L,c.e.sharedCooldownRemaining(c.elapsed));eq(0,c.e.availableCount(c.wall,c.elapsed));eq(null,c.e.nextAvailableApp(c.wall,c.elapsed));eq(0L,total(c));
                eq(TimerPresentation.Kind.NO_ALLOWANCE,c.notification().kind);eq(false,c.notification().ticking());eq("",c.notification().shortCriticalText());
                if(mode==TimerMode.SHARED)eq(MINUTE,c.e.sharedRemaining());
            }
        });
        test("Stop and lunch remain unrestricted even for zero-allowance apps",()->{
            Clock c=configured(TimerMode.SHARED,MINUTE,0,0,0);c.stopWhenUnlocked();eq(false,c.blocked(I));eq(TimerPresentation.Kind.HIDDEN,c.notification().kind);
            c.e.start(c.wall,c.elapsed);c.e.startManualLunch(c.wall,c.elapsed);eq(false,c.blocked(I));eq(3,c.e.availableCount(c.wall,c.elapsed));c.focus(I);c.minutes(60);
            eq(Mode.LUNCH_COOLDOWN,c.mode());eq(true,c.blocked(I));eq(0L,c.cooldown(I));c.minutes(60);eq(Mode.READY,c.mode());eq(true,c.blocked(I));eq(0L,total(c));
        });
        test("zero presentation has an explicit state and safe progress",()->{
            Clock c=configured(TimerMode.INDIVIDUAL,20*MINUTE,0,MINUTE,10*MINUTE);
            eq(TimerPresentation.RowState.NO_ALLOWANCE,row(c,I).state());eq(true,row(c,I).noAllowance());eq(false,row(c,I).cooling());eq(0,row(c,I).progress());
            eq(TimerPresentation.RowState.AVAILABLE,row(c,X).state());c.focus(X);c.millis(30_000);eq(TimerPresentation.RowState.USAGE,row(c,X).state());eq(50,row(c,X).progress());
            c.millis(30_000);eq(TimerPresentation.RowState.COOLDOWN,row(c,X).state());eq(true,row(c,X).cooling());eq(TimerPresentation.Kind.OVERVIEW,c.notification().kind);
        });
        test("mixed zeros and cooling apps never invent a zero-app unlock",()->{
            Clock c=configured(TimerMode.INDIVIDUAL,20*MINUTE,0,MINUTE,0);c.focus(X);c.minutes(1);
            eq(0,c.e.availableCount(c.wall,c.elapsed));eq(TimerPresentation.Kind.OVERVIEW,c.notification().kind);eq(X,c.e.nextAvailableApp(c.wall,c.elapsed));eq(COOLDOWN,c.notification().remaining);
            c.minutes(60);eq(false,c.blocked(X));eq(true,c.blocked(I));eq(TimerPresentation.Kind.OVERVIEW,c.notification().kind);
        });
        test("zero app retains no-allowance state during and after shared cooldown",()->{
            Clock c=configured(TimerMode.SHARED,MINUTE,0,2*MINUTE,0);c.focus(X);c.minutes(1);
            eq(Mode.SHARED_COOLDOWN,c.mode());eq(TimerPresentation.Kind.SHARED_COOLDOWN,c.notification().kind);eq(COOLDOWN,c.notification().remaining);
            eq(true,row(c,I).noAllowance());eq(false,row(c,I).cooling());eq(0L,c.cooldown(I));eq(X,c.e.nextAvailableApp(c.wall,c.elapsed));
            c.minutes(60);eq(true,c.blocked(I));eq(false,c.blocked(X));eq(0L,c.cooldown(I));eq(MINUTE,c.e.sharedRemaining());
        });
        test("all positive app caps start individual hours without a shared trigger",()->{
            Clock c=configured(TimerMode.SHARED,20*MINUTE,MINUTE,MINUTE,MINUTE);
            c.focus(I);c.minutes(1);c.focus(X);c.minutes(1);c.focus(R);c.minutes(1);
            eq(0L,c.e.sharedCooldownRemaining(c.elapsed));eq(17*MINUTE,c.e.sharedRemaining());eq(TimerPresentation.Kind.ALL_COOLDOWN,c.notification().kind);
            eq(58*MINUTE,c.cooldown(I));eq(59*MINUTE,c.cooldown(X));eq(COOLDOWN,c.cooldown(R));
            c.minutes(58);eq(false,c.blocked(I));eq(MINUTE,c.e.remaining(I));eq(17*MINUTE,c.e.sharedRemaining());eq(true,c.blocked(X));
        });
        test("zero apps do not alter independent resets or trigger shared cooldown",()->{
            Clock c=configured(TimerMode.SHARED,20*MINUTE,0,MINUTE,0);c.focus(X);c.minutes(1);
            eq(COOLDOWN,c.cooldown(X));eq(0L,c.e.sharedCooldownRemaining(c.elapsed));c.minutes(60);eq(MINUTE,c.e.remaining(X));eq(19*MINUTE,c.e.sharedRemaining());
        });
        test("one-minute shared allowance and custom caps use the effective countdown",()->{
            Clock c=configured(TimerMode.SHARED,MINUTE,7*MINUTE,12*MINUTE,0);c.focus(X);eq("01:00",c.notification().shortCriticalText());eq(true,c.notification().sharedLimiting);
            c.millis(MINUTE-1);eq(false,c.blocked(X));eq("00:01",c.notification().shortCriticalText());c.millis(1);eq(Mode.SHARED_COOLDOWN,c.mode());eq(COOLDOWN,c.cooldown(X));
        });
        test("per-app settings survive cooldown reset and preserve history",()->{
            Clock c=configured(TimerMode.INDIVIDUAL,20*MINUTE,2*MINUTE,12*MINUTE,0);c.focus(I);c.minutes(2);c.minutes(60);
            eq(2*MINUTE,c.e.remaining(I));eq(2*MINUTE,c.e.limit(I));eq(12*MINUTE,c.e.limit(X));eq(0L,c.e.limit(R));eq(2*MINUTE,total(c));
        });
        test("settings survive mode changes selection and monitoring restarts",()->{
            Clock c=configured(TimerMode.SHARED,13*MINUTE,2*MINUTE,12*MINUTE,0);c.focus(I);c.minutes(1);c.stopWhenUnlocked();
            c.e.setTimerMode(TimerMode.INDIVIDUAL);c.e.select(Set.of(X));c.e.select(new LinkedHashSet<>(List.of(I,X,R)));c.e.start(c.wall,c.elapsed);
            eq(2*MINUTE,c.e.limit(I));eq(12*MINUTE,c.e.limit(X));eq(0L,c.e.limit(R));eq(13*MINUTE,c.e.configuredSharedLimit());eq(1*MINUTE,total(c));
            c.stopWhenUnlocked();c.e.setTimerMode(TimerMode.SHARED);c.e.start(c.wall,c.elapsed);eq(13*MINUTE,c.e.sharedRemaining());
        });
        test("settings survive lunch process recovery and reboot",()->{
            Clock c=configured(TimerMode.SHARED,13*MINUTE,2*MINUTE,12*MINUTE,0);c.e.startManualLunch(c.wall,c.elapsed);c.minutes(15);c.e=copy(c.e,true);
            eq(45*MINUTE,c.e.countdown(c.wall,c.elapsed));c.e=copy(c.e,false);c.elapsed=100;c.e.advance(c.wall,c.elapsed);eq(Mode.STOPPED,c.mode());c.e.start(c.wall,c.elapsed);
            eq(45*MINUTE,c.e.countdown(c.wall,c.elapsed));eq(true,c.e.manualLunchUsedToday(c.wall));eq(2*MINUTE,c.e.limit(I));eq(12*MINUTE,c.e.limit(X));eq(0L,c.e.limit(R));
            c.minutes(105);eq(13*MINUTE,c.e.sharedRemaining());eq(2*MINUTE,c.e.remaining(I));eq(true,c.blocked(R));
        });
        test("unknown-app configured limit persists across deselection",()->{
            Clock c=new Clock(false);c.e.setAppLimit("other.app",12*MINUTE);c.e.select(Set.of("other.app"));c.e.start(c.wall,c.elapsed);c.focus("other.app");c.minutes(12);
            eq(COOLDOWN,c.cooldown("other.app"));c.stopWhenUnlocked();c.e.select(Set.of(I));c.e=copy(c.e,true);eq(12*MINUTE,c.e.limit("other.app"));
        });
        test("changing an allowance while stopped preserves history and starts fresh",()->{
            Clock c=new Clock();c.focus(X);c.minutes(3);c.stopWhenUnlocked();c.e.setAppLimit(X,MINUTE);c.e.start(c.wall,c.elapsed);eq(MINUTE,c.e.remaining(X));eq(3*MINUTE,total(c));
        });
        test("v05 shared45 migration preserves active usage and independent deadlines",()->{
            Clock c=partial45();eq(TimerMode.SHARED,c.e.timerMode());eq(45*MINUTE,c.e.sharedLimit());eq(30*MINUTE,c.e.configuredSharedLimit());eq(26*MINUTE,c.e.sharedRemaining());
            eq(48*MINUTE,c.cooldown(I));eq(58*MINUTE,c.cooldown(X));eq(2*MINUTE,c.e.used(R));eq(19*MINUTE,total(c));eq(900,c.e.lunchMinute);eq(1260,c.e.sleepStart);eq(540,c.e.sleepEnd);eq(null,c.e.focused());
            eq(7*MINUTE,c.e.limit(I));eq(10*MINUTE,c.e.limit(X));eq(10*MINUTE,c.e.limit(R));
        });
        test("migrated active shared budget survives repeated process recovery",()->{
            Clock c=partial45();c.e=copy(c.e,true);c.minutes(20);eq(45*MINUTE,c.e.sharedLimit());eq(26*MINUTE,c.e.sharedRemaining());eq(28*MINUTE,c.cooldown(I));eq(38*MINUTE,c.cooldown(X));
            c.e=copy(c.e,true);eq(30*MINUTE,c.e.configuredSharedLimit());eq(45*MINUTE,c.e.sharedLimit());eq(19*MINUTE,total(c));
        });
        test("individual cooldown expiry never adopts or refills migrated shared budget",()->{
            Clock c=partial45();c.minutes(48);eq(false,c.blocked(I));eq(INSTAGRAM_LIMIT,c.e.remaining(I));eq(10*MINUTE,c.cooldown(X));eq(45*MINUTE,c.e.sharedLimit());eq(26*MINUTE,c.e.sharedRemaining());
            c.focus(I);c.minutes(7);eq(19*MINUTE,c.e.sharedRemaining());eq(45*MINUTE,c.e.sharedLimit());eq(26*MINUTE,total(c));
        });
        test("migrated shared cooldown preserves deadline then adopts30 once",()->{
            Clock c=fixture("legacy-v05-shared45-cooldown.bin",wall(11,40),6_001_000);
            eq(Mode.SHARED_COOLDOWN,c.mode());eq(55*MINUTE,c.e.sharedCooldownRemaining(c.elapsed));eq(45*MINUTE,c.e.sharedLimit());eq(30*MINUTE,c.e.configuredSharedLimit());eq(45*MINUTE,total(c));
            c.minutes(55);eq(Mode.READY,c.mode());eq(30*MINUTE,c.e.sharedLimit());eq(30*MINUTE,c.e.sharedRemaining());eq(APP_LIMIT,c.e.remaining(X));
            c.focus(X);c.minutes(2);c.e=copy(c.e,true);eq(28*MINUTE,c.e.sharedRemaining());eq(47*MINUTE,total(c));
        });
        test("migrated partial shared cycle reaches its original45 before cooldown",()->{
            Clock c=partial45();c.focus(R);c.minutes(8);eq(18*MINUTE,c.e.sharedRemaining());c.minutes(50);
            c.focus(I);c.minutes(7);c.focus(X);c.minutes(10);eq(MINUTE,c.e.sharedRemaining());eq(0L,c.e.sharedCooldownRemaining(c.elapsed));
            c.focus(R);c.minutes(1);eq(Mode.SHARED_COOLDOWN,c.mode());eq(COOLDOWN,c.e.sharedCooldownRemaining(c.elapsed));eq(45*MINUTE,total(c));
            c.minutes(60);eq(30*MINUTE,c.e.sharedRemaining());
        });
        test("manual Stop Start and manual lunch adopt migrated configured budget",()->{
            Clock restart=partial45();restart.e.stop(restart.wall,restart.elapsed);restart.e.start(restart.wall,restart.elapsed);eq(30*MINUTE,restart.e.sharedLimit());eq(30*MINUTE,restart.e.sharedRemaining());eq(19*MINUTE,total(restart));
            Clock lunch=partial45();lunch.e.startManualLunch(lunch.wall,lunch.elapsed);eq(Mode.LUNCH,lunch.mode());eq(30*MINUTE,lunch.e.sharedLimit());eq(19*MINUTE,total(lunch));
        });
        test("automatic lunch adopts migrated future budget without resetting history",()->{
            Clock c=partial45();c.minutes(281);eq(Mode.LUNCH,c.mode());eq(30*MINUTE,c.e.sharedLimit());eq(30*MINUTE,c.e.sharedRemaining());eq(19*MINUTE,total(c));
        });
        test("reboot adopts capped budget but retains settings and history",()->{
            Clock c=partial45();c.e=copy(c.e,false);c.elapsed=100;c.e.advance(c.wall,c.elapsed);eq(30*MINUTE,c.e.sharedLimit());eq(30*MINUTE,c.e.sharedRemaining());eq(0L,c.cooldown(I));eq(19*MINUTE,total(c));eq(900,c.e.lunchMinute);
        });
        test("stopped v05 state adopts valid configuration without losing history",()->{
            Clock c=fixture("legacy-v05-shared45-stopped.bin",wall(10,19),1_141_000);eq(Mode.STOPPED,c.mode());eq(30*MINUTE,c.e.configuredSharedLimit());eq(30*MINUTE,c.e.sharedLimit());eq(19*MINUTE,total(c));
            c.e.start(c.wall,c.elapsed);eq(30*MINUTE,c.e.sharedRemaining());eq(INSTAGRAM_LIMIT,c.e.remaining(I));
        });
        test("v05 manual lunch migration retains deadline quota and suppression",()->{
            Clock c=fixture("legacy-v05-manual-lunch.bin",wall(10,24),1_441_000);eq(Mode.LUNCH,c.mode());eq(55*MINUTE,c.e.countdown(c.wall,c.elapsed));eq(true,c.e.isManualLunch());eq(true,c.e.manualLunchUsedToday(c.wall));eq(45*MINUTE,c.e.sharedLimit());
            eq(7*MINUTE,total(c));c.e=copy(c.e,false);c.elapsed=100;c.e.advance(c.wall,c.elapsed);eq(Mode.STOPPED,c.mode());c.e.start(c.wall,c.elapsed);eq(55*MINUTE,c.e.countdown(c.wall,c.elapsed));eq(true,c.e.manualLunchUsedToday(c.wall));
            c.minutes(115);eq(Mode.READY,c.mode());eq(30*MINUTE,c.e.sharedRemaining());eq(7*MINUTE,total(c));
        });
        test("v05 early-stop block migration keeps full remaining block",()->{
            Clock c=fixture("legacy-v05-manual-block.bin",wall(10,34),2_041_000);eq(Mode.LUNCH_COOLDOWN,c.mode());eq(55*MINUTE,c.cooldown(X));eq(true,c.e.manualLunchUsedToday(c.wall));eq(true,c.e.isManualLunch());
            c.minutes(55);eq(Mode.READY,c.mode());eq(30*MINUTE,c.e.sharedRemaining());eq(7*MINUTE,total(c));
        });
        randomizedConfigurable();
    }
    static void randomizedConfigurable() {
        test("ten thousand configured-limit transitions match independent reference models",()->{
            for(TimerMode mode:TimerMode.values()){
                long[] limits=mode==TimerMode.INDIVIDUAL?new long[]{0,2*MINUTE,12*MINUTE}:new long[]{MINUTE,0,4*MINUTE};
                Clock c=configured(mode,3*MINUTE,limits[0],limits[1],limits[2]);Random random=new Random(mode==TimerMode.SHARED?606:607);
                String[] packages={I,X,R};long[] used=new long[3],ends=new long[3];long sharedUsed=0,globalEnd=0,history=0;
                for(int n=0;n<5000;n++){
                    int focus=random.nextInt(5);boolean unlocked=random.nextBoolean();c.e.focus(focus<3?packages[focus]:null,unlocked,c.wall,c.elapsed);
                    long delta=random.nextInt(240001),end=c.elapsed+delta;
                    if(focus<3&&unlocked&&limits[focus]>0&&ends[focus]==0&&globalEnd==0){
                        long charge=Math.min(delta,limits[focus]-used[focus]);if(mode==TimerMode.SHARED)charge=Math.min(charge,3*MINUTE-sharedUsed);
                        used[focus]+=charge;history+=charge;if(mode==TimerMode.SHARED)sharedUsed+=charge;
                        if(used[focus]==limits[focus])ends[focus]=c.elapsed+charge+COOLDOWN;
                        if(mode==TimerMode.SHARED&&sharedUsed==3*MINUTE)globalEnd=c.elapsed+charge+COOLDOWN;
                    }
                    if(globalEnd>0&&end>=globalEnd){Arrays.fill(used,0);Arrays.fill(ends,0);sharedUsed=0;globalEnd=0;}
                    for(int a=0;a<3;a++)if(ends[a]>0&&end>=ends[a]){ends[a]=0;used[a]=0;}
                    c.millis(delta);if(mode==TimerMode.SHARED)eq(3*MINUTE-sharedUsed,c.e.sharedRemaining());
                    for(int a=0;a<3;a++){
                        eq(used[a],c.e.used(packages[a]));eq(limits[a]==0||globalEnd>end||ends[a]>end,c.blocked(packages[a]));
                        eq(limits[a]==0?0L:Math.max(0,(globalEnd>0?globalEnd:ends[a])-end),c.cooldown(packages[a]));
                        if(limits[a]==0){eq(true,row(c,packages[a]).noAllowance());eq(0,row(c,packages[a]).progress());}
                    }
                }
                eq(history,total(c));
            }
        });
    }
}
