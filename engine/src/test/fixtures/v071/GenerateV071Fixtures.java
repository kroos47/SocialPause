import app.socialpause.engine.RulesEngine;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Compile against the unchanged 0.7.1 engine, commit 41b9711. Synthetic data only. */
public final class GenerateV071Fixtures {
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        long wall=LocalDate.of(2026,9,16).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),elapsed=1000;
        RulesEngine e=new RulesEngine();e.lunchEnabled=false;e.sleepEnabled=false;e.setTimerMode(RulesEngine.TimerMode.SHARED);e.start(wall,elapsed);
        e.focus("com.twitter.android",true,wall,elapsed);wall+=120_000;elapsed+=120_000;e.advance(wall,elapsed);
        e.focus("com.instagram.android",true,wall,elapsed);wall+=420_000;elapsed+=420_000;e.advance(wall,elapsed);
        save(args[0],"partial",e);
        e.startManualLunch(wall,elapsed);wall+=300_000;elapsed+=300_000;e.advance(wall,elapsed);save(args[0],"lunch",e);
        wall+=3_600_000;elapsed+=3_600_000;e.advance(wall,elapsed);save(args[0],"lunch-cooldown",e);
    }
    static void save(String directory,String name,RulesEngine e)throws Exception {
        try(var out=new ObjectOutputStream(Files.newOutputStream(Path.of(directory,"legacy-v071-"+name+".bin")))){out.writeObject(e);}
    }
}
