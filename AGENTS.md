# SocialPause project guidance

## Product contract
- Personal, offline, normal Android app for Galaxy S24 Ultra / Android 16. No Device Owner or factory reset.
- Independent app timers: Instagram gets 7 accumulated foreground minutes; every other selected app gets 10. There is no shared usage budget.
- Leaving an app, locking the screen, or focusing an unselected app pauses usage. The notification drawer / Quick Settings panel retains the underlying app and keeps charging it. Opening the Settings app or Recents pauses usage. No two-app cap.
- Exhausting an app starts its own continuous 60-minute cooldown immediately. Reset only that app when its deadline passes; other apps retain usage, focus, and deadlines.
- Lunch defaults to 14:00–15:00 unrestricted and resets usage; 15:00–16:00 blocks all selected apps, even if lunch was unused.
- Lunch edits apply the following day and must not shorten an overnight lunch/cooldown already in progress.
- Sleep Time defaults to 22:00–10:00: hide app timer notifications; keep enforcement active.
- Stop is an intentional unrestricted override. Start refreshes usage but respects the current lunch window.
- Reboot may reset usage. Ordinary process recreation preserves every app deadline/allowance and clears stale focus. 0.4 preserves 0.3 timers without a new schema reset. The 0.3 upgrade resets old timers exactly once, preserving history, schedules, selection, running status, and current lunch restrictions.
- One ongoing notification shows the focused app, or an overview of every app when none is focused. Only active app usage requests a live status chip with an app icon and system countdown; idle/cooldown uses the SocialPause icon without chip text. Use a standard template for live eligibility and custom per-app rows only for the expanded idle overview. Restore dismissed ordinary notifications while monitoring is active, except Sleep Time. Suppress live promotion after dismissal until manual Start; never promise an unremovable Android notification.
- Home shows availability plus independent app cards. Today Insights shows per-app used time only. Weekly bars stack app colors; selecting a day filters the total and app breakdown, not the chart. Tap the same day or outside the day targets to clear selection; scrolling must not clear it. Do not add a Show whole week button.
- Usage history excludes lunch/Stop and survives resets, selection changes, upgrades, and reboot.
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
- For timing changes, add meaningful fake-clock scenarios for independent deadlines, delayed callbacks, lunch, process recovery, and migration. Keep the real v0.2 serialized fixture and randomized reference-model coverage.
- Verify notification dismissal/restoration, focused/overview transitions, day selection, dark mode, and large text at runtime; separate emulator evidence from Samsung acceptance.
- Do not equate an APK build with phone acceptance. Report each separately in `docs/VALIDATION.md`.
- For the focused build/test workflow, use `.agents/skills/socialpause-build/SKILL.md`.

## Design reference
- V2 local exports are the visual reference: mint Home summary, app glyph badges, timer/state cards, compact Insights, app-colored weekly stacks, and light/dark tokens. Use real engine data, never reference sample numbers.
- Local design documents are reference material; explicit user behavior changes take precedence.
- FocusResolver owns the testable distinction between system panels and real app switches; read package/window metadata only, never screen text.
