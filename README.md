# SocialPause 0.7.0

Personal, offline Android app for the Galaxy S24 Ultra / Android 16. Instagram defaults to **7 focused minutes**, and other selected apps to **10 each**. Set per-app limits while stopped: Instagram **0–7 minutes**, other apps **0–12**. Zero blocks usage without a cooldown. Exhausting an allowance immediately starts that app's **60-minute cooldown**. Optional **Shared timer** adds a combined allowance, defaulting to 20 minutes.

## Build and install

Open the repository root (the folder containing `settings.gradle.kts`) in Android Studio. Follow [Android Studio setup](docs/ANDROID_STUDIO.md), then run `sh scripts/verify.sh`. The build uses Java 17 source, JDK 21, Gradle 8.13, AGP 8.13.0 and SDK 36. No Kotlin or Compose plugin is required.

The raw debug APK is `app/build/outputs/apk/debug/app-debug.apk`; the delivered copy is `artifacts/SocialPause.apk`. Publishing is unnecessary. Preserve the signing key to install updates without uninstalling and losing data.

GitHub Actions runs the timing tests on Java 17 and 21, then builds the Android APK and runs lint on pushes to `main`, version tags and pull requests. See the repository's [Actions page](https://github.com/kroos47/SocialPause/actions/workflows/ci.yml) for logs and reports. CI's `SocialPause-ci.apk` uses a temporary key; install the matching-key `SocialPause.apk` from [Releases](https://github.com/kroos47/SocialPause/releases) for updates. The [publishing guide](docs/PUBLISHING.md) explains local release packaging and publication without uploading the private signing key.

## Behavior

- Leaving an app, switching apps, or locking the screen pauses usage. Unused minutes stay available. Opening the notification drawer or Quick Settings over an app keeps its timer running; opening the Settings app pauses it.
- Each app's cooldown runs continuously and resets only that app. For example, Instagram exhausted at 09:07 unlocks at 10:07 even if no other app is used.
- Choose Individual or Shared on Home while stopped. In Shared mode, set its allowance in Settings from 1 to 30 minutes in one-minute steps. The bottom-sheet slider saves immediately; Done closes it. App cooldown completion never refills shared time; shared exhaustion blocks all selected apps for 60 minutes, then restores all allowances.
- Home shows availability plus each app's remaining usage/cooldown. Blocked launches return to phone Home with a brief explanation.
- Lunch defaults to 14:00–15:00 unrestricted, resets all timers, then blocks all selected apps until 16:00. Start manual lunch from the Home Lunch card once per local day, even during a cooldown. It replaces the upcoming automatic lunch, or grants an extra lunch if scheduled lunch already happened. Stop lunch starts a full blocked hour immediately.
- Lunch schedule edits apply today only before today’s old start, before any lunch has happened, and when the new time is still future; otherwise tomorrow. The hour/minute/AM–PM wheel picker saves only when Save is pressed; Cancel, Back and outside taps discard it. Overnight phases finish without truncation.
- Sleep Time defaults to 22:00–10:00 and hides timer notifications without disabling enforcement.
- Start refreshes allowances while honoring current lunch restrictions and disables the main Stop button for **six continuous hours**. Home shows the remaining lock time. Lunch, cooldowns, screen-off time and time outside selected apps count toward those hours; changing the phone's clock does not shorten them. Stop becomes available afterward and then allows free use and hides notifications. Lunch controls remain available under their usual rules and do not restart the six-hour lock.
- Closing the dashboard, removing it from Recents, or ordinary process recreation preserves monitoring, allowances, cooldown deadlines and the Stop lock. Reboot or a confirmed Android Force stop leaves monitoring stopped until Start; reboot may reset ordinary usage. Force-stop detection uses current-process startup information on Android 15+; when reliable evidence is unavailable, saved state is preserved. Revoking SocialPause Accessibility stops it even while Stop is locked; re-enable access and press Start to resume.
- Updating from 0.3/0.4/0.5/0.6 preserves current timers and history. An already-running installation does not receive a retrospective six-hour lock; its next Start activates it. An active older shared budget above 30 minutes finishes unchanged; the saved next-cycle budget is capped at 30. Daily manual lunch eligibility and lunch phases survive reboot. Upgrading from 0.2 resets timers once while preserving history, settings, selection and running status.

Use the sun/moon control below Start/Stop to choose a persistent light or dark appearance without changing timer state. Selected apps and permission/setup controls remain in Settings below the timer sections.

## Notifications and Insights

One silent ongoing notification shows the focused app's countdown, limited by shared remaining time when enabled. The limiting allowance is identified in the notification, and the idle overview includes shared remaining time. On Home or an unselected app, its expanded view lists every selected app's remaining usage or cooldown. Only active usage requests a status chip: app icon/countdown normally, or SocialPause icon/countdown when the shared allowance is smaller. Otherwise the status bar uses the SocialPause icon with no timer chip, and the expanded notification shows independent usage/cooldown rows and progress bars. Visible rows refresh while the screen is on; the phone is not woken every second for display updates.

Dismissal restores the ordinary notification while monitoring is active. Swiping away lunch, cooldown or idle notifications does not prevent the focused live countdown from returning. Dismissing an active live countdown suppresses live promotion until the next Start. Ambiguous dismissal records from older versions remain in effect until that next Start. Android controls dismissal and Samsung controls live-chip presentation, so neither a permanently unremovable notification nor promotion is guaranteed. Permission denial, Stop, disconnection and Sleep Time hide the timer surface.

Today Insights shows per-app usage only. This week shows stacked daily bars with an app-color legend, a total, and a By App breakdown. Tap a day to filter the total/list; tap it again or tap outside the chart day targets to reset. Scrolling keeps the selection. History includes only allowance-consuming usage, excluding lunch and Stop, and remains local.

## Files and testing

- [File guide](docs/FILE_GUIDE.md): source responsibilities and agent instructions.
- [Version changes](docs/UI_UPDATE.md): implementation notes and migration.
- [Validation](docs/VALIDATION.md): checks actually performed and device limits.
- [Samsung test checklist](docs/DEVICE_TESTS.md): physical-phone acceptance.
- [Codex guide](docs/CODEX_GUIDE.md): how to ask for changes and review agent work.
- [Publishing safely](docs/PUBLISHING.md): files to commit, local-only files, and secret checks.

The dependency-free engine suite runs through Gradle `:engine:checkRules` or `scripts/test-engine.sh`. It includes independent timer, schedule, history and notification-presentation scenarios, genuine v0.2/v0.4/v0.5 serialization fixtures, and 20,000 randomized reference-model transitions.

Normal Android apps are bypassable through force-stop, uninstall or disabling Accessibility. Accessibility can redirect Home, not force-stop another process or stop background audio. Samsung battery behavior, live notifications and multi-window require phone acceptance. No INTERNET permission, account, analytics or external service is used.

Keep **Phone Settings → Developer options → Live notifications for all apps** enabled on the tested Samsung; this made the live countdown visible. The temporary in-app diagnostic remains removed.
