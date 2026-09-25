# Original v0.6 serialization fixtures

`GenerateV06Fixtures.java` was compiled against the frozen, unmodified v0.6 engine before the v0.7 Stop-lock fields were added. The original `RulesEngine.java` SHA-256 is `61226fb840f4a74ab751443e06c4fda0702178443a4c44ce3e0e354950ff75f0`.

These states use only synthetic dates, social package IDs, fake elapsed times and generated usage totals. They contain no real phone data, paths, credentials or signing material. Do not regenerate them against the current engine: the missing Stop-lock field is what the migration tests exercise.

All scenarios start at 2026-09-16 10:00 Asia/Kolkata, elapsed time 1,000 ms, Shared mode with a 13-minute allowance, app limits of Instagram 2 minutes, X 12 minutes and Reddit zero, and Sleep Time 21:00–09:00.

- `legacy-v06-partial.bin`: 10:05, elapsed 301,000 ms. Instagram has 57 minutes of cooldown left, X has used three minutes, shared remaining is eight minutes, and history is five minutes. A same-day lunch schedule edit enables 15:00.
- `legacy-v06-stopped.bin`: the same partial state after overall Stop.
- `legacy-v06-shared-cooldown.bin`: 10:18, elapsed 1,081,000 ms. Shared exhaustion occurred at 10:13; its cooldown has 55 minutes remaining. History is 13 minutes.
- `legacy-v06-manual-lunch.bin`: 10:20, elapsed 1,201,000 ms. Manual lunch started at 10:05 and has 45 unrestricted minutes left. History is two minutes, and the daily entitlement is consumed.
- `legacy-v06-manual-cooldown.bin`: 10:30, elapsed 1,801,000 ms. That manual lunch was stopped early at 10:20, so its post-lunch cooldown has 50 minutes left.

SHA-256:

```text
327bf198ddc4dbc17f8dcd1e5194a3caa24c748c85a652e3aaa69cb51ab7925d  legacy-v06-manual-cooldown.bin
cc5ee196cf1e03f7e6ed474049d142ce9808f9a1a2ee489b90d88185822ea666  legacy-v06-manual-lunch.bin
70d81919a99352bdc8b1fc3f0f7d505cc54266bf12665eb631767eb07b19eaca  legacy-v06-partial.bin
b172cc21849fa9dd9c1febfcf02602003eef58084297ca2d5587bafaba7050a1  legacy-v06-shared-cooldown.bin
efb98e52fc4b15f508ed672ae435910c7eb38633340e23da6ab507f077e224cb  legacy-v06-stopped.bin
```
