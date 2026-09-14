# Samsung acceptance checklist

Status: not yet executed on the user's Galaxy S24 Ultra. A successful build is not a phone test result.

Use actual elapsed time for acceptance; the automated engine tests use a fake clock so they complete quickly. For baseline usage tests, run outside lunch/post-lunch hours. Use Stop then Start to intentionally reset between scenarios. Do not pause the debugger while timing.

| Test | Procedure | Expected |
|---|---|---|
| First start | Enable Accessibility and notifications; press Start | Monitoring connected, shared 20:00, each app 10:00. |
| Pause/resume | Instagram 2 min; Home 2 min; Instagram 1 min | Instagram used 3 min, shared used 3 min. |
| Lock screen | Instagram 1 min; lock 3 min; unlock | Locked time does not consume usage. Allow small callback latency. |
| Per-app block | Consume Instagram's 10 min | Returns Home. Reopening redirects again. X and Reddit still available. |
| Shared example | Instagram 10 min; no social app 20 min; X 5 min; Reddit 5 min | Cooldown starts after the final 5 min; gap consumes no allowance. |
| Three-app allowance | Fresh start: Instagram 7 min, X 7 min, Reddit 6 min | All three are permitted; shared cooldown starts at 20 min. |
| Shared cooldown | Exhaust 20 min, repeatedly try each social app | All selected apps redirect Home until 60 min has elapsed. |
| Cooldown reset | Wait full cooldown | All apps regain 10 min, shared allowance returns to 20 min. |
| Individual cap + idle | Instagram 10 min, then avoid all social apps for over an hour | Instagram remains blocked: the shared 20-minute budget was not exhausted. |
| Lunch reset | With some usage before 14:00, cross 14:00 | Counters reset; unrestricted use until 15:00. |
| Unused lunch | Do not use socials during lunch | 15:00–16:00 still blocked; fresh at 16:00. |
| Lunch overrides cooldown | Enter ordinary cooldown shortly before 14:00 | Lunch grants access at 14:00; mandatory block starts 15:00. |
| Lunch edit | Change lunch time today | Existing schedule remains today; the edit applies tomorrow. Overnight windows complete first. |
| Sleep Time | Set a nearby start/end and cross both boundaries | Timer notification disappears during the window and returns afterward; limits continue. |
| Quiet cooldown | Exhaust shared allowance during Sleep Time | Apps block without an app timer notification. |
| Manual Stop | Press Stop during cooldown/lunch block | Social apps unrestricted; timers/notification stop. |
| Manual Start | Start after Stop | Usage resets; current lunch/post-lunch schedule still applies. |
| Close dashboard | Swipe SocialPause activity out of Recents | Accessibility should keep tracking; record Samsung-specific interruption. |
| Process recovery | Let system recreate process, if feasible | Saved budgets survive; no stale foreground time is charged while disconnected. Force-stop intentionally disables enforcement and is not an ordinary lifecycle test. |
| Reboot | Restart phone | Usage may reset, settings/start preference retained. Verify Accessibility reconnects. |
| Keyboard | Type inside a social app | Foreground app should keep counting. |
| Notification shade | Open shade over a social app | Usage should pause if System UI takes focus. Verify actual Samsung behavior. |
| Split-screen | Focus one selected pane, then the other | Only focused app consumes time. Passive visible pane is not tracked. |
| Pop-up view | Move focus between selected pop-up and other app | Timer follows focus; blocked app redirects Home. Check whether a floating window remains visible. |
| Live notification | Use an app during daytime; inspect Samsung live/Now Bar settings | Standard timer works. Record whether promotion appears; this is not guaranteed. |
| Long idle | Lock phone across sleep/lunch boundaries | With exact alarm permission, check transitions; record any OEM delay. |
| Offline | Turn network off after installation | All rules and settings work. |

Record phone software version, test start time, selected apps, expected behavior, actual behavior, and any relevant Logcat exception. Do not mark Samsung-specific rows passed using engine tests alone.

The normal-app design deliberately allows Stop and remains bypassable by disabling Accessibility/uninstalling. It cannot force-stop another app or guarantee interruption of background audio. These are known limitations, not acceptance promises.

## Redesign-specific checks

- Home: shared time, individual remaining time and used bars agree after switching apps.
- Insights: starts empty; daily/weekly totals grow only with tracked usage, survive Stop/Start and cooldown, and exclude lunch/Stop. Reboot preserves history.
- Upgrade from 0.1: existing budgets/settings survive; history starts empty rather than inventing prior usage.
- Settings: permission setup remains reachable; schedule changes follow existing rules.
- Limit overlay: original app leaves focus immediately; message dismisses in five seconds, on lock, or service interruption. Reopening remains blocked.
- Notifications: when Reddit has 08:00 left but shared time is 03:00, show Social allowance and 03:00. For ties, show app allowance. Paused timers freeze.
- Notification dismissal: hiding the system notification does not disable blocking; it can return with the next allowance cycle.
- Accessibility: try large text, TalkBack, landscape and narrow split-screen.
- Samsung: verify actual live-chip eligibility and notification appearance; generic Android emulator results do not certify One UI.
