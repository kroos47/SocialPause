# Original v0.5 serialization fixtures

`GenerateV05Fixtures.java` was compiled against the frozen, unmodified v0.5 engine before the v0.6 configuration fields were added. The original `RulesEngine.java` SHA-256 was `4130d8594303c01e68a522dbe5e06758dcb01e9779fbdc4be4e554b3e28ebb18`.

The serialized states in `engine/src/test/resources/` are entirely synthetic: fixed local dates, social package IDs, fake elapsed time and generated usage totals. They contain no real phone data, user paths, credentials or signing material. Do not regenerate these using the current engine; the missing v0.6 fields are what the upgrade tests exercise.

All examples start at 2026-09-16 10:00 Asia/Kolkata with elapsed time 1,000 ms, Shared mode set to 45 minutes, and Sleep Time 21:00–09:00.

- `legacy-v05-shared45-partial.bin`: 10:19, elapsed 1,141,000 ms. Instagram has 48 cooldown minutes left, X has 58, and Reddit has used two minutes. Shared remaining is 26 minutes; usage history is 19 minutes. The lunch schedule is enabled at 15:00 that day through the existing conditional same-day rule.
- `legacy-v05-shared45-stopped.bin`: the same state after overall monitoring Stop. History remains 19 minutes. It verifies that the saved future allowance can be capped without losing history.
- `legacy-v05-shared45-cooldown.bin`: 11:40, elapsed 6,001,000 ms. The old 45-minute shared allowance was exhausted at 11:35 after individual cooldowns had allowed more usage. Shared cooldown has 55 minutes left; history is 45 minutes. Its deadline must survive upgrade unchanged, and only the subsequent fresh cycle adopts 30 minutes.
- `legacy-v05-manual-lunch.bin`: 10:24, elapsed 1,441,000 ms. A manual lunch started at 10:19 and has 55 unrestricted minutes left. The day's manual entitlement is consumed and automatic lunch suppressed. History is seven minutes, and the old shared budget is 45 minutes.
- `legacy-v05-manual-block.bin`: 10:34, elapsed 2,041,000 ms. The preceding manual lunch was stopped early at 10:29, leaving 55 minutes in its full post-lunch block. Its deadline and manual-use date must survive upgrade/reboot.

SHA-256:

```text
56e9dc52ab1db2cdcad3eb40bd0879da27017fddb3a280cfb014cbf82aa0a9b5  legacy-v05-shared45-partial.bin
dfa56f316406befac8f8aacab621d55c9cd6ec17816312c3fe064841a3e2df25  legacy-v05-shared45-stopped.bin
e0d551ee917311ec011dd85122c875a37e99e55efee02459387b6630eca33240  legacy-v05-shared45-cooldown.bin
98fb40ee1c35c68091e8e34e0cfe964821e4578399fc5abe1a988e904b3f6a19  legacy-v05-manual-lunch.bin
5c6be991800b88a42dbebe34d29d63a8b1c1a2105760f32b6e9b700c34b5a604  legacy-v05-manual-block.bin
```
