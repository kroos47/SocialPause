---
name: socialpause-build
description: Build and verify this SocialPause Android project, including Java timing scenarios, debug APK assembly, lint, and Samsung phone test handoff.
---

Read the project-root AGENTS.md and docs/ANDROID_STUDIO.md. The app uses Java native Views and a separate Java rules module; do not assume Kotlin/Compose.

Use a complete JDK 17 or 21 (including jlink) and the checked-in Gradle 8.13 wrapper. scripts/verify.sh provides a project-local Gradle cache and debug keystore directory. local.properties is machine-specific. SDK 37 does not replace compile SDK 36.

For timing defects, reproduce the sequence in engine/src/test/java/app/socialpause/engine/EngineTests.java with its fake Clock. Include switching or screen lock where relevant. Verify deadlines and remaining budgets, not just a mode label.

Run sh scripts/verify.sh. Investigate the first causal build error. Preserve compiler and lint checks. Report permission or dependency blockers rather than claiming a successful build.

After passing, identify the APK and test/lint reports. Update docs/VALIDATION.md with commands and observed results. Use docs/DEVICE_TESTS.md for phone acceptance: compilation does not establish Samsung Accessibility reliability, live notification promotion, or split-screen correctness.
