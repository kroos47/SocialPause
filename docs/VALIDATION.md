# Validation results — 0.2.0

Verified on 2026-09-13 on the Apple Silicon Mac and a temporary generic Android 16 ARM64 emulator.

## Build and engine evidence

Run from the project root:

```sh
sh scripts/verify.sh
```

This invokes the wrapper for `:engine:check :app:assembleDebug :app:lintDebug`. Final result: **BUILD SUCCESSFUL in 22s**, 50 actionable tasks (14 executed, 36 up-to-date).

| Check | Observed result |
|---|---|
| Project timing suite | 25 scenarios passed, including 5,000 randomized focus/lock transitions. |
| Extended redesign suite | 42 scenarios passed against the current engine, including history, notification selection and 0.1 saved-state compatibility. |
| Android debug assembly | Passed. Version 0.2.0, version code 2. |
| Android lint | 0 errors; 1 warning that a newer Gradle version is available. Pinned build tools retained. |
| APK signature | `apksigner verify --verbose` verifies; APK v2 signature valid. |
| APK manifest | App ID `app.socialpause`; min SDK 33, target/compile SDK 36; launchable MainActivity; no INTERNET permission. |

The extended test-file copy into the project was declined. That write was not retried. The original project suite therefore remains 25 scenarios. The complete 42-scenario runner and the current engine are delivered separately in `artifacts/design-tests.zip`; extract and run `scripts/test-engine.sh` with a complete JDK 17 or 21. It includes the original 25 scenarios plus 17 checks for this update.

Toolchain: Gradle 8.13, Android Gradle Plugin 8.13.0, complete Temurin JDK 21.0.12.1+1, Android SDK Platform 36 and Build Tools 36.0.0. Java source level remains 17. Build/lint evidence and SHA-256 checksums are in `artifacts/`.

## Generic Android runtime evidence

The final APK was installed on an Android 16 ARM64 emulator with Accessibility and notifications enabled. Synthetic state fixtures were placed only in emulator-private app storage; they are not shipped in the APK or restored into the user's phone.

- Home rendering checked with 12:30 shared time, Instagram 05:00 and X 07:30 remaining.
- Insights navigation checked for a 3h 20m week and 32m day, with per-app totals and chart changes.
- Settings, cooldown, dark appearance and 150% font-size smoke captures inspected. Main controls remained accessible; longer content scrolls.
- An individual-limit test used the emulator's Clock app with 2 seconds remaining. The limit overlay appeared. Subsequent window inspection confirmed Launcher focus and the Accessibility service still bound with no crashed services.
- Final paused and cooldown notifications were inspected. Cooldown has a single system chronometer; paused remainder is static.
- No AndroidRuntime crash was recorded during the final capture sequence.

Evidence: `artifacts/screenshots/`, `runtime-window.txt`, `runtime-accessibility.txt`, `runtime-crash-log.txt`, `build.log`, `extended-tests.log`, and lint reports. Initial automation using Monkey did not produce reliable overlay captures; the final bounded check launched Clock directly and captured successive frames. Only validated captures are delivered.

## Not yet verified / observed limitations

- Installation and all acceptance scenarios on the user's Galaxy S24 Ultra / One UI 8.5.
- Samsung live/Now Bar promotion, custom app marks and system formatting. The API request is implemented; promotion is not guaranteed.
- Real-hour schedule/cooldown delivery, idle battery management, screen-lock behavior and multi-window on Samsung.
- Full TalkBack, landscape and narrow-window accessibility acceptance.
- Light-mode AOSP three-button navigation glyph contrast was poor despite requested light-bar appearance (dark glyphs); validate Samsung gesture and button navigation. App bottom-navigation icons/labels are separate and readable.

Normal Android apps remain bypassable by force-stop, uninstall or disabling Accessibility. Stop is an intentional unrestricted override. Home redirection does not force-stop another process or stop background audio. Callback latency can permit a brief launch flash. Exact schedule delivery depends on permission and OS behavior. Reboot may reset allowances; local asynchronous persistence can lose a small final increment on abrupt process death. Wall-clock edits affect daily schedules.
