# Validation — SocialPause 0.6.0

Validated on 2026-09-23 on the Apple Silicon development Mac and an isolated Android 16 ARM64 emulator. Physical Samsung acceptance is separate.

## Build and compatibility

- `sh scripts/verify.sh`: **BUILD SUCCESSFUL**, 14 seconds, 51 actionable tasks (33 executed, 18 up-to-date).
- **129 timing/presentation scenarios passed**, including **20,000 randomized reference-model transitions**. Coverage includes configured/zero limits, independent/shared exhaustion, delayed callbacks, pause/focus behavior, lunch overrides and schedule boundaries, history, and serialization recovery.
- Android lint: **0 errors, 1 existing warning** about a newer Gradle version. No checks suppressed.
- APK: **0.6.0**, version code **8**, compile/target SDK 36, min SDK 33; no INTERNET permission. Verified signature matches the previously delivered APK, enabling an in-place update.
- Synthetic fixtures serialized by the original 0.5 engine preserve partial shared usage, independent cooldowns, stopped state, active manual lunch and post-lunch cooldown. Existing genuine 0.2/0.4 migration fixtures remain covered.
- Upgraded the emulator from the actual 0.5 APK with a synthetic active 45-minute shared cycle. Its 32 remaining minutes, partial app usage and running independent cooldown survived; configured future shared allowance became 30 minutes. A separate original-engine 60-minute serialization check also preserved the active budget and verified that only group reset adopted the new 30-minute configuration.

## Android runtime checks

The emulator uses synthetic state only. Clock and Calendar substitute for additional selected apps available offline. Production timer constants were not shortened; near-boundary fixtures accelerate waiting for exhaustion.

- Appearance: switched and persisted light/dark mode, including process recreation, without changing usage or cooldown deadlines. Visually inspected Home, app allowance settings, shared sheet and Lunch wheels in both themes and at 150% font scale. Save/Cancel/Done remained reachable.
- App sliders: zero and maximum values persisted. Shared slider saved 1 and 30 minutes immediately; Back retained changes. Shared duration was disabled in Individual mode, and mode/allowance editing was disabled while running.
- Zero allowance: blocked the app with No allowance and no invented unlock time. An all-zero selection remained blocked without repeatedly starting cooldowns; the notification described No allowance. Lunch and overall Stop still allowed unrestricted use.
- Positive app cap in Shared mode: exhausted a one-minute Clock allowance and observed an independent 60-minute cooldown while four shared minutes remained. It did not start a group block.
- Shared exhaustion: exhausted the remaining shared budget before the app cap and observed a distinct shared cooldown across selected apps.
- Focused notification: shared time limited the active countdown; the notification requested live promotion. Opening the drawer continued counting the underlying app; Home and screen lock paused usage.
- Lunch wheels: validated 12:00 AM, 12:00 PM and 11:59 PM conversion. Cancel and outside dismissal discarded edited drafts. The scheduling engine retains 0.5 conditional today/tomorrow rules, covered by fake-clock tests; the design handoff's always-tomorrow policy was not adopted.
- App picker: added Calendar, saved once, reopened with it selected, then cancelled a draft removal without changing persisted selection. Previous 0.5 picker regression evidence additionally covers repeated opens and empty-selection validation; YouTube remains a phone check.
- Accessibility: revocation stopped monitoring and removed its notification; re-enabling did not restart it. Previous 0.5 runtime coverage also exercised revocation during independent cooldown and lunch; those controller paths were retained.
- No SocialPause crash or ANR appeared in the emulator crash log or app exit history.

UI inspection used a temporary automation helper with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`, so it did not disconnect the monitoring service. The helper, emulator image and synthetic runtime state are not app dependencies or source-archive contents.

## Delivery and device limits

`artifacts/SocialPause.apk` and `SocialPause-debug.apk` are identical personal-install builds. `SocialPause-source.zip` contains current source, guides and synthetic migration fixtures, excluding signing keys, SDK paths, caches and build output. Build/lint results and isolated emulator evidence are under `artifacts/v0.6.0/`; checksums are in `artifacts/SHA256SUMS.txt`.

Open the main repository root in Android Studio. The old extracted `artifacts/SocialPauseBuild` folder is not the working project and has not been refreshed.

Keep Samsung **Developer options → Live notifications for all apps** enabled as previously confirmed on the user's phone. Emulator success does not establish Samsung live-pill rendering, multi-window behavior, battery management or a full real-time cooldown. Follow DEVICE_TESTS.md for physical Galaxy S24 Ultra acceptance, including YouTube selection, theme/wheel appearance and upgrade behavior.


## Release automation preparation — 2026-09-25

No Android application or timing-engine source changed in this preparation.

- `sh scripts/verify.sh` passed again on the local JDK 21: **129 scenarios**, **20,000 reference-model transitions**, successful APK assembly, and lint **0 errors / 1 existing warning**. The run finished in 7 seconds.
- The GitHub workflow passed **actionlint 1.7.12** validation. Action commit hashes were resolved from the official action repositories, and the validator download was checked against its published SHA-256 checksum.
- Five isolated packaging-guard checks rejected invalid tags, a version mismatch, a missing original key, an uncommitted tree, and an existing tag pointing to a different commit. The real repository's dirty-tree check also rejected packaging before commit.
- Gitleaks found no leaks in the proposed workflow, release helper, notes and guides. The committed signing fingerprint is public certificate metadata; the private key remains ignored and local.
- GitHub-hosted execution and release publication are **pending**. The user chose to finish local preparation and sign in to GitHub CLI later. Local/static checks do not claim that the Ubuntu CI jobs have run. The workflow is configured to test both JDK 17 and 21 once pushed.

`scripts/package-release.py` records its own final verification log under ignored `artifacts/releases/v0.6.0/`, verifies APK identity and the original signing certificate, and packages four reviewed release assets. Publishing instructions are in PUBLISHING.md. No emulator or ADB server was started for this CI/release work.


## GitHub SDK setup correction — 2026-09-25

The first GitHub Android job failed before compilation: `sdkmanager: command not found` (exit 127). The original workflow assumed the runner exposed that tool on PATH. Static workflow validation cannot verify installed tools on a hosted runner.

The workflow now uses a commit-pinned `android-actions/setup-android` step to bootstrap command-line tools, accept SDK licenses, install platform 36 / Build-Tools 36.0.0, and export the SDK paths. The command-line tools build is explicitly pinned to 15859902. Timing-engine and Android app source are unchanged.

The corrected workflow passes actionlint. The release signing fingerprint was compared directly with the prepared APK's public certificate and matches; no private signing files are tracked. Final local build/signature/checksum results are recorded by `scripts/package-release.py` in `artifacts/releases/v0.6.0/build.log` and `BUILD-INFO.txt`. The corrected GitHub-hosted run still requires pushing this fix; a rerun of the old commit would use the old workflow.
