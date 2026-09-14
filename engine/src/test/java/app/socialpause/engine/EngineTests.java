package app.socialpause.engine;
import java.io.*;
import java.time.*;
import java.util.*;
import static app.socialpause.engine.RulesEngine.*;

/** Dependency-free scenario suite, run by :engine:checkRules and scripts/test-engine.sh. */
public final class EngineTests {
    static int tests;
    static class Clock {
        RulesEngine e = new RulesEngine();
        long wall = LocalDate.of(2026, 9, 12).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), elapsed = 1000;
        Clock() { e.lunchEnabled = false; e.select(new LinkedHashSet<>(List.of("i", "x", "r"))); e.start(wall, elapsed); }
        void focus(String p) { e.focus(p, true, wall, elapsed); }
        void minutes(long n) { millis(n * MINUTE); }
        void millis(long n) { wall += n; elapsed += n; e.advance(wall, elapsed); }
        boolean blocked(String p) { return e.blocked(p, wall, elapsed); }
        Mode mode() { return e.mode(wall, elapsed); }
    }
    static void eq(Object expected, Object actual) { if (!Objects.equals(expected, actual)) throw new AssertionError("expected " + expected + ", got " + actual); }
    static void test(String name, Runnable run) { run.run(); tests++; System.out.println("PASS " + name); }
    static RulesEngine copy(RulesEngine engine, boolean sameBoot) {
        try {
            var bytes = new ByteArrayOutputStream(); new ObjectOutputStream(bytes).writeObject(engine);
            RulesEngine value = (RulesEngine) new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
            value.attach(sameBoot); return value;
        } catch (Exception ex) { throw new AssertionError(ex); }
    }
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        test("10 Instagram, gap, 5 X, 5 Reddit then cooldown", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(10); eq(true, c.blocked("i")); c.focus(null); c.minutes(20);
            c.focus("x"); c.minutes(5); c.focus("r"); c.minutes(5); eq(Mode.COOLDOWN, c.mode()); eq(COOLDOWN, c.e.countdown(c.wall, c.elapsed));
        });
        test("leaving pauses and reopening resumes", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(3); c.focus(null); c.minutes(30); eq(3 * MINUTE, c.e.used("i"));
            c.focus("i"); c.minutes(2); eq(5 * MINUTE, c.e.used("i"));
        });
        test("screen locking pauses focus", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(2); c.e.focus("i", false, c.wall, c.elapsed); c.minutes(30); eq(2 * MINUTE, c.e.total());
        });
        test("individual limit remains blocked across long idle", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(10); c.focus(null); c.minutes(180); eq(true, c.blocked("i")); eq(false, c.blocked("x"));
        });
        test("three apps can share allowance", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(7); c.focus("x"); c.minutes(7); c.focus("r"); c.minutes(6); eq(Mode.COOLDOWN, c.mode());
        });
        test("cooldown resets exactly at 60 minutes", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(10); c.focus("x"); c.minutes(10); c.minutes(59); eq(true, c.blocked("r")); c.minutes(1); eq(0L, c.e.total()); eq(false, c.blocked("i"));
        });
        test("late callback anchors cooldown to exhaustion", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(10); c.focus("x"); c.minutes(15); eq(55 * MINUTE, c.e.countdown(c.wall, c.elapsed));
        });
        test("late callback never charges after cooldown", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(10); c.focus("x"); c.minutes(90); eq(0L, c.e.total()); eq(null, c.e.focused());
        });
        test("fractional milliseconds are preserved", () -> {
            Clock c = new Clock(); c.focus("i"); c.millis(1234); c.focus("x"); c.millis(456); eq(1690L, c.e.total());
        });
        test("unselected apps consume nothing", () -> { Clock c = new Clock(); c.focus("other"); c.minutes(60); eq(0L, c.e.total()); });
        test("stop overrides all usage limits", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(10); c.e.stop(c.wall, c.elapsed); eq(false, c.blocked("i")); c.focus("x"); c.minutes(60); eq(10 * MINUTE, c.e.total());
        });
        test("start grants fresh allowance", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(7); c.e.stop(c.wall, c.elapsed); c.e.start(c.wall, c.elapsed); eq(0L, c.e.total());
        });
        test("lunch resets usage and blocks following hour", () -> {
            Clock c = new Clock(); c.e.lunchEnabled = true; c.minutes(230); c.focus("i"); c.minutes(5); c.focus(null); c.minutes(5);
            eq(Mode.LUNCH, c.mode()); eq(0L, c.e.total()); c.focus("x"); c.minutes(60); eq(Mode.COOLDOWN, c.mode()); eq(0L, c.e.total()); c.minutes(60); eq(Mode.READY, c.mode());
        });
        test("unused lunch still has mandatory cooldown", () -> {
            Clock c = new Clock(); c.e.lunchEnabled = true; c.minutes(300); eq(Mode.COOLDOWN, c.mode());
        });
        test("lunch overrides existing cooldown", () -> {
            Clock c = new Clock(); c.e.lunchEnabled = true; c.minutes(210); c.focus("i"); c.minutes(10); c.focus("x"); c.minutes(10); eq(Mode.COOLDOWN, c.mode()); c.minutes(10); eq(Mode.LUNCH, c.mode());
        });
        test("stop during lunch cooldown allows free use; start honors it", () -> {
            Clock c = new Clock(); c.e.lunchEnabled = true; c.minutes(300); c.e.stop(c.wall, c.elapsed); eq(false, c.blocked("i")); c.e.start(c.wall, c.elapsed); eq(true, c.blocked("i"));
        });
        test("sleep hides notifications without changing usage", () -> {
            Clock c = new Clock(); c.minutes(720); eq(true, c.e.quiet(c.wall)); c.focus("i"); c.minutes(3); eq(3 * MINUTE, c.e.total()); c.focus(null); c.minutes(717); eq(false, c.e.quiet(c.wall));
        });
        test("same-day quiet window and boundaries", () -> {
            Clock c = new Clock(); c.e.sleep(true, 600, 660); eq(true, c.e.quiet(c.wall)); c.minutes(60); eq(false, c.e.quiet(c.wall));
        });
        test("process recovery keeps usage and drops stale focus", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(3); c.e = copy(c.e, true); c.minutes(30); eq(3 * MINUTE, c.e.total()); eq(null, c.e.focused());
        });
        test("process recovery preserves cooldown deadline", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(10); c.focus("x"); c.minutes(10); c.e = copy(c.e, true); c.minutes(30); eq(30 * MINUTE, c.e.countdown(c.wall, c.elapsed));
        });
        test("reboot resets budgets and retains preference", () -> {
            Clock c = new Clock(); c.focus("i"); c.minutes(10); c.e = copy(c.e, false); c.e.advance(c.wall, 100); eq(0L, c.e.total()); eq(true, c.e.running);
        });
        test("lunch edit waits until tomorrow without midnight reset", () -> {
            Clock c = new Clock(); c.e.lunch(false, 840, c.wall, c.elapsed); c.focus("i"); c.minutes(3); c.focus(null); c.minutes(837); eq(3 * MINUTE, c.e.total()); eq(0L, c.e.pendingAt());
        });
        test("overnight lunch completes before edit", () -> {
            Clock c = new Clock(); c.e.lunchEnabled = true; c.e.lunchMinute = 23 * 60 + 30; c.minutes(810); eq(Mode.LUNCH, c.mode());
            c.e.lunch(false, 840, c.wall, c.elapsed); c.minutes(60); eq(Mode.COOLDOWN, c.mode()); c.minutes(60); eq(Mode.READY, c.mode()); eq(false, c.e.lunchEnabled);
        });
        test("configuration guards", () -> {
            Clock c = new Clock(); boolean rejected = false;
            try { c.e.select(Set.of("x")); } catch (IllegalStateException ex) { rejected = true; } eq(true, rejected);
            rejected = false; try { c.e.sleep(true, 0, 0); } catch (IllegalArgumentException ex) { rejected = true; } eq(true, rejected);
        });
        test("random focus and lock sequences preserve limits", () -> {
            Clock c = new Clock(); Random random = new Random(42); String[] apps = {"i", "x", "r", "other", null};
            for (int i = 0; i < 5000; i++) {
                c.e.focus(apps[random.nextInt(apps.length)], random.nextBoolean(), c.wall, c.elapsed); c.millis(random.nextInt(180_000));
                if (c.e.total() > TOTAL_LIMIT || c.e.total() < 0) throw new AssertionError("shared limit");
                for (String pkg : c.e.selected) if (c.e.used(pkg) > APP_LIMIT) throw new AssertionError("per-app limit");
            }
        });
        System.out.println(tests + " scenarios passed.");
    }
}
