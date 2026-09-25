# Validation — SocialPause 0.7.0

Validated on 2026-09-25 on the Apple Silicon development Mac and an isolated Android 16 ARM64 emulator (API 36). Samsung device acceptance remains separate.

## Build and saved-state compatibility

- `sh scripts/verify.sh :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**. The final run completed in 8 seconds (77 actionable tasks). Engine scenarios, personal-install APK assembly, emulator test APK assembly, and Android lint passed.
- **165 timing/presentation/recovery scenarios passed**, including the existing **20,000 randomized reference-model transitions**. New coverage exercises the exact six-hour deadline, delayed callbacks, repeated Start, rejected Stop, wall-clock changes, paused usage, lunch/cooldown resets, reboot, same-boot process recreation, dismissal identities and current-process startup evidence.
- Lint: **0 errors, 1 existing warning** about a newer Gradle version. No checks were suppressed.
- Five synthetic fixtures serialized with the original 0.6 engine verify partial app/shared usage, independent/group cooldowns, active manual lunch, early post-lunch cooldown and stopped state. Existing 0.2/0.4/0.5 migration fixtures remain covered. Existing runs receive no retrospective Stop lock; their next Start creates one.
- APK identity: **0.7.0**, version code **9**, package `app.socialpause`, compile/target SDK 36, minimum SDK 33. No INTERNET permission.
- The APK signature matches the original public fingerprint in `.github/release-signing.sha256`; it can update the existing personal installation without uninstalling. The private key remains local and excluded from source archives.

## Emulator notification and UI checks

The dependency-free `RuntimeChecks` instrumentation requires a debug build, emulator hardware and explicit `synthetic=true`. It refuses physical phones. Its test-only fixtures adjust phase endpoints near boundaries without changing production timer constants. Notification callbacks are delivered through actual Android PendingIntents; assertions inspect actual posted notification payloads, rather than inferring them from engine labels.

**Eight runtime checks passed:**

- In both Individual and Shared modes, dismissing ordinary manual-lunch and post-lunch notifications preserves the subsequent focused promotion request, app/limiting icon, compact timer text, countdown chronometer and standard ProgressStyle expanded template. A delayed ordinary callback remains ordinary after the live notification replaces it.
- Dismissing a real focused live-request notification suppresses promotion for that monitoring run while retaining its drawer countdown. Start clears suppression; a delayed callback from the previous run cannot suppress the new run.
- Existing ambiguous `ordinary-run` suppression remains until Start.
- The controller rejects early Stop; repeated Start preserves the run and lock.
- Home displays the pre-Start explanation, disables Stop after Start, and displays HH:MM:SS. Its existing ticker enables Stop at the deadline without stopping monitoring automatically.
- Manual lunch and Stop lunch remain usable while the main Stop stays locked, and manual daily eligibility is consumed once.

The three Home checks passed again in dark mode at **150% font scale**. Light, dark and large-text screenshots were visually inspected: the disabled button and countdown remain readable without overlap, and the page remains scrollable. No SocialPause crash was recorded in the emulator crash log.

An initial synthetic test burst exceeded Android's notification enqueue rate limit. The harness now spaces transitions realistically; production notification logic was not changed to retry or bypass the platform limit. Test setup also toggles Accessibility to clear Android's crashed-service state after instrumentation terminates its target process.

## External lifecycle checks

**Six lifecycle checks passed**, using normal launcher starts and persisted-state inspection outside instrumentation:

- Android Force stop followed by a normal launch leaves monitoring stopped, clears the Stop lock, and retains manual lunch eligibility/history.
- Killing only the app process, then launching normally, retains monitoring, usage, monitoring-run identity and the remaining lock. This was not simulated with Force stop.
- Accessibility removal during usage, an independent cooldown, and manual lunch stops monitoring despite the lock. Restoring permission leaves monitoring stopped. All three checks show no active SocialPause notification or scheduled alarm afterward; history and lunch eligibility survive.
- An actual emulator reboot leaves monitoring stopped until Start, clears the elapsed-time lock, and preserves manual lunch eligibility/history. The subsequent normal Start and disabled Stop were also visually checked.

## Delivery and remaining acceptance

- Named personal APKs: `artifacts/SocialPause.apk` and `artifacts/SocialPause-debug.apk`.
- `artifacts/SocialPause-source.zip` is a snapshot of the reviewed working source, including guides and synthetic test fixtures. It excludes private keys, SDK paths, caches, generated build output and runtime data. It is not a published GitHub release or a clean-commit release bundle.
- Build/lint output, synthetic runtime results, screenshots, lifecycle snapshots and checksums are under ignored `artifacts/v0.7.0/`. Top-level artifact checksums are in `artifacts/SHA256SUMS.txt`.
- Open the main repository root in Android Studio. The old extracted `artifacts/SocialPauseBuild` folder is not the working project.
- After updating an already-running 0.6 installation, use Stop then Start once to clear any old notification suppression and begin the first six-hour lock. History and daily lunch eligibility remain intact.
- Keep Samsung **Developer options → Live notifications for all apps** enabled. Actual One UI live-pill rendering after lunch still needs the Galaxy S24 Ultra check in DEVICE_TESTS.md; emulator eligibility and notification payloads do not prove Samsung presentation.
- GitHub-hosted CI and publishing were not run for these uncommitted changes. The existing build/test workflow remains in place; release notes and publishing guidance now target 0.7.0/code 9.
