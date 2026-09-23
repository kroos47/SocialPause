# SocialPause project guidance

## Product contract
- Personal, offline, normal Android app for Galaxy S24 Ultra / Android 16. No Device Owner or factory reset.
- Individual mode is the upgrade/install default: App allowances default to Instagram 7 minutes and other apps 10, adjustable in one-minute steps to 0–7 and 0–12 respectively. Optional shared mode adds a combined allowance (default 20 minutes, 1–30 in one-minute steps), preserving independent app cooldowns. Mode/app edits require monitoring stopped; shared duration also requires Shared mode. Shared-sheet slider changes save immediately; Done/Back/backdrop only closes the sheet.
- Leaving an app, locking the screen, or focusing an unselected app pauses usage. The notification drawer / Quick Settings panel retains the underlying app and keeps charging it. Opening the Settings app or Recents pauses usage. No two-app cap.
- Exhausting an app starts its own continuous 60-minute cooldown immediately. Reset only that app when its deadline passes; other apps and shared consumption retain usage, focus, and deadlines. Shared exhaustion starts a separate 60-minute global block; completing it resets all app/shared allowances once.
- Lunch defaults to 14:00–15:00 unrestricted and resets usage; 15:00–16:00 blocks all selected apps, even if lunch was unused.
- A manual lunch can start once per local day, overriding any lunch/cooldown and suppressing that day’s automatic occurrence. Scheduled lunch does not consume manual eligibility. Early Stop lunch begins a full 60-minute global cooldown. Main Stop/Start, app selection and reboot must not replenish manual eligibility or replay suppressed lunch.
- Lunch edits apply today only before the old scheduled start, with no lunch started today and a strictly future new start; otherwise tomorrow. Cancel/Back/outside dismissal never saves. Preserve active overnight phases; automatic triggers during them are consumed, not replayed. Saving a lunch time enables an existing disabled schedule.
- Sleep Time defaults to 22:00–10:00: hide app timer notifications; keep enforcement active.
- Stop is an intentional unrestricted override. Start refreshes usage but respects the current lunch window.
- Reboot may reset usage. Ordinary process recreation preserves every app deadline/allowance and clears stale focus. 0.6 preserves 0.3/0.4/0.5 timers without a new schema reset; explicitly initialize newly deserialized fields. Lunch phases and daily records survive reboot. The 0.3 upgrade resets old timers exactly once, preserving history, schedules, selection, running status, and current lunch restrictions.
- One ongoing notification shows the focused app, or an overview of every app when none is focused. Only active app usage requests a live status chip with explicit compact MM:SS short critical text and the drawer chronometer. In shared mode use the smaller app/shared allowance, with the SocialPause icon when shared time limits use; otherwise use the app icon; idle/cooldown uses the SocialPause icon without chip text. Use a standard template for live eligibility and custom per-app rows only for the expanded idle overview. Restore dismissed ordinary notifications while monitoring is active, except Sleep Time. Suppress live promotion after dismissal until manual Start; never promise an unremovable Android notification.
- Home shows a stopped-only Individual / Shared selector, shared remaining when enabled, app cards and a Lunch card with daily manual eligibility and phase controls. A sun/moon switch below Start/Stop persists appearance independently of the engine. Today Insights shows per-app used time only. Weekly bars stack app colors; selecting a day filters the total and app breakdown, not the chart. Tap the same day or outside the day targets to clear selection; scrolling must not clear it. Do not add a Show whole week button.
- Confirmed revocation of this app’s Accessibility service stops and persists monitoring, clears overlay/notification/focus, and cancels alarms. Re-enabling requires Start. A temporary disconnect while permission stays enabled preserves the run; do not treat connected=false as revocation.
- App selection must have one loading/dialog instance, one draft, validation before Save/dismissal and no stale async callback. Cancel is nonmutating.
- Usage history excludes lunch/Stop and survives resets, selection changes, upgrades, and reboot.
- Explain limitations honestly: Accessibility can redirect Home, not force-stop other apps. Normal apps are bypassable. Samsung live promotion and multi-window behavior need device testing.

## Version 0.6 design decisions
- User instructions override the V2.1 handoff: KEEP independent 60-minute app cooldowns in both modes. Only shared-budget exhaustion triggers a shared cooldown; do not implement an all-app-caps trigger or indefinite app-cap lock.
- Zero allowance means No allowance while monitoring, with no cooldown loop or invented unlock time. Lunch and overall Stop still provide unrestricted access. Positive App limit reached always has a real independent or shared cooldown deadline.
- Use hour/minute/AM–PM wheels for Lunch, with draft Save/Cancel and 0.5 conditional today/tomorrow scheduling; do not adopt the handoff's always-tomorrow rule. Keep minute-of-day conversion and overnight phase preservation in tests.
- Preserve active shared budgets above 30 minutes during upgrade. Store the clamped next-cycle configuration separately from the effective cycle allowance; individual recovery never replaces/refills shared allowance. Fresh cycle, shared reset or lunch reset adopts the new configuration.
- Keep Selected apps and permission/setup rows below the redesigned settings sections. Preserve the 0.5 focused notification icon/limiting-allowance logic and Samsung fallback.
- Theme changes affect only app appearance, never timer configuration, focus allowances, history or lunch eligibility.

## Architecture and editing
- Current implementation uses Java 17 language features and native Android Views, with no Kotlin plugin or Compose.
- Keep platform-independent rules in `engine/`. Use elapsed time for usage/cooldown, local wall time for daily schedules.
- `app/` owns Android permissions, focused-window detection, notifications, persistence, and UI.
- Preserve offline behavior: no INTERNET permission, analytics, account, or external service.
- Do not move timer logic into UI callbacks or weaken tests to get a green build.
- Keep changes limited to the requested behavior and explain the relevant files to this beginner.

## Verification
- Read `docs/ANDROID_STUDIO.md` before changing build configuration.
- Use a complete JDK 21 or 17, including jlink, for Gradle 8.13.
- `sh scripts/verify.sh` runs timing tests, debug APK assembly, and Android lint.
- `:engine:checkRules` is a dependency-free scenario runner; an empty JUnit report does not replace it.
- For timing changes, add meaningful fake-clock scenarios for independent deadlines, delayed callbacks, lunch, process recovery, and migration. Keep the real v0.2/v0.4 serialized fixtures and randomized reference-model coverage.
- Verify notification dismissal/restoration, focused/overview transitions, day selection, dark mode, and large text at runtime; separate emulator evidence from Samsung acceptance.
- Do not equate an APK build with phone acceptance. Report each separately in `docs/VALIDATION.md`.
- For the focused build/test workflow, use `.agents/skills/socialpause-build/SKILL.md`.

- The temporary Status-bar countdown diagnostic was removed after the user confirmed that Samsung Developer options → Live notifications for all apps enabled the working countdown. Keep the actual live countdown and document this phone setting; do not add the diagnostic back without a new request. Android promotion flags alone do not establish that Samsung renders a live pill. Do not force flags or fabricate Samsung support. Start with the uncolorized live format; only if this OS rejects it, try the legacy Android 16 colorized format and accept it only when the OS reports eligibility. Never apply that fallback to an ordinary/dismissed notification.

## Design reference
- V2.1 local exports are the visual reference, subject to the user-approved 0.6 hybrid rules: mint Home summary, app glyph badges, timer/state cards, compact Insights, app-colored weekly stacks, and light/dark tokens. Use real engine data, never reference sample numbers.
- Local design documents are reference material; explicit user behavior changes take precedence.
- FocusResolver owns the testable distinction between system panels and real app switches; read package/window metadata only, never screen text.

## GitHub publishing hygiene
- Keep signing keys, credentials, local SDK paths, real usage data, screenshots/logs, caches and generated artifacts out of Git. `.gitignore` explicitly lists the reviewed guides allowed under docs/. Review new guides before allowing them.
- Keep the Gradle wrapper JAR and synthetic `legacy-v02.bin` / `legacy-v04-*.bin` / `legacy-v05-*.bin` test fixtures. Generated `engine/bin` class files are local output, not source.
- Use portable paths in committed documentation. Check both the current files and reachable history when auditing secrets; ignore rules do not remove prior commits.
- Never rewrite published history or change Git author identity without a specific user request. Do not commit, push or force-add ignored files as part of a local audit.
