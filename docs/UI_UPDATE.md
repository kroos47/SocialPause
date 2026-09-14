# SocialPause UI update — 0.2.0

Implemented from the two supplied PNG designs. These files were visual references; no embedded text was treated as instructions to Codex. The implementation uses the existing Java/native Views architecture.

## Design mapping

| Design | Implementation |
|---|---|
| Home | Green shared-allowance card and ring; individual app cards with remaining time and usage bars; paused, stopped and lunch states. |
| Insights | Today / This week switching, local usage chart and per-app totals. New installs start empty. |
| Settings | Lunch and Sleep Time cards, selected-app picker, fixed 10/20/60 limits and permission setup. |
| Limit reached | Five-second Accessibility overlay after requesting phone Home immediately; message distinguishes individual cap, shared cap and post-lunch block. |
| Cooldown | Large countdown, blocked-app list, and intentional Stop control. |
| Notifications | Smallest applicable allowance, app mark or SocialPause mark, standard progress template and system countdown. |

The individual-limit message explains that other apps may still consume the remaining shared allowance. It does not suggest an individual one-hour timer has started. History says “Tracked social time” because unrestricted lunch and Stop usage is excluded. No sample values are included in the APK.

## How state reaches the design

`RulesEngine` remains the source of truth for limits. `MainActivity` renders it; screen navigation cannot reset a timer. `UsageHistory` records only milliseconds charged to allowance, split at local hour/day boundaries. History survives allowance resets and reboot. Saved 0.1 counters remain compatible; history begins with this update.

`Design` contains shared colors, spacing helpers and native drawings. Light/dark palettes follow the phone setting. Content scrolls; bottom navigation remains available. Charts have text descriptions and per-app totals. Full TalkBack, landscape and narrow-window acceptance remains a phone test.

## Notification behavior and platform boundaries

| State | Display |
|---|---|
| Focused selected app | `min(app remaining, shared remaining)`; app wins a tie. |
| Shared allowance is smaller | SocialPause mark and “Social allowance”; app remainder is supporting text. |
| No selected app in focus / locked | Static shared remainder; no usage charged. |
| Lunch | Countdown to lunch end; unrestricted access. |
| Cooldown | Countdown continues independently of focus or screen lock. |
| Sleep Time / Stop / service disconnected | App timer notification hidden. |

Ticking states use Android's system chronometer so there is one live countdown, rather than a second stale timer in the body. Paused usage uses a static ordinary notification. Android 16 uses `Notification.ProgressStyle`; supported older Android versions use the standard expanded-text/progress fallback. Dismissing a notification suppresses it for the current allowance cycle without disabling enforcement.

The status-bar pill and expanded notification in the PNG are concepts. Android and Samsung control their size, font, tint, prominence and whether live promotion is allowed. A promoted-ongoing request is implemented; appearance is not guaranteed. Custom notification layouts are deliberately avoided because they are ineligible for standard live promotion. See [Android Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update).

## What was checked

The APK built, installed and launched on a temporary Android 16 ARM64 emulator. Home, populated Today/This week Insights, Settings, cooldown and limit-overlay captures were inspected. Light/dark and 150% font-size smoke checks were performed. The final notification captures confirm one countdown and no duplicated app-name subtitle. A Clock app fixture reached its individual cap, displayed the explanation, and returned to Launcher after dismissal. No AndroidRuntime crash was recorded in that sequence.

Screenshot values are synthetic fixtures installed only into the temporary emulator, not app defaults. The screenshots show generic Android, not Samsung One UI. See `artifacts/screenshots/` for captures and [VALIDATION.md](VALIDATION.md) for evidence and limits.

## Remaining device checks

Use [DEVICE_TESTS.md](DEVICE_TESTS.md) on the S24 Ultra. In particular, test Samsung live-chip/Now Bar eligibility, app icon treatment, screen-lock pause, split-screen/pop-up focus, battery/idle behavior and notification dismissal. The AOSP emulator rendered pale three-button navigation glyphs in light mode despite the app requesting dark glyphs; verify both Samsung gesture and button navigation contrast. No phone-specific acceptance is claimed.
