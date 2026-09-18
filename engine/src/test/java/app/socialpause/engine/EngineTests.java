package app.socialpause.engine;

import java.io.*;
import java.time.*;
import java.util.*;
import static app.socialpause.engine.RulesEngine.*;

/** Behavioral scenarios with fake time, an independent randomized model, and a real v0.2 fixture. */
public final class EngineTests {
    static final String I="com.instagram.android",X="com.twitter.android",R="com.reddit.frontpage";
    static int tests;
    static class Clock {
        RulesEngine e=new RulesEngine();
        long wall=LocalDate.of(2026,9,16).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),elapsed=1000;
        Clock(){e.lunchEnabled=false;e.sleepEnabled=false;e.start(wall,elapsed);}
        void focus(String pkg){e.focus(pkg,true,wall,elapsed);}
        void minutes(long n){millis(n*MINUTE);}
        void millis(long n){wall+=n;elapsed+=n;e.advance(wall,elapsed);}
        boolean blocked(String p){return e.blocked(p,wall,elapsed);}
        long cooldown(String p){return e.cooldownRemaining(p,wall,elapsed);}
        Mode mode(){return e.mode(wall,elapsed);}
        void lunch(){e.lunchEnabled=true;e.advance(wall,elapsed);}
        TimerPresentation notification(){return TimerPresentation.of(e,true,wall,elapsed);}
    }
    static void eq(Object expected,Object actual){if(!Objects.equals(expected,actual))throw new AssertionError("expected "+expected+", got "+actual);}
    static void test(String name,Runnable run){run.run();tests++;System.out.println("PASS "+name);}
    static RulesEngine copy(RulesEngine e,boolean boot){
        try{var bytes=new ByteArrayOutputStream();try(var out=new ObjectOutputStream(bytes)){out.writeObject(e);}try(var in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){RulesEngine result=(RulesEngine)in.readObject();result.attach(boot);return result;}}catch(Exception ex){throw new AssertionError(ex);}
    }
    static FocusResolver.Window window(String app,FocusResolver.Kind kind,int layer){return new FocusResolver.Window(app,kind,true,true,layer);}
    public static void main(String[] args){
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        test("Instagram seven-minute boundary",()->{Clock c=new Clock();c.focus(I);c.millis(7*MINUTE-1);eq(false,c.blocked(I));eq(1L,c.e.remaining(I));c.millis(1);eq(true,c.blocked(I));eq(COOLDOWN,c.cooldown(I));eq(false,c.blocked(X));});
        test("X and Reddit retain ten minutes",()->{Clock c=new Clock();c.focus(X);c.minutes(10);eq(COOLDOWN,c.cooldown(X));c.focus(R);c.minutes(10);eq(COOLDOWN,c.cooldown(R));eq(50*MINUTE,c.cooldown(X));});
        test("no shared twenty-minute cutoff",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.focus(X);c.minutes(9);c.focus(R);c.minutes(9);eq(false,c.blocked(X));eq(false,c.blocked(R));eq(25*MINUTE,c.e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));});
        test("Instagram unlocks without using any other app",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.minutes(59);eq(true,c.blocked(I));c.minutes(1);eq(false,c.blocked(I));eq(INSTAGRAM_LIMIT,c.e.remaining(I));eq(APP_LIMIT,c.e.remaining(X));});
        test("independent staggered deadlines",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.focus(X);c.minutes(10);c.minutes(50);eq(false,c.blocked(I));eq(true,c.blocked(X));eq(10*MINUTE,c.cooldown(X));c.minutes(10);eq(false,c.blocked(X));});
        test("one reset leaves other focused app counting",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.minutes(55);c.focus(X);c.minutes(6);eq(false,c.blocked(I));eq(X,c.e.focused());eq(6*MINUTE,c.e.used(X));eq(4*MINUTE,c.e.remaining(X));});
        test("leaving pauses and reopening resumes",()->{Clock c=new Clock();c.focus(I);c.minutes(3);c.focus(null);c.minutes(90);eq(4*MINUTE,c.e.remaining(I));eq(0L,c.cooldown(I));c.focus(I);c.minutes(4);eq(COOLDOWN,c.cooldown(I));});
        test("screen locking pauses usage but not cooldown",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.focus(X);c.minutes(2);c.e.focus(X,false,c.wall,c.elapsed);c.minutes(30);eq(2*MINUTE,c.e.used(X));eq(28*MINUTE,c.cooldown(I));});
        test("switching apps preserves independent fractions",()->{Clock c=new Clock();c.focus(I);c.millis(1234);c.focus(X);c.millis(456);eq(1234L,c.e.used(I));eq(456L,c.e.used(X));});
        test("unselected apps consume no allowance",()->{Clock c=new Clock();c.focus("other");c.minutes(100);eq(0L,c.e.used(I));eq(0L,c.e.used(X));});
        test("all cooldowns retain distinct unlock times",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.focus(X);c.minutes(10);c.focus(R);c.minutes(10);eq(0,c.e.availableCount(c.wall,c.elapsed));eq(I,c.e.nextAvailableApp(c.wall,c.elapsed));eq(40*MINUTE,c.cooldown(I));eq(50*MINUTE,c.cooldown(X));eq(60*MINUTE,c.cooldown(R));});
        test("late callback anchors cooldown at actual exhaustion",()->{Clock c=new Clock();c.focus(I);c.minutes(12);eq(55*MINUTE,c.cooldown(I));eq(INSTAGRAM_LIMIT,c.e.used(I));});
        test("late callback never counts after cooldown expires",()->{Clock c=new Clock();c.focus(I);c.minutes(100);eq(false,c.blocked(I));eq(0L,c.e.used(I));eq(null,c.e.focused());eq(INSTAGRAM_LIMIT,c.e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));});
        test("earliest cooldown is an alarm boundary",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.focus(X);c.minutes(10);eq(c.wall+50*MINUTE,c.e.nextBoundary(c.wall,c.elapsed));});
        test("stop grants unrestricted access",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.e.stop(c.wall,c.elapsed);eq(false,c.blocked(I));c.focus(X);c.minutes(5);eq(0L,c.e.used(X));eq(TimerPresentation.Kind.HIDDEN,c.notification().kind);});
        test("start refreshes every allowance and changes run ID",()->{Clock c=new Clock();c.focus(I);c.minutes(7);long run=c.e.cycleId();c.e.stop(c.wall,c.elapsed);c.e.start(c.wall,c.elapsed);eq(run+1,c.e.cycleId());eq(INSTAGRAM_LIMIT,c.e.remaining(I));eq(0L,c.cooldown(I));});
        test("cooldown completion preserves monitoring run ID",()->{Clock c=new Clock();long run=c.e.cycleId();c.focus(I);c.minutes(7);c.minutes(60);eq(run,c.e.cycleId());});
        test("lunch resets pending app cooldowns",()->{Clock c=new Clock();c.lunch();c.minutes(230);c.focus(I);c.minutes(7);eq(COOLDOWN,c.cooldown(I));c.minutes(3);eq(Mode.LUNCH,c.mode());eq(0L,c.cooldown(I));eq(0L,c.e.used(I));});
        test("unused lunch still requires blocked hour",()->{Clock c=new Clock();c.lunch();c.minutes(300);eq(Mode.LUNCH_COOLDOWN,c.mode());for(String pkg:c.e.selected){eq(true,c.blocked(pkg));eq(COOLDOWN,c.cooldown(pkg));}c.minutes(60);eq(Mode.READY,c.mode());eq(INSTAGRAM_LIMIT,c.e.remaining(I));});
        test("lunch usage is unrestricted and excluded from history",()->{Clock c=new Clock();c.lunch();c.minutes(240);c.focus(I);c.minutes(60);eq(0L,c.e.used(I));eq(0L,c.e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));eq(true,c.blocked(X));});
        test("start respects active post-lunch block",()->{Clock c=new Clock();c.lunch();c.minutes(300);c.e.stop(c.wall,c.elapsed);eq(false,c.blocked(I));c.e.start(c.wall,c.elapsed);eq(true,c.blocked(I));eq(COOLDOWN,c.cooldown(I));});
        test("lunch edits wait until tomorrow",()->{Clock c=new Clock();c.lunch();c.e.lunch(false,840,c.wall,c.elapsed);c.minutes(300);eq(true,c.blocked(X));c.minutes(60);c.minutes(8*60);eq(false,c.e.lunchEnabled);eq(0L,c.e.pendingAt());});
        test("overnight lunch completes before schedule edit",()->{Clock c=new Clock();c.e.lunchMinute=1410;c.lunch();c.minutes(810);eq(Mode.LUNCH,c.mode());c.e.lunch(false,840,c.wall,c.elapsed);c.minutes(60);eq(Mode.LUNCH_COOLDOWN,c.mode());c.minutes(60);eq(Mode.READY,c.mode());eq(false,c.e.lunchEnabled);});
        test("sleep hides notification and cooldown continues",()->{Clock c=new Clock();c.e.sleep(true,1320,600);c.minutes(713);c.focus(I);c.minutes(7);eq(true,c.e.quiet(c.wall));eq(TimerPresentation.Kind.HIDDEN,c.notification().kind);c.minutes(60);eq(false,c.blocked(I));});
        test("sleep does not disable usage limits",()->{Clock c=new Clock();c.e.sleep(true,1320,600);c.minutes(720);c.focus(X);c.minutes(10);eq(true,c.blocked(X));eq(COOLDOWN,c.cooldown(X));});
        test("same-day quiet boundary",()->{Clock c=new Clock();c.e.sleep(true,600,660);eq(true,c.e.quiet(c.wall));c.minutes(60);eq(false,c.e.quiet(c.wall));});
        test("process recovery preserves budgets and clears stale focus",()->{Clock c=new Clock();c.focus(I);c.minutes(3);c.e=copy(c.e,true);c.minutes(30);eq(3*MINUTE,c.e.used(I));eq(null,c.e.focused());});
        test("process recovery preserves every cooldown deadline",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.focus(X);c.minutes(10);c.e=copy(c.e,true);c.minutes(20);eq(30*MINUTE,c.cooldown(I));eq(40*MINUTE,c.cooldown(X));});
        test("reboot resets timers but preserves history and settings",()->{Clock c=new Clock();c.e.sleep(true,1260,540);c.focus(I);c.minutes(7);c.e=copy(c.e,false);c.elapsed=100;c.e.advance(c.wall,c.elapsed);eq(INSTAGRAM_LIMIT,c.e.remaining(I));eq(1260,c.e.sleepStart);eq(INSTAGRAM_LIMIT,c.e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));});
        test("v0.2 upgrade retains history schedules and running state",()->{
            try(var in=new ObjectInputStream(Objects.requireNonNull(EngineTests.class.getResourceAsStream("/legacy-v02.bin")))){
                RulesEngine e=(RulesEngine)in.readObject();e.attach(true);long w=LocalDate.of(2026,9,16).atTime(10,20).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t=1201000;e.advance(w,t);
                eq(true,e.running);eq(3,e.selected.size());eq(1260,e.sleepStart);eq(540,e.sleepEnd);eq(true,e.pendingAt()>w);eq(INSTAGRAM_LIMIT,e.remaining(I));eq(0L,e.cooldownRemaining(X,w,t));eq(20*MINUTE,e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));
                e.focus(I,true,w,t);e.advance(w+2*MINUTE,t+2*MINUTE);e=copy(e,true);eq(2*MINUTE,e.used(I));
            }catch(Exception ex){throw new AssertionError(ex);}
        });
        test("v0.2 upgrade still honors lunch restrictions",()->{
            try(var in=new ObjectInputStream(Objects.requireNonNull(EngineTests.class.getResourceAsStream("/legacy-v02.bin")))){
                RulesEngine e=(RulesEngine)in.readObject();e.attach(true);long w=LocalDate.of(2026,9,16).atTime(15,15).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();e.advance(w,20000000);eq(true,e.blocked(I,w,20000000));eq(45*MINUTE,e.cooldownRemaining(I,w,20000000));
            }catch(Exception ex){throw new AssertionError(ex);}
        });
        test("notification shows focused app only as primary",()->{Clock c=new Clock();c.focus(I);c.minutes(2);var p=c.notification();eq(TimerPresentation.Kind.APP,p.kind);eq(I,p.app);eq(5*MINUTE,p.remaining);eq(INSTAGRAM_LIMIT,p.limit);eq(true,p.ticking());});
        test("paused overview preserves all app allowances",()->{Clock c=new Clock();c.focus(I);c.minutes(2);c.focus(null);var p=c.notification();eq(TimerPresentation.Kind.OVERVIEW,p.kind);eq(3,p.rows.size());eq(5*MINUTE,p.rows.get(0).remaining());eq(false,p.ticking());});
        test("mixed notification lists usage and cooldown separately",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.focus(X);c.minutes(3);c.focus(null);var p=c.notification();eq(TimerPresentation.Kind.OVERVIEW,p.kind);eq(I,p.app);eq(57*MINUTE,p.remaining);eq(true,p.rows.get(0).cooling());eq(false,p.rows.get(1).cooling());eq(7*MINUTE,p.rows.get(1).remaining());});
        test("all cooldown notification chooses earliest not sum",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.focus(X);c.minutes(10);c.focus(R);c.minutes(10);var p=c.notification();eq(TimerPresentation.Kind.ALL_COOLDOWN,p.kind);eq(40*MINUTE,p.remaining);eq(3,p.rows.size());});
        test("notification becomes mixed after first app unlocks",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.focus(X);c.minutes(10);c.focus(R);c.minutes(10);c.minutes(40);var p=c.notification();eq(TimerPresentation.Kind.OVERVIEW,p.kind);eq(false,p.rows.get(0).cooling());eq(INSTAGRAM_LIMIT,p.rows.get(0).remaining());eq(X,p.app);});
        test("disconnection hides notifications without resetting timers",()->{Clock c=new Clock();c.focus(I);c.minutes(7);eq(TimerPresentation.Kind.HIDDEN,TimerPresentation.of(c.e,false,c.wall,c.elapsed).kind);eq(COOLDOWN,c.cooldown(I));});
        test("lunch presentation overrides individual overview",()->{Clock c=new Clock();c.lunch();c.minutes(240);eq(TimerPresentation.Kind.LUNCH,c.notification().kind);c.minutes(60);eq(TimerPresentation.Kind.LUNCH_COOLDOWN,c.notification().kind);});
        test("history spans cycles without being reset",()->{Clock c=new Clock();c.focus(I);c.minutes(7);c.minutes(60);c.focus(I);c.minutes(3);eq(10*MINUTE,c.e.history().byApp(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)).get(I));});
        test("history splits at midnight",()->{UsageHistory h=new UsageHistory();ZoneId z=ZoneId.of("Asia/Kolkata");long w=LocalDate.of(2026,9,16).atTime(23,58).atZone(z).toInstant().toEpochMilli();h.record(I,w,5*MINUTE,z);eq(2*MINUTE,h.total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));eq(3*MINUTE,h.total(LocalDate.of(2026,9,17),LocalDate.of(2026,9,17)));});
        test("weekly app segments sum to selected-day and week totals",()->{UsageHistory h=new UsageHistory();ZoneId z=ZoneId.systemDefault();LocalDate mon=LocalDate.of(2026,9,14);for(int i=0;i<7;i++){long w=mon.plusDays(i).atTime(10,0).atZone(z).toInstant().toEpochMilli();h.record(I,w,(i+1)*MINUTE,z);h.record(X,w,2*MINUTE,z);}eq(42*MINUTE,h.total(mon,mon.plusDays(6)));eq(6*MINUTE,h.total(mon.plusDays(3),mon.plusDays(3)));eq(4*MINUTE,h.byApp(mon.plusDays(3),mon.plusDays(3)).get(I));eq(0L,h.total(mon.minusDays(1),mon.minusDays(1)));});
        test("history handles DST transitions",()->{UsageHistory h=new UsageHistory();ZoneId z=ZoneId.of("America/New_York");LocalDate day=LocalDate.of(2026,11,1);long w=day.atTime(0,30).atZone(z).toInstant().toEpochMilli();h.record(I,w,4*60*MINUTE,z);eq(4*60*MINUTE,h.total(day,day));eq(4*60*MINUTE,Arrays.stream(h.hours(day)).sum());});
        test("unknown selected apps default to ten minutes",()->{Clock c=new Clock();c.e.stop(c.wall,c.elapsed);c.e.select(Set.of("other"));c.e.start(c.wall,c.elapsed);c.focus("other");c.minutes(10);eq(COOLDOWN,c.cooldown("other"));});
        test("changing selected apps retains historical totals",()->{Clock c=new Clock();c.focus(I);c.minutes(2);c.e.stop(c.wall,c.elapsed);c.e.select(Set.of(X));eq(2*MINUTE,c.e.history().byApp(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)).get(I));});
        test("configuration guards remain enforced",()->{Clock c=new Clock();boolean rejected=false;try{c.e.select(Set.of(X));}catch(IllegalStateException ex){rejected=true;}eq(true,rejected);rejected=false;try{c.e.sleep(true,1,1);}catch(IllegalArgumentException ex){rejected=true;}eq(true,rejected);});
        test("five thousand transitions match independent timer model",()->{
            Clock c=new Clock();Random random=new Random(308);String[] packages={I,X,R};long[] used=new long[3],deadlines=new long[3];long history=0;
            for(int n=0;n<5000;n++){
                int focus=random.nextInt(5);boolean unlocked=random.nextBoolean();String pkg=focus<3?packages[focus]:null;
                c.e.focus(pkg,unlocked,c.wall,c.elapsed);long delta=random.nextInt(150001),end=c.elapsed+delta;
                if(focus<3&&unlocked&&deadlines[focus]==0){long limit=focus==0?INSTAGRAM_LIMIT:APP_LIMIT,charged=Math.min(delta,limit-used[focus]);used[focus]+=charged;history+=charged;if(used[focus]==limit)deadlines[focus]=c.elapsed+charged+COOLDOWN;}
                for(int a=0;a<3;a++)if(deadlines[a]>0&&end>=deadlines[a]){deadlines[a]=0;used[a]=0;}
                c.millis(delta);
                for(int a=0;a<3;a++){eq(used[a],c.e.used(packages[a]));eq(deadlines[a]>end,c.blocked(packages[a]));eq(Math.max(0,deadlines[a]-end),c.cooldown(packages[a]));}
            }
            eq(history,c.e.history().total(LocalDate.of(2026,9,1),LocalDate.of(2026,10,31)));
        });
        test("notification drawer keeps charging the underlying app",()->{
            Clock c=new Clock();FocusResolver f=new FocusResolver();
            c.focus(f.resolve(true,true,List.of(window(I,FocusResolver.Kind.APP,1)),I,false));c.minutes(2);
            c.focus(f.resolve(true,true,List.of(window("com.android.systemui",FocusResolver.Kind.SYSTEM_PANEL,10)),"com.android.systemui",false));c.minutes(3);
            eq(5*MINUTE,c.e.used(I));eq(I,c.notification().app);eq(true,c.notification().activeChip());
            c.focus(f.resolve(true,true,List.of(window(I,FocusResolver.Kind.APP,1)),I,false));c.minutes(1);eq(6*MINUTE,c.e.used(I));
        });
        test("allowance exhausts while notification drawer stays open",()->{
            Clock c=new Clock();FocusResolver f=new FocusResolver();c.focus(f.resolve(true,true,List.of(window(I,FocusResolver.Kind.APP,1)),I,false));c.minutes(6);
            c.focus(f.resolve(true,true,List.of(window("com.android.systemui",FocusResolver.Kind.SYSTEM_PANEL,10)),null,false));c.minutes(2);
            eq(true,c.blocked(I));eq(59*MINUTE,c.cooldown(I));eq(false,c.notification().activeChip());eq(false,c.blocked(X));
        });
        test("Settings and Home replace the previous social app",()->{
            Clock c=new Clock();FocusResolver f=new FocusResolver();c.focus(f.resolve(true,true,List.of(window(X,FocusResolver.Kind.APP,1)),X,false));c.minutes(2);
            for(String app:List.of("com.android.settings","com.sec.android.app.launcher")){
                c.focus(f.resolve(true,true,List.of(window(app,FocusResolver.Kind.APP,2)),app,false));c.minutes(3);
                eq(app,f.resolve(true,true,List.of(window("com.android.systemui",FocusResolver.Kind.SYSTEM_PANEL,10)),null,false));
            }eq(2*MINUTE,c.e.used(X));eq(TimerPresentation.Kind.OVERVIEW,c.notification().kind);
        });
        test("screen lock discards remembered app under drawer",()->{
            FocusResolver f=new FocusResolver();eq(I,f.resolve(true,true,List.of(window(I,FocusResolver.Kind.APP,1)),I,false));
            eq(null,f.resolve(false,true,List.of(window("com.android.systemui",FocusResolver.Kind.SYSTEM_PANEL,10)),null,false));
            eq(null,f.resolve(true,true,List.of(window("com.android.systemui",FocusResolver.Kind.SYSTEM_PANEL,10)),null,false));
            eq(X,f.resolve(true,true,List.of(window(X,FocusResolver.Kind.APP,1)),X,false));
        });
        test("recents and missing windows clear stale app identity",()->{
            FocusResolver f=new FocusResolver();f.resolve(true,true,List.of(window(I,FocusResolver.Kind.APP,1)),I,false);
            eq(null,f.resolve(true,true,List.of(window("com.android.systemui",FocusResolver.Kind.SYSTEM_PANEL,10)),null,true));
            eq(null,f.resolve(true,true,List.of(),"com.android.systemui",false));
            f.resolve(true,true,List.of(window(X,FocusResolver.Kind.APP,1)),X,false);eq(null,f.resolve(true,true,List.of(),null,false));
            eq(null,f.resolve(true,true,List.of(),"com.android.systemui",false));
        });
        test("keyboard does not replace focused app and highest window wins",()->{
            FocusResolver f=new FocusResolver();eq(X,f.resolve(true,true,List.of(window(I,FocusResolver.Kind.APP,1),window(X,FocusResolver.Kind.APP,2),window("keyboard",FocusResolver.Kind.INPUT_METHOD,10)),X,false));
            eq(X,f.resolve(true,true,List.of(window("com.android.systemui",FocusResolver.Kind.SYSTEM_PANEL,20),window(I,FocusResolver.Kind.APP,1)),I,false));
        });
        test("Stop and service reset discard previous drawer app",()->{
            FocusResolver f=new FocusResolver();f.resolve(true,true,List.of(window(I,FocusResolver.Kind.APP,1)),I,false);
            eq(null,f.resolve(true,false,List.of(window("com.android.systemui",FocusResolver.Kind.SYSTEM_PANEL,10)),null,false));
            eq(null,f.resolve(true,true,List.of(),"com.android.systemui",false));
            f.resolve(true,true,List.of(window(X,FocusResolver.Kind.APP,1)),X,false);f.reset();eq(null,f.resolve(true,true,List.of(),"com.android.systemui",false));
        });
        test("only an actively used app requests a timer chip",()->{
            Clock c=new Clock();eq(false,c.notification().activeChip());c.focus(X);eq(true,c.notification().activeChip());c.minutes(10);eq(false,c.notification().activeChip());
            c.focus(I);c.minutes(7);c.focus(R);c.minutes(10);eq(TimerPresentation.Kind.ALL_COOLDOWN,c.notification().kind);eq(false,c.notification().activeChip());
            c.e.stop(c.wall,c.elapsed);eq(false,c.notification().activeChip());
        });
        test("overview progress represents each independent allowance",()->{
            Clock c=new Clock();c.focus(X);c.minutes(5);c.focus(null);var rows=c.notification().rows;eq(0,rows.get(0).progress());eq(50,rows.get(1).progress());eq(0,rows.get(2).progress());
            c.focus(I);c.minutes(7);c.minutes(30);c.focus(null);eq(50,c.notification().rows.get(0).progress());eq(50,c.notification().rows.get(1).progress());
        });
        test("redacted system panel keeps underlying app identity",()->{
            Clock c=new Clock();FocusResolver f=new FocusResolver();c.focus(f.resolve(true,true,List.of(window(X,FocusResolver.Kind.APP,1)),X,false));c.minutes(2);
            c.focus(f.resolve(true,true,List.of(window(null,FocusResolver.Kind.SYSTEM_PANEL,10)),null,false));c.minutes(3);eq(5*MINUTE,c.e.used(X));eq(X,c.notification().app);
            eq(null,f.resolve(false,true,List.of(window(null,FocusResolver.Kind.SYSTEM_PANEL,10)),null,false));
        });
        test("drawer transition uses active root before stale window snapshot",()->{
            Clock c=new Clock();FocusResolver f=new FocusResolver();c.focus(f.resolve(true,true,List.of(window(X,FocusResolver.Kind.APP,0)),X,false));c.minutes(2);
            var transitional=List.of(new FocusResolver.Window("com.android.systemui",FocusResolver.Kind.SYSTEM_PANEL,false,false,1),window(null,FocusResolver.Kind.APP,0));
            c.focus(f.resolve(true,true,transitional,"com.android.systemui",false));c.minutes(1);eq(X,c.notification().app);eq(3*MINUTE,c.e.used(X));
            c.focus(f.resolve(true,true,List.of(window("com.android.systemui",FocusResolver.Kind.SYSTEM_PANEL,0)),"com.android.systemui",false));c.minutes(2);eq(5*MINUTE,c.e.used(X));
            eq("com.android.settings",f.resolve(true,true,List.of(window(null,FocusResolver.Kind.APP,0)),"com.android.settings",false));
        });
        System.out.println(tests+" scenarios passed.");
    }
}
