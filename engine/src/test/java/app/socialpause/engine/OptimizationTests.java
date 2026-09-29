package app.socialpause.engine;

import java.io.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static app.socialpause.engine.EngineTests.*;
import static app.socialpause.engine.RulesEngine.*;

/** Deterministic acceptance for runtime bookkeeping, projections, indexes and scheduling. */
final class OptimizationTests {
    static void changed(PersistenceCheckpoint p, RulesEngine e) { eq(true,p.needsSave(e));p.saved(e);eq(false,p.needsSave(e)); }
    static void run() {
        test("initial checkpoint and engine identity are checked before serialization",()->{
            Clock c=new Clock();PersistenceCheckpoint p=new PersistenceCheckpoint();changed(p,c.e);
            for(int i=0;i<1000;i++){c.e.advance(c.wall,c.elapsed);eq(false,p.needsSave(c.e));}
            c.e=copy(c.e,true);changed(p,c.e);
        });
        test("focus and snapshot reads do not dirty persistent state",()->{
            Clock c=new Clock();c.e.history();PersistenceCheckpoint p=new PersistenceCheckpoint();p.saved(c.e);
            for(String app:Arrays.asList(X,null,I,"com.android.settings")){c.focus(app);c.e.snapshot(c.wall,c.elapsed);c.notification();eq(false,p.needsSave(c.e));}
        });
        test("every active usage checkpoint remains eligible for saving",()->{
            Clock c=new Clock();c.focus(X);PersistenceCheckpoint p=new PersistenceCheckpoint();p.saved(c.e);
            for(int i=0;i<40;i++){c.millis(500);changed(p,c.e);}
            eq(20_000L,c.e.used(X));
        });
        test("idle countdown passage does not serialize until cooldown expiry",()->{
            Clock c=new Clock();c.focus(I);c.minutes(7);PersistenceCheckpoint p=new PersistenceCheckpoint();p.saved(c.e);
            c.minutes(59);eq(false,p.needsSave(c.e));c.minutes(1);changed(p,c.e);eq(INSTAGRAM_LIMIT,c.e.remaining(I));
        });
        test("settings and selection mutations publish revisions but identical writes do not",()->{
            Clock c=new Clock(false);PersistenceCheckpoint p=new PersistenceCheckpoint();p.saved(c.e);
            c.e.setTimerMode(TimerMode.SHARED);changed(p,c.e);c.e.setTimerMode(TimerMode.SHARED);eq(false,p.needsSave(c.e));
            c.e.setSharedLimit(12*MINUTE);changed(p,c.e);c.e.setSharedLimit(12*MINUTE);eq(false,p.needsSave(c.e));
            c.e.setAppLimit(X,5*MINUTE);changed(p,c.e);c.e.setAppLimit(X,5*MINUTE);eq(false,p.needsSave(c.e));
            c.e.select(new LinkedHashSet<>(List.of(X,I)));changed(p,c.e);c.e.select(new LinkedHashSet<>(List.of(X,I)));eq(false,p.needsSave(c.e));
            c.e.sleep(true,1300,610);changed(p,c.e);c.e.sleep(true,1300,610);eq(false,p.needsSave(c.e));
            c.e.lunch(true,900,c.wall,c.elapsed);changed(p,c.e);c.e.lunch(true,900,c.wall,c.elapsed);eq(false,p.needsSave(c.e));
        });
        test("monitoring and manual lunch eligibility changes are checkpointed",()->{
            Clock c=new Clock(false);PersistenceCheckpoint p=new PersistenceCheckpoint();p.saved(c.e);
            c.e.start(c.wall,c.elapsed);changed(p,c.e);c.e.start(c.wall,c.elapsed);eq(false,p.needsSave(c.e));
            c.e.startManualLunch(c.wall,c.elapsed);changed(p,c.e);c.e.stopLunch(c.wall,c.elapsed);changed(p,c.e);
            c.e.systemStop(c.wall,c.elapsed);changed(p,c.e);eq(true,c.e.manualLunchUsedToday(c.wall));
        });
        test("history-only changes invalidate the persistence checkpoint",()->{
            Clock c=new Clock(false);c.e.history();PersistenceCheckpoint p=new PersistenceCheckpoint();p.saved(c.e);
            c.e.history().record(X,c.wall,MINUTE,ZoneId.systemDefault());changed(p,c.e);
            c.e.history().record(X,c.wall,0,ZoneId.systemDefault());eq(false,p.needsSave(c.e));
        });
        test("revision counters and indexes are absent from the serialized field schema",()->{
            var engine=Arrays.stream(ObjectStreamClass.lookup(RulesEngine.class).getFields()).map(ObjectStreamField::getName).toList();
            eq(false,engine.contains("revision"));
            eq(List.of("days"),Arrays.stream(ObjectStreamClass.lookup(UsageHistory.class).getFields()).map(ObjectStreamField::getName).toList());
            Clock c=new Clock();c.focus(X);c.minutes(1);RulesEngine loaded=copy(c.e,true);eq(0L,loaded.revision());eq(0L,loaded.historyRevision());
            changed(new PersistenceCheckpoint(),loaded);
        });
        test("read-only projections count elapsed time without saving or advancing",()->{
            Clock c=new Clock();c.focus(X);long revision=c.e.revision();
            var view=c.e.snapshot(c.wall+1500,c.elapsed+1500);eq(APP_LIMIT-1500,view.apps().get(X).remaining());
            eq(1500L,view.apps().get(X).used());eq(0L,c.e.used(X));eq(revision,c.e.revision());
            c.millis(1500);eq(view.apps().get(X).remaining(),c.e.remaining(X));
        });
        test("projections freeze on Home lock and lunch and stop at app cap",()->{
            Clock c=new Clock();c.focus(X);c.focus(null);eq(APP_LIMIT,c.e.snapshot(c.wall+5000,c.elapsed+5000).apps().get(X).remaining());
            c.focus(X);eq(0L,c.e.snapshot(c.wall+30*MINUTE,c.elapsed+30*MINUTE).apps().get(X).remaining());eq(0L,c.e.used(X));
            c.e.startManualLunch(c.wall,c.elapsed);c.focus(X);eq(APP_LIMIT,c.e.snapshot(c.wall+MINUTE,c.elapsed+MINUTE).apps().get(X).remaining());
        });
        test("shared projected use and delayed settlement retain exact boundaries",()->{
            Clock c=new Clock(false);c.e.setTimerMode(TimerMode.SHARED);c.e.setSharedLimit(MINUTE);c.e.start(c.wall,c.elapsed);c.focus(X);
            var view=c.e.snapshot(c.wall+2*MINUTE,c.elapsed+2*MINUTE);eq(0L,view.sharedRemaining());eq(APP_LIMIT-MINUTE,view.apps().get(X).remaining());
            c.minutes(2);eq(59*MINUTE,c.e.sharedCooldownRemaining(c.elapsed));eq(MINUTE,c.e.used(X));
        });
        test("repeated history queries reuse one immutable range summary",()->{
            Clock c=new Clock();c.focus(X);c.minutes(2);LocalDate day=LocalDate.of(2026,9,16);var h=c.e.history();var summary=h.summary(day.minusDays(2),day.plusDays(4));
            for(int i=0;i<1000;i++)eq(true,summary==h.summary(day.minusDays(2),day.plusDays(4)));
            eq(summary.byApp(),h.byApp(day.minusDays(2),day.plusDays(4)));eq(2*MINUTE,summary.total());
            try { summary.byApp().put(X,0L);throw new AssertionError("Mutable summary"); } catch(UnsupportedOperationException expected) { }
        });
        test("new history invalidates cached totals and the date index",()->{
            Clock c=new Clock();LocalDate day=LocalDate.of(2026,9,16);var h=c.e.history();var before=h.summary(day,day.plusDays(1));
            h.record(X,c.wall,MINUTE,ZoneId.systemDefault());var after=h.summary(day,day.plusDays(1));eq(false,before==after);eq(MINUTE,after.total());
            h.record(I,c.wall+24*COOLDOWN,2*MINUTE,ZoneId.systemDefault());eq(3*MINUTE,h.summary(day,day.plusDays(1)).total());
        });
        test("year and three-year histories only return the requested week",()->{
            for(int days:new int[]{7,365,1095}){
                UsageHistory h=new UsageHistory();LocalDate last=LocalDate.of(2026,9,29);ZoneId z=ZoneId.systemDefault();
                for(int i=0;i<days;i++)h.record(X,last.minusDays(i).atTime(12,0).atZone(z).toInstant().toEpochMilli(),MINUTE,z);
                var week=h.summary(last.minusDays(6),last);eq(7,week.days().size());eq(7*MINUTE,week.total());eq(0L,h.summary(last.plusDays(1),last).total());
            }
        });
        test("midnight and week rollover use fresh summary ranges without losing old totals",()->{
            UsageHistory h=new UsageHistory();ZoneId z=ZoneId.systemDefault();LocalDate sun=LocalDate.of(2026,9,20);
            h.record(X,sun.atTime(23,59).atZone(z).toInstant().toEpochMilli(),2*MINUTE,z);
            eq(MINUTE,h.summary(sun.minusDays(6),sun).total());eq(MINUTE,h.summary(sun.plusDays(1),sun.plusDays(7)).total());
            var copy=copyHistory(h);eq(2*MINUTE,copy.summary(sun,sun.plusDays(1)).total());
        });
        test("window scheduling uses 2 seconds outside apps and 500 ms during selected use",()->{
            MonitoringSchedule s=new MonitoringSchedule();eq(2000L,s.next(true,true,"home",false));eq(500L,s.next(true,true,X,true));
            eq(500L,s.next(true,true,X,true));eq(2000L,s.next(true,true,"com.android.settings",false));
        });
        test("uncertain selected visibility stays fast until a switch or lock is confirmed",()->{
            MonitoringSchedule s=new MonitoringSchedule();s.next(true,true,X,true);eq(500L,s.next(true,true,null,false));
            eq(30_000L,s.next(true,false,null,false));eq(2000L,s.next(true,true,null,false));eq(0L,s.next(false,true,X,true));
        });
        test("quiet fallback frequency falls by 75 percent and missed event detection is bounded",()->{
            MonitoringSchedule s=new MonitoringSchedule();long delay=s.next(true,true,"home",false);eq(30L,60_000/delay);eq(120L,60_000L/500);
            eq(500L,s.next(true,true,X,true)); // next fallback discovers a missed entry within two seconds
        });
        test("notification display keys reuse equal displayed seconds",()->{
            Clock c=new Clock();c.focus(X);c.millis(1000);var a=NotificationDisplay.of(c.notification(),1,true,0,c.wall);
            c.millis(400);var b=NotificationDisplay.of(c.notification(),1,true,0,c.wall);eq(a,b);
            c.millis(600);eq(false,a.equals(NotificationDisplay.of(c.notification(),1,true,0,c.wall)));
        });
        test("notification keys include theme generation app identity and limiting allowance",()->{
            Clock c=new Clock();c.focus(X);var p=c.notification();var a=NotificationDisplay.of(p,1,true,0,c.wall);
            eq(false,a.equals(NotificationDisplay.of(p,2,true,0,c.wall)));eq(false,a.equals(NotificationDisplay.of(p,1,true,1,c.wall)));
            c.focus(I);eq(false,a.equals(NotificationDisplay.of(c.notification(),1,true,0,c.wall)));
            c.focus(null);eq(false,a.equals(NotificationDisplay.of(c.notification(),1,true,0,c.wall)));
        });
        test("cooldown state and no allowance are separate notification key states",()->{
            Clock c=new Clock();var a=NotificationDisplay.of(c.notification(),1,true,0,c.wall);c.focus(X);c.minutes(10);
            eq(false,a.equals(NotificationDisplay.of(c.notification(),1,true,0,c.wall)));
            c.e.systemStop(c.wall,c.elapsed);c.e.setAppLimit(X,0);c.e.start(c.wall,c.elapsed);eq(false,a.equals(NotificationDisplay.of(c.notification(),1,true,0,c.wall)));
        });
        test("clock-only keys share a surface while state changes bypass throttling",()->{
            Clock c=new Clock();c.focus(X);c.minutes(1);var a=NotificationDisplay.of(c.notification(),1,true,0,c.wall);
            c.millis(1000);var b=NotificationDisplay.of(c.notification(),1,true,0,c.wall);eq(false,a.equals(b));eq(true,a.sameSurface(b));
            c.focus(null);eq(false,a.sameSurface(NotificationDisplay.of(c.notification(),1,true,0,c.wall)));
        });
        test("idle phase alarm stays at midnight instead of sliding on each refresh",()->{
            Clock c=new Clock();long boundary=c.e.nextBoundary(c.wall,c.elapsed);c.minutes(3);eq(boundary,c.e.nextBoundary(c.wall,c.elapsed));
            eq(LocalDate.of(2026,9,17).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),boundary);
        });
        for(String fixture:List.of("partial","lunch","lunch-cooldown"))test("0.7.1 "+fixture+" fixture preserves allowances history lunch and active Stop lock",()->{
            try(var in=new ObjectInputStream(Objects.requireNonNull(EngineTests.class.getResourceAsStream("/legacy-v071-"+fixture+".bin")))) {
                RulesEngine e=(RulesEngine)in.readObject();e.attach(true);long wall=LocalDate.of(2026,9,16).atTime(10,9).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();long elapsed=1000+9*MINUTE;
                if(!fixture.equals("partial")){long minutes=fixture.equals("lunch")?5:65;wall+=minutes*MINUTE;elapsed+=minutes*MINUTE;}
                eq(true,e.running);eq(TimerMode.SHARED,e.timerMode());eq(9*MINUTE,e.history().total(LocalDate.of(2026,9,16),LocalDate.of(2026,9,16)));
                eq(1000+STOP_LOCK-elapsed,e.stopLockRemaining(elapsed));eq(fixture.equals("partial")?11*MINUTE:20*MINUTE,e.sharedRemaining());
                eq(!fixture.equals("partial"),e.manualLunchUsedToday(wall));
                if(fixture.equals("partial")){eq(2*MINUTE,e.used(X));eq(COOLDOWN,e.cooldownRemaining(I,wall,elapsed));}
                else {eq(fixture.equals("lunch")?Mode.LUNCH:Mode.LUNCH_COOLDOWN,e.mode(wall,elapsed));eq(55*MINUTE,e.countdown(wall,elapsed));}
                changed(new PersistenceCheckpoint(),e);
            }catch(Exception ex){throw new AssertionError(ex);}
        });
    }
    private static UsageHistory copyHistory(UsageHistory h) {
        try {var bytes=new ByteArrayOutputStream();try(var out=new ObjectOutputStream(bytes)){out.writeObject(h);}try(var in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){return (UsageHistory)in.readObject();}}
        catch(Exception ex){throw new AssertionError(ex);}
    }
}
