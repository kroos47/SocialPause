package app.socialpause.engine;

import java.io.Serializable;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Local hourly aggregates. Recorded only when the engine consumes allowance; resets never erase history. */
public final class UsageHistory implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Map<String, long[]>> days = new TreeMap<>();
    public void record(String pkg, long startWall, long duration, ZoneId zone) {
        while (duration > 0) {
            ZonedDateTime time = Instant.ofEpochMilli(startWall).atZone(zone);
            long nextHour = time.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli();
            long part = Math.min(duration, nextHour - startWall);
            if (part <= 0) throw new IllegalStateException("History boundary must advance");
            long[] hours = days.computeIfAbsent(time.toLocalDate().toString(), key -> new HashMap<>())
                    .computeIfAbsent(pkg, key -> new long[24]);
            hours[time.getHour()] += part;
            startWall += part; duration -= part;
        }
    }
    public Map<String, Long> byApp(LocalDate from, LocalDate through) {
        Map<String, Long> result = new TreeMap<>();
        for (var day : days.entrySet()) {
            LocalDate date = LocalDate.parse(day.getKey());
            if (date.isBefore(from) || date.isAfter(through)) continue;
            for (var app : day.getValue().entrySet())
                result.merge(app.getKey(), Arrays.stream(app.getValue()).sum(), Long::sum);
        }
        return Collections.unmodifiableMap(result);
    }
    public long total(LocalDate from, LocalDate through) { return byApp(from, through).values().stream().mapToLong(Long::longValue).sum(); }
    public long[] hours(LocalDate day) {
        long[] result = new long[24];
        for (long[] hours : days.getOrDefault(day.toString(), Collections.emptyMap()).values())
            for (int h = 0; h < 24; h++) result[h] += hours[h];
        return result;
    }
}
