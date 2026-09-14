# SocialPause

An offline, personal Android app that gives each selected social app 10 accumulated foreground minutes, with a shared 20-minute allowance followed by 60 minutes of cooldown. Built for Galaxy S24 Ultra / Android 16.

This is a fresh Java implementation of the agreed plan. The earlier source in Documents was inaccessible; this project is independent of that copy. It uses native Android Views and a separate pure-Java timing engine. Kotlin syntax is used only by Gradle configuration files (`.gradle.kts`).

## UI update — version 0.2.0

The app now follows the supplied SocialPause mockups: Home with app cards and a shared ring, Today/This week Insights, schedule settings, a cooldown screen, and a five-second limit explanation. It supports light/dark appearance and scrolling for large text. Insights records only allowance-consuming usage from this version onward; lunch and Stop periods are excluded. Daily/hourly aggregates stay local and survive allowance resets and reboot.

The system notification shows the smaller of current-app and shared remaining time, with the app winning ties. Paused usage uses a static ordinary notification; active usage, lunch and cooldown request live promotion. Standard Android progress templates replace the mockup's custom notification layout. Samsung still decides promotion, icon treatment and formatting. Dismissing a notification suppresses it for that allowance cycle without stopping enforcement.

At a limit, the service returns to phone Home immediately and displays a brief Accessibility overlay; the overlay does not grant five more seconds of usage. It closes on screen lock, service interruption or when blocking no longer applies.

See [UI implementation and verification](docs/UI_UPDATE.md).

## Start here

1. [Android Studio setup and build](docs/ANDROID_STUDIO.md)
2. [What each file does](docs/FILE_GUIDE.md)

Open this folder in Android Studio, not its parent and not `app/`:

`/Users/kroos/KROOS/codex/SocialPauseBuild`

From this folder on the configured Mac:

```sh
sh scripts/verify.sh
```

The output APK is `app/build/outputs/apk/debug/app-debug.apk`. It is a development build signed with a debug key, not a Play Store release.

## Behavior

- Switching apps, leaving social apps, and locking the screen pause usage. Only the focused pane counts in multi-window.
- Individual apps stop at 10 minutes. All selected apps may contribute to the shared 20 minutes; there is no two-app restriction.
- At 20 minutes combined, all selected apps are blocked for one continuous hour. A depleted individual app stays blocked until that shared reset.
- Lunch defaults to 14:00–15:00 unrestricted, with usage reset. 15:00–16:00 is mandatory cooldown, even if lunch was unused. At 16:00, allowances are fresh.
- Lunch edits apply tomorrow; an overnight lunch/cooldown finishes before an edit takes effect.
- Sleep Time defaults to 22:00–10:00 and hides app timer notifications. Enforcement continues. System-owned permission/accessibility notices are outside the app's control.
- Stop intentionally disables tracking, blocking, and timer notifications. Start gives fresh usage while honoring the current lunch schedule.
- Reboot resets allowances while retaining settings and whether tracking was enabled. Process recreation retains saved counters; asynchronous persistence may lose a small final increment on abrupt termination.

## Permissions and limitations

No INTERNET permission, account, server, analytics, or Device Owner enrollment. Initial builds need internet to fetch developer tools; the installed app works offline.

Accessibility reads the focused package identity and redirects to Home at a limit. It does not traverse or store screen text. This cannot force-stop another app, guarantee zero flashes on launch, or stop background audio. Disable/force-stop/uninstall can bypass it, and Stop is an intentional override.

The app requests promoted ongoing notifications. Samsung decides whether a Now Bar/live surface appears; phone acceptance is still required. Accessibility can be delayed or disabled by OEM behavior. Exact alarms improve scheduled transitions while idle. Without permission, transitions may be delayed until Android runs the service/alarm again.

## Codex project guidance

`AGENTS.md` is persistent project guidance. `.agents/skills/socialpause-build/SKILL.md` is a focused build/test workflow. Neither file ships in the APK or changes the app at runtime.
