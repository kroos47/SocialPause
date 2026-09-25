# Version changes

## Version 0.7.0 — lunch notification recovery and six-hour Stop lock

Start now locks the main Stop button for six continuous elapsed hours. Home explains this before Start and displays “Stop available in HH:MM:SS” while locked. Lunch, cooldowns, Sleep Time and screen-off time count toward the deadline. At the deadline the button enables; monitoring continues until Stop is pressed. The engine/controller rejects early Stop and repeated Start, so the disabled View is not the only enforcement. Lunch controls retain their existing rules and never move this deadline.

Dismissal now uses the original notification surface and monitoring-run identity. Swiping away ordinary lunch, cooldown or idle notifications does not suppress the later focused live countdown. Deliberately dismissing an active live countdown still suppresses promotion until the next Start. Ambiguous dismissal records from older versions are preserved until that Start. App icons, focused chronometers, shared-limit selection, standard live templates and Samsung format handling remain intact.

Ordinary process recreation, app reopening and Recents removal preserve the Stop lock and saved timers. Reboot or a confirmed Android Force stop leaves monitoring stopped until Start. Force-stop evidence must belong to the current process; Android 15+ supplies startup records, and unavailable evidence must not turn an ordinary restart into a reset. Accessibility revocation remains a separate system stop even while the main Stop action is locked. History, settings, lunch phases and daily eligibility are preserved.

An already-running 0.6 installation receives no retrospective lock and keeps its current allowances. Its next Start applies the new behavior. This release is **0.7.0, version code 9**, using the existing signing identity. See `DEVICE_TESTS.md` for acceptance and `VALIDATION.md` for checks actually performed; emulator promotion evidence is separate from Samsung rendering.

## Version 0.6.0 — V2.1 controls with 0.5 timing rules

The Home sun/moon control saves an app-wide appearance preference. Individual / Shared segmented controls replace the old toggle. Settings contains per-app one-minute sliders (Instagram 0–7, other apps 0–12), a compact Shared allowance row and an immediately saved 1–30-minute bottom sheet. Selected apps and permission/setup remain below the new sections.

The Lunch editor now uses hour, minute and AM/PM wheels. Its draft commits only on Save. The 0.5 rule is retained: today only before both the old and new start, with no lunch started today; otherwise tomorrow. Overnight lunch and block deadlines, daily eligibility and manual override behavior remain intact.

Each positive app allowance still starts an independent 60-minute cooldown when exhausted, including in Shared mode. Individual recovery never refills shared time. Only exhausting the shared budget creates a shared 60-minute cooldown and resets all app/shared usage afterward. The V2.1 all-app-cap trigger and indefinite shared-mode cap lock are intentionally not adopted.

Zero allowance blocks while monitoring is running, without starting a cooldown or claiming an unlock time. Lunch and overall Stop grant free use. Zero-safe notification rows and limit messages distinguish No allowance from a real cooldown. Active status chips retain the 0.5 limiting-allowance and Samsung promotion/dismissal handling.

Configured shared time is separate from the effective budget of a running cycle. Upgrading clamps a saved value above 30 minutes for future cycles while preserving the old cycle's usage, budget and deadlines until reset. Per-app defaults initialize to 7/10 without clearing history, lunch records or existing deadlines. The existing signing certificate is reused for installation over 0.5.

See DEVICE_TESTS.md for acceptance and VALIDATION.md for observed results. The V2.1 label names the design package; the Android release is 0.6.0, version code 8.
