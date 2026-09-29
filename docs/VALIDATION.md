# Validation — SocialPause 0.7.2

Validated on 2026-09-29 using the Apple Silicon development Mac and a disposable Android 16/API 36 ARM64 AOSP emulator. Samsung acceptance remains separate.

## Build, compatibility and identity

- `sh scripts/verify.sh :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**. Application/test APK assembly, engine checks and lint pass.
- **197 engine scenarios passed**, retaining all 171 previous scenarios and 20,000 randomized reference-model transitions. Added revision/no-op, snapshot, cached/indexed history, midnight/week rollover, display-signature, adaptive-fallback and original 0.7.1 migration coverage.
- Original 0.7.1 fixtures preserve partial app/shared usage, an app cooldown, history, manual lunch/after-lunch phases, daily eligibility and an active six-hour Stop deadline. Existing older upgrade fixtures remain in the suite. Revisions and caches are transient; serialized field layout is unchanged.
- Lint: **0 errors, 2 existing warnings** (`AndroidGradlePluginVersion`, `ApplySharedPref`). The small notification-generation checkpoint deliberately uses synchronous `commit()` before publication; it was not weakened or suppressed.
- **0.7.2 / version code 11**, package `app.socialpause`, compile/target SDK 36, minimum SDK 33, no INTERNET permission. The APK retains the original certificate SHA-256 recorded in `.github/release-signing.sha256`.
- Verification used JDK 21 with Java 17 source compatibility. CI retains the Java 17/21 engine matrix and now compiles the separate instrumentation APK. Remote Java 17/21 and Android results are recorded by the **Build and test** workflow for the release commit/tag; the results in this document describe local verification.
- **8 runtime-wrapper unit checks passed**, including ADB exit-zero/test-failure, missing results, cancelled/crashed instrumentation and incomplete suites.

## Clean shipping-build emulator checks

**28 checks passed** through `scripts/verify-runtime.py`, which validates the actual instrumentation result and count:

- **11 retained notification/Home checks:** both modes; lunch/cooldown/Home/focused dismissal, deferred and delayed recovery, stale/duplicate callbacks, old suppression migration, correct icons/expanded chrono/progress/compact timer, main Stop lock and lunch controls.
- **8 retained actual drawer gesture checks:** Individual/Shared × focused Clock/Home × swipe/Clear all, with real Accessibility windows. Usage continues under the drawer and pauses on Home; the focused live request returns with one notification and the original Stop deadline.
- **6 optimization/UI checks:** unchanged checkpoint and notification reuse; Sleep visibility; unchanged Insights and history invalidation; day selection/deselection; repeated app-picker taps, adding Clock and Save; empty-selection validation; Cancel and lunch draft cancellation.
- **3 real scheduling checks:** 2-second Home vs 500 ms selected fallback, intentionally missed entry event recovered within fallback tolerance, 30-second screen-off fallback and no scheduled inspection while stopped.

Clear all can retain ongoing notifications on Android. Each case verifies removal of a disposable shell notification and the returning focused payload; individual swipes require an actual changed SocialPause dismissal generation. Gesture checks are separate from direct callback tests.

During development, a new test exposed an initial locale-cache invalidation that rebuilt the first notification once; it was corrected. The picker test initially checked its dismiss listener before Android dispatched it; the test now checks immediate dialog dismissal and waits for the listener, without changing production picker behavior. One cold gesture run missed its drawer control; the full final suite passed all eight gestures. The wrapper rejected each failed run rather than reporting success from ADB's exit code.

## External lifecycle and appearance

**6 external Android recovery checks passed**, using full-duration synthetic fixtures and normal launcher starts after platform actions:

1. Force stop leaves monitoring stopped and retains lunch/history.
2. Killing only the ordinary app process preserves the run, usage and decreasing Stop-lock deadline.
3. Accessibility removal during usage stops monitoring and stays stopped after restoring access.
4. The same during app cooldown.
5. The same during lunch, retaining manual eligibility/history.
6. Reboot leaves monitoring stopped and retains manual eligibility/history.

The three permission cases also check that the active timer notification and pending schedule alarm are absent. Re-enabling permission does not auto-start monitoring.

The three Home/Stop/lunch UI scenarios passed again in light mode and in dark mode at **150% text**. Local screenshots were inspected for readable countdown/disabled Stop controls, wrapping and navigation. Font scale was restored afterward. The task emulator and unused ADB server were stopped after verification. Screenshots between instrumentation runs can show a temporary unbound monitoring service; they establish layout, not live monitoring reliability.

## Measured optimization

See [PERFORMANCE.md](PERFORMANCE.md) for methods, before/after CPU/allocation tables, retained costs and reproduction steps.

- Host: 1,000 unchanged save checks perform **0 additional serializations**, after the initial checkpoint.
- Android: 200 unchanged Insights renders perform **0 repeated aggregations and 0 chart-data rebuilds**, compared with 2,000 and 200 in the baseline.
- Entering Sleep Time and checking 100 times cancels once, compared with 100 cancellations.
- Over 20 seconds on Home, measured window checks fell from 39 to 10. Selected-app checks and active-use serialization stayed at the prior rate, with 20 countdown posts in each version.
- Synthetic histories cover 7, 365 and 1,095 days. JVM comparisons use three runs per version; Android numbers are single paired observations with local test-only probes. The personal-install APK contains no probe counters.
- Actual active usage still serializes the existing combined history record. No Samsung battery percentage is claimed.

## Delivery and remaining phone acceptance

- Named APKs: `artifacts/SocialPause.apk` and `artifacts/SocialPause-debug.apk`; source snapshot: `artifacts/SocialPause-source.zip`; hashes: `artifacts/SHA256SUMS.txt`.
- Reports, synthetic measurements, local probe source and emulator captures are under ignored `artifacts/v0.7.2/`. They are excluded from the source archive and proposed Git contents, together with keys, SDK paths and caches.
- The 0.7.1 fix remains preserved at commit `41b9711`. The 0.7.2 release uses tag `0.7.2`. The clean-commit packager reruns verification, checks the original signing certificate and writes the exact source commit to `artifacts/releases/0.7.2/BUILD-INFO.txt`; only the four reviewed assets are published after CI succeeds.
- Open the main repository root in Android Studio, not the old extracted `artifacts/SocialPauseBuild` directory. Install over the existing app using the same key; do not clear data or uninstall.
- **Samsung visible status-bar recovery, app-entry/blocking responsiveness and comparable battery measurements remain pending.** Keep Sleep Time inactive and the previously working “Live notifications for all apps” setting enabled. Repeat focused/Home swipes and Clear all in both modes and after lunch/cooldown. Valid emulator payloads and promotion eligibility do not prove One UI renders the countdown.
- A missed foreground event outside selected apps can take approximately two seconds to recover; normal Accessibility events remain immediate.
