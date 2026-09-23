# Original v0.4 serialization fixtures

`GenerateV04Fixtures.java` was compiled and run against the unmodified v0.4 engine before the v0.5 fields were introduced. The original `RulesEngine.java` SHA-256 was `724504864cc7dcf58fd31c17da50bebc58791ec1454dec5d6c6152f13280ab9f`.

These are synthetic states with fixed dates, standard social package IDs and fake elapsed time. They contain no real phone data, user paths or signing material. They remain in `engine/src/test/resources/` and must not be regenerated using the current engine: that would no longer exercise deserialization of missing v0.5 fields.

- `legacy-v04-partial.bin`: 2026-09-16 10:05 Asia/Kolkata, elapsed 301,000 ms. Instagram has used three minutes and X two minutes. History is five minutes; lunch disabled; Sleep Time 21:00–09:00.
- `legacy-v04-cooldowns.bin`: same day at 10:19, elapsed 1,141,000 ms. Instagram has 48 cooldown minutes left; X has 58; Reddit has used two minutes. History is 19 minutes. A 15:00 automatic lunch is pending for the next day.
- `legacy-v04-lunch.bin`: same day at 14:20, elapsed 15,601,000 ms. The original scheduled lunch started at 14:00. Its old serialized representation holds a start timestamp only, so this fixture is also read at 15:20 to validate the remaining 40-minute post-lunch cooldown. No history.

SHA-256:

```text
e0a16c0940e2af17b9c3871b9165f5742a8e519d969f041247545ec3753b10f0  legacy-v04-partial.bin
cc76e85d537a6b34aa716fcb886e1f81987c9154973f5228421dd6bc4565b28f  legacy-v04-cooldowns.bin
907fcb941e537f10defade319e0d70eb14e8b7b5cf6dde7923a6414fecb748f4  legacy-v04-lunch.bin
```
