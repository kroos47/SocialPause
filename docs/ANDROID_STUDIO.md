# Build and run with Android Studio

## 1. Open the repository root

Clone or extract the project to a folder of your choice. In Android Studio choose Open and select the folder containing `settings.gradle.kts`, `app/`, `engine/`, and `gradlew`.

## 2. Choose the build JDK

Open **Android Studio → Settings → Build, Execution, Deployment → Build Tools → Gradle**. Select or download a **complete JDK 21**, including `jlink`, for Gradle. JDK 17 is also supported by the verification helper. Use Gradle from the project's wrapper.

The project uses Gradle 8.13, Android Gradle Plugin 8.13.0 and Java 17 source syntax. The JDK used to run Android Studio itself can differ from the Gradle JDK; do not assume the bundled runtime is compatible with this build. [Android's JDK configuration guide](https://developer.android.com/build/jdks)

For terminal builds, set `JAVA_HOME` to the same complete JDK installation. The verification helper can also discover an existing local JDK under `.tools/jdk21/*/Contents/Home`, but that private folder is not part of a clone or source archive.

## 3. Check the Android SDK

Open **Tools → SDK Manager** and install:

- Android SDK Platform **36** (Android 16)
- Android SDK Build-Tools **36.0.0**
- Android SDK Platform-Tools (for USB/device deployment)

Let Android Studio create `local.properties` with your own SDK path. Both that file and any local `.tools` setup are ignored by Git. SDK 37 alone is insufficient for `compileSdk = 36`. [Android SDK Manager](https://developer.android.com/studio/intro/update#sdk-manager)

## 4. Sync and build

Choose **File → Sync Project with Gradle Files**. The first sync needs internet. Wait for dependency resolution to finish.

Open Android Studio's Terminal in the project root and run:

```sh
sh scripts/verify.sh
```

If JAVA_HOME is not already configured, set your JDK first:

```sh
export JAVA_HOME='/absolute/path/to/jdk-21/Contents/Home'
sh scripts/verify.sh
```

The helper calls the standard wrapper and runs:

```sh
./gradlew --no-daemon :engine:check :app:assembleDebug :app:lintDebug
```

It keeps Gradle caches and debug signing state under `.tools/`. Success means the process exits zero and ends with `BUILD SUCCESSFUL`. The timing suite prints its scenario count. `:engine:checkRules` is a custom Java runner, so an empty JUnit test report is not evidence that these scenarios ran.

Outputs:

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint HTML: `app/build/reports/lint-results-debug.html`
- Lint text: `app/build/reports/lint-results-debug.txt`

You can run timing tests alone with `./gradlew :engine:checkRules`, using the same JDK environment, or `sh scripts/test-engine.sh` with `JAVA_HOME` set.

## 5. Connect the Samsung

1. On the phone, open **Settings → About phone → Software information** and tap **Build number** seven times. Enter the phone PIN if asked.
2. Open **Developer options** and enable **USB debugging**.
3. Connect the phone with a USB data cable. Accept **Allow USB debugging** on the phone.
4. In Android Studio's device selector, choose the Galaxy S24 Ultra. Select the **app** run configuration and press the green **Run** button.
5. Android Studio builds, installs, and opens SocialPause. If the device does not appear, use **Tools → Troubleshoot Device Connections**. [Android hardware-device instructions](https://developer.android.com/studio/run/device)

If installing the delivered APK first and later deploying from Studio reports an incompatible signing certificate, the builds used different debug keys. Preserve the existing installation and use the original signing key for an update. The key must be backed up privately and is intentionally absent from Git and source archives. Uninstalling would remove the app’s local data.

## 6. Enable app functions

In SocialPause, open the Settings tab and scroll to App setup:

1. Enable SocialPause under Accessibility / Installed apps. Read its disclosure first.
2. Allow timer notifications.
3. Allow precise alarms for lunch and Sleep Time transitions during idle.
4. Review battery settings. Use unrestricted battery access if Samsung delays monitoring.
5. For the live status-bar countdown on the tested Samsung, keep **Phone Settings → Developer options → Live notifications for all apps** enabled. The user confirmed this made the countdown appear with 0.4.1. The temporary in-app diagnostic was removed in 0.4.2; the countdown itself remains.

If Samsung disables Accessibility for a sideloaded app, inspect the app's **App info → menu → Allow restricted settings**, where available. Only enable this for your own trusted build. Return to Accessibility afterward.

Return to Home and press **Start**. If Start remains disabled, the Accessibility service has not connected. SocialPause may need reopening after enabling it.

## 7. Test and debug

Follow [DEVICE_TESTS.md](DEVICE_TESTS.md). Use the physical Samsung to test Now Bar, multi-window, screen locking, and battery behavior; an emulator cannot establish One UI behavior.

In Android Studio's Logcat select the phone and filter `package:app.socialpause`. For a crash, capture the `FATAL EXCEPTION` and first `Caused by` section. Record which screen was open and which permission had just changed. Avoid sharing unrelated logs containing personal app data.

For debugging, place a breakpoint in `MainActivity` for screen actions or `RulesEngine.advance` for timing decisions, then click Debug. Pausing the debugger stalls service callbacks; do normal timing acceptance without a paused debugger.

## Common build issues

| Message | Action |
|---|---|
| Unsupported Java/Gradle version | Select complete JDK 21 for Gradle and terminal JAVA_HOME. |
| `jlink` does not exist | The selected runtime is incomplete. Select or install a complete JDK 21. |
| Platform android-36 missing | Install Platform 36 and confirm `local.properties`. |
| Debug keystore directory not writable | Use `sh scripts/verify.sh`, which keeps signing files in `.tools/android-user`. |
| Dependency download fails | Check network access and retry the same build; do not remove lint/tests. |
| Gradle daemon launch permission error | Check Codex's filesystem/network permission result. Report the exact rejection rather than changing the app. |
| App compiles but social limits do nothing | Check the Accessibility connection and whether tracking is started. |

Do not accept automatic Gradle/plugin/SDK upgrades merely to resolve a first sync error. First reproduce the documented configuration. Upgrades are separate changes needing verification.

## Testing the redesigned UI

Use the Home, Insights and Settings bottom tabs. Insights starts empty on a new installation; sample values from the design are not shipped. Generate real tracked usage to populate it. Verify both Today and This week, switch system light/dark mode, and increase the phone's font size. The screen scrolls while navigation stays accessible.

Limit reached screens return to phone Home immediately and show a short explanation, then dismiss after five seconds. Verify they disappear on locking the phone and that reopening a depleted app is still blocked.

## Version 0.6.0 checks

Use the project timing suite for this release; `artifacts/design-tests.zip` belongs to the old 0.2 redesign and is obsolete for current timer rules. Current tests include the legacy fixture in `engine/src/test/resources/`.

After building, copy `app/build/outputs/apk/debug/app-debug.apk` to `artifacts/SocialPause.apk` if you need a named personal-install APK. No publishing or Play account is needed. The supplied verification script reuses `.tools/android-user/debug.keystore`; use the same signing identity for future installs. If a signing mismatch occurs, preserve your existing installation and locate the original key rather than uninstalling automatically.

Updating from 0.3/0.4/0.5 preserves timers, history and schedules. Stop monitoring before changing Individual / Shared mode or app allowances; the 00:01–00:30 shared slider also requires Shared mode. An active older shared cycle above 30 minutes keeps its existing budget until reset. Verify the theme switch, allowance sliders, Lunch wheels and Save/Cancel, app selection and automatic Stop after Accessibility revocation using `DEVICE_TESTS.md`. Only upgrades from the legacy 0.2 rules reset app allowances once.
