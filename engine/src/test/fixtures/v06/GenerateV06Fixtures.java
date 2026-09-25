import app.socialpause.engine.RulesEngine;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.TimeZone;

/** Compile against the original v0.6 engine, never the current engine. All data is synthetic. */
public final class GenerateV06Fixtures {
    static final long M=60_000L, START=1_000L;
    static final String I="com.instagram.android", X="com.twitter.android", R="com.reddit.frontpage";
    static long wall;
    static RulesEngine fresh() {
        RulesEngine e=new RulesEngine();e.lunchEnabled=false;e.sleep(true,1260,540);
        e.setTimerMode(RulesEngine.TimerMode.SHARED);e.setSharedLimit(13*M);
        e.setAppLimit(I,2*M);e.setAppLimit(X,12*M);e.setAppLimit(R,0);
        e.start(wall,START);return e;
    }
    static void save(RulesEngine e,Path directory,String name) throws Exception {
        try(var out=new ObjectOutputStream(Files.newOutputStream(directory.resolve(name)))){out.writeObject(e);}
    }
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        wall=LocalDate.of(2026,9,16).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Path directory=Path.of(args[0]);RulesEngine e=fresh();
        e.focus(I,true,wall,START);e.focus(X,true,wall+2*M,START+2*M);e.advance(wall+5*M,START+5*M);
        e.lunch(true,900,wall+5*M,START+5*M);
        save(e,directory,"legacy-v06-partial.bin");
        e.stop(wall+5*M,START+5*M);save(e,directory,"legacy-v06-stopped.bin");
        e=fresh();e.focus(I,true,wall,START);e.focus(X,true,wall+2*M,START+2*M);e.advance(wall+18*M,START+18*M);
        save(e,directory,"legacy-v06-shared-cooldown.bin");
        e=fresh();e.focus(I,true,wall,START);e.advance(wall+M,START+M);
        e.startManualLunch(wall+5*M,START+5*M);e.advance(wall+20*M,START+20*M);
        save(e,directory,"legacy-v06-manual-lunch.bin");
        e.stopLunch(wall+20*M,START+20*M);e.advance(wall+30*M,START+30*M);
        save(e,directory,"legacy-v06-manual-cooldown.bin");
    }
}
