# Version 0.4.2 Settings cleanup

Removed the temporary Status-bar countdown Settings row and dialog, the controller accessors, and the promotion sampling used only by that diagnostic. The working notification builder, explicit MM:SS text, drawer chronometer and Android 16 format compatibility fallback are preserved. No timer rules, persistence, permission or dependency changes.

The user confirmed that enabling Samsung **Developer options → Live notifications for all apps** made the status-bar countdown work with 0.4.1. Setup and phone-test documentation now record that requirement for the tested device. The diagnostic's promotion flag was not proof that Samsung's live pill was visible.

# Version 0.4.1 status-bar timer correction

The 0.4 countdown in the drawer worked, but the phone displayed only an app icon in the status bar. 0.4.1 supplies explicit five-character `MM:SS` through API 36 `setShortCriticalText`, updated from the same engine snapshot as the notification. The drawer retains its system chronometer. Idle, cooldown, lunch, Stop and Sleep Time do not supply active chip text. This removes dependence on the chip's automatic chronometer fallback; it does not force Samsung to promote a notification.

Initial Android 16 and later updates have opposite colorization requirements for live eligibility. The code builds the modern uncolorized notification first. Only if this OS rejects that format does it test a separate colorized candidate; it uses the candidate only if `hasPromotableCharacteristics()` succeeds. The fallback is restricted to active, non-dismissed live attempts. It does not fabricate a call/media notification, set system-assigned flags, or add a foreground service. [Original Android 16 implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/core/java/android/app/Notification.java).

In 0.4.1, the Settings → App setup → Status-bar countdown diagnostic showed live permission/channel state and the last active notification's Android format eligibility and system-assigned promotion flag. It reads only SocialPause's notifications; observations remain in memory. Its settings shortcut uses API 36 `ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`, falling back to ordinary app notification settings. Opening it preserves allowances. Dismissed runs still suppress live promotion until manual Start.

There is no SDK/dependency/permission change or new timing migration. See [short critical text](https://developer.android.com/reference/android/app/Notification.Builder#setShortCriticalText(java.lang.String)), [promotion settings](https://developer.android.com/reference/android/provider/Settings#ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS), and [Samsung Now bar settings](https://www.samsung.com/ca/support/mobile-devices/how-to-use-the-now-bar-on-the-lock-screen-of-your-samsung-galaxy-device/). The user subsequently confirmed the countdown works after enabling Samsung Developer options → Live notifications for all apps. This observation does not certify every Samsung firmware or notification state.

# Version 0.4.0 implementation

## Design

The local `SocialPause-V2-designs` exports supplied the visual reference. Screens use the exported light/dark palette, native Roboto/sans-serif typography, mint Home summary, actual app glyphs, timer/state cards, compact weekly summary, chart grid and legend, and per-app Insights rows. Settings leads with allowances; permission controls remain available below the schedule. The limit overlay centers the app icon, explanation, cooldown and Home button. No reference sample data ships with the app.

Weekly selection filters the total and app breakdown. Tap the selected day again or tap outside the day targets to restore weekly totals. A drag/scroll does not clear selection. The selected-summary card provides the same action for accessibility users without adding a separate visible button. Future days are disabled; empty past/today bars remain selectable.

## Focus through system panels

`SocialAccessibilityService` collects window type, layer, focus and package metadata. `FocusResolver` remembers the last real app while a SystemUI panel is in front. Android can redact a notification panel's root/package; a focused TYPE_SYSTEM window remains a system-panel signal in that case. During shade animations, a stale application window can lose its root before the window list updates; the current active root is used as a fallback so this transition does not erase focus. No screen text is read, and the service does not claim to be a disability accessibility tool.

The notification shade and Quick Settings keep charging the underlying app. Home, Settings, another app, Recents, screen lock, missing window evidence, interruption, or stopped monitoring clear/replace that identity. Keyboard windows do not replace the app. Exhaustion under the shade starts the same independent cooldown and dismisses the shade/redirects Home immediately. OEM split-screen, pop-up and Recents metadata still require Samsung acceptance.

## Notifications

Only an actively used selected app requests a live status chip. Its app glyph, standard Android ProgressStyle and system countdown chronometer stay attached to that app while the drawer is open. Idle, cooldown and lunch states use the SocialPause small icon without live promotion or chip text. The expanded ordinary overview renders a row and progress bar for each selected app. Android controls custom-view height; large app lists/font sizes may need opening the dashboard to see every row.

Android Live Updates require a standard supported template; custom RemoteViews cannot be promoted. Consequently the active notification follows Android's system layout rather than reproducing the SVG pixel for pixel. The system chronometer is supplied for a smoothly updating chip, but Samsung determines whether and how it appears. See [Live Update requirements](https://developer.android.com/develop/ui/views/notifications/live-update) and [Notification.Builder](https://developer.android.com/reference/android/app/Notification.Builder).

Ongoing/restoration behavior, Sleep Time and manual Stop remain. After dismissal the ordinary notification may return, with promotion suppressed until manual Start. There is no new foreground service, permission, network access or fake call/media notification.

## Persistence and rules

0.4 uses the same timer serialization/schema as 0.3, preserving its allowances, individual cooldowns, history, settings and run state on update. It introduces no new migration/reset. The existing one-time pre-0.3 migration and optional reboot reset remain. Instagram still gets seven accumulated minutes, other apps ten, and each has its own continuous one-hour cooldown. Lunch and Sleep Time behavior are unchanged.
