# What each file does

Read in this order: README → this guide → RulesEngine → AppController → MainActivity → Accessibility service. You do not need to understand all Android APIs before testing a small change.

## Build and project configuration

| File (relative to project root) | Purpose | When you edit it |
|---|---|---|
| `settings.gradle.kts` | Names the project, includes `app` and `engine`, and declares dependency repositories. | Adding/removing a module or repository. |
| `build.gradle.kts` | Pins Android Gradle Plugin 8.13.0. | A deliberate build-tool upgrade. |
| `gradle.properties` | Gradle memory and Android build settings. | A diagnosed build configuration issue. |
| `gradlew` | Standard Gradle launcher for macOS/Linux. | Usually generated, not hand-edited. |
| `gradlew.bat` | Standard Gradle launcher for Windows. | Usually generated. |
| `gradle/wrapper/gradle-wrapper.jar` | Small official bootstrap program that starts/downloads Gradle. | Regenerate through the wrapper task for upgrades. |
| `gradle/wrapper/gradle-wrapper.properties` | Pins Gradle 8.13 download URL and SHA-256 checksum. | An intentional Gradle upgrade. |
| `app/build.gradle.kts` | App ID, SDK levels, version, Java compatibility, and engine dependency. | Android build configuration or dependency changes. |
| `engine/build.gradle.kts` | Configures pure Java and wires the scenario runner into `check`. | Timing-test/build workflow changes. |
| `.gitignore` | Excludes generated output, local settings, caches, and private signing files. | Adding a new generated/private path. |
| `local.properties` | Absolute Android SDK path on this computer. Ignored and excluded from source archive. | Moving SDK or using another computer. |

The `.kts` extension means the Gradle configuration uses Kotlin syntax. The application itself is Java; no Kotlin compiler plugin is needed.

## Android application files

These Java files are under `app/src/main/java/app/socialpause/`.

| File | Responsibility |
|---|---|
| `SocialPauseApp.java` | Android Application entry point; creates one shared controller per process. |
| `MainActivity.java` | Home, Insights and Settings navigation, Start/Stop and its six-hour availability countdown, app picker, schedule dialogs, permission setup, and system-bar insets. Native screen Views are constructed here. Notification layouts use XML. |
| `AppController.java` | Coordinates the engine, persistence, notification refresh, and alarm scheduling on the main thread. Also reconciles startup/monitoring permission, guards Start/Stop/lunch operations, and provides labels/time formatting. System stops remain separate from locked user Stop. |
| `StartupRecovery.java` | Checks Android 15+ startup evidence for the current process before initial publishing; a confirmed Force stop leaves monitoring stopped, while missing or old evidence preserves state. |
| `StateStore.java` | Serializes private timing state into app-local SharedPreferences. Uses boot count to distinguish reboot from process recreation so reboot leaves monitoring stopped without erasing history or lunch records. Format changes need compatibility migration. |
| `SocialAccessibilityService.java` | Determines focused package identity, checks lock state, updates usage, and sends blocked apps to Home. A recurring callback observes limits while an app stays open. |
| `TimerNotifications.java` | Silent ongoing notification, remaining allowances, active-only countdown chronometer, custom overview rows, sleep hiding, and best-effort promoted-notification request. |
| `ScheduleAlarms.java` | Requests the next schedule boundary using AlarmManager; exact if permitted, otherwise best effort. |
| `ScheduleReceiver.java` | Validates incoming actions and reconciles timers after scheduled alarms, boot, time/timezone changes, app update, or alarm permission changes. |

Other Android files:

| File | Responsibility |
|---|---|
| `app/src/main/AndroidManifest.xml` | Declares the launcher activity, Application, Accessibility service, receiver, permissions, and package visibility queries. No INTERNET permission. |
| `app/src/main/res/xml/accessibility_service.xml` | Requests window events and package-root access and supplies the service description. |
| `app/src/main/res/xml/data_extraction_rules.xml` | Excludes app data from cloud backup and device transfer. |
| `app/src/main/res/values/strings.xml` | App name and Accessibility disclosure text. Most first-version UI copy is currently English in MainActivity. |
| `app/src/main/res/drawable/ic_pause.xml` | Vector pause icon used by the app/notification. |

### Added for the Figma-inspired UI

| File | Responsibility |
|---|---|
| `app/src/main/java/app/socialpause/Design.java` | Shared light/dark colors, cards, app glyph badges, progress bars, grid/stack chart drawing and accessible day targets. |
| `app/src/main/java/app/socialpause/BlockOverlay.java` | Brief limit-reached explanation using an Accessibility overlay; returns Home immediately and dismisses after five seconds. |
| `app/src/main/java/app/socialpause/NotificationDismissReceiver.java` | Handles a dismissal using its original surface/run identity. Ordinary lunch/cooldown/idle dismissals do not suppress live promotion; an active live dismissal suppresses it until the next Start. |
| `engine/src/main/java/app/socialpause/engine/UsageHistory.java` | Local hourly aggregates split at hour/day boundaries. Preserved independently of allowance resets. |
| `engine/src/main/java/app/socialpause/engine/NotificationDismissalPolicy.java` | Platform-independent dismissal identity and policy: distinguish ordinary surfaces from live countdowns and reject stale monitoring-run callbacks. |
| `engine/src/main/java/app/socialpause/engine/TimerPresentation.java` | Testable notification state: focused app, per-app overview, all cooling, lunch, post-lunch block, or hidden. |
| `app/src/main/res/values/styles.xml` | Light system-bar and window appearance. |
| `app/src/main/res/values-night/styles.xml` | Dark system-bar and window appearance. |
| `app/src/main/res/drawable/ic_launcher.xml` | Green launcher pause icon. |
| `app/src/main/res/drawable/ic_instagram.xml`, `ic_x.xml`, `ic_reddit.xml` | Monochrome notification marks for supported social apps. Unknown selected apps use SocialPause's pause mark. |

## Timing engine and tests

| File | Responsibility |
|---|---|
| `engine/src/main/java/app/socialpause/engine/RulesEngine.java` | Platform-independent state machine: independent per-app allowances and cooldowns, pauses, lunch, Sleep Time, schedule edits, six-hour guarded Stop, and versioned restore/migration. |
| `engine/src/test/java/app/socialpause/engine/EngineTests.java` | Scenario entry point, including real legacy upgrade fixtures and version-specific suites, plus 20,000 transitions checked against independent models. Read the current run output and VALIDATION.md for the observed scenario count. Uses a fake clock and no JUnit dependency. |
| `engine/src/main/java/app/socialpause/engine/ProcessStartEvidence.java` | Correlates system startup timestamps with this process; avoids treating a reused historical process ID as a new Force stop. |
| `engine/src/test/java/app/socialpause/engine/StartupRecoveryTests.java` | Tests current, stale, missing and invalid startup evidence. |
| `app/src/androidTest/java/app/socialpause/RuntimeChecks.java` | Explicitly opted-in, disposable-emulator-only notification/UI checks and synthetic recovery fixtures; excluded from the personal APK. |
| `scripts/test-engine.sh` | Compiles/runs the engine tests directly with javac/java when Android tools are unnecessary. |
| `scripts/verify.sh` | Runs the checked-in wrapper for engine checks, debug APK assembly, and lint using project-local caches/signing state. |
| `.github/workflows/ci.yml` | GitHub Actions timing-test jobs on Java 17/21, followed by Android APK/lint checks, with downloadable logs and a disposable test APK. |
| `scripts/package-release.py` | Builds from a clean commit, verifies the original APK signing certificate and version, and packages official local release assets and checksums. |
| `.github/release-signing.sha256` | Public certificate fingerprint used to reject APKs signed with the wrong key; this is not the private signing key. |
| `.github/release-notes/v0.7.0.md` | Release text for the lunch-notification fix and six-hour Stop lock. Older versions retain their own notes. |

A focus event flows like this:

```text
Android Accessibility event
  → SocialAccessibilityService gathers package/window metadata and lock state
  → FocusResolver keeps the app through system panels, clearing it on app exit/lock
  → AppController passes time/focus into RulesEngine
  → RulesEngine accounts for the previous focus and decides which apps are blocked
  → AppController saves state, updates notifications, and schedules the next alarm
  → Accessibility service redirects to Home if the observed package is blocked
```

`MainActivity` displays the same engine state. Leaving the dashboard does not own or stop the engine's lifetime; the Accessibility service does the monitoring.

## Agent instructions and human guides

| File | Audience and purpose |
|---|---|
| `AGENTS.md` | Codex: persistent product rules, code boundaries, and verification expectations. |
| `.agents/skills/socialpause-build/SKILL.md` | Codex: a reusable project-specific build/diagnosis/test procedure. |
| `README.md` | You: project entry point and behavior overview. |
| `docs/ANDROID_STUDIO.md` | You: precise setup, build, installation, and debugging steps. |
| `docs/CODEX_GUIDE.md` | You: prompts, reviewing changes, skills, and permissions. |
| `docs/FILE_GUIDE.md` | You: this file map. |
| `docs/DEVICE_TESTS.md` | You: Samsung acceptance checklist. |
| `docs/VALIDATION.md` | Both: evidence of checks actually run and outstanding device work. |

## Generated files: do not edit as source

- `app/build/`: compiled Android output, APK, intermediates, and lint reports.
- `engine/build/`: compiled timing engine/test classes and reports.
- `build/`: root Gradle reports.
- `.gradle/`: project Gradle state, including local JDK hint in `config.properties`.
- `.tools/`: downloaded JDK/SDK/Gradle, local cache, skill-validation environment, logs, and debug signing state. This folder is large and excluded from the source archive.
- `artifacts/`: delivered APK, source archive, build/lint evidence, and checksums. Regenerate when code changes.
- `.idea/`: Android Studio's local project settings if/when Studio creates them.

Editing a generated APK or compiled class does not change the source. Make changes in `app/src` or `engine/src`, then rebuild.

`engine/src/test/resources/legacy-v02.bin` is synthetic serialized state created with the previous release. It verifies preservation of history and settings during the one-time timer migration. It contains no user data.

## Added in 0.4

- `engine/src/main/java/app/socialpause/engine/FocusResolver.java`: testable focus memory for notification/Quick Settings panels, real app switches, keyboard, Recents, screen lock and monitoring reset.
- `app/src/main/res/layout/notification_overview.xml`: expanded overview container.
- `app/src/main/res/layout/notification_timer_row.xml`: per-app glyph, usage/cooldown timer and progress bar. These layouts are used only for ordinary overview notifications; Android live promotion requires standard templates.

## Added in 0.5

- `app/src/main/java/app/socialpause/MonitoringPermission.java`: observes whether this exact Accessibility service is enabled, independently of whether it is temporarily bound. Revocation stops monitoring through the controller.
- `engine/src/test/java/app/socialpause/engine/V05Tests.java`: shared budgets/cooldowns, manual lunch and schedule changes, persistent daily eligibility, and upgrade scenarios. Invoked by the existing engine test runner.
- `engine/src/test/fixtures/v04/`: generator and provenance for synthetic state captured using the original 0.4 engine. The three `legacy-v04-*.bin` resources validate migration without resetting old timers.

`RulesEngine` now owns manual lunch phases and optional shared accounting. `MainActivity` displays the mode selector, shared allowance slider and Lunch card; `TimerPresentation` supplies the app/shared limiting countdown. No timing decisions belong in the UI.


## Changed in 0.6

- `MainActivity.java`: sun/moon control, segmented mode selector, per-app sliders, shared bottom sheet and Lunch wheel editor; retained app picker and permission/setup rows.
- `Appearance.java` and `Design.java`: app-wide saved appearance and matching screen/overlay colors, separate from timer state.
- `RulesEngine.java`: persisted app allowance settings, zero blocking and separate configured/current shared budget for compatible upgrades. Independent app cooldowns still apply in both modes.
- `TimerPresentation.java`, `TimerNotifications.java`, `BlockOverlay.java`: zero-safe progress, honest No allowance wording, and actual cooldown deadlines.
- New v0.5 serialized fixtures and engine scenarios cover the upgrade from active shared cycles, zero settings and the narrower next-cycle shared range. Fixtures contain synthetic data only.


## Changed in 0.7

- `RulesEngine.java`: elapsed-time Stop lock, separate guarded user/system stop paths, and migration that preserves an old active run without adding a retrospective lock. Allowance/lunch resets cannot change the Stop deadline.
- `MainActivity.java` and `AppController.java`: display and enforce Stop availability; ordinary recreation preserves state, while reboot, confirmed Force stop and permission removal wait for a new Start.
- `TimerNotifications.java` and `NotificationDismissReceiver.java`: preserve notification origin so dismissing lunch/cooldown/idle does not suppress the next focused live countdown. Preserve the existing Samsung format and deliberate live-dismissal behavior.
- `V07Tests.java` and `NotificationDismissalTests.java` under the engine test package cover the Stop-lock rules and dismissal policy. The Android device checklist covers restart distinctions and the complete lunch-to-focused-notification sequence. Build results and device observations belong in `VALIDATION.md`.
