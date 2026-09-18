# SocialPause 0.4.2

Personal, offline Android app for the Galaxy S24 Ultra / Android 16. Instagram gets **7 focused minutes**, and other selected apps get **10 focused minutes each**. Exhausting an allowance immediately starts that app's **60-minute cooldown**. There is no shared 20-minute limit.

## Build and install

Open `/Users/kroos/KROOS/codex/SocialPauseBuild` in Android Studio, not a copy extracted inside `artifacts`. Follow [Android Studio setup](docs/ANDROID_STUDIO.md), then run `sh scripts/verify.sh`. The build uses Java 17 source, JDK 21, Gradle 8.13, AGP 8.13.0 and SDK 36. No Kotlin or Compose plugin is required.

The raw debug APK is `app/build/outputs/apk/debug/app-debug.apk`; the delivered copy is `artifacts/SocialPause.apk`. Publishing is unnecessary. Preserve the signing key to install updates without uninstalling and losing data.

## Behavior

- Leaving an app, switching apps, or locking the screen pauses usage. Unused minutes stay available. Opening the notification drawer or Quick Settings over an app keeps its timer running; opening the Settings app pauses it.
- Each app's cooldown runs continuously and resets only that app. For example, Instagram exhausted at 09:07 unlocks at 10:07 even if no other app is used.
- Home shows availability plus each app's remaining usage/cooldown. Blocked launches return to phone Home with a brief explanation.
- Lunch defaults to 14:00–15:00 unrestricted, resets all timers, then blocks all selected apps until 16:00. Lunch changes apply tomorrow; overnight windows complete first.
- Sleep Time defaults to 22:00–10:00 and hides timer notifications without disabling enforcement.
- Stop intentionally allows free use and hides notifications. Start refreshes allowances while honoring the current lunch restrictions. Closing the dashboard does not stop monitoring.
- Reboot may reset timers. Process recreation preserves allowances and cooldown deadlines. Updating from 0.3 preserves current timers. Upgrading from 0.2 resets timers once while preserving history, settings, selection and running status.

## Notifications and Insights

One silent ongoing notification shows the focused app's countdown. On Home or an unselected app, its expanded view lists every selected app's remaining usage or cooldown. Only active usage requests an app-icon/countdown status chip. Otherwise the status bar uses the SocialPause icon with no timer chip, and the expanded notification shows independent usage/cooldown rows and progress bars. Visible rows refresh while the screen is on; the phone is not woken every second for display updates.

Dismissal restores the ordinary notification while monitoring is active. Live promotion is suppressed after dismissal until manual Start. Android controls dismissal and Samsung controls live-chip presentation, so neither a permanently unremovable notification nor promotion is guaranteed. Permission denial, Stop, disconnection and Sleep Time hide the timer surface.

Today Insights shows per-app usage only. This week shows stacked daily bars with an app-color legend, a total, and a By App breakdown. Tap a day to filter the total/list; tap it again or tap outside the chart day targets to reset. Scrolling keeps the selection. History includes only allowance-consuming usage, excluding lunch and Stop, and remains local.

## Files and testing

- [File guide](docs/FILE_GUIDE.md): source responsibilities and agent instructions.
- [Version changes](docs/UI_UPDATE.md): implementation notes and migration.
- [Validation](docs/VALIDATION.md): checks actually performed and device limits.
- [Samsung test checklist](docs/DEVICE_TESTS.md): physical-phone acceptance.
- [Codex guide](docs/CODEX_GUIDE.md): how to ask for changes and review agent work.

The dependency-free engine suite runs through Gradle `:engine:checkRules` or `scripts/test-engine.sh`. It includes independent timer, schedule, history and notification-presentation scenarios, a real v0.2 serialization fixture, and 5,000 randomized transitions compared with an independent model.

Normal Android apps are bypassable through force-stop, uninstall or disabling Accessibility. Accessibility can redirect Home, not force-stop another process or stop background audio. Samsung battery behavior, live notifications and multi-window require phone acceptance. No INTERNET permission, account, analytics or external service is used.

0.4.2 removes the temporary Status-bar countdown diagnostic from Settings and its unused sampling code. The working notification countdown and compatibility handling remain. On the user's Galaxy S24 Ultra, enabling **Phone Settings → Developer options → Live notifications for all apps** made the status-bar countdown work. Keep that phone setting enabled. This update preserves existing timers and history.
