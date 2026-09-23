import app.socialpause.engine.RulesEngine;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.TimeZone;

/** Run against the unmodified v0.4 RulesEngine, not the current engine. Synthetic data only. */
public final class GenerateV04Fixtures {
    static final long M = 60_000L, START = 1000L;
    static long wall;
    static RulesEngine fresh(boolean lunch) {
        RulesEngine e = new RulesEngine();
        e.lunchEnabled = lunch;
        e.sleep(true, 1260, 540);
        e.start(wall, START);
        return e;
    }
    static void save(RulesEngine e, Path directory, String name) throws Exception {
        try (var out = new ObjectOutputStream(Files.newOutputStream(directory.resolve(name)))) { out.writeObject(e); }
    }
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        wall = LocalDate.of(2026,9,16).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Path directory = Path.of(args[0]);
        RulesEngine e = fresh(false);
        e.focus("com.instagram.android",true,wall,START);
        e.focus("com.twitter.android",true,wall+3*M,START+3*M);
        e.advance(wall+5*M,START+5*M);
        save(e,directory,"legacy-v04-partial.bin");
        e = fresh(false);
        e.focus("com.instagram.android",true,wall,START);
        e.focus("com.twitter.android",true,wall+7*M,START+7*M);
        e.focus("com.reddit.frontpage",true,wall+17*M,START+17*M);
        e.advance(wall+19*M,START+19*M);
        e.lunch(true,900,wall+19*M,START+19*M);
        save(e,directory,"legacy-v04-cooldowns.bin");
        e = fresh(true);
        e.advance(wall+260*M,START+260*M);
        save(e,directory,"legacy-v04-lunch.bin");
    }
}
