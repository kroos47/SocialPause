# SocialPause project guidance

## Product contract
- Personal, offline, normal Android app for Galaxy S24 Ultra / Android 16. No Device Owner or factory reset.
- Each selected app gets 10 accumulated foreground minutes; all selected apps share 20 minutes.
- Leaving an app, locking the screen, or focusing an unselected app pauses usage. No two-app cap.
- A depleted app stays blocked until shared cooldown resets. Exhausting 20 minutes starts a continuous 60-minute cooldown.
- Lunch defaults to 14:00–15:00 unrestricted and resets usage; 15:00–16:00 blocks all selected apps, even if lunch was unused.
- Lunch edits apply the following day and must not shorten an overnight lunch/cooldown already in progress.
- Sleep Time defaults to 22:00–10:00: hide app timer notifications; keep enforcement active.
- Stop is an intentional unrestricted override. Start refreshes usage but respects the current lunch window.
- Reboot may reset usage. Ordinary process recreation must preserve saved allowances and clear stale focus.
- Explain limitations honestly: Accessibility can redirect Home, not force-stop other apps. Normal apps are bypassable. Samsung live promotion and multi-window behavior need device testing.

## Architecture and editing
- Current implementation uses Java 17 language features and native Android Views, with no Kotlin plugin or Compose.
- Keep platform-independent rules in `engine/`. Use elapsed time for usage/cooldown, local wall time for daily schedules.
- `app/` owns Android permissions, focused-window detection, notifications, persistence, and UI.
- Preserve offline behavior: no INTERNET permission, analytics, account, or external service.
- Do not move timer logic into UI callbacks or weaken tests to get a green build.
- Keep changes limited to the requested behavior and explain the relevant files to this beginner.

## Verification
- Read `docs/ANDROID_STUDIO.md` before changing build configuration.
- Use a complete JDK 21 or 17, including jlink, for Gradle 8.13. The installed Android Studio JDK 25 is not the project JDK.
- `sh scripts/verify.sh` runs timing tests, debug APK assembly, and Android lint.
- `:engine:checkRules` is a dependency-free scenario runner; an empty JUnit report does not replace it.
- For timing changes, add a scenario with a meaningful expected outcome; use a fake clock, not real sleeps.
- Do not equate an APK build with phone acceptance. Report each separately in `docs/VALIDATION.md`.
- For the focused build/test workflow, use `.agents/skills/socialpause-build/SKILL.md`.
