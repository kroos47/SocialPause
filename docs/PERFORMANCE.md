# Performance — SocialPause 0.7.2

Measured on 2026-09-29 against the reviewed **0.7.1 baseline commit 41b97118882a057805fed4229c0aee2879811114**. That commit preserves the dismissal fix separately from these optimization changes. No production diagnostic counters were added.

## What improves

| Change | Why it helps | Behavior preserved |
|---|---|---|
| Revision checkpoint before serialization | Unchanged updates skip encoding the complete engine/history. | Active usage and important transitions still save; initial load/migration always checkpoints. |
| Adaptive window fallback | Quiet Home/unselected periods inspect every 2 seconds rather than 500 ms. | Events are immediate; selected/drawer-covered/temporarily uncertain selected visibility stays at 500 ms; screen off at 30 seconds; stopped has no recurring inspection. |
| Notification signature and metadata cache | Unchanged updates skip label lookups, text/layout construction and posting; hidden transitions cancel once. | Icons, expanded countdown, limiting allowance, synchronous generation checkpoint, one-ID recovery and Samsung eligibility fallback. |
| Indexed/cached history summary | Queries visit the requested dates and reuse immutable results; unchanged Insights does not rebuild bars. | Original hourly serialized map, app colors, Today/Week totals and selected-day interaction. |
| One controller for state publication | UI countdowns read snapshots without triggering additional state writes, notification work or alarms. | Monotonic usage/Stop deadlines, one-second visible countdowns, lunch and alarm boundaries. |

The default no-other-boundary alarm is now anchored at local midnight instead of sliding forward on every refresh. Persistent state, configured allowances, lunch records, selection and the six-hour lock retain their existing serialized representation.

## Actual Android work counts

Disposable Android 16/API 36 ARM64 AOSP emulator, same 1080 × 2400 configuration and seven-day synthetic history for the timed-window cases. Measurement builds used local test-only counters in isolated copies; the delivered APK contains none of these probes. Cold initialization is excluded from unchanged-refresh cases. The hidden case includes the transition into Sleep Time.

| Workload | 0.7.1 | 0.7.2 |
|---|---:|---:|
| 100 unchanged controller updates: serializations | 100 | 0 |
| Same updates: app-label lookups | 300 | 0 |
| Enter Sleep Time, then 100 checks: cancellations | 100 | 1 |
| Same hidden checks: serializations | 100 | 1 (changed Sleep setting) |
| 200 unchanged Insights renders: history aggregations | 2,000 | 0 |
| Same renders: chart-data rebuilds | 200 | 0 |
| 20 seconds on Home: window inspections / scheduled fallbacks | 39 / 39 | 10 / 10 |
| 20 seconds in selected Clock: window inspections / fallbacks | 39 / 38 | 39 / 38 |
| Same selected interval: serialization calls | 39 | 39 |
| Same selected interval: notification posts | 20 | 20 |
| Same selected interval: notification builder calls | 40 | 40 |

The emulator requires the existing eligibility fallback, hence two builder calls per posted active notification. This behavior was preserved. Quiet scheduled checks fall **75% by policy** (500 ms → 2,000 ms); the measured finite interval yielded 39 → 10. Selected-app inspections, active saves and visible countdown publication stayed at the prior rate.

## Android CPU and allocations

Single before/after Android runs, intended to establish removed work rather than statistical battery estimates. Batch rows measure main-thread CPU; timed-window rows measure total app-process CPU. Allocation bytes use ART's process allocation counter and include runtime/background allocations. No profiler or trace collector ran during these measurements.

| Synthetic history | Workload | CPU ms, before → after | Allocated MB, before → after |
|---|---|---:|---:|
| 7 days | 100 unchanged updates | 134.6 → 14.3 | 7.999 → 0.364 |
| 7 days | 100 hidden checks | 73.9 → 4.3 | 7.275 → 0.217 |
| 7 days | 200 Insights renders | 957.8 → 8.5 | 11.731 → 0.524 |
| 365 days | 100 unchanged updates | 3185.7 → 25.8 | 243.543 → 0.503 |
| 365 days | 100 hidden checks | 2643.4 → 12.9 | 240.165 → 2.494 |
| 365 days | 200 Insights renders | 4531.3 → 3.0 | 337.347 → 0.524 |
| 1095 days | 100 unchanged updates | 8866.1 → 18.4 | 762.478 → 0.364 |
| 1095 days | 100 hidden checks | 6400.8 → 17.9 | 759.849 → 7.688 |
| 1095 days | 200 Insights renders | 2982.5 → 3.2 | 1002.930 → 0.524 |
| 7 days | 20 seconds Home | 233 → 47 | 4.768 → 0.361 |
| 7 days | 20 seconds selected app | 341 → 338 | 12.405 → 12.341 |

Active-use cost is intentionally retained. The substantial gains are in unchanged state and repeated Insights rendering. The small differences between single selected-app runs are not evidence of a battery improvement.

## Reproducible JVM comparison

`scripts/measure-performance.py` compiles the preserved baseline and current engine with Java 17 source compatibility and runs each in three JDK processes. It creates seven-day, one-year and three-year synthetic histories, then measures unchanged saves, repeated range queries, and forty real active-use checkpoints. CPU and allocation counters are for the benchmark thread, not the Android phone. Serializer warm-up is equalized before the active-save comparison; otherwise removing idle serialization would unfairly leave only the optimized active serializer cold.

- 1,000 unchanged save checks: **1,000 → 0 serializations**, after the initial checkpoint.
- Serialized bytes avoided over those checks: **5,918,000 / 241,840,000 / 722,910,000**, for 7 / 365 / 1,095 days respectively (before Base64).
- Forty active-use checkpoints still serialize **240,040 / 9,676,920 / 28,919,720 bytes** in both versions. This explicitly records the retained history-serialization cost.
- Repeated range-query results are cached; the host query benchmark still calls the query API 1,600 times, whereas the real UI additionally skips those calls and chart updates when unchanged.

Run `python3 scripts/measure-performance.py --output artifacts/v0.7.2/host-performance` with JAVA_HOME configured. The baseline commit must exist in local Git history. Raw CSVs, local Android probe source, test outputs and measurement notes are kept under ignored `artifacts/v0.7.2/` for this delivery.

## Limits and phone acceptance

This release **does not claim a battery percentage**. Comparable measurements on the Galaxy S24 Ultra remain necessary, along with visible Samsung live-countdown recovery and acceptable app-entry/blocking responsiveness. A lost entry event outside selected apps can take approximately two seconds to recover; normal event delivery remains immediate.

The storage format still contains history alongside engine state, so actual usage continues serializing that record. Moving history to separate storage is deferred to a migration with its own compatibility tests. Cache initialization rebuilds the date index once after loading; the repeated-query measurements exclude that first initialization.

## Proposed commit groups

Keep the existing 0.7.1 commit intact. These are preparation suggestions; no commit, push, tag or release publication is performed here.

1. `perf: skip unchanged state encoding and centralize timer publication` — transient revisions/checkpoint, snapshots, controller commands and projection/migration tests.
2. `perf: cache notification displays and indexed Insights summaries` — labels/style/signature, hidden transitions, chart/history caches and invalidation tests.
3. `perf: reduce quiet window checks while preserving immediate monitoring events` — 2-second fallback, uncertainty/lock/dismissal rules, event coalescing and real-window tests.
4. `test: validate runtime outcomes and prepare SocialPause 0.7.2` — CI instrumentation build, failing-result wrapper, version 11, guidance, measurements and release notes.

Review the combined diff and dependency order before splitting commits; each proposed group needs its matching tests. The named local APK is a working-tree delivery, not a published clean-commit release.
