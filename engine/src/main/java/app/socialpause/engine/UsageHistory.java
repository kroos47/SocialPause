package app.socialpause.engine;

import java.io.Serializable;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Local hourly aggregates. Serialized storage remains compatible; caches are rebuilt on demand. */
public final class UsageHistory implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Map<String, long[]>> days = new TreeMap<>();
    private transient long revision;
    private transient NavigableMap<String, Map<String,long[]>> index;
    private transient Map<Range,Summary> summaries;
    private record Range(LocalDate from, LocalDate through) {}
    public record Summary(Map<LocalDate,Map<String,Long>> days, Map<String,Long> byApp, long total) {}
    public long revision() { return revision; }
    public void record(String pkg, long startWall, long duration, ZoneId zone) {
        if (duration <= 0) return;
        revision++; if (summaries != null) summaries.clear();
        while (duration > 0) {
            ZonedDateTime time = Instant.ofEpochMilli(startWall).atZone(zone);
            long nextHour = time.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli();
            long part = Math.min(duration, nextHour - startWall);
            if (part <= 0) throw new IllegalStateException("History boundary must advance");
            String date = time.toLocalDate().toString();
            Map<String,long[]> apps = days.computeIfAbsent(date, key -> new HashMap<>());
            if (index != null) index.put(date, apps);
            apps.computeIfAbsent(pkg, key -> new long[24])[time.getHour()] += part;
            startWall += part; duration -= part;
        }
    }
    public Summary summary(LocalDate from, LocalDate through) {
        if (summaries == null) summaries = new LinkedHashMap<>();
        Range range = new Range(from, through);Summary cached = summaries.get(range);
        if (cached != null) return cached;
        if (index == null) index = new TreeMap<>(days);
        Map<LocalDate,Map<String,Long>> daily = new LinkedHashMap<>();Map<String,Long> result = new TreeMap<>();long total = 0;
        if (!through.isBefore(from)) for (var day:index.subMap(from.toString(), true, through.toString(), true).entrySet()) {
            Map<String,Long> apps = new TreeMap<>();
            for (var app:day.getValue().entrySet()) {
                long used = 0;for(long hour:app.getValue())used += hour;
                apps.put(app.getKey(),used);result.merge(app.getKey(),used,Long::sum);total += used;
            }
            daily.put(LocalDate.parse(day.getKey()),Collections.unmodifiableMap(apps));
        }
        Summary summary = new Summary(Collections.unmodifiableMap(daily),Collections.unmodifiableMap(result),total);
        // Bound caching even when a caller explores many different ranges.
        if (summaries.size() >= 32) summaries.remove(summaries.keySet().iterator().next());
        summaries.put(range,summary);return summary;
    }
    public Map<String,Long> byApp(LocalDate from,LocalDate through){return summary(from,through).byApp();}
    public long total(LocalDate from,LocalDate through){return summary(from,through).total();}
    public long[] hours(LocalDate day) {
        long[] result = new long[24];
        for (long[] hours : days.getOrDefault(day.toString(), Collections.emptyMap()).values())
            for (int h = 0; h < 24; h++) result[h] += hours[h];
        return result;
    }
}
