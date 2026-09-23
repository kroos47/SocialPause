import app.socialpause.engine.RulesEngine;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.TimeZone;

/** Compile against the original v0.5 engine. All data is synthetic. */
public final class GenerateV05Fixtures {
    static final long M = 60_000L, START = 1000L;
    static final String I="com.instagram.android", X="com.twitter.android", R="com.reddit.frontpage";
    static long wall;
    static RulesEngine fresh() {
        RulesEngine e = new RulesEngine();
        e.lunchEnabled=false; e.sleep(true,1260,540);
        e.setTimerMode(RulesEngine.TimerMode.SHARED); e.setSharedLimit(45*M);
        e.start(wall,START); return e;
    }
    static void save(RulesEngine e, Path directory, String name) throws Exception {
        try(var out=new ObjectOutputStream(Files.newOutputStream(directory.resolve(name)))){out.writeObject(e);}
    }
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        wall=LocalDate.of(2026,9,16).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Path directory=Path.of(args[0]);
        RulesEngine e=fresh();
        e.focus(I,true,wall,START);e.focus(X,true,wall+7*M,START+7*M);
        e.focus(R,true,wall+17*M,START+17*M);e.advance(wall+19*M,START+19*M);
        e.lunch(true,900,wall+19*M,START+19*M);
        save(e,directory,"legacy-v05-shared45-partial.bin");
        e.stop(wall+19*M,START+19*M);
        save(e,directory,"legacy-v05-shared45-stopped.bin");
        e=fresh();
        e.focus(I,true,wall,START);e.focus(X,true,wall+7*M,START+7*M);
        e.focus(R,true,wall+17*M,START+17*M);e.advance(wall+27*M,START+27*M);
        e.focus(I,true,wall+77*M,START+77*M);e.focus(X,true,wall+84*M,START+84*M);
        e.focus(R,true,wall+94*M,START+94*M);e.advance(wall+100*M,START+100*M);
        save(e,directory,"legacy-v05-shared45-cooldown.bin");
        e=fresh();e.focus(I,true,wall,START);e.advance(wall+3*M,START+3*M);
        e.startManualLunch(wall+19*M,START+19*M);e.advance(wall+24*M,START+24*M);
        save(e,directory,"legacy-v05-manual-lunch.bin");
        e.stopLunch(wall+29*M,START+29*M);e.advance(wall+34*M,START+34*M);
        save(e,directory,"legacy-v05-manual-block.bin");
    }
}
