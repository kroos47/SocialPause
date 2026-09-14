# Build and run with Android Studio

## 1. Open the right folder

In Android Studio choose Open, then select:

`/Users/kroos/KROOS/codex/SocialPauseBuild`

The folder contains `settings.gradle.kts`, `app/`, `engine/`, and `gradlew`. Do not create a new Android project over these files. If sync starts before configuring Java, set the JDK below and sync again.

## 2. Choose the build JDK

Open **Android Studio → Settings → Build, Execution, Deployment → Build Tools → Gradle**. Select **Gradle JDK → Add JDK** and use this complete JDK downloaded for the build:

`/Users/kroos/KROOS/codex/SocialPauseBuild/.tools/jdk21/jdk-21.0.12.1+1/Contents/Home`

Use Gradle from the project's wrapper. The project uses Gradle 8.13, Android Gradle Plugin 8.13.0, and Java 17 source syntax, built with JDK 21.

The installed Android Studio runtime is Java 25; leave Studio itself on its bundled runtime, but choose the separate JDK above for Gradle. The RustRover runtime found on this Mac lacks `jlink` and cannot complete this Android build.

On another machine, use the Gradle JDK menu to download a **complete JDK 21**, and set `JAVA_HOME` to the same installation for terminal builds. [Android's JDK configuration guide](https://developer.android.com/build/jdks)

## 3. Check the Android SDK

The configured Mac uses a local SDK at `.tools/sdk` via `local.properties`. Platform 36 was downloaded there; its `build-tools` and `licenses` entries refer to the existing Android SDK on this Mac.

This setup is machine-specific. If importing the source archive elsewhere, open **Tools → SDK Manager** and install:

- Android SDK Platform **36** (Android 16)
- Android SDK Build-Tools **36.0.0**
- Android SDK Platform-Tools (for USB/device deployment)

Point Android Studio to that SDK. Let Studio create `local.properties`, or set `sdk.dir` to its absolute path. SDK 37 alone is insufficient for a project with `compileSdk = 36`. Do not copy the current Mac's `local.properties` to another computer.

## 4. Sync and build

Choose **File → Sync Project with Gradle Files**. The first sync needs internet. Wait for dependency resolution to finish.

Open Android Studio's Terminal in the project root and run:

```sh
sh scripts/verify.sh
```

On a different machine, set your JDK first:

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

If installing the delivered APK first and later deploying from Studio reports an incompatible signing certificate, the two builds used different debug keys. Uninstall the earlier debug app before installing the new one; this removes its test settings and counters. Alternatively keep using the same debug signing environment.

## 6. Enable app functions

In SocialPause, open the Settings tab and scroll to App setup:

1. Enable SocialPause under Accessibility / Installed apps. Read its disclosure first.
2. Allow timer notifications.
3. Allow precise alarms for lunch and Sleep Time transitions during idle.
4. Review battery settings. Use unrestricted battery access if Samsung delays monitoring.

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
| `jlink` does not exist | The selected runtime is incomplete. Use the supplied Temurin JDK. |
| Platform android-36 missing | Install Platform 36 and confirm `local.properties`. |
| Debug keystore directory not writable | Use `sh scripts/verify.sh`, which keeps signing files in `.tools/android-user`. |
| Dependency download fails | Check network access and retry the same build; do not remove lint/tests. |
| Gradle daemon launch permission error | Check Codex's filesystem/network permission result. Report the exact rejection rather than changing the app. |
| App compiles but social limits do nothing | Check the Accessibility connection and whether tracking is started. |

Do not accept automatic Gradle/plugin/SDK upgrades merely to resolve a first sync error. First reproduce the documented configuration. Upgrades are separate changes needing verification.

## Testing the redesigned UI

Use the Home, Insights and Settings bottom tabs. Insights starts empty on a new installation; sample values from the design are not shipped. Generate real tracked usage to populate it. Verify both Today and This week, switch system light/dark mode, and increase the phone's font size. The screen scrolls while navigation stays accessible.

Limit reached screens return to phone Home immediately and show a short explanation, then dismiss after five seconds. Verify they disappear on locking the phone and that reopening a depleted app is still blocked.

The additional 42-scenario suite is packaged separately as `artifacts/design-tests.zip` because permission to copy that test file into the project was declined. The project Gradle check still runs its original 25 scenarios. Both suites were executed against the redesigned engine. Extract the supplementary test archive and run its `scripts/test-engine.sh` with the same JAVA_HOME.
