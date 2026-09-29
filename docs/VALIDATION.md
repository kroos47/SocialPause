# Validation — SocialPause 0.7.1

Validated on 2026-09-29 on the Apple Silicon development Mac and a disposable Android 16 ARM64 AOSP emulator (API 36). Samsung device acceptance remains separate.

## Build and compatibility

- `sh scripts/verify.sh :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**. Engine checks, personal-install debug APK, emulator test APK and lint pass.
- **171 engine scenarios passed**, including the existing **20,000 randomized reference-model transitions** and legacy serialized fixtures. New fake-clock cases cover repeated dismissal, drawer return, Home return, delayed/duplicate/wrong-generation callbacks, lunch and cooldown transitions, preserved usage and Stop deadlines, pending restoration across recreation, and hidden notifications. Five focus-visibility scenarios cover SystemUI panels, redacted/stale windows, keyboard, app switches, lock and Recents.
- Lint: **0 errors, 2 warnings**. One existing Gradle-update warning; one `ApplySharedPref` warning for the deliberately synchronous, small notification-identity checkpoint. It runs only on notification lifecycle transitions, not countdown ticks, to persist generation identity before posting. No lint checks were suppressed.
- APK: **0.7.1**, code **10**, package `app.socialpause`, compile/target SDK 36, minimum SDK 33. No INTERNET permission.
- Certificate SHA-256 matches `.github/release-signing.sha256` and the existing personal-install key. Install over the current app; do not uninstall or clear its data.
- No serialized engine schema or timer rules changed. Old `ordinary-run` and `dismissed-cycle` notification preferences are removed automatically; no Stop/Start is needed. State, history, lunch records and the six-hour Stop lock remain intact.

## Android notification and Home checks

**11 runtime checks passed** using actual posted notifications and PendingIntent callbacks:

- Both modes recover focused live requests after ordinary lunch/cooldown dismissal, including delayed ordinary callbacks after focused replacement.
- Both modes defer live restoration while the drawer covers a selected app, continue usage accounting, and restore on closure. Repeated and delayed active callbacks restore once; callbacks from an older generation cannot replace the newer notification.
- Both modes restore after Home dismissal. Only one timer notification remains.
- Upgrade removes legacy suppression and notification-publisher recreation preserves pending recovery and the engine's run/Stop deadline.
- App/limiting icons, explicit compact timer text, chronometer countdown, standard expanded ProgressStyle and OS format eligibility remain present.
- Main Stop remains guarded, repeated Start preserves its deadline, Home shows the disabled Stop/countdown and enables it at the boundary, and lunch controls retain their behavior.

These callback checks isolate synthetic focus; the separate gesture suite below exercises actual Accessibility window observation. Fixtures adjust deadlines or selected apps only in the emulator test APK. Production durations and behavior are unchanged.

## Actual notification drawer gestures

**8 gesture scenarios passed**: Individual/Shared × focused app/Home × individual swipe/Clear all.

The test-only drawer phase selects AOSP Clock and leaves the real Accessibility service active. It pulls down SystemUI, confirms covered visibility, verifies that focused usage continues (or stays paused on Home), performs real swipes/taps, closes the drawer or enters Clock, then verifies the recovered focused payload and unchanged run/Stop deadline.

- Every individual-swipe scenario requires a changed delete-intent generation, demonstrating a real SocialPause dismissal rather than merely calling its receiver.
- Clear all is made available with a disposable notification from Android's shell package. The test verifies that it was cleared and SocialPause's focused countdown request remains/restores afterward. Android can retain ongoing notifications during Clear all; the suite does not assume that this action always dismisses SocialPause.
- The final app notification count is one, and the focused notification retains live eligibility, MM:SS and the drawer countdown.
- Initial harness issues involved a same-app auxiliary notification triggering Android automatic grouping, test startup reconciliation and shell argument parsing. The fixtures were corrected; production behavior was not weakened to pass them.

## Delivery and remaining acceptance

- Named personal APKs: `artifacts/SocialPause.apk` and `artifacts/SocialPause-debug.apk`.
- `artifacts/SocialPause-source.zip` contains reviewed source and synthetic fixtures, excluding signing keys, local SDK paths, caches, generated binaries and emulator data. This is a working-tree snapshot, not a published clean-commit release bundle.
- Build/lint and emulator reports are retained under ignored `artifacts/v0.7.1/`; artifact hashes are in `artifacts/SHA256SUMS.txt`.
- Open the main repository root in Android Studio. Do not use the older extracted `artifacts/SocialPauseBuild` copy.
- Keep Sleep Time inactive and Samsung **Developer options → Live notifications for all apps** enabled for acceptance. Swipe during a selected app and close the drawer without reopening it; repeat from Home, after lunch/cooldown, and in both modes. Confirm the **visible status-bar MM:SS**, not merely the drawer countdown or an Android promotion flag.
- **Samsung visible-pill recovery is not yet verified.** Emulator eligibility/payloads cannot certify One UI presentation.
- Actual reboot/Force-stop/permission-removal lifecycle checks from 0.7.0 were not repeated for this patch; their engine and startup-evidence regressions remain in the passing suite. Notification-publisher recreation is explicitly tested here, rather than described as an external OS process-kill test.
- No GitHub commit, push, tag or release was created by this patch task. Existing CI remains in place.
